# android2apk

Compilar un **APK Android firmado desde Java (o Kotlin) plano, dentro de Alpine Linux** — sin
Gradle, sin Android Studio y sin PC. Solo `aapt2` → `javac`/`kotlinc` → `d8` → zip + align → `apksigner`.

Es el pipeline de build *on-device* que usa [Buddy](https://github.com/ghorbelhamdi/buddy-android),
ejecutado **directamente en una shell Alpine**. Buddy normalmente lo corre dentro de un Alpine
embebido (vía [proot](https://proot-me.github.io/), porque Android solo deja ejecutar binarios desde
la carpeta nativa del APK); dentro de Alpine ese andamiaje no hace falta, así que llamamos a los
scripts directamente.

## Pipeline

```
res/ ──aapt2 compile──▶ res.zip
manifest + res.zip ──aapt2 link──▶ base.apk + R.java
java/ + R.java ──javac (+ .kt con kotlinc)──▶ .class ──d8──▶ classes.dex
base.apk + classes.dex ──zip (+alineado a 4 bytes)──▶ aligned.apk
aligned.apk ──apksigner──▶ app.apk ✅
```

## Empezar rápido

```sh
./setup-toolchain.sh     # una vez: JDK, aapt2, d8, apksigner, android.jar (~400 MB)
./build.sh examples/hello    # compila y firma → examples/hello/build/app.apk
```

Los proyectos de ejemplo viven en **`examples/`**.

Desde cero (Android → Alpine → opencode → primer APK):
**[docs/01-camino-simple.md](docs/01-camino-simple.md)**.

## Documentación

| Doc | Contenido |
|---|---|
| [docs/01-camino-simple.md](docs/01-camino-simple.md) | Montar Alpine desde Android (proot-distro), `apk add`, opencode y el primer APK |
| [docs/02-ejemplos.md](docs/02-ejemplos.md) | Los ejemplos (en `examples/`): `hello`, `pulse`, `notes`, `todo`, `khello` |
| [docs/03-kotlin.md](docs/03-kotlin.md) | Añadir Kotlin sin Gradle (etapa 1) |
| [docs/04-compose.md](docs/04-compose.md) | Jetpack Compose / Material 3 Expressive: qué haría falta |

> **Filosofía:** esto es el **camino simple** (Alpine + `javac`/`kotlinc` + `d8`). Si necesitas algo
> más "profesional" (Gradle, SDK completo, emulador, dependencias de AndroidX), este repo no es la
> herramienta: usa Android Studio o un `gradle` normal. Aquí primamos que quepa y funcione en el móvil.

## Reglas del proyecto (evitan los fallos habituales)

- Los paquetes empiezan por `com.buddyapps.` para no chocar con apps reales.
- `package="…"` en `<manifest>` y `android:exported="true"` en la activity de lanzamiento.
- Tema `@android:style/Theme.DeviceDefault` (sin AppCompat).
- UI en código (`LinearLayout`, `Button`, `TextView`…) o en `res/layout/`.
- Java o Kotlin + el framework de Android: sin AndroidX ni librerías externas.
  (Kotlin necesita `./setup-kotlin.sh` una vez; AndroidX/Compose es una etapa posterior.)
- Sube `versionCode` en `app.properties` en cada actualización.

Estas reglas (paquete `com.buddyapps.*`, `android:exported`, tema del sistema, sin AndroidX salvo
Compose) las comprueba **`./lint.sh`**.

## Scripts

| Archivo | Qué hace |
|---|---|
| `setup-toolchain.sh` | Instala `javac`, `aapt2`, `d8`, `apksigner`, `android.jar` (idempotente) |
| `setup-kotlin.sh` | Instala el compilador de Kotlin + `kotlin-stdlib.jar` (solo proyectos Kotlin) |
| `build-apk.sh <proyecto>` | Compila y firma `<proyecto>/build/app.apk` (Java y/o Kotlin) |
| `build.sh <proyecto>` | Toolchain + build en un solo comando |
| `check-compose-plugin.sh` | Verifica que el plugin de Compose compila `@Composable` (etapa 2) |
| `lint.sh` | Valida scripts (`shellcheck`), XML (`xmllint`) y las reglas de los proyectos |
| `SKILL.md` | La *skill* de agente: cómo crear/compilar/instalar una app con este repo |

Scripts y `SKILL.md` adaptados de **buddy-android** (Apache-2.0):
https://github.com/ghorbelhamdi/buddy-android
