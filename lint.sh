#!/bin/bash
# lint.sh — valida scripts, XML y las reglas de los ejemplos de este repo.
# Además sugiere mejoras (icono de app, emoji) SIN fallar por ellas.
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
  if shellcheck build.sh build-apk.sh build-compose.sh \
      setup-toolchain.sh setup-kotlin.sh setup-compose.sh \
      check-compose-plugin.sh lint.sh; then
    echo "OK: sin avisos"
  else
    fail=1
  fi
else
  echo "omitido (instala con: apk add shellcheck)"
fi

# --- 1b) Python: sintaxis de las herramientas -------------------------------------
say "python (tools/*.py)"
if command -v python3 >/dev/null 2>&1; then
  pyok=1
  for f in tools/*.py; do
    [ -f "$f" ] || continue
    python3 -c 'import ast, sys; ast.parse(open(sys.argv[1]).read())' "$f" || pyok=0
  done
  [ "$pyok" = 1 ] && echo "OK: sintaxis válida" || fail=1
else
  echo "omitido (falta python3)"
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
    grep -q 'package="com\.klei\.' "$manifest" \
      || errors="$errors\n    el <manifest> no usa package=\"com.klei.*\""
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

# --- 4) Sugerencias: NO fallan, solo orientan al usuario --------------------------
say "sugerencias (no fallan; solo orientan)"
python3 - "$@" <<'PY'
import os, sys, re, glob

args = sys.argv[1:]
dirs = args if args else sorted(d for d in glob.glob("examples/*") if os.path.isdir(d))

emoji_re = re.compile(
    "[\U0001F000-\U0001FAFF\U00002600-\U000027BF\U00002B00-\U00002BFF"
    "\U0001F1E6-\U0001F1FF\U00002190-\U000021FF\U0000FE0F]"
)

sin_icono, con_emoji = [], []
for d in dirs:
    man = os.path.join(d, "AndroidManifest.xml")
    if not os.path.isfile(man):
        continue
    name = os.path.basename(os.path.normpath(d))
    s = open(man, encoding="utf-8", errors="replace").read()
    m = re.search(r'android:icon="([^"]+)"', s)
    icon = m.group(1) if m else ""
    if (not icon) or "sym_def_app_icon" in icon:
        sin_icono.append(name)
    n = 0
    for dp, _dns, fns in os.walk(d):
        if os.sep + "build" + os.sep in dp + os.sep:
            continue
        for fn in fns:
            if fn.endswith((".java", ".kt")):
                try:
                    t = open(os.path.join(dp, fn), encoding="utf-8").read()
                except OSError:
                    continue
                n += len(emoji_re.findall(t))
    if n:
        con_emoji.append((name, n))

if sin_icono:
    print("Icono de app: usan el icono del sistema -> " + ", ".join(sin_icono))
    print("  \u00bfLe damos uno propio? Opciones:")
    print("    (1) el usuario aporta una imagen (PNG/SVG) y la convertimos a un vector")
    print("        en res/drawable/ic_launcher.xml (+ android:icon y android:roundIcon)")
    print("    (2) el agente lo crea seg\u00fan la idea de la app")
else:
    print("Icono de app: todos los proyectos tienen icono propio.")

if con_emoji:
    print("Emoji en fuentes (recuerda: Icons.* en botones/navegaci\u00f3n; emoji solo para contenido):")
    for name, c in con_emoji:
        print("  - %s: %d emoji(s)" % (name, c))

print("(ninguna sugerencia es un error: lint sigue en verde)")
PY

printf '\n'
if [ "$fail" = 0 ]; then
  echo "lint: todo OK"
else
  echo "lint: hay fallos" >&2
fi
exit "$fail"
