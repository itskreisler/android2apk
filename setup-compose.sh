#!/bin/bash
# Etapa 3: prepara TODO lo necesario para compilar Compose, sin Gradle:
#   1) asegura el toolchain base (setup-toolchain.sh),
#   2) asegura el compilador de Kotlin (setup-kotlin.sh) — Compose es Kotlin,
#   3) descarga el cierre de dependencias de AndroidX (AAR/JAR) y lo prepara.
# Idempotente: solo baja lo que falta.
#
#   ./setup-compose.sh
#
# Resultado en ~/compose-libs/ (o $COMPOSE_LIBS):
#   jars/          classes.jar de cada AAR + los .jar sueltos  (classpath y dex)
#   res/<art>/     el res/ de cada AAR con recursos           (se fusionan)
#   res-merged/    res/ fusionado de todas las librerías      (aapt2 compile)
#   aars/          los .aar/.jar descargados
#   lib-jars.txt   lista de jars para compilar y dexear
#   res-dirs.txt   lista de directorios res/ (queda solo res-merged/)
#   packages.txt   packages de las AAR con código (aapt2 --extra-packages)
#   R.txt          unión de los R.txt (informativo)
set -euo pipefail
cd "$(dirname "$0")"

# Compose es Kotlin: primero el toolchain base y el compilador de Kotlin.
./setup-toolchain.sh
./setup-kotlin.sh

DEST=${COMPOSE_LIBS:-$HOME/compose-libs}
# ABIs nativos a incluir de los AAR (p. ej. graphics-path trae .so).
#   NATIVE_ABIS=all (por defecto) → todas las que traiga cada AAR (APK universal)
#   NATIVE_ABIS="arm64-v8a"       → solo arm64 (el teléfono suele ser arm64)
#   NATIVE_ABIS=""                → ninguna (APK 100% bytecode, universal)
NATIVE_ABIS=${NATIVE_ABIS-all}
DEPS=${COMPOSE_DEPS:-./compose-deps.txt}
GOOGLE=${GOOGLE_MAVEN:-https://dl.google.com/dl/android/maven2}
CENTRAL=${CENTRAL_MAVEN:-https://repo1.maven.org/maven2}

command -v curl >/dev/null || { echo "Falta curl." >&2; exit 1; }
command -v unzip >/dev/null || { echo "Falta unzip (apk add unzip)." >&2; exit 1; }
command -v python3 >/dev/null || { echo "Falta python3." >&2; exit 1; }
[ -f "$DEPS" ] || { echo "Falta $DEPS." >&2; exit 1; }

mkdir -p "$DEST/aars" "$DEST/jars" "$DEST/res"
rm -rf "$DEST/jni"
: > "$DEST/lib-jars.txt"
: > "$DEST/res-dirs.txt"
: > "$DEST/packages.txt"
: > "$DEST/R.txt"

downloaded=0
failed=0

while IFS=$'\t' read -r coord pkg; do
  [ -n "${coord:-}" ] || continue
  case "$coord" in \#*) continue ;; esac
  [ "${pkg:-}" = "pom" ] && continue
  g=${coord%%:*}; rest=${coord#*:}; a=${rest%%:*}; v=${rest##*:}
  gpath=${g//.//}
  fname="$a-$v.$pkg"
  artifact="$DEST/aars/$fname"

  if [ ! -f "$artifact" ]; then
    if ! curl -fsSL -o "$artifact" "$GOOGLE/$gpath/$a/$v/$fname" 2>/dev/null; then
      if ! curl -fsSL -o "$artifact" "$CENTRAL/$gpath/$a/$v/$fname" 2>/dev/null; then
        echo "  FALLO $coord" >&2
        rm -f "$artifact"
        failed=$((failed + 1))
        continue
      fi
    fi
    downloaded=$((downloaded + 1))
  fi

  if [ "$pkg" = "jar" ]; then
    cp -f "$artifact" "$DEST/jars/$fname"
    echo "$DEST/jars/$fname" >> "$DEST/lib-jars.txt"
    continue
  fi

  # AAR: classes.jar, res/, R.txt y package del manifest
  tmp="$DEST/.unpack.$$"
  rm -rf "$tmp"; mkdir -p "$tmp"
  unzip -o -q "$artifact" -d "$tmp"
  has_classes=0
  if [ -f "$tmp/classes.jar" ]; then
    mv -f "$tmp/classes.jar" "$DEST/jars/$a-$v.jar"
    echo "$DEST/jars/$a-$v.jar" >> "$DEST/lib-jars.txt"
    has_classes=1
  fi
  if [ -d "$tmp/res" ]; then
    rm -rf "$DEST/res/$a-$v"
    mv -f "$tmp/res" "$DEST/res/$a-$v"
    echo "$DEST/res/$a-$v" >> "$DEST/res-dirs.txt"
  fi
  # Bibliotecas nativas (p. ej. graphics-path): jni/<abi>/*.so → jni/<abi>/
  if [ -n "$NATIVE_ABIS" ] && [ -d "$tmp/jni" ]; then
    if [ "$NATIVE_ABIS" = all ]; then
      abis=$(find "$tmp/jni" -mindepth 1 -maxdepth 1 -type d | sed 's#.*/##')
    else
      abis=$NATIVE_ABIS
    fi
    for abi in $abis; do
      [ -d "$tmp/jni/$abi" ] || continue
      mkdir -p "$DEST/jni/$abi"
      cp -f "$tmp/jni/$abi"/*.so "$DEST/jni/$abi/" 2>/dev/null || true
    done
  fi
  if [ -f "$tmp/R.txt" ]; then
    cat "$tmp/R.txt" >> "$DEST/R.txt"
  fi
  if [ "$has_classes" = 1 ] && [ -f "$tmp/AndroidManifest.xml" ]; then
    pkgname=$(grep -o 'package="[^"]*"' "$tmp/AndroidManifest.xml" 2>/dev/null | head -1 | sed 's/package="//; s/"$//')
    # solo librerías con código: su bytecode referencia su propio R$...
    [ -n "$pkgname" ] && echo "$pkgname" >> "$DEST/packages.txt"
  fi
  rm -rf "$tmp"
done < "$DEPS"

sort -u -o "$DEST/packages.txt" "$DEST/packages.txt"

# Fusiona los recursos de las librerías en un único directorio, resolviendo los
# duplicados (mismo tipo+nombre+configuración) igual que hace AGP: gana el primero
# según el orden de res-dirs.txt. aapt2 no permite dos definiciones del mismo recurso.
python3 - "$DEST" <<'PY'
import os, shutil, sys, xml.etree.ElementTree as ET

dest = sys.argv[1]
res = os.path.join(dest, "res")
merged = os.path.join(dest, "res-merged")
order = [l.strip() for l in open(os.path.join(dest, "res-dirs.txt")) if l.strip()]

ET.register_namespace("tools", "http://schemas.android.com/tools")
ET.register_namespace("xliff", "urn:oasis:names:tc:xliff:document:1.2")

if os.path.isdir(merged):
    shutil.rmtree(merged)
os.makedirs(merged)

seen_values = set()   # (config, tipo, nombre) ya definidos
seen_files = set()    # rutas de recursos no-values ya copiadas
configs = {}          # config ("values", "values-af", ...) -> <resources> de salida


def local(tag):
    return tag.split("}", 1)[1] if "}" in tag else tag


for lib_res in order:
    for root, _dirs, files in os.walk(lib_res):
        rel = os.path.relpath(root, lib_res)
        parts = [] if rel == "." else rel.split(os.sep)
        config = parts[0] if parts and parts[0].startswith("values") else ""
        for fn in files:
            src = os.path.join(root, fn)
            relpath = os.path.join(rel, fn) if rel != "." else fn
            if not config:
                if relpath in seen_files:
                    continue
                seen_files.add(relpath)
                dst = os.path.join(merged, relpath)
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                shutil.copyfile(src, dst)
                continue
            # values*: acumula por configuración, descartando recursos ya definidos
            try:
                tree = ET.parse(src)
            except ET.ParseError:
                if relpath in seen_files:
                    continue
                seen_files.add(relpath)
                dst = os.path.join(merged, relpath)
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                shutil.copyfile(src, dst)
                continue
            src_root = tree.getroot()
            out_root = configs.get(config)
            if out_root is None:
                out_root = ET.Element("resources")
                out_root.attrib.update(src_root.attrib)
                configs[config] = out_root
            for el in list(src_root):
                tag = local(el.tag)
                name = el.get("name")
                if name is None:
                    out_root.append(el)
                    continue
                typ = (el.get("type") or "id") if tag == "item" else tag
                key = (config, typ, name)
                if key in seen_values:
                    continue
                seen_values.add(key)
                out_root.append(el)

for config, out_root in configs.items():
    d = os.path.join(merged, config)
    os.makedirs(d, exist_ok=True)
    ET.ElementTree(out_root).write(os.path.join(d, "merged.xml"),
                                   encoding="utf-8", xml_declaration=True)

print(f"  recursos fusionados: {len(seen_values)} valores, {len(seen_files)} ficheros, {len(configs)} configs")
PY
printf '%s\n' "$DEST/res-merged" > "$DEST/res-dirs.txt"

echo "Listo en $DEST"
echo "  bajados ahora: $downloaded   fallos: $failed"
echo "  jars: $(wc -l < "$DEST/lib-jars.txt")   res/: $(wc -l < "$DEST/res-dirs.txt")   packages: $(wc -l < "$DEST/packages.txt")"
if [ -d "$DEST/jni" ]; then
  echo "  nativos: $(find "$DEST/jni" -name '*.so' | wc -l) .so en $(find "$DEST/jni" -mindepth 1 -maxdepth 1 -type d | sed 's#.*/##' | tr '\n' ' ')"
fi
[ "$failed" = 0 ] || { echo "Hubo fallos; revisa la salida." >&2; exit 1; }
