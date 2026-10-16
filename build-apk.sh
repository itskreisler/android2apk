#!/bin/bash
# Build a signed APK from a plain-Java Android project, without Gradle.
#   build-apk.sh <project-dir>
# Project layout:
#   <project>/AndroidManifest.xml   (with package="..." on <manifest>)
#   <project>/java/**.java
#   <project>/res/...               (optional)
#   <project>/assets/...            (optional)
#   <project>/app.properties        (optional: versionCode=1, versionName=1.0, minSdk=26, name=MiApp)
# Output: <project>/build/app.apk  (+ <name>-<version>-<abi>.apk)
#
# Adapted from buddy-android (Apache-2.0): https://github.com/ghorbelhamdi/buddy-android
set -euo pipefail
P=$(cd "${1:?usage: build-apk.sh <project-dir>}" && pwd)
SDK_JAR=${SDK_JAR:-$HOME/android-sdk/android-36/android.jar}
KEYDIR=$HOME/.buddy/keys
OUT=$P/build

prop() { [ -f "$P/app.properties" ] && sed -n "s/^$1=//p" "$P/app.properties" | head -1 || true; }
VCODE=$(prop versionCode); VCODE=${VCODE:-1}
VNAME=$(prop versionName); VNAME=${VNAME:-1.0}
MINSDK=$(prop minSdk); MINSDK=${MINSDK:-26}
COMPOSE=$(prop compose); COMPOSE=${COMPOSE:-false}
LIBS=${COMPOSE_LIBS:-$HOME/compose-libs}
COMPOSE_PLUGIN=${COMPOSE_PLUGIN:-$HOME/kotlin/kotlinc/lib/compose-compiler-plugin.jar}
# AndroidX Compose se publica con JVM target 11 (inline de bytecode); el resto, 8.
JVM_TARGET=$(prop jvmTarget); JVM_TARGET=${JVM_TARGET:-$([ "$COMPOSE" = true ] && echo 11 || echo 8)}

[ -f "$SDK_JAR" ] || { echo "android.jar missing at $SDK_JAR. Run setup-toolchain.sh first." >&2; exit 1; }
for t in javac d8 aapt2 apksigner keytool python3; do
  command -v $t >/dev/null || { echo "$t missing. Run setup-toolchain.sh first." >&2; exit 1; }
done
[ -f "$P/AndroidManifest.xml" ] || { echo "No AndroidManifest.xml in $P" >&2; exit 1; }

rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "1/5 Resources"
RES=()
if [ -d "$P/res" ] && [ -n "$(ls -A "$P/res")" ]; then
  aapt2 compile --dir "$P/res" -o "$OUT/res.zip"
  RES=("$OUT/res.zip")
fi
EXTRA=()
if [ "$COMPOSE" = true ]; then
  [ -f "$LIBS/lib-jars.txt" ] || { echo "Faltan las librerías de Compose. Corre ./setup-compose.sh." >&2; exit 1; }
  i=0
  while IFS= read -r rdir; do
    [ -n "$rdir" ] || continue
    aapt2 compile --dir "$rdir" -o "$OUT/libres-$i.zip"
    RES+=("$OUT/libres-$i.zip")
    i=$((i + 1))
  done < "$LIBS/res-dirs.txt"
  # Cada librería referencia su propio R$... ; aapt2 genera esas R con --extra-packages.
  while IFS= read -r pkg; do
    [ -n "$pkg" ] && EXTRA+=(--extra-packages "$pkg")
  done < "$LIBS/packages.txt"
fi
ASSETS=()
[ -d "$P/assets" ] && ASSETS=(-A "$P/assets")
aapt2 link -I "$SDK_JAR" --manifest "$P/AndroidManifest.xml" \
  --min-sdk-version "$MINSDK" --target-sdk-version 36 \
  --version-code "$VCODE" --version-name "$VNAME" \
  "${ASSETS[@]}" --java "$OUT/gen" -o "$OUT/base.apk" "${RES[@]}" "${EXTRA[@]}"

echo "2/5 Java + Kotlin"
CP="$SDK_JAR:$OUT/classes"
if [ "$COMPOSE" = true ]; then
  CP="$CP:$(tr '\n' ':' < "$LIBS/lib-jars.txt")"
fi
KFILES=$(find "$P/java" -name '*.kt' 2>/dev/null || true)
JFILES=$(find "$P/java" "$OUT/gen" -name '*.java' 2>/dev/null || true)
if [ -n "$KFILES" ]; then
  command -v kotlinc >/dev/null || { echo "kotlinc missing for .kt files. Run setup-kotlin.sh first." >&2; exit 1; }
  KPLUGIN=()
  if [ "$COMPOSE" = true ]; then
    [ -f "$COMPOSE_PLUGIN" ] || { echo "Falta el plugin de Compose ($COMPOSE_PLUGIN). Corre ./setup-kotlin.sh." >&2; exit 1; }
    KPLUGIN=(-Xplugin="$COMPOSE_PLUGIN")
  fi
  # shellcheck disable=SC2086
  kotlinc -classpath "$CP" "${KPLUGIN[@]}" -jvm-target "$JVM_TARGET" -nowarn -d "$OUT/classes" $KFILES
fi
if [ -n "$JFILES" ]; then
  # shellcheck disable=SC2086
  javac --release "$JVM_TARGET" -classpath "$CP" -Xlint:-options -encoding UTF-8 -nowarn \
    -d "$OUT/classes" $JFILES
fi

echo "3/5 Dex"
DEX_IN=$(find "$OUT/classes" -name '*.class')
if [ -n "$KFILES" ]; then
  KOTLIN_STDLIB=${KOTLIN_STDLIB:-$HOME/kotlin/kotlinc/lib/kotlin-stdlib.jar}
  [ -f "$KOTLIN_STDLIB" ] || { echo "kotlin-stdlib.jar missing. Run setup-kotlin.sh first." >&2; exit 1; }
  DEX_IN="$DEX_IN $KOTLIN_STDLIB"
fi
if [ "$COMPOSE" = true ]; then
  DEX_IN="$DEX_IN $(tr '\n' ' ' < "$LIBS/lib-jars.txt")"
fi
# shellcheck disable=SC2086
d8 --release --min-api "$MINSDK" --lib "$SDK_JAR" --output "$OUT/dex" $DEX_IN

echo "4/5 Package"
JNI_DIR=""
if [ "$COMPOSE" = true ] && [ -d "$LIBS/jni" ]; then
  JNI_DIR=$LIBS/jni
  echo "  nativos: $(find "$JNI_DIR" -name '*.so' | wc -l) .so ($(find "$JNI_DIR" -mindepth 1 -maxdepth 1 -type d | sed 's#.*/##' | tr '\n' ' '))"
fi
python3 - "$OUT/base.apk" "$OUT/dex/classes.dex" "$OUT/aligned.apk" "$JNI_DIR" <<'EOF'
import sys, os, zipfile
src, dex, dst, jni = sys.argv[1:5]
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w") as zout:
    entries = [(i, zin.read(i.filename)) for i in zin.infolist()]
    entries.append((zipfile.ZipInfo("classes.dex"), open(dex, "rb").read()))
    entries[-1][0].compress_type = zipfile.ZIP_DEFLATED
    if jni and os.path.isdir(jni):  # lib/<abi>/*.so venidos de las AAR
        for abi in sorted(os.listdir(jni)):
            adir = os.path.join(jni, abi)
            if not os.path.isdir(adir):
                continue
            for fn in sorted(os.listdir(adir)):
                if not fn.endswith(".so"):
                    continue
                data = open(os.path.join(adir, fn), "rb").read()
                zi = zipfile.ZipInfo("lib/%s/%s" % (abi, fn))
                zi.compress_type = zipfile.ZIP_DEFLATED
                entries.append((zi, data))
    for info, data in entries:
        out = zipfile.ZipInfo(info.filename, date_time=info.date_time)
        out.compress_type = info.compress_type
        out.external_attr = info.external_attr
        if out.compress_type == zipfile.ZIP_STORED:  # align stored data (resources.arsc) to 4 bytes
            start = zout.fp.tell() + 30 + len(out.filename.encode())
            out.extra = b"\0" * ((-start) % 4)
        zout.writestr(out, data)
EOF

echo "5/5 Sign"
if [ ! -f "$KEYDIR/apps.jks" ]; then
  mkdir -p "$KEYDIR"; chmod 700 "$KEYDIR"
  head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24 > "$KEYDIR/password"
  chmod 600 "$KEYDIR/password"
  keytool -genkeypair -keystore "$KEYDIR/apps.jks" -alias apps -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$(cat "$KEYDIR/password")" -keypass "$(cat "$KEYDIR/password")" -dname "CN=Buddy apps" >/dev/null 2>&1
fi
apksigner sign --ks "$KEYDIR/apps.jks" --ks-key-alias apps --ks-pass "file:$KEYDIR/password" \
  --out "$OUT/app.apk" "$OUT/aligned.apk"
apksigner verify "$OUT/app.apk"

# Copia con nombre descriptivo:  <nombre>-<versionName>-<abi>.apk
#   nombre: app.properties (name=MiApp) > variable APK_NAME > carpeta del proyecto
#   abi:    arquitecturas empaquetadas (lib/<abi>/), o "universal" sin código nativo
APK_NAME=${APK_NAME:-$(prop name)}
APK_NAME=${APK_NAME:-$(basename "$P")}
APK_NAME=$(printf '%s' "$APK_NAME" | tr ' ' '-' | tr -cd 'A-Za-z0-9._-')
VNAME_TAG=$(printf '%s' "$VNAME" | tr -cd 'A-Za-z0-9._-')
ABIS=""
if [ -n "$JNI_DIR" ]; then
  ABIS=$(find "$JNI_DIR" -mindepth 1 -maxdepth 1 -type d |
    sed 's#.*/##' | LC_ALL=C sort | tr '\n' '_' | sed 's/_$//')
fi
case "$ABIS" in
  "" | arm64-v8a_armeabi-v7a_x86_x86_64) ABI_TAG=universal ;;
  *) ABI_TAG=$ABIS ;;
esac
APK_FILE="$APK_NAME-$VNAME_TAG-$ABI_TAG.apk"
ln -f "$OUT/app.apk" "$OUT/$APK_FILE" 2>/dev/null || cp -f "$OUT/app.apk" "$OUT/$APK_FILE"
echo "Built $OUT/$APK_FILE"
echo "      $OUT/app.apk  (ruta estable)"
