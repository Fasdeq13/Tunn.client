#include <jni.h>
#include <string.h>
#include "fqc.h"

JNIEXPORT jstring JNICALL Java_app_fasdeq_vpn_NativeC_sha256(JNIEnv *env, jclass cls, jstring s) {
    (void)cls;
    const char *u = (*env)->GetStringUTFChars(env, s, NULL);
    if (!u) return NULL;
    char hex[65];
    fqc_sha256_hex(u, strlen(u), hex);
    (*env)->ReleaseStringUTFChars(env, s, u);
    return (*env)->NewStringUTF(env, hex);
}

JNIEXPORT jint JNICALL Java_app_fasdeq_vpn_NativeC_cidrContains(JNIEnv *env, jclass cls, jstring ranges, jstring ip) {
    (void)cls;
    const char *r = (*env)->GetStringUTFChars(env, ranges, NULL);
    const char *a = (*env)->GetStringUTFChars(env, ip, NULL);
    jint res = -2;
    if (r && a) {
        fqc_cidr_set set;
        fqc_cidr_set_init(&set);
        if (fqc_cidr_set_load(&set, r) >= 0) res = fqc_cidr_set_contains(&set, a);
        fqc_cidr_set_free(&set);
    }
    if (r) (*env)->ReleaseStringUTFChars(env, ranges, r);
    if (a) (*env)->ReleaseStringUTFChars(env, ip, a);
    return res;
}
