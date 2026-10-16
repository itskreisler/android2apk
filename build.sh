#!/bin/bash
# One-shot: ensure the toolchain, then build a project.
#   ./build.sh <project-dir>      e.g. ./build.sh hello
set -euo pipefail
cd "$(dirname "$0")"
./setup-toolchain.sh
./build-apk.sh "${1:?usage: ./build.sh <project-dir>}"
