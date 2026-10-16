# android2apk

Build a **signed Android APK from plain Java, inside Alpine Linux** — no Gradle, no
Android Studio, no PC. Just `aapt2` → `javac` → `d8` → zip + align → `apksigner`.

This is the on-phone build pipeline used by [Buddy](https://github.com/ghorbelhamdi/buddy-android),
run **directly in an Alpine shell**. Buddy normally runs it inside an embedded Alpine through
[proot](https://proot-me.github.io/) (because Android only lets apps execute binaries from the APK's
own library folder). Inside Alpine that scaffolding isn't needed, so we skip it and call the scripts
straight.

## Pipeline

```
res/ ──aapt2 compile──▶ res.zip
manifest + res.zip ──aapt2 link──▶ base.apk + R.java
java/ + R.java ──javac──▶ .class ──d8──▶ classes.dex
base.apk + classes.dex ──zip (+4-byte align)──▶ aligned.apk
aligned.apk ──apksigner──▶ app.apk ✅
```

## Steps

### 0. Base packages (once)

```sh
apk update -q && apk add -q bash curl jq libgcc libstdc++ ripgrep git \
  ca-certificates procps coreutils findutils nodejs npm
```

### 1. Toolchain (once, ~400 MB)

```sh
./setup-toolchain.sh
```

Installs OpenJDK 17 and Alpine's `aapt2` (`android-build-tools@edge`), then Google's
`d8.jar` + `apksigner.jar` (build-tools 36) and the Android 36 platform (`android.jar`).
`d8`/`apksigner` become small JVM wrappers in `/usr/local/bin`, so no glibc is needed.

Expected output:

```
Installing Java and aapt2…
Downloading d8 and apksigner (Android build-tools 36, 64 MB)…
Downloading the Android 36 platform (android.jar, 66 MB)…
Toolchain ready: javac 17.0.20, Android Asset Packaging Tool (aapt) 2.20-android-16.0.0_r2, android.jar at /root/android-sdk/android-36/android.jar
```

### 2. Hello world project

```
hello/
  AndroidManifest.xml                 package="com.buddyapps.hello"
  java/com/buddyapps/hello/MainActivity.java
  app.properties                      versionCode=1 versionName=1.0 minSdk=26
```

### 3. Build

```sh
./build-apk.sh hello
# or: ./build.sh hello   (toolchain + build in one shot)

1/5 Resources
2/5 Java
3/5 Dex
4/5 Package
5/5 Sign
Built /root/android2apk/hello/build/app.apk
```

### 4. Verify

```sh
aapt2 dump badging hello/build/app.apk | head -6
apksigner verify --verbose hello/build/app.apk
```

```
package: name='com.buddyapps.hello' versionCode='1' versionName='1.0' ... compileSdkVersion='36'
minSdkVersion:'26'
targetSdkVersion:'36'
launchable-activity: name='com.buddyapps.hello.MainActivity'

Verifies
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
```

The signed APK is at `hello/build/app.apk`. The signing key is created on first build at
`~/.buddy/keys/apps.jks` (RSA 2048). **Keep it**: updates must be signed with the same key.

### 5. Example 2: `pulse` — custom icon + resources

`pulse/` adds a `res/` tree, so `aapt2 compile` runs and the launcher gets its **own icon**:

```
pulse/
  AndroidManifest.xml                 package="com.buddyapps.pulse"
  res/drawable/ic_launcher.xml        a unique vector icon (108dp)
  res/layout/activity_main.xml        a real XML layout
  res/values/strings.xml              app_name / subtitle
  java/com/buddyapps/pulse/MainActivity.java   setContentView(R.layout.activity_main)
  app.properties
```

```sh
./build-apk.sh pulse
aapt2 dump badging pulse/build/app.apk | grep -E "application:|package:"
```

```
package: name='com.buddyapps.pulse' versionCode='1' versionName='1.0' ... compileSdkVersion='36'
application: label='Pulse' icon='res/drawable/ic_launcher.xml'
```

**How to give an APK its own icon.** Put a vector (or PNG) in `res/drawable/` and point the
manifest at it: `android:icon="@drawable/ic_launcher"` (and `android:roundIcon`). No Gradle needed —
`aapt2 link` bundles it and `resources.arsc` records the reference. For an adaptive icon, add
`res/mipmap-anydpi-v26/ic_launcher.xml` referencing `@drawable/ic_launcher_background` and
`@drawable/ic_launcher_foreground`.

## Can we use Jetpack Compose (Material 3 Expressive)?

**Not with this pipeline.** The snippet below is Kotlin + AndroidX, while `build-apk.sh` is
`javac` + `d8` with only `android.jar` on the classpath. See `expressive/Expressive.kt`.

```kotlin
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable

@Composable
fun MiAppExpressiva(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(motionScheme = MotionScheme.expressive(), content = content)
}
```

What we checked:

| Test | Result |
|---|---|
| `javac Expressive.kt` | `error: Class names, 'Expressive.kt', are only accepted if annotation processing is explicitly requested` |
| Kotlin compiler on Alpine | none (`apk search kotlin` → only `kotlin-language-server`) |
| `androidx` on classpath | none (only `android.jar`) |
| API exists? | yes — `material3-android:1.4.0` contains `MaterialExpressiveTheme` and `MotionScheme.expressive()` |

Compose needs, at minimum: a **Kotlin compiler**, the **Compose compiler plugin** (to lower
`@Composable`), the **AndroidX Compose AARs** and their transitive deps (`material3` alone pulls
`runtime`, `foundation`, `ui-text`, `foundation-layout`, `graphics-shapes`, `material3-*`,
`kotlin-stdlib`, …), **AAR resource/manifest merging** into `aapt2 link`, and **dexing all of it**.
That is a build system — i.e. this is the point where Gradle (or a large custom bootstrap) becomes
unavoidable.

## Project rules (avoid the usual failures)

- Package names start with `com.buddyapps.` so they never clash with real apps.
- Put `package="…"` on `<manifest>`, and `android:exported="true"` on the launcher activity.
- Use `android:theme="@android:style/Theme.DeviceDefault"` (no AppCompat).
- Build the UI in Java code (`LinearLayout`, `Button`, `TextView`…) unless you add layouts.
- Plain Java + the Android framework only: no AndroidX, no Kotlin, no external libraries.
- Bump `versionCode` in `app.properties` for every update.

## Scripts

| File | What it does |
|---|---|
| `setup-toolchain.sh` | Installs `javac`, `aapt2`, `d8`, `apksigner`, `android.jar` (idempotent) |
| `build-apk.sh <project>` | Builds and signs `<project>/build/app.apk` |
| `build.sh <project>` | Toolchain + build in one command |

Adapted from **buddy-android** (Apache-2.0): https://github.com/ghorbelhamdi/buddy-android

## Install on a phone (optional)

There's no Android here, so the APK is just a file. To install it, either copy it to the phone, or
serve it locally and let Buddy's `install_apk` tool fetch it:

```sh
python3 -m http.server 8080 --directory hello/build
# then ask Buddy to install_apk http://127.0.0.1:8080/app.apk
```
