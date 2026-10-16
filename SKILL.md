---
name: android2apk
description: Create, build and sign a small Android app (APK) from plain Java in Alpine, without Gradle or a computer. Use when the user asks to make, build, change or install an Android app.
---

# Building Android apps in Alpine

Everything runs in an Alpine shell. There is no Gradle and no Android Studio. Default to plain Java
and the Android framework only (no AndroidX, no external libraries). Kotlin is available through
`setup-kotlin.sh`, and Jetpack Compose through `setup-compose.sh`.

This skill lives in this repository. `SKILL_DIR` below means this folder.

Adapted from the `android-app` skill in **buddy-android** (Apache-2.0):
https://github.com/ghorbelhamdi/buddy-android

## 1. Toolchain (first time only)

```sh
bash $SKILL_DIR/setup-toolchain.sh
```

Downloads about 400 MB the first time (allow up to 10 minutes): OpenJDK 17, `aapt2`, `d8`,
`apksigner`, and the Android platform at `~/android-sdk/android-36/android.jar`. It is fast when
everything already exists, so run it before the first build.

For Kotlin projects, also:

```sh
bash $SKILL_DIR/setup-kotlin.sh
```

Downloads the Kotlin compiler (~90 MB) and exposes `kotlinc` + `kotlin-stdlib.jar`.

For **Jetpack Compose** projects, one command sets up everything (toolchain + Kotlin + AndroidX):

```sh
bash $SKILL_DIR/setup-compose.sh
```

It runs the two setups above and then downloads the AndroidX AAR/JAR closure (~50 MB) into
`~/compose-libs/` (jars, merged resources, native libs).

## 2. Project layout

Create one folder per app. Ready-made projects live in **`examples/`** — copy one as a starting
point: `hello` (Java mínimo), `pulse` (icono + `res/`), `notes` (app completa con layouts XML),
`todo` (lista de tareas con la UI en código), `khello` (Kotlin), `compose` (Jetpack Compose).

```
AndroidManifest.xml      <manifest package="com.buddyapps.<name>"> … one launcher <activity>
java/com/buddyapps/<name>/MainActivity.java
res/values/strings.xml   (optional; you can also build the UI in code)
res/drawable/ic_launcher.xml   (optional: a vector icon for this app)
app.properties           versionCode=1  versionName=1.0  minSdk=26
                         (add compose=true for a Compose project)
```

Rules that avoid the usual failures:
- Package names start with `com.buddyapps.` so they never clash with real apps.
- Put `package="…"` on `<manifest>`, and `android:exported="true"` on the launcher activity.
- Use `android:theme="@android:style/Theme.DeviceDefault"` (no AppCompat).
- Build the UI in Java code (LinearLayout, Button, TextView…) unless you use XML layouts.
- Java 8 syntax is fine (lambdas OK). Don't use `R.layout` unless you created that layout.
- For an icon, reuse `@android:drawable/sym_def_app_icon` or add `res/drawable/ic_launcher.xml`
  (a vector) and set `android:icon="@drawable/ic_launcher"`.

## 3. Build

```sh
bash $SKILL_DIR/build-apk.sh <app-dir>
```

Produces `<app-dir>/build/app.apk` plus a descriptive copy `<name>-<version>-<abi>.apk` (e.g.
`Tareas-1.0-universal.apk`), signed with `~/.buddy/keys/apps.jks` (created on first build). Set
`name=MiApp` in `app.properties` to control `<name>`; `<abi>` is `universal` without native code.
If compilation fails, read the error, fix the Java, and build again. Each update must bump
`versionCode` in `app.properties`.

For a **Compose** project (`compose=true` in `app.properties`) use the Compose one-shot instead;
it sets everything up and then builds:

```sh
bash $SKILL_DIR/build-compose.sh <app-dir>
```

Compose APKs have no native code by default (pure dex + resources → universal). One AndroidX AAR
ships `.so` files; `NATIVE_ABIS` in `setup-compose.sh` controls which ones are packaged
(`all` = universal, default; `arm64-v8a` = only arm64; empty = none).

## 4. Install (the user taps the final button)

Android's installer and Play Protect dialogs ignore taps from accessibility apps, so you can open
the installer but **the user has to tap Install/Update and confirm (often with a fingerprint)**.

1. Copy the APK to the phone (or serve it): `python3 -m http.server 8080 --directory <app-dir>/build`.
2. Ask Buddy to install it: `install_apk` with the APK path inside Buddy's Linux, or the local URL
   `http://127.0.0.1:8080/app.apk`.
3. Call `wait_for_install` with the package name (and `min_version_code` for an update). Do not tap
   Install yourself; never claim you did, and never write your own shell polling loop.
4. Open the new app and check it works, then report what you built.
