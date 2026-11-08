#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

VERSION="4.3"
SERIAL="${1:-1}"
BASE="dist/classes-$VERSION-full"
CACHE="${FQ_CACHE:-$ROOT/.fqbuild}"
GRADLE_VERSION="8.11.1"

say() { echo ">> $*"; }
die() { echo "!! $*" >&2; exit 1; }

if [ -z "${TUNN_FHS:-}" ] && [ -e /etc/NIXOS ]; then
  export TUNN_FHS=1
  exec nix-shell "$ROOT/shell.nix" --run "$(printf '%q ' "$ROOT/fix.sh" "$@")"
fi

[ -d "$BASE" ] || die "нет $BASE: это классы сборки, из которой установлен APK. Её делает ./build.sh"
[ -f "$HOME/.tunn/pack.pem" ] || die "нет ключа подписи: python3 tools/pack.py keygen"
[ -f android/local.properties ] || die "сначала один раз выполните ./build.sh"

find_jdk() {
  local v d
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then echo "$JAVA_HOME"; return 0; fi
  for v in 21 17; do
    for d in /usr/lib/jvm/java-$v-openjdk /usr/lib/jvm/java-$v-* /usr/lib/jvm/jdk-$v* /usr/lib/jvm/temurin-$v*; do
      [ -x "$d/bin/javac" ] && { echo "$d"; return 0; }
    done
  done
  return 1
}
JDK="$(find_jdk || true)"
[ -n "$JDK" ] || die "нужен JDK 17 или 21"
export JAVA_HOME="$JDK" PATH="$JDK/bin:$PATH"

SDK="$(sed -n 's/^sdk.dir=//p' android/local.properties)"
[ -d "$SDK" ] || die "SDK не найден: $SDK"
export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"

if [ -x android/gradlew ]; then
  GRADLE="$ROOT/android/gradlew"
else
  GRADLE="$CACHE/gradle-$GRADLE_VERSION/bin/gradle"
  [ -x "$GRADLE" ] || die "gradle не найден, сначала один раз выполните ./build.sh"
fi

say "компиляция"
(cd android && "$GRADLE" :app:compileFullReleaseKotlin :app:compileFullReleaseJavaWithJavac --no-daemon -q)

NEW="dist/classes-new"
rm -rf "$NEW"; mkdir -p "$NEW"
for d in android/app/build/tmp/kotlin-classes/full* android/app/build/intermediates/javac/full*/classes; do
  [ -d "$d" ] && cp -r "$d/." "$NEW/"
done

say "пакет исправления $VERSION.$SERIAL"
python3 tools/pack.py fix --old-classes "$BASE" --new-classes "$NEW" --version "$VERSION" --serial "$SERIAL" --out dist
say "готово: dist/Tunn-$VERSION.$SERIAL.fix, выложите его в релиз $VERSION"
