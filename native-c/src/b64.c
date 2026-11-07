#include "fqc.h"

static const char STD[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
static const char URL[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

size_t fqc_b64_encoded_len(size_t n) { return (n + 2) / 3 * 4; }

size_t fqc_b64_encode(const uint8_t *in, size_t n, char *out, size_t cap, int urlsafe) {
    const char *a = urlsafe ? URL : STD;
    size_t need = fqc_b64_encoded_len(n);
    if (cap < need + 1) return (size_t)-1;
    size_t o = 0;
    for (size_t i = 0; i < n; i += 3) {
        uint32_t v = (uint32_t)in[i] << 16;
        if (i + 1 < n) v |= (uint32_t)in[i + 1] << 8;
        if (i + 2 < n) v |= in[i + 2];
        out[o++] = a[v >> 18 & 63];
        out[o++] = a[v >> 12 & 63];
        out[o++] = i + 1 < n ? a[v >> 6 & 63] : '=';
        out[o++] = i + 2 < n ? a[v & 63] : '=';
    }
    out[o] = 0;
    return o;
}

static int val(unsigned char c) {
    if (c >= 'A' && c <= 'Z') return c - 'A';
    if (c >= 'a' && c <= 'z') return c - 'a' + 26;
    if (c >= '0' && c <= '9') return c - '0' + 52;
    if (c == '+' || c == '-') return 62;
    if (c == '/' || c == '_') return 63;
    return -1;
}

size_t fqc_b64_decode(const char *in, size_t n, uint8_t *out, size_t cap) {
    uint32_t acc = 0;
    int bits = 0;
    size_t o = 0;
    int pad = 0;
    for (size_t i = 0; i < n; i++) {
        unsigned char c = (unsigned char)in[i];
        if (c == ' ' || c == '\n' || c == '\r' || c == '\t') continue;
        if (c == '=') { pad = 1; continue; }
        if (pad) return (size_t)-1; /* data after padding */
        int v = val(c);
        if (v < 0) return (size_t)-1;
        acc = acc << 6 | (uint32_t)v;
        bits += 6;
        if (bits >= 8) {
            bits -= 8;
            if (o >= cap) return (size_t)-1;
            out[o++] = (uint8_t)(acc >> bits & 0xFF);
        }
    }
    if (bits >= 6) return (size_t)-1; /* a lone trailing symbol carries no byte */
    return o;
}
