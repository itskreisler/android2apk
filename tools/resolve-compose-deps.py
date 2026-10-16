#!/usr/bin/env python3
"""resolve-compose-deps.py — genera un cierre de dependencias AndroidX desde sus POM.

Lo usamos una vez para producir `compose-deps.txt` (la lista fija de AAR/JAR que baja
`setup-compose.sh`). Sirve para regenerar esa lista si cambias de versión.

No es un resolutor de Gradle: lee los `.pom` de Google Maven y Maven Central, sigue las
dependencias `compile`/`runtime`, y se queda con la versión más alta de cada artefacto.
Los módulos Kotlin Multiplatform publican un POM "base" que redirige a `-android`/`-jvm`;
este script prueba el artefacto tal cual y, si no existe, su variante `-android`/`-jvm`.

Uso:
    python3 tools/resolve-compose-deps.py <group:artifact:version> [más raíces...]

Ejemplo (lo que hay en compose-deps.txt):
    python3 tools/resolve-compose-deps.py \\
        androidx.compose.material3:material3-android:1.5.0-beta01 \\
        androidx.activity:activity-compose:1.8.2

Salida (stdout), una por línea y ordenada:
    group:artifact:version<TAB>aar|jar
"""
import os
import re
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

REPOS = [
    "https://dl.google.com/dl/android/maven2/",
    "https://repo1.maven.org/maven2/",
]
CACHE = os.environ.get("POM_CACHE", os.path.join(os.environ.get("TMPDIR", "/tmp"), "pomcache"))
os.makedirs(CACHE, exist_ok=True)


def fetch(url):
    key = os.path.join(CACHE, re.sub(r"[^A-Za-z0-9._-]", "_", url))
    if os.path.exists(key):
        return open(key, "rb").read()
    req = urllib.request.Request(url, headers={"User-Agent": "android2apk"})
    data = urllib.request.urlopen(req, timeout=60).read()
    open(key, "wb").write(data)
    return data


def path(g, a, v, ext):
    return f"{g.replace('.', '/')}/{a}/{v}/{a}-{v}.{ext}"


def try_fetch(g, a, v, ext):
    for base in REPOS:
        try:
            return fetch(base + path(g, a, v, ext))
        except urllib.error.HTTPError:
            continue
        except Exception:
            continue
    return None


def tag(e):
    return re.sub(r"\{.*\}", "", e.tag)


def vkey(v):
    """Clave de orden para versiones: 1.10.0 > 1.9.0 (comparación numérica por trozos)."""
    return [int(x) if x.isdigit() else x for x in re.split(r"[.\-+]", v)]


def clean_version(v):
    """Extrae una versión concreta de un rango tipo `[1.2.3]` o `(1.2.3,2.0)`."""
    v = v.strip()
    m = re.match(r"^[\[\(]([^,\]\)]+)", v)
    if m:
        return m.group(1).strip()
    return v


def resolve(g, a, v):
    """Devuelve (packaging, deps, artifact) probando el nombre dado y su -android/-jvm."""
    candidates = [(a, v)]
    if not a.endswith(("-android", "-jvm")):
        candidates.append((a + "-android", v))
        candidates.append((a + "-jvm", v))
    for name, ver in candidates:
        data = try_fetch(g, name, ver, "pom")
        if data is None:
            continue
        root = ET.fromstring(data)
        props = {}
        pel = root.find("{*}properties")
        if pel is not None:
            for c in pel:
                props[tag(c)] = (c.text or "").strip()
        packaging = "jar"
        for c in root:
            if tag(c) == "packaging":
                packaging = (c.text or "").strip()
        deps = []
        del_ = root.find("{*}dependencies")
        if del_ is not None:
            for d in del_:
                if tag(d) != "dependency":
                    continue
                gg, aa = d.find("{*}groupId"), d.find("{*}artifactId")
                vv, sc, op = d.find("{*}version"), d.find("{*}scope"), d.find("{*}optional")
                if gg is None or aa is None:
                    continue
                g2, a2 = gg.text.strip(), aa.text.strip()
                v2 = clean_version(vv.text) if vv is not None and vv.text else None
                scope = sc.text.strip() if sc is not None and sc.text else "compile"
                if op is not None and op.text and op.text.strip() == "true":
                    continue
                if scope in ("test", "provided", "system"):
                    continue
                if not v2 or v2 in ("unspecified",):
                    continue
                m = re.match(r"^\$\{(.+)\}$", v2)
                if m:
                    v2 = props.get(m.group(1), v2)
                deps.append((g2, a2, v2))
        return packaging, deps, name
    return None, None, a


# Artefactos que no hacen falta para compilar/dexear una app.
SKIP_A = ("lint", "espresso", "uiautomator", "benchmark", "ui-test", "ui-tooling")


def skip(g, a):
    if g.startswith("androidx.test"):
        return True
    if any(s in a for s in SKIP_A):
        return True
    if a.startswith("kotlin-stdlib"):
        return True
    if g == "org.jetbrains.kotlin":
        return True
    return False


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    stack = [tuple(x.split(":")) for x in sys.argv[1:]]
    resolved = {}  # group:artifact -> (version, packaging, deps)
    while stack:
        g, a, v = stack.pop()
        if skip(g, a):
            continue
        packaging, deps, real = resolve(g, a, v)
        if packaging is None:
            continue
        key = f"{g}:{real}"
        if key in resolved and vkey(resolved[key][0]) >= vkey(v):
            continue
        resolved[key] = (v, packaging, deps or [])
        for d in deps or []:
            stack.append(d)
    for key, (v, pkg, _deps) in sorted(resolved.items()):
        print(f"{key}:{v}\t{pkg}")


if __name__ == "__main__":
    main()
