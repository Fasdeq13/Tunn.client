/*
 * tunn-helper: small privileged helper for Tunn on Linux (started through pkexec).
 *
 * It runs a userspace WireGuard / AmneziaWG implementation (amneziawg-go), configures it over the
 * UAPI socket, sets addresses, routes (wg-quick style policy routing with fwmark) and DNS, then waits
 * until the stop file appears or the parent process exits, and tears everything down again.
 *
 *   tunn-helper run  <amneziawg-go> <conf> <stopfile> <parent-pid> <ifname> <ip> <resolvectl|->
 *   tunn-helper plan <conf> <ifname>          (prints what would be done, needs no privileges)
 *
 * No shell is ever involved: every external program is started with execv and validated arguments.
 * Hook options of the config (PreUp, PostUp, ...) are ignored on purpose.
 */
#define _GNU_SOURCE
#include <arpa/inet.h>
#include <ctype.h>
#include <errno.h>
#include <fcntl.h>
#include <netdb.h>
#include <signal.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <unistd.h>

#define MAXN 64
#define FWMARK 51820
#define TABLE "51820"

struct peer {
    char pub[65];
    char psk[65];
    char endpoint[300];
    char keepalive[16];
    char allowed[MAXN][64];
    int nallowed;
};

struct conf {
    char priv[65];
    char listen_port[16];
    char extra[MAXN][96]; /* amnezia obfuscation parameters, already as "key=value" */
    int nextra;
    char addr[MAXN][64];
    int naddr;
    char dns[MAXN][64];
    int ndns;
    int mtu;
    struct peer peers[16];
    int npeers;
};

static int g_dry;
static char g_ip[512] = "ip";
static char g_resolvectl[512] = "";
static pid_t g_child;
static char g_if[32];
static char g_dir[128];
static int g_v4_default, g_v6_default;

static void die(const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    vfprintf(stderr, fmt, ap);
    va_end(ap);
    fputc('\n', stderr);
    exit(1);
}

/* ---------- validation helpers ---------- */

static int valid_ifname(const char *s) {
    size_t n = strlen(s);
    if (n < 1 || n > 15) return 0;
    for (size_t i = 0; i < n; i++)
        if (!isalnum((unsigned char)s[i]) && !strchr("_=+.-", s[i])) return 0;
    return 1;
}

/* "1.2.3.4", "1.2.3.4/24", "fd00::1/64"; returns 4, 6 or 0 */
static int valid_cidr(const char *s, int allow_prefix) {
    char buf[64];
    if (strlen(s) >= sizeof buf) return 0;
    strcpy(buf, s);
    char *slash = strchr(buf, '/');
    int pfx = -1;
    if (slash) {
        if (!allow_prefix) return 0;
        *slash++ = 0;
        char *end;
        long v = strtol(slash, &end, 10);
        if (*slash == 0 || *end != 0 || v < 0 || v > 128) return 0;
        pfx = (int)v;
    }
    unsigned char a[16];
    if (inet_pton(AF_INET, buf, a) == 1) return (pfx < 0 || pfx <= 32) ? 4 : 0;
    if (inet_pton(AF_INET6, buf, a) == 1) return 6;
    return 0;
}

static int b64val(int c) {
    if (c >= 'A' && c <= 'Z') return c - 'A';
    if (c >= 'a' && c <= 'z') return c - 'a' + 26;
    if (c >= '0' && c <= '9') return c - '0' + 52;
    if (c == '+') return 62;
    if (c == '/') return 63;
    return -1;
}

/* 44-char WireGuard key (base64 of 32 bytes) -> 64 hex chars */
static int key_to_hex(const char *b64, char out[65]) {
    if (strlen(b64) != 44 || b64[43] != '=') return 0;
    unsigned char raw[33];
    int n = 0;
    for (int i = 0; i < 44; i += 4) {
        int v[4], pad = 0;
        for (int j = 0; j < 4; j++) {
            if (b64[i + j] == '=') { v[j] = 0; pad++; }
            else if ((v[j] = b64val(b64[i + j])) < 0) return 0;
        }
        raw[n++] = (unsigned char)((v[0] << 2) | (v[1] >> 4));
        if (pad < 2) raw[n++] = (unsigned char)((v[1] << 4) | (v[2] >> 2));
        if (pad < 1) raw[n++] = (unsigned char)((v[2] << 6) | v[3]);
    }
    if (n != 32) return 0;
    for (int i = 0; i < 32; i++) sprintf(out + 2 * i, "%02x", raw[i]);
    return 1;
}

static int all_digits(const char *s) {
    if (!*s) return 0;
    for (; *s; s++)
        if (!isdigit((unsigned char)*s)) return 0;
    return 1;
}

static int amnezia_key(const char *k) {
    static const char *names[] = {"jc", "jmin", "jmax", "s1", "s2", "s3", "s4", "h1", "h2", "h3", "h4",
                                  "i1", "i2", "i3", "i4", "i5", "j1", "j2", "j3", "itime"};
    for (size_t i = 0; i < sizeof names / sizeof *names; i++)
        if (strcmp(k, names[i]) == 0) return 1;
    return 0;
}

/* ---------- config parsing ---------- */

static char *trim(char *s) {
    while (isspace((unsigned char)*s)) s++;
    char *e = s + strlen(s);
    while (e > s && isspace((unsigned char)e[-1])) *--e = 0;
    return s;
}

static void split_list(const char *val, char dst[][64], int *n, int (*check)(const char *), const char *what) {
    char buf[1024];
    if (strlen(val) >= sizeof buf) die("%s: слишком длинное значение", what);
    strcpy(buf, val);
    for (char *tok = strtok(buf, ","); tok; tok = strtok(NULL, ",")) {
        tok = trim(tok);
        if (!*tok) continue;
        if (*n >= MAXN) die("%s: слишком много значений", what);
        if (strlen(tok) >= 64) die("%s: неверное значение", what);
        if (check && !check(tok)) die("%s: неверное значение '%s'", what, tok);
        strcpy(dst[(*n)++], tok);
    }
}

static int chk_cidr(const char *s) { return valid_cidr(s, 1) != 0; }
static int chk_addr(const char *s) { return valid_cidr(s, 0) != 0; }

static void parse_conf(const char *path, struct conf *c) {
    struct stat st;
    if (lstat(path, &st) != 0 || !S_ISREG(st.st_mode)) die("конфиг не найден или не обычный файл");
    FILE *f = fopen(path, "r");
    if (!f) die("не удалось открыть конфиг");
    memset(c, 0, sizeof *c);
    c->mtu = 1280;
    char line[2048];
    int sect = 0; /* 1 interface, 2 peer */
    struct peer *p = NULL;
    while (fgets(line, sizeof line, f)) {
        char *h = strchr(line, '#');
        if (h) *h = 0;
        char *s = trim(line);
        if (!*s) continue;
        if (*s == '[') {
            if (strncasecmp(s, "[interface]", 11) == 0) sect = 1;
            else if (strncasecmp(s, "[peer]", 6) == 0) {
                if (c->npeers >= 16) die("слишком много peer");
                p = &c->peers[c->npeers++];
                memset(p, 0, sizeof *p);
                sect = 2;
            } else die("неизвестная секция");
            continue;
        }
        char *eq = strchr(s, '=');
        if (!eq) continue;
        *eq = 0;
        char *k = trim(s);
        char *v = trim(eq + 1);
        for (char *q = k; *q; q++) *q = (char)tolower((unsigned char)*q);
        if (sect == 1) {
            if (!strcmp(k, "privatekey")) {
                if (!key_to_hex(v, c->priv)) die("неверный PrivateKey");
            } else if (!strcmp(k, "listenport")) {
                if (!all_digits(v) || strlen(v) > 5) die("неверный ListenPort");
                strcpy(c->listen_port, v);
            } else if (!strcmp(k, "address")) split_list(v, c->addr, &c->naddr, chk_cidr, "Address");
            else if (!strcmp(k, "dns")) {
                /* DNS may also list search domains; keep only IP addresses */
                char buf[1024];
                if (strlen(v) < sizeof buf) {
                    strcpy(buf, v);
                    for (char *tok = strtok(buf, ","); tok; tok = strtok(NULL, ",")) {
                        tok = trim(tok);
                        if (valid_cidr(tok, 0) && c->ndns < MAXN) strcpy(c->dns[c->ndns++], tok);
                    }
                }
            } else if (!strcmp(k, "mtu")) {
                if (!all_digits(v)) die("неверный MTU");
                c->mtu = atoi(v);
                if (c->mtu < 576 || c->mtu > 9000) die("неверный MTU");
            } else if (amnezia_key(k)) {
                /* values are numbers or i1..i5 style tags; allow a conservative character set only */
                for (char *q = v; *q; q++)
                    if (!isalnum((unsigned char)*q) && !strchr("<>-_=+/.:, ", *q)) die("неверное значение %s", k);
                if (c->nextra >= MAXN) die("слишком много параметров");
                snprintf(c->extra[c->nextra++], sizeof c->extra[0], "%s=%s", k, v);
            }
        } else if (sect == 2 && p) {
            if (!strcmp(k, "publickey")) {
                if (!key_to_hex(v, p->pub)) die("неверный PublicKey");
            } else if (!strcmp(k, "presharedkey")) {
                if (!key_to_hex(v, p->psk)) die("неверный PresharedKey");
            } else if (!strcmp(k, "endpoint")) {
                if (strlen(v) >= sizeof p->endpoint) die("неверный Endpoint");
                strcpy(p->endpoint, v);
            } else if (!strcmp(k, "persistentkeepalive")) {
                if (!all_digits(v) || strlen(v) > 5) die("неверный PersistentKeepalive");
                strcpy(p->keepalive, v);
            } else if (!strcmp(k, "allowedips")) split_list(v, p->allowed, &p->nallowed, chk_cidr, "AllowedIPs");
        }
    }
    fclose(f);
    if (!c->priv[0]) die("в конфиге нет PrivateKey");
    if (c->npeers == 0) die("в конфиге нет [Peer]");
    for (int i = 0; i < c->npeers; i++)
        if (!c->peers[i].pub[0]) die("у peer нет PublicKey");
}

/* "host:port" or "[v6]:port" -> numeric "ip:port" / "[ip]:port" for the UAPI */
static int resolve_endpoint(const char *ep, char *out, size_t outn) {
    char host[256];
    const char *port;
    if (ep[0] == '[') {
        const char *e = strchr(ep, ']');
        if (!e || e[1] != ':' || (size_t)(e - ep - 1) >= sizeof host) return 0;
        memcpy(host, ep + 1, (size_t)(e - ep - 1));
        host[e - ep - 1] = 0;
        port = e + 2;
    } else {
        const char *c = strrchr(ep, ':');
        if (!c || (size_t)(c - ep) >= sizeof host) return 0;
        memcpy(host, ep, (size_t)(c - ep));
        host[c - ep] = 0;
        port = c + 1;
    }
    if (!all_digits(port) || strlen(port) > 5) return 0;
    for (char *q = host; *q; q++)
        if (!isalnum((unsigned char)*q) && !strchr(".-:_", *q)) return 0;
    struct addrinfo hints, *res = NULL;
    memset(&hints, 0, sizeof hints);
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_DGRAM;
    if (getaddrinfo(host, port, &hints, &res) != 0 || !res) return 0;
    char ip[INET6_ADDRSTRLEN];
    int ok = 0;
    if (res->ai_family == AF_INET) {
        inet_ntop(AF_INET, &((struct sockaddr_in *)res->ai_addr)->sin_addr, ip, sizeof ip);
        snprintf(out, outn, "%s:%s", ip, port);
        ok = 1;
    } else if (res->ai_family == AF_INET6) {
        inet_ntop(AF_INET6, &((struct sockaddr_in6 *)res->ai_addr)->sin6_addr, ip, sizeof ip);
        snprintf(out, outn, "[%s]:%s", ip, port);
        ok = 1;
    }
    freeaddrinfo(res);
    return ok;
}

/* ---------- UAPI ---------- */

static size_t build_uapi(const struct conf *c, char *buf, size_t n, int resolve) {
    size_t o = 0;
#define ADD(...) do { if (o < n) o += (size_t)snprintf(buf + o, n - o, __VA_ARGS__); } while (0)
    ADD("set=1\nprivate_key=%s\n", c->priv);
    if (c->listen_port[0]) ADD("listen_port=%s\n", c->listen_port);
    ADD("fwmark=%d\n", FWMARK);
    for (int i = 0; i < c->nextra; i++) ADD("%s\n", c->extra[i]);
    ADD("replace_peers=true\n");
    for (int i = 0; i < c->npeers; i++) {
        const struct peer *p = &c->peers[i];
        ADD("public_key=%s\n", p->pub);
        if (p->psk[0]) ADD("preshared_key=%s\n", p->psk);
        if (p->endpoint[0]) {
            char ep[300];
            if (resolve) {
                if (!resolve_endpoint(p->endpoint, ep, sizeof ep)) die("не удалось разобрать Endpoint '%s'", p->endpoint);
            } else snprintf(ep, sizeof ep, "%s", p->endpoint);
            ADD("endpoint=%s\n", ep);
        }
        if (p->keepalive[0]) ADD("persistent_keepalive_interval=%s\n", p->keepalive);
        ADD("replace_allowed_ips=true\n");
        for (int j = 0; j < p->nallowed; j++) ADD("allowed_ip=%s\n", p->allowed[j]);
    }
    ADD("\n");
#undef ADD
    return o;
}

static void uapi_send(const char *ifname, const char *data, size_t len) {
    static const char *dirs[] = {"/var/run/amneziawg", "/var/run/wireguard", "/run/amneziawg", "/run/wireguard"};
    int fd = -1;
    for (size_t i = 0; i < sizeof dirs / sizeof *dirs && fd < 0; i++) {
        struct sockaddr_un a;
        memset(&a, 0, sizeof a);
        a.sun_family = AF_UNIX;
        snprintf(a.sun_path, sizeof a.sun_path, "%s/%s.sock", dirs[i], ifname);
        int s = socket(AF_UNIX, SOCK_STREAM, 0);
        if (s < 0) continue;
        if (connect(s, (struct sockaddr *)&a, sizeof a) == 0) fd = s;
        else close(s);
    }
    if (fd < 0) die("не удалось подключиться к сокету amneziawg-go");
    size_t off = 0;
    while (off < len) {
        ssize_t w = write(fd, data + off, len - off);
        if (w <= 0) die("ошибка записи в сокет amneziawg-go");
        off += (size_t)w;
    }
    char resp[256];
    size_t r = 0;
    ssize_t k;
    while (r < sizeof resp - 1 && (k = read(fd, resp + r, sizeof resp - 1 - r)) > 0) {
        r += (size_t)k;
        resp[r] = 0;
        if (strstr(resp, "\n\n")) break;
    }
    resp[r] = 0;
    close(fd);
    if (!strstr(resp, "errno=0")) die("amneziawg-go отклонил настройки: %s", resp);
}

/* ---------- running programs ---------- */

static int run_prog(const char *prog, int ignore_fail, ...) {
    char *argv[32];
    int n = 0;
    va_list ap;
    va_start(ap, ignore_fail);
    argv[n++] = (char *)prog;
    const char *a;
    while ((a = va_arg(ap, const char *)) != NULL && n < 30) argv[n++] = (char *)a;
    va_end(ap);
    argv[n] = NULL;
    if (g_dry) {
        for (int i = 0; i < n; i++) printf("%s%s", i ? " " : "", argv[i]);
        printf("\n");
        return 0;
    }
    pid_t pid = fork();
    if (pid < 0) die("fork: %s", strerror(errno));
    if (pid == 0) {
        int dn = open("/dev/null", O_RDWR);
        if (dn >= 0) { dup2(dn, 0); dup2(dn, 1); }
        execv(prog, argv);
        _exit(127);
    }
    int st = 0;
    waitpid(pid, &st, 0);
    int ok = WIFEXITED(st) && WEXITSTATUS(st) == 0;
    if (!ok && !ignore_fail) die("команда не выполнилась: %s %s %s", prog, argv[1] ? argv[1] : "", argv[2] ? argv[2] : "");
    return ok;
}

#define IP(...) run_prog(g_ip, 0, __VA_ARGS__, (char *)NULL)
#define IPQ(...) run_prog(g_ip, 1, __VA_ARGS__, (char *)NULL)

/* ---------- network setup / teardown ---------- */

static void setup_network(const struct conf *c) {
    char mtu[16];
    snprintf(mtu, sizeof mtu, "%d", c->mtu);
    for (int i = 0; i < c->naddr; i++) IP(valid_cidr(c->addr[i], 1) == 6 ? "-6" : "-4", "address", "add", c->addr[i], "dev", g_if);
    IP("link", "set", "mtu", mtu, "up", "dev", g_if);
    for (int i = 0; i < c->npeers; i++) {
        for (int j = 0; j < c->peers[i].nallowed; j++) {
            const char *a = c->peers[i].allowed[j];
            int fam = valid_cidr(a, 1);
            if (strcmp(a, "0.0.0.0/0") == 0) g_v4_default = 1;
            else if (strcmp(a, "::/0") == 0) g_v6_default = 1;
            else IP(fam == 6 ? "-6" : "-4", "route", "add", a, "dev", g_if);
        }
    }
    if (g_v4_default) {
        IP("-4", "route", "add", "0.0.0.0/0", "dev", g_if, "table", TABLE);
        IP("-4", "rule", "add", "not", "fwmark", TABLE, "table", TABLE);
        IP("-4", "rule", "add", "table", "main", "suppress_prefixlength", "0");
    }
    if (g_v6_default) {
        IP("-6", "route", "add", "::/0", "dev", g_if, "table", TABLE);
        IP("-6", "rule", "add", "not", "fwmark", TABLE, "table", TABLE);
        IP("-6", "rule", "add", "table", "main", "suppress_prefixlength", "0");
    }
    if (c->ndns > 0 && g_resolvectl[0]) {
        char *argv[40];
        int n = 0;
        argv[n++] = g_resolvectl;
        argv[n++] = "dns";
        argv[n++] = g_if;
        for (int i = 0; i < c->ndns && n < 36; i++) argv[n++] = (char *)c->dns[i];
        if (g_dry) {
            for (int i = 0; i < n; i++) printf("%s%s", i ? " " : "", argv[i]);
            printf("\n");
        } else {
            pid_t pid = fork();
            if (pid == 0) {
                int dn = open("/dev/null", O_RDWR);
                if (dn >= 0) { dup2(dn, 0); dup2(dn, 1); }
                argv[n] = NULL;
                execv(g_resolvectl, argv);
                _exit(127);
            }
            int st;
            waitpid(pid, &st, 0);
        }
        run_prog(g_resolvectl, 1, "domain", g_if, "~.", (char *)NULL);
        run_prog(g_resolvectl, 1, "default-route", g_if, "yes", (char *)NULL);
    }
}

static void teardown_network(void) {
    if (g_v4_default) {
        IPQ("-4", "rule", "del", "not", "fwmark", TABLE, "table", TABLE);
        IPQ("-4", "rule", "del", "table", "main", "suppress_prefixlength", "0");
        IPQ("-4", "route", "flush", "table", TABLE);
    }
    if (g_v6_default) {
        IPQ("-6", "rule", "del", "not", "fwmark", TABLE, "table", TABLE);
        IPQ("-6", "rule", "del", "table", "main", "suppress_prefixlength", "0");
        IPQ("-6", "route", "flush", "table", TABLE);
    }
    if (g_resolvectl[0]) run_prog(g_resolvectl, 1, "revert", g_if, (char *)NULL);
    IPQ("link", "del", "dev", g_if);
}

static void cleanup(void) {
    static int done;
    if (done) return;
    done = 1;
    if (!g_dry) teardown_network();
    if (g_child > 0) {
        kill(g_child, SIGTERM);
        for (int i = 0; i < 30; i++) {
            if (waitpid(g_child, NULL, WNOHANG) != 0) { g_child = 0; break; }
            usleep(100000);
        }
        if (g_child > 0) { kill(g_child, SIGKILL); waitpid(g_child, NULL, 0); }
    }
    if (g_dir[0]) {
        char p[200];
        snprintf(p, sizeof p, "%s/amneziawg-go", g_dir);
        unlink(p);
        rmdir(g_dir);
    }
}

static void on_signal(int sig) {
    (void)sig;
    cleanup();
    _exit(0);
}

static int alive(pid_t pid) {
    char p[64], buf[512];
    snprintf(p, sizeof p, "/proc/%d/stat", (int)pid);
    FILE *f = fopen(p, "r");
    if (!f) return 0;
    size_t n = fread(buf, 1, sizeof buf - 1, f);
    fclose(f);
    buf[n] = 0;
    char *r = strrchr(buf, ')');
    return r && r[1] == ' ' && r[2] != 'Z' && r[2] != 'X';
}

static int copy_binary(const char *src, const char *dst) {
    int in = open(src, O_RDONLY | O_NOFOLLOW | O_CLOEXEC);
    if (in < 0) return 0;
    int out = open(dst, O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC, 0700);
    if (out < 0) { close(in); return 0; }
    char buf[65536];
    ssize_t r;
    int ok = 1;
    while ((r = read(in, buf, sizeof buf)) > 0) {
        if (write(out, buf, (size_t)r) != r) { ok = 0; break; }
    }
    if (r < 0) ok = 0;
    close(in);
    close(out);
    return ok;
}

static int cmd_run(char **a) {
    const char *go = a[0], *confp = a[1], *stop = a[2];
    pid_t parent = (pid_t)atoi(a[3]);
    snprintf(g_if, sizeof g_if, "%s", a[4]);
    if (!valid_ifname(g_if)) die("неверное имя интерфейса");
    if (a[5][0] != '/' || strlen(a[5]) >= sizeof g_ip) die("неверный путь к ip");
    strcpy(g_ip, a[5]);
    if (strcmp(a[6], "-") != 0) {
        if (a[6][0] != '/' || strlen(a[6]) >= sizeof g_resolvectl) die("неверный путь к resolvectl");
        strcpy(g_resolvectl, a[6]);
    }
    if (geteuid() != 0) die("нужны права root (запускайте через pkexec)");

    struct conf c;
    parse_conf(confp, &c);

    strcpy(g_dir, "/run/tunn.XXXXXX");
    if (!mkdtemp(g_dir)) die("не удалось создать временный каталог в /run");
    char bin[200];
    snprintf(bin, sizeof bin, "%s/amneziawg-go", g_dir);
    if (!copy_binary(go, bin)) { cleanup(); die("не удалось скопировать amneziawg-go"); }

    signal(SIGTERM, on_signal);
    signal(SIGINT, on_signal);
    signal(SIGHUP, on_signal);

    g_child = fork();
    if (g_child < 0) { cleanup(); die("fork: %s", strerror(errno)); }
    if (g_child == 0) {
        setenv("LOG_LEVEL", "error", 1);
        execl(bin, "amneziawg-go", "-f", g_if, (char *)NULL);
        _exit(127);
    }

    char sock[300];
    int ready = 0;
    static const char *dirs[] = {"/var/run/amneziawg", "/var/run/wireguard", "/run/amneziawg", "/run/wireguard"};
    for (int i = 0; i < 100 && !ready; i++) {
        for (size_t d = 0; d < sizeof dirs / sizeof *dirs; d++) {
            snprintf(sock, sizeof sock, "%s/%s.sock", dirs[d], g_if);
            if (access(sock, F_OK) == 0) { ready = 1; break; }
        }
        if (!ready) {
            if (!alive(g_child)) { cleanup(); die("amneziawg-go завершился сразу после запуска"); }
            usleep(100000);
        }
    }
    if (!ready) { cleanup(); die("amneziawg-go не создал интерфейс вовремя"); }

    char *buf = malloc(65536);
    if (!buf) { cleanup(); die("нет памяти"); }
    size_t len = build_uapi(&c, buf, 65536, 1);
    uapi_send(g_if, buf, len);
    free(buf);
    setup_network(&c);
    printf("READY\n");
    fflush(stdout);

    while (alive(g_child) && (parent <= 1 || alive(parent)) && access(stop, F_OK) != 0) sleep(1);
    int child_died = !alive(g_child);
    cleanup();
    if (child_died) die("amneziawg-go остановился");
    return 0;
}

static int cmd_plan(char **a) {
    struct conf c;
    g_dry = 1;
    snprintf(g_if, sizeof g_if, "%s", a[1]);
    if (!valid_ifname(g_if)) die("неверное имя интерфейса");
    strcpy(g_ip, "ip");
    strcpy(g_resolvectl, "resolvectl");
    parse_conf(a[0], &c);
    char buf[65536];
    build_uapi(&c, buf, sizeof buf, 0);
    printf("--- UAPI ---\n%s--- commands ---\n", buf);
    setup_network(&c);
    return 0;
}

int main(int argc, char **argv) {
    if (argc == 10 && strcmp(argv[1], "run") == 0) return cmd_run(argv + 2);
    if (argc == 4 && strcmp(argv[1], "plan") == 0) return cmd_plan(argv + 2);
    fprintf(stderr, "usage: tunn-helper run <amneziawg-go> <conf> <stopfile> <parent-pid> <ifname> <ip> <resolvectl|->\n"
                    "       tunn-helper plan <conf> <ifname>\n");
    return 2;
}
