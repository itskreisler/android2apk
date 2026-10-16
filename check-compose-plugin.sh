#!/bin/bash
# Etapa 2: comprueba que el plugin de Compose que trae Kotlin compila @Composable.
# No necesita AndroidX completo: solo descarga compose-runtime y verifica que el
# bytecode generado recibe el parámetro `Composer` (la transformación de Compose).
set -euo pipefail
cd "$(dirname "$0")"

SDK_JAR=${SDK_JAR:-$HOME/android-sdk/android-36/android.jar}
PLUGIN=${COMPOSE_PLUGIN:-$HOME/kotlin/kotlinc/lib/compose-compiler-plugin.jar}
RT_VER=${COMPOSE_RUNTIME_VERSION:-1.9.0}
CACHE=${XDG_CACHE_HOME:-$HOME/.cache}/android2apk
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT

[ -f "$PLUGIN" ] || { echo "Falta el plugin de Compose. Corre ./setup-kotlin.sh primero." >&2; exit 1; }
[ -f "$SDK_JAR" ] || { echo "Falta android.jar. Corre ./setup-toolchain.sh primero." >&2; exit 1; }

RT=$CACHE/compose-runtime-$RT_VER.jar
if [ ! -f "$RT" ]; then
  mkdir -p "$CACHE"
  echo "Bajando compose-runtime $RT_VER…"
  curl -fsSL -o "$OUT/rt.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/compose/runtime/runtime-android/$RT_VER/runtime-android-$RT_VER.aar"
  unzip -p "$OUT/rt.aar" classes.jar > "$RT"
fi

echo "Compilando examples/expressive/ComposeCheck.kt con -Xplugin=compose-compiler-plugin.jar…"
kotlinc -classpath "$SDK_JAR:$RT" -Xplugin="$PLUGIN" \
  -jvm-target 1.8 -nowarn -d "$OUT/out" examples/expressive/ComposeCheck.kt

echo "Firma generada:"
javap -p -classpath "$OUT/out" com.buddyapps.composecheck.ComposeCheckKt
if javap -p -classpath "$OUT/out" com.buddyapps.composecheck.ComposeCheckKt | grep -q "Composer"; then
  echo "OK: el plugin de Compose transformó @Composable (aparece Composer)."
else
  echo "FALLO: no se ve el parámetro Composer." >&2
  exit 1
fi
