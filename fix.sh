#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

VERSION="4.3"
CACHE="${FQ_CACHE:-$ROOT/.fqbuild}"
GRADLE_VERSION="8.11.1"
BASE="dist/classes-$VERSION-full"
NEW="dist/classes-new"

die() { echo "!! $*" >&2; exit 1; }

if [ -z "${TUNN_FHS:-}" ] && [ -e /etc/NIXOS ]; then
  export TUNN_FHS=1
  exec nix-shell "$ROOT/shell.nix" --run "$(printf '%q ' "$ROOT/fix.sh" "$@")"
fi

last="$(ls dist/Tunn-$VERSION.*.fix 2>/dev/null | sed -E "s/.*Tunn-$VERSION\.([0-9]+)\.fix/\1/" | sort -n | tail -1 || true)"
SERIAL="${1:-$(( ${last:-0} + 1 ))}"

[ -d "$BASE" ] || die "нет $BASE, выполните ./build.sh"
[ -f "$HOME/.tunn/pack.pem" ] || die "нет ключа, выполните python3 tools/pack.py keygen"
[ -f android/local.properties ] || die "выполните ./build.sh"

JDK=""
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
  JDK="$JAVA_HOME"
else
  for v in 21 17; do
    for d in /usr/lib/jvm/java-$v-openjdk /usr/lib/jvm/java-$v-* /usr/lib/jvm/jdk-$v* /usr/lib/jvm/temurin-$v*; do
      [ -z "$JDK" ] && [ -x "$d/bin/javac" ] && JDK="$d"
    done
  done
fi
[ -n "$JDK" ] || die "нужен JDK 17 или 21"
export JAVA_HOME="$JDK" PATH="$JDK/bin:$PATH"

SDK="$(sed -n 's/^sdk.dir=//p' android/local.properties)"
[ -d "$SDK" ] || die "SDK не найден: $SDK"
export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"

if [ -x android/gradlew ]; then
  GRADLE="$ROOT/android/gradlew"
else
  GRADLE="$CACHE/gradle-$GRADLE_VERSION/bin/gradle"
fi
[ -x "$GRADLE" ] || die "gradle не найден, выполните ./build.sh"

(cd android && "$GRADLE" :app:compileFullReleaseKotlin :app:compileFullReleaseJavaWithJavac --no-daemon -q)

rm -rf "$NEW"
mkdir -p "$NEW"
for d in android/app/build/tmp/kotlin-classes/full* android/app/build/intermediates/javac/full*/classes; do
  [ -d "$d" ] && cp -r "$d/." "$NEW/"
done

python3 tools/pack.py fix --old-classes "$BASE" --new-classes "$NEW" --version "$VERSION" --serial "$SERIAL" --out dist
