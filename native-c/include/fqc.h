/* libfqc: small dependency-free C helpers for Tunn (base64, SHA-256, share-link parsing, CIDR matching). */
#ifndef FQC_H
#define FQC_H
#include <stddef.h>
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif

/* ---- base64 (standard and url-safe alphabets, padding optional, whitespace ignored) ---- */
size_t fqc_b64_encoded_len(size_t n);
size_t fqc_b64_encode(const uint8_t *in, size_t n, char *out, size_t cap, int urlsafe);
/* returns decoded size, or (size_t)-1 on invalid input / small buffer */
size_t fqc_b64_decode(const char *in, size_t n, uint8_t *out, size_t cap);

/* ---- SHA-256 ---- */
typedef struct { uint32_t h[8]; uint64_t len; uint8_t buf[64]; size_t fill; } fqc_sha256;
void fqc_sha256_init(fqc_sha256 *c);
void fqc_sha256_update(fqc_sha256 *c, const void *p, size_t n);
void fqc_sha256_final(fqc_sha256 *c, uint8_t out[32]);
void fqc_sha256_hex(const void *p, size_t n, char out[65]);

/* ---- percent decoding ---- */
size_t fqc_url_decode(const char *in, size_t n, char *out, size_t cap); /* '+' stays '+'; returns length or -1 */

/* ---- share links: scheme://[user@]host:port[?k=v&...][#name] ---- */
#define FQC_MAXP 24
typedef struct {
    char scheme[16], user[256], host[256], name[256];
    int port;
    int nparams;
    char key[FQC_MAXP][32], val[FQC_MAXP][256];
} fqc_link;
/* 0 ok, <0 error. IPv6 hosts in [brackets] are returned without brackets. */
int fqc_link_parse(const char *s, fqc_link *out);
const char *fqc_link_param(const fqc_link *l, const char *key); /* NULL if absent */

/* ---- CIDR set (IPv4 + IPv6) ---- */
typedef struct { uint8_t addr[16]; uint8_t bits; uint8_t v6; } fqc_cidr;
typedef struct { fqc_cidr *items; size_t n, cap; } fqc_cidr_set;
int fqc_cidr_parse(const char *s, fqc_cidr *out);              /* "10.0.0.0/8", "::1/128", bare address = host route */
int fqc_ip_parse(const char *s, uint8_t out[16], int *v6);
void fqc_cidr_set_init(fqc_cidr_set *s);
void fqc_cidr_set_free(fqc_cidr_set *s);
int fqc_cidr_set_add(fqc_cidr_set *s, const char *cidr);       /* 0 ok */
int fqc_cidr_set_load(fqc_cidr_set *s, const char *text);       /* one entry per line or comma, '#' comments; returns count added or <0 */
int fqc_cidr_set_contains(const fqc_cidr_set *s, const char *ip); /* 1 / 0 / -1 invalid ip */

#ifdef __cplusplus
}
#endif
#endif
