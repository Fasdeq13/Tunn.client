#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

TARGET="${1:-all}"
MODE="${2:-release}"
case "$TARGET" in all|android|desktop|windows) ;; *) echo "usage: $0 [all|android|desktop|windows] [debug|release|play]"; exit 2 ;; esac

CACHE="${FQ_CACHE:-$ROOT/.fqbuild}"
PLATFORM="android-36"
BUILD_TOOLS="36.0.0"
NDK_VERSION="27.0.12077973"
GRADLE_VERSION="8.11.1"
XRAY_TAG="${XRAY_TAG:-}"            # empty = latest AndroidLibXrayLite release
mkdir -p "$CACHE" dist

say() { echo ">> $*"; }
die() { echo "!! $*" >&2; exit 1; }

if [ -z "${TUNN_FHS:-}" ] && [ -e /etc/NIXOS ]; then
  command -v nix-shell >/dev/null 2>&1 || die "nix-shell not found"
  say "NixOS: entering FHS environment from shell.nix (first run downloads packages)"
  export TUNN_FHS=1
  exec nix-shell "$ROOT/shell.nix" --run "$(printf '%q ' "$ROOT/build.sh" "$@")"
fi

case "$(uname -s)" in Linux) OS=linux ;; Darwin) OS=mac ;; MINGW*|MSYS*|CYGWIN*) OS=win ;; *) die "unsupported OS" ;; esac

if command -v pacman >/dev/null 2>&1; then
  NEED=()
  for c in git curl unzip; do command -v "$c" >/dev/null 2>&1 || NEED+=("$c"); done
  case "$TARGET" in android|all) command -v java >/dev/null 2>&1 || NEED+=(jdk21-openjdk) ;; esac
  if [ "$TARGET" = windows ] && [ "$OS" = linux ]; then
    command -v cc >/dev/null 2>&1 || NEED+=(base-devel)
    command -v x86_64-w64-mingw32-gcc >/dev/null 2>&1 || NEED+=(mingw-w64-gcc)
  elif [ "$TARGET" != android ]; then
    command -v cc >/dev/null 2>&1 || NEED+=(base-devel)
    if [ "$OS" = linux ]; then
      command -v pkg-config >/dev/null 2>&1 || NEED+=(pkgconf)
      for lib in gtk3 libayatana-appindicator xdotool; do pacman -Qi "$lib" >/dev/null 2>&1 || NEED+=("$lib"); done
    fi
  fi
  [ "${#NEED[@]}" -gt 0 ] && { say "pacman: ${NEED[*]}"; sudo pacman -S --needed --noconfirm "${NEED[@]}"; }
fi
find_jdk() {
  local v d
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then echo "$JAVA_HOME"; return 0; fi
  for v in 21 17; do
    for d in /usr/lib/jvm/java-$v-openjdk /usr/lib/jvm/java-$v-* /usr/lib/jvm/jdk-$v* /usr/lib/jvm/temurin-$v* "$HOME"/.sdkman/candidates/java/$v.* /Library/Java/JavaVirtualMachines/*-$v*/Contents/Home; do
      [ -x "$d/bin/javac" ] && { echo "$d"; return 0; }
    done
  done
  return 1
}
JDK=""
if [ "$TARGET" = android ] || [ "$TARGET" = all ]; then JDK="$(find_jdk || true)"; fi
if [ "$TARGET" = android ] || [ "$TARGET" = all ]; then
if [ -z "$JDK" ] && command -v pacman >/dev/null 2>&1; then
  say "installing JDK 21 (Gradle 8 does not run on newer JDKs)"
  sudo pacman -S --needed --noconfirm jdk21-openjdk
  JDK="$(find_jdk || true)"
fi
[ -n "$JDK" ] || die "JDK 17 or 21 is required (found only: $(java -version 2>&1 | head -1)). Install it and run again"
export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$PATH"
say "JDK: $JDK"
fi

if ! command -v cargo >/dev/null 2>&1 || ! cargo --version >/dev/null 2>&1; then
  if command -v rustup >/dev/null 2>&1; then
    say "rustup default stable"
    rustup default stable
  else
    say "installing rust (rustup)"
    curl -fsSL https://sh.rustup.rs | sh -s -- -y --profile minimal
  fi
fi
# shellcheck disable=SC1091
[ -f "$HOME/.cargo/env" ] && . "$HOME/.cargo/env"

TDIR="${TUNN_TARGET:-$HOME/.cache/tunn-target}"
export CARGO_TARGET_DIR="$TDIR"

build_desktop() {
  say "desktop (cargo build --release)"
  (cd desktop && cargo build --release)
  for f in "$TDIR/release/tunn" "$TDIR/release/tunn.exe"; do
    [ -f "$f" ] && cp "$f" dist/ && say "desktop: dist/$(basename "$f")"
  done
  return 0
}

build_windows() {
  if [ "$OS" = win ]; then
    say "windows (native cargo build --release)"
    (cd desktop && cargo build --release)
  else
    say "windows (cross-compile x86_64-pc-windows-gnu)"
    command -v x86_64-w64-mingw32-gcc >/dev/null 2>&1 || die "install mingw-w64 (pacman -S mingw-w64-gcc | apt install gcc-mingw-w64-x86-64)"
    rustup target add x86_64-pc-windows-gnu >/dev/null
    (cd desktop && CARGO_TARGET_X86_64_PC_WINDOWS_GNU_LINKER=x86_64-w64-mingw32-gcc cargo build --release --target x86_64-pc-windows-gnu)
  fi
  local f
  for f in "$TDIR/x86_64-pc-windows-gnu/release/tunn.exe" "$TDIR/release/tunn.exe"; do
    if [ -f "$f" ]; then
      cp "$f" dist/Tunn-3.8-windows.exe
      say "windows: dist/Tunn-3.8-windows.exe"
      return 0
    fi
  done
  die "tunn.exe not found"
}

build_android() {

  local sdk=""
  for d in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Android/Sdk" "$HOME/.local/lib/android/sdk" \
           "$HOME/Library/Android/sdk" "$HOME/Downloads/v2rayng/.tunnbuild/android-sdk" "$CACHE/android-sdk"; do
    if [ -n "$d" ] && [ -x "$d/cmdline-tools/latest/bin/sdkmanager" ]; then sdk="$d"; break; fi
  done
  if [ -z "$sdk" ]; then
    sdk="$CACHE/android-sdk"
    say "downloading Android cmdline-tools"
    curl -fL --retry 3 -o "$CACHE/cmdline-tools.zip" "https://dl.google.com/android/repository/commandlinetools-${OS}-11076708_latest.zip"
    rm -rf "$sdk/cmdline-tools"; mkdir -p "$sdk/cmdline-tools"
    unzip -q -o "$CACHE/cmdline-tools.zip" -d "$sdk/cmdline-tools"
    mv "$sdk/cmdline-tools/cmdline-tools" "$sdk/cmdline-tools/latest"
    rm -f "$CACHE/cmdline-tools.zip"
  fi
  export ANDROID_HOME="$sdk" ANDROID_SDK_ROOT="$sdk"
  local ndk="$sdk/ndk/$NDK_VERSION"
  if [ ! -d "$sdk/platforms/$PLATFORM" ] || [ ! -d "$sdk/build-tools/$BUILD_TOOLS" ] || [ ! -f "$ndk/source.properties" ]; then
    say "installing SDK components"
    yes | "$sdk/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1 || true
    "$sdk/cmdline-tools/latest/bin/sdkmanager" --install "platform-tools" "platforms;$PLATFORM" "build-tools;$BUILD_TOOLS" "ndk;$NDK_VERSION" >/dev/null
  fi
  export ANDROID_NDK_HOME="$ndk"
  printf 'sdk.dir=%s\n' "$sdk" > android/local.properties

  say "native core (cargo-ndk)"
  rustup target add aarch64-linux-android armv7-linux-androideabi >/dev/null
  command -v cargo-ndk >/dev/null 2>&1 || cargo install cargo-ndk --locked
  local jni="$ROOT/android/app/src/main/jniLibs"
  rm -rf "$jni"; mkdir -p "$jni"
  (cd android-native && cargo ndk -t arm64-v8a -t armeabi-v7a -o "$jni" build --release)

  mkdir -p android/app/libs
  if [ ! -f android/app/libs/libv2ray.aar ]; then
    local tag="$XRAY_TAG"
    if [ -z "$tag" ]; then
      local json
      json="$(curl -fsSL https://api.github.com/repos/2dust/AndroidLibXrayLite/releases/latest || true)"
      tag="$(printf '%s' "$json" | sed -n 's/.*"tag_name": *"\([^"]*\)".*/\1/p' | head -1 || true)"
      [ -n "$tag" ] || tag="$(git ls-remote --tags --refs https://github.com/2dust/AndroidLibXrayLite 2>/dev/null | sed 's#.*refs/tags/##' | sort -V | tail -1 || true)"
    fi
    [ -n "$tag" ] || die "could not determine the AndroidLibXrayLite release; set XRAY_TAG=vX.Y.Z"
    say "libv2ray.aar $tag"
    curl -fL --retry 3 -o android/app/libs/libv2ray.aar "https://github.com/2dust/AndroidLibXrayLite/releases/download/$tag/libv2ray.aar"
  fi

  mkdir -p android/app/src/main/assets
  mkdir -p android/app/src/main/assets/builtin-plugins
  ls plugins/builtin/*.zip >/dev/null 2>&1 && cp plugins/builtin/*.zip android/app/src/main/assets/builtin-plugins/ || true

  if [ "${SINGBOX:-1}" = 1 ]; then
    say "sing-box core"
    local rel; rel="$(curl -fsSL https://api.github.com/repos/SagerNet/sing-box/releases/latest || true)"
    for pair in "arm64-v8a:arm64" "armeabi-v7a:armv7"; do
      local abi="${pair%%:*}" arch="${pair##*:}" url
      url="$(printf '%s' "$rel" | grep -o "https://[^\"]*android-$arch[^\"]*\.tar\.gz" | head -1 || true)"
      if [ -z "$url" ]; then echo "!! no sing-box build for $arch, skipping"; continue; fi
      local tmp; tmp="$(mktemp -d)"
      if curl -fL --retry 3 -o "$tmp/sb.tgz" "$url" && tar -xzf "$tmp/sb.tgz" -C "$tmp" --strip-components=1 && [ -f "$tmp/sing-box" ]; then
        mkdir -p "android/app/src/main/jniLibs/$abi"
        cp "$tmp/sing-box" "android/app/src/main/jniLibs/$abi/libsingbox.so"
      else echo "!! sing-box $arch download failed, skipping"; fi
      rm -rf "$tmp"
    done
  fi

  local gradle
  if [ -x android/gradlew ]; then
    gradle="$ROOT/android/gradlew"
  else
    local gdir="$CACHE/gradle-$GRADLE_VERSION"
    if [ ! -x "$gdir/bin/gradle" ]; then
      say "downloading Gradle $GRADLE_VERSION"
      curl -fL --retry 3 -o "$CACHE/gradle.zip" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
      rm -rf "$gdir"; unzip -q "$CACHE/gradle.zip" -d "$CACHE"; rm -f "$CACHE/gradle.zip"
    fi
    gradle="$gdir/bin/gradle"
  fi

  local task="assembleDebug" args=(-Papp.vcode="$(( $(date +%s) / 60 ))")
  if [ "$MODE" = release ] || [ "$MODE" = play ]; then
    task="assembleRelease"
    if [ "$MODE" = play ]; then task="bundleRelease"; fi
    local ks="${KS_PATH:-}" kpass="${KS_PASS:-}" kalias="${KS_ALIAS:-}"
    if [ -z "$ks" ]; then
      mkdir -p "$HOME/.fasdeq"
      if [ -f "$HOME/.fasdeq/release.jks" ]; then
        ks="$HOME/.fasdeq/release.jks"
        # shellcheck disable=SC1091
        . "$HOME/.fasdeq/ks.env"
        kpass="$KS_PASS_SAVED"; kalias="fasdeq"
      elif [ -f "$HOME/.android/debug.keystore" ]; then
        ks="$HOME/.android/debug.keystore"; kpass="android"; kalias="androiddebugkey"
      else
        ks="$HOME/.fasdeq/release.jks"; kpass="$(od -An -N12 -tx1 /dev/urandom | tr -d ' \n')"; kalias="fasdeq"
        keytool -genkeypair -v -keystore "$ks" -storepass "$kpass" -keypass "$kpass" -alias "$kalias" -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Tunn" >/dev/null 2>&1
        printf 'KS_PASS_SAVED=%s\n' "$kpass" > "$HOME/.fasdeq/ks.env"
        chmod 600 "$ks" "$HOME/.fasdeq/ks.env"
      fi
    fi
    [ -f "$ks" ] || die "keystore not created: $ks (is keytool working? set KS_PATH, KS_PASS, KS_ALIAS to use your own)"
    say "signing with $ks"
    args+=(-Pks.path="$ks" -Pks.pass="$kpass" -Pks.alias="$kalias")
  fi
  say "gradle $task"
  (cd android && "$gradle" ":app:$task" "${args[@]}" --no-daemon)

  if [ "$MODE" = play ]; then
    local aab; aab="$(find android/app/build/outputs/bundle -name '*.aab' | head -1)"
    [ -n "$aab" ] || die "AAB not found"
    cp "$aab" "dist/Tunn-3.8-play.aab"
    say "AAB for Google Play: $ROOT/dist/Tunn-3.8-play.aab (signed with your upload key; keep $HOME/.fasdeq/release.jks safe)"
    return 0
  fi
  local apk
  apk="$(find android/app/build/outputs/apk -name '*.apk' ! -name '*unsigned*' | head -1)"
  [ -n "$apk" ] || die "APK not found"
  local asg; asg="$(find "${sdk:-$CACHE/android-sdk}/build-tools" -name apksigner 2>/dev/null | sort | tail -1)"
  if [ -n "$asg" ] && [ "$MODE" != debug ]; then "$asg" verify --print-certs "$apk" | head -3 || die "APK signature check failed"; fi
  cp "$apk" "dist/Tunn-3.8-$MODE.apk"
  say "APK: $ROOT/dist/Tunn-3.8-$MODE.apk"
}

case "$TARGET" in
  android) build_android ;;
  desktop) if [ "$OS" = win ]; then build_windows; else build_desktop; fi ;;
  windows) build_windows ;;
  all) build_android; if [ "$OS" = win ]; then build_windows; else build_desktop; fi ;;
esac
say "done"
