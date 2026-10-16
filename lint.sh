#!/bin/bash
# lint.sh — valida scripts, XML y las reglas de los ejemplos de este repo.
#
#   ./lint.sh                 # scripts + todos los examples/
#   ./lint.sh examples/todo   # scripts + uno o varios proyectos
#
# Las reglas de los proyectos son las de SKILL.md (las que evitan los fallos habituales).
set -uo pipefail
cd "$(dirname "$0")" || exit 1
fail=0

say() { printf '\n== %s ==\n' "$1"; }

# --- 1) Scripts: shellcheck (el "eslint" de bash) --------------------------------
say "shellcheck (scripts .sh)"
if command -v shellcheck >/dev/null 2>&1; then
  if shellcheck build.sh build-apk.sh setup-toolchain.sh setup-kotlin.sh \
      check-compose-plugin.sh lint.sh; then
    echo "OK: sin avisos"
  else
    fail=1
  fi
else
  echo "omitido (instala con: apk add shellcheck)"
fi

# --- 2) XML: xmllint (manifest, layouts, drawables…) -----------------------------
say "xmllint (manifests y recursos)"
if command -v xmllint >/dev/null 2>&1; then
  found=0
  while IFS= read -r -d '' f; do
    found=1
    xmllint --noout "$f" || fail=1
  done < <(find examples -name '*.xml' -print0 2>/dev/null)
  [ "$found" = 1 ] && echo "OK: XML bien formado" || echo "(no hay XML)"
else
  echo "omitido (instala con: apk add libxml2-utils)"
fi

# --- 3) Reglas de los proyectos (SKILL.md) ---------------------------------------
say "reglas de proyectos"
if [ "$#" -gt 0 ]; then
  targets=("$@")
else
  targets=(examples/*/)
fi

for dir in "${targets[@]}"; do
  [ -d "$dir" ] || continue
  manifest="$dir/AndroidManifest.xml"
  # En modo automático, ignora carpetas que no son apps (p. ej. snippets de Compose).
  if [ "$#" -eq 0 ] && [ ! -f "$manifest" ]; then
    continue
  fi
  name=$(basename "${dir%/}")
  props="$dir/app.properties"
  errors=""

  if [ -f "$manifest" ]; then
    grep -q 'package="com\.buddyapps\.' "$manifest" \
      || errors="$errors\n    el <manifest> no usa package=\"com.buddyapps.*\""
    grep -q 'android:exported="true"' "$manifest" \
      || errors="$errors\n    la activity de lanzamiento no tiene android:exported=\"true\""
    grep -qE 'android:theme="@android:style/' "$manifest" \
      || errors="$errors\n    el tema no es @android:style/* (¿AppCompat?)"
  else
    errors="$errors\n    falta AndroidManifest.xml"
  fi

  if [ -f "$props" ]; then
    grep -q '^versionCode=' "$props" \
      || errors="$errors\n    app.properties sin versionCode"
  else
    errors="$errors\n    falta app.properties"
  fi

  # AndroidX solo se permite si el proyecto es Compose (compose=true).
  if [ -f "$props" ] && grep -q '^compose=true' "$props"; then
    :
  elif grep -rqE 'import +androidx\.' "$dir" 2>/dev/null; then
    errors="$errors\n    usa androidx.* sin compose=true (regla: sin AndroidX)"
  fi

  if [ -n "$errors" ]; then
    printf 'FALLO %s%b\n' "$name" "$errors"
    fail=1
  else
    printf 'OK    %s\n' "$name"
  fi
done

printf '\n'
if [ "$fail" = 0 ]; then
  echo "lint: todo OK"
else
  echo "lint: hay fallos" >&2
fi
exit "$fail"
