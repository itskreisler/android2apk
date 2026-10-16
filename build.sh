#!/bin/bash
# One-shot: ensure the toolchain, then build a project.
#   ./build.sh <project-dir>      e.g. ./build.sh hello
set -euo pipefail
cd "$(dirname "$0")"
P="${1:?usage: ./build.sh <project-dir>}"
./setup-toolchain.sh
if find "$P/java" -name '*.kt' 2>/dev/null | grep -q .; then
  ./setup-kotlin.sh
fi
./build-apk.sh "$P"
