#!/bin/bash
# Build "con Compose": asegura todo (toolchain + Kotlin + AndroidX) y compila un proyecto Compose.
#   ./build-compose.sh <project-dir>      e.g. ./build-compose.sh examples/compose
# Equivale a: ./setup-compose.sh && ./build-apk.sh <project-dir>
set -euo pipefail
cd "$(dirname "$0")"
P="${1:?usage: ./build-compose.sh <project-dir>}"
./setup-compose.sh
./build-apk.sh "$P"
