#include "fqc.h"
#include <stdio.h>
#include <string.h>

static int fails;
#define CHECK(c) do { if (!(c)) { printf("FAIL %s:%d  %s\n", __FILE__, __LINE__, #c); fails++; } } while (0)

int main(void) {
    char b[256]; uint8_t d[256];
    const char *pl[] = {"", "f", "fo", "foo", "foob", "fooba", "foobar"};
    const char *en[] = {"", "Zg==", "Zm8=", "Zm9v", "Zm9vYg==", "Zm9vYmE=", "Zm9vYmFy"};
    for (int i = 0; i < 7; i++) {
        CHECK(fqc_b64_encode((const uint8_t *)pl[i], strlen(pl[i]), b, sizeof b, 0) == strlen(en[i]));
        CHECK(strcmp(b, en[i]) == 0);
        size_t n = fqc_b64_decode(en[i], strlen(en[i]), d, sizeof d);
        CHECK(n == strlen(pl[i]) && memcmp(d, pl[i], n) == 0);
    }
    CHECK(fqc_b64_decode("Zm9vYg", 6, d, sizeof d) == 4);
    CHECK(fqc_b64_decode("Zm9v\nYmFy", 9, d, sizeof d) == 6);
    CHECK(fqc_b64_decode("_-8", 3, d, sizeof d) == 2 && d[0] == 0xFF && d[1] == 0xEF);
    CHECK(fqc_b64_decode("Zm9v!", 5, d, sizeof d) == (size_t)-1);
    CHECK(fqc_b64_decode("Zg==Zg", 6, d, sizeof d) == (size_t)-1);
    CHECK(fqc_b64_decode("Zm9vYmFy", 8, d, 3) == (size_t)-1);

    char h[65];
    fqc_sha256_hex("", 0, h);
    CHECK(strcmp(h, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855") == 0);
    fqc_sha256_hex("abc", 3, h);
    CHECK(strcmp(h, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad") == 0);
    const char *two = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq";
    fqc_sha256_hex(two, strlen(two), h);
    CHECK(strcmp(h, "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1") == 0);
    char big[1000]; memset(big, 'a', sizeof big);
    fqc_sha256 c; uint8_t dg[32]; fqc_sha256_init(&c);
    for (int i = 0; i < 1000; i++) fqc_sha256_update(&c, big, 1000);
    fqc_sha256_final(&c, dg);
    char hx[65]; for (int i = 0; i < 32; i++) sprintf(hx + i * 2, "%02x", dg[i]);
    CHECK(strcmp(hx, "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0") == 0);

    fqc_link l;
    CHECK(fqc_link_parse("vless://11111111-2222-3333-4444-555555555555@example.com:443?type=tcp&security=reality&sni=max.ru&fm=%7B%22a%22%3A1%7D&flow=xtls-rprx-vision#%D0%A1%D0%B5%D1%80%D0%B2%D0%B5%D1%80%201", &l) == 0);
    CHECK(strcmp(l.scheme, "vless") == 0 && strcmp(l.host, "example.com") == 0 && l.port == 443);
    CHECK(strcmp(l.user, "11111111-2222-3333-4444-555555555555") == 0);
    CHECK(strcmp(fqc_link_param(&l, "sni"), "max.ru") == 0);
    CHECK(strcmp(fqc_link_param(&l, "fm"), "{\"a\":1}") == 0);
    CHECK(strcmp(l.name, "Сервер 1") == 0);
    CHECK(fqc_link_param(&l, "nope") == NULL);
    CHECK(fqc_link_parse("TROJAN://pa%40ss@[2001:db8::1]:8443#x", &l) == 0 && strcmp(l.scheme, "trojan") == 0);
    CHECK(strcmp(l.host, "2001:db8::1") == 0 && l.port == 8443 && strcmp(l.user, "pa@ss") == 0);
    CHECK(fqc_link_parse("ss://YWVzOnB3@1.2.3.4:8388", &l) == 0 && l.port == 8388);
    CHECK(fqc_link_parse("nolink", &l) < 0);
    CHECK(fqc_link_parse("vless://u@h:99999", &l) < 0);
    CHECK(fqc_link_parse("vless://@", &l) < 0);

    fqc_cidr_set s; fqc_cidr_set_init(&s);
    CHECK(fqc_cidr_set_load(&s, "# ru\n10.0.0.0/8, 192.168.1.5\n2001:db8::/32\nbad/line\n77.88.0.0/18 # yandex\n") == 4);
    CHECK(fqc_cidr_set_contains(&s, "10.200.3.4") == 1);
    CHECK(fqc_cidr_set_contains(&s, "11.0.0.1") == 0);
    CHECK(fqc_cidr_set_contains(&s, "192.168.1.5") == 1);
    CHECK(fqc_cidr_set_contains(&s, "192.168.1.6") == 0);
    CHECK(fqc_cidr_set_contains(&s, "77.88.63.255") == 1);
    CHECK(fqc_cidr_set_contains(&s, "77.88.64.0") == 0);
    CHECK(fqc_cidr_set_contains(&s, "2001:db8:ffff::1") == 1);
    CHECK(fqc_cidr_set_contains(&s, "2001:db9::1") == 0);
    CHECK(fqc_cidr_set_contains(&s, "::ffff:10.1.1.1") == 0);
    CHECK(fqc_cidr_set_contains(&s, "300.1.1.1") == -1);
    fqc_cidr_set_free(&s);
    uint8_t ip[16]; int v6;
    CHECK(fqc_ip_parse("::", ip, &v6) == 0 && v6);
    CHECK(fqc_ip_parse("::1", ip, &v6) == 0 && ip[15] == 1);
    CHECK(fqc_ip_parse("1::2::3", ip, &v6) < 0);
    CHECK(fqc_ip_parse("1:2:3:4::5:6:7:8", ip, &v6) < 0);
    CHECK(fqc_ip_parse("1:2:3:4:5:6:7:8", ip, &v6) == 0);
    CHECK(fqc_ip_parse("1.2.3", ip, &v6) < 0);
    fqc_cidr cc;
    CHECK(fqc_cidr_parse("10.1.2.3/8", &cc) == 0 && cc.addr[1] == 0 && cc.bits == 8);
    CHECK(fqc_cidr_parse("10.0.0.0/33", &cc) < 0);

    printf(fails ? "%d FAILED\n" : "all tests passed\n", fails);
    return fails != 0;
}
