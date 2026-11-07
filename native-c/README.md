# libfqc — C helpers

Dependency-free C11: base64, SHA-256, share-link parser (vless/trojan/ss…), IPv4/IPv6 CIDR sets.

    make -C native-c          # host build with ASan/UBSan + test vectors (RFC 4648, FIPS 180-2)

Android: `src/jni.c` + `CMakeLists.txt` build `libfqc.so` with `Java_app_fasdeq_vpn_NativeC_*`.
The Gradle `externalNativeBuild` hook and the Kotlin `NativeC` object are intentionally NOT wired yet:
the host tests pass, but the NDK build has not been run. To enable: add
`externalNativeBuild { cmake { path = file("../../native-c/CMakeLists.txt") } }` to `android {}` and a Kotlin
`object NativeC { init { System.loadLibrary("fqc") }; external fun sha256(s: String): String; external fun cidrContains(r: String, ip: String): Int }`.
