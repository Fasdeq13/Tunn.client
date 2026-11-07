#include "fqc.h"
#include <string.h>
#include <stdlib.h>

static int hex(int c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

size_t fqc_url_decode(const char *in, size_t n, char *out, size_t cap) {
    size_t o = 0;
    for (size_t i = 0; i < n; i++) {
        int c = (unsigned char)in[i];
        if (c == '%' && i + 2 < n + 0 && hex(in[i + 1]) >= 0 && hex(in[i + 2]) >= 0) {
            c = hex(in[i + 1]) << 4 | hex(in[i + 2]);
            i += 2;
        }
        if (o + 1 >= cap) return (size_t)-1;
        out[o++] = (char)c;
    }
    if (cap == 0) return (size_t)-1;
    out[o] = 0;
    return o;
}

static int put(char *dst, size_t cap, const char *s, size_t n) { return fqc_url_decode(s, n, dst, cap) == (size_t)-1 ? -1 : 0; }

int fqc_link_parse(const char *s, fqc_link *o) {
    memset(o, 0, sizeof *o);
    const char *sep = strstr(s, "://");
    if (!sep || sep == s || (size_t)(sep - s) >= sizeof o->scheme) return -1;
    for (const char *p = s; p < sep; p++) o->scheme[p - s] = (char)((*p >= 'A' && *p <= 'Z') ? *p + 32 : *p);
    const char *p = sep + 3;
    const char *end = p + strlen(p);
    const char *hash = memchr(p, '#', (size_t)(end - p));
    if (hash) {
        if (put(o->name, sizeof o->name, hash + 1, (size_t)(end - hash - 1))) return -2;
        end = hash;
    }
    const char *q = memchr(p, '?', (size_t)(end - p));
    const char *auth_end = q ? q : end;
    const char *slash = memchr(p, '/', (size_t)(auth_end - p));
    if (slash) auth_end = slash;
    const char *at = NULL;
    for (const char *c = p; c < auth_end; c++) if (*c == '@') at = c;
    const char *hp = p;
    if (at) {
        if (put(o->user, sizeof o->user, p, (size_t)(at - p))) return -3;
        hp = at + 1;
    }
    if (hp >= auth_end) return -4;
    const char *hostEnd = auth_end;
    if (*hp == '[') {
        const char *rb = memchr(hp, ']', (size_t)(auth_end - hp));
        if (!rb) return -5;
        if ((size_t)(rb - hp - 1) >= sizeof o->host) return -5;
        memcpy(o->host, hp + 1, (size_t)(rb - hp - 1));
        hostEnd = rb + 1;
        if (hostEnd < auth_end) {
            if (*hostEnd != ':') return -5;
            hostEnd++;
        } else hostEnd = NULL;
        if (hostEnd) {
            char tmp[8];
            if ((size_t)(auth_end - hostEnd) >= sizeof tmp) return -6;
            memcpy(tmp, hostEnd, (size_t)(auth_end - hostEnd)); tmp[auth_end - hostEnd] = 0;
            o->port = atoi(tmp);
        }
    } else {
        const char *colon = NULL;
        for (const char *c = hp; c < auth_end; c++) if (*c == ':') colon = c;
        const char *he = colon ? colon : auth_end;
        if ((size_t)(he - hp) >= sizeof o->host) return -5;
        memcpy(o->host, hp, (size_t)(he - hp));
        if (colon) {
            char tmp[8];
            if ((size_t)(auth_end - colon - 1) >= sizeof tmp) return -6;
            memcpy(tmp, colon + 1, (size_t)(auth_end - colon - 1)); tmp[auth_end - colon - 1] = 0;
            o->port = atoi(tmp);
        }
    }
    if (o->port < 0 || o->port > 65535) return -6;
    if (q) {
        const char *k = q + 1;
        while (k < end && o->nparams < FQC_MAXP) {
            const char *amp = memchr(k, '&', (size_t)(end - k));
            const char *e = amp ? amp : end;
            const char *eq = memchr(k, '=', (size_t)(e - k));
            if (e > k) {
                int i = o->nparams;
                const char *ke = eq ? eq : e;
                if (put(o->key[i], sizeof o->key[i], k, (size_t)(ke - k)) == 0) {
                    if (eq) { if (put(o->val[i], sizeof o->val[i], eq + 1, (size_t)(e - eq - 1))) o->val[i][0] = 0; }
                    o->nparams++;
                }
            }
            k = e + 1;
        }
    }
    return 0;
}

const char *fqc_link_param(const fqc_link *l, const char *key) {
    for (int i = 0; i < l->nparams; i++) if (strcmp(l->key[i], key) == 0) return l->val[i];
    return NULL;
}
