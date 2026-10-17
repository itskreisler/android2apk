# android2apk

Build a small **signed Android APK from plain Java/Kotlin inside Alpine Linux** — no Gradle, no
Android Studio, no PC: `aapt2` → `javac`/`kotlinc` → `d8` → zip + align → `apksigner`.

## For agents

The instructions for creating, building, changing and installing an app live in the skill:

**`.opencode/skills/android2apk/SKILL.md`**

Read it first. OpenCode loads it automatically when this repo is opened; other agents that read
`AGENTS.md` should open that file before acting.

- Toolchain: `./setup-toolchain.sh` (once), `./setup-kotlin.sh` for Kotlin, `./setup-compose.sh` for Compose.
- Build: `./build.sh <app-dir>` (or `./build-compose.sh <app-dir>` for Compose) → `<app-dir>/build/app.apk`.
- Validate: `./lint.sh`.
- Human docs: [`README.md`](README.md) and [`docs/`](docs/).
