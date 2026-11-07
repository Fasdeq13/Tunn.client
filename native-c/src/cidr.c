#include "fqc.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int parse4(const char *s, uint8_t *o) {
    int part = 0, digits = 0, v = 0;
    for (const char *p = s;; p++) {
        if (*p >= '0' && *p <= '9') {
            v = v * 10 + (*p - '0');
            if (++digits > 3 || v > 255) return -1;
        } else if (*p == '.' || *p == 0) {
            if (!digits || part > 3) return -1;
            o[part++] = (uint8_t)v;
            v = digits = 0;
            if (*p == 0) break;
        } else return -1;
    }
    return part == 4 ? 0 : -1;
}

static int hexv(int c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

static int parse6(const char *s, uint8_t *o) {
    uint16_t head[8], tail[8];
    int nh = 0, nt = 0, gap = 0;
    const char *p = s;
    if (p[0] == ':' && p[1] == ':') { gap = 1; p += 2; if (!*p) { memset(o, 0, 16); return 0; } }
    else if (p[0] == ':') return -1;
    for (;;) {
        const char *dot = strchr(p, '.');
        const char *col = strchr(p, ':');
        if (dot && (!col || dot < col)) {
            uint8_t v4[4];
            if (parse4(p, v4)) return -1;
            uint16_t a = (uint16_t)(v4[0] << 8 | v4[1]), b = (uint16_t)(v4[2] << 8 | v4[3]);
            uint16_t *arr = gap ? tail : head; int *n = gap ? &nt : &nh;
            if (*n > 6) return -1;
            arr[(*n)++] = a; arr[(*n)++] = b;
            break;
        }
        int v = 0, d = 0;
        while (hexv(*p) >= 0) { v = v << 4 | hexv(*p); p++; if (++d > 4) return -1; }
        if (!d) return -1;
        uint16_t *arr = gap ? tail : head; int *n = gap ? &nt : &nh;
        if (*n >= 8) return -1;
        arr[(*n)++] = (uint16_t)v;
        if (*p == 0) break;
        if (*p != ':') return -1;
        p++;
        if (*p == ':') {
            if (gap) return -1;
            gap = 1; p++;
            if (!*p) break;
        } else if (!*p) return -1;
    }
    int total = nh + nt;
    if (gap ? total > 7 : total != 8) return -1;
    uint16_t all[8] = {0};
    for (int i = 0; i < nh; i++) all[i] = head[i];
    for (int i = 0; i < nt; i++) all[8 - nt + i] = tail[i];
    for (int i = 0; i < 8; i++) { o[i * 2] = (uint8_t)(all[i] >> 8); o[i * 2 + 1] = (uint8_t)all[i]; }
    return 0;
}

int fqc_ip_parse(const char *s, uint8_t out[16], int *v6) {
    memset(out, 0, 16);
    if (strchr(s, ':')) { *v6 = 1; return parse6(s, out); }
    *v6 = 0;
    return parse4(s, out);
}

int fqc_cidr_parse(const char *s, fqc_cidr *o) {
    char buf[80];
    size_t n = strlen(s);
    if (n == 0 || n >= sizeof buf) return -1;
    memcpy(buf, s, n + 1);
    char *slash = strchr(buf, '/');
    int bits = -1;
    if (slash) {
        *slash = 0;
        char *e;
        long v = strtol(slash + 1, &e, 10);
        if (*e || e == slash + 1 || v < 0) return -1;
        bits = (int)v;
    }
    int v6;
    memset(o, 0, sizeof *o);
    if (fqc_ip_parse(buf, o->addr, &v6)) return -1;
    int max = v6 ? 128 : 32;
    if (bits < 0) bits = max;
    if (bits > max) return -1;
    o->v6 = (uint8_t)v6;
    o->bits = (uint8_t)bits;
    for (int i = 0; i < 16; i++) {
        int keep = bits - i * 8;
        if (keep >= 8) continue;
        o->addr[i] = (uint8_t)(keep <= 0 ? 0 : (o->addr[i] & (0xFF << (8 - keep))));
    }
    return 0;
}

void fqc_cidr_set_init(fqc_cidr_set *s) { s->items = NULL; s->n = s->cap = 0; }
void fqc_cidr_set_free(fqc_cidr_set *s) { free(s->items); fqc_cidr_set_init(s); }

int fqc_cidr_set_add(fqc_cidr_set *s, const char *cidr) {
    fqc_cidr c;
    if (fqc_cidr_parse(cidr, &c)) return -1;
    if (s->n == s->cap) {
        size_t nc = s->cap ? s->cap * 2 : 32;
        fqc_cidr *p = realloc(s->items, nc * sizeof *p);
        if (!p) return -2;
        s->items = p; s->cap = nc;
    }
    s->items[s->n++] = c;
    return 0;
}

int fqc_cidr_set_load(fqc_cidr_set *s, const char *text) {
    int added = 0;
    const char *p = text;
    while (*p) {
        while (*p == ' ' || *p == '\t' || *p == '\r' || *p == '\n' || *p == ',') p++;
        if (!*p) break;
        if (*p == '#') { while (*p && *p != '\n') p++; continue; }
        char tok[80];
        size_t n = 0;
        while (*p && *p != ' ' && *p != '\t' && *p != '\r' && *p != '\n' && *p != ',' && *p != '#') { if (n + 1 < sizeof tok) tok[n++] = *p; p++; }
        tok[n] = 0;
        int r = fqc_cidr_set_add(s, tok);
        if (r == -2) return -2;
        if (r == 0) added++;
    }
    return added;
}

static int match(const fqc_cidr *c, const uint8_t *ip, int v6) {
    if (c->v6 != v6) return 0;
    int full = c->bits / 8, rest = c->bits % 8;
    if (memcmp(c->addr, ip, (size_t)full)) return 0;
    if (rest && ((c->addr[full] ^ ip[full]) & (0xFF << (8 - rest)))) return 0;
    return 1;
}

int fqc_cidr_set_contains(const fqc_cidr_set *s, const char *ip) {
    uint8_t a[16];
    int v6;
    if (fqc_ip_parse(ip, a, &v6)) return -1;
    for (size_t i = 0; i < s->n; i++) if (match(&s->items[i], a, v6)) return 1;
    return 0;
}
