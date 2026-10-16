#!/bin/bash
# One-time setup of the Kotlin compiler for the no-Gradle pipeline (~90 MB). Safe to run again.
# Downloads the official Kotlin compiler and exposes `kotlinc`; keeps kotlin-stdlib.jar handy so
# build-apk.sh can dex it into the APK.
#
# Version note: Kotlin 2.2.x emits class metadata 2.2.0, which is the newest that `d8`
# (R8 8.10.9, shipped with build-tools 36) can parse. Newer Kotlin (2.3/2.4) builds fine but
# floods the dex step with "malformed kotlin.Metadata" warnings, so we pin to 2.2.21 by default.
# Override with KOTLIN_VERSION=… if a newer d8 is available.
set -euo pipefail
VER=${KOTLIN_VERSION:-2.2.21}
DEST=$HOME/kotlin
STAMP=$DEST/.kotlin-version

if [ ! -x "$DEST/kotlinc/bin/kotlinc" ] || [ "$(cat "$STAMP" 2>/dev/null || true)" != "$VER" ]; then
  echo "Downloading the Kotlin $VER compiler (~90 MB)…"
  rm -rf "$DEST"
  mkdir -p "$DEST"
  tmp=$(mktemp -d)
  curl -fsSL -o "$tmp/kotlin.zip" \
    "https://github.com/JetBrains/kotlin/releases/download/v$VER/kotlin-compiler-$VER.zip"
  unzip -q -o "$tmp/kotlin.zip" -d "$DEST"
  rm -rf "$tmp"
  echo "$VER" > "$STAMP"
fi

STDLIB=$DEST/kotlinc/lib/kotlin-stdlib.jar
if [ ! -f "$STDLIB" ]; then
  echo "Downloading kotlin-stdlib…"
  curl -fsSL -o "$STDLIB" \
    "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/$VER/kotlin-stdlib-$VER.jar"
fi

cat > /usr/local/bin/kotlinc <<W
#!/bin/sh
exec "$DEST/kotlinc/bin/kotlinc" "\$@"
W
chmod 755 /usr/local/bin/kotlinc

echo "Kotlin ready: $(kotlinc -version 2>&1)"
echo "kotlin-stdlib.jar at $STDLIB"
