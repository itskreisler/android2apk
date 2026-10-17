# android2apk

Compilar un **APK Android firmado desde Java (o Kotlin) plano, dentro de Alpine Linux** — sin
Gradle, sin Android Studio y sin PC. Solo `aapt2` → `javac`/`kotlinc` → `d8` → zip + align → `apksigner`.

Es el pipeline de build *on-device* que usa **Klei**, ejecutado **directamente en una shell Alpine**.
Klei normalmente lo corre dentro de un Alpine
embebido (vía [proot](https://proot-me.github.io/), porque Android solo deja ejecutar binarios desde
la carpeta nativa del APK); dentro de Alpine ese andamiaje no hace falta, así que llamamos a los
scripts directamente.

## Pipeline

```
res/ ──aapt2 compile──▶ res.zip
manifest + res.zip ──aapt2 link──▶ base.apk + R.java
java/ + R.java ──javac (+ .kt con kotlinc)──▶ .class ──d8──▶ classes*.dex
base.apk + classes*.dex ──zip (+alineado a 4 bytes)──▶ aligned.apk
aligned.apk ──apksigner──▶ app.apk ✅
```

Además soporta **Kotlin** y, opcionalmente, **Jetpack Compose / Material 3** (etapa 3): baja las AAR
de AndroidX, las fusiona y las dexa junto a la app. Con Compose, `d8` suele pasar de 64K métodos y emite
**multidex** (`classes.dex`, `classes2.dex`…); el empaquetado los incluye **todos**.
Detalles en [docs/04-compose.md](docs/04-compose.md).

## Empezar rápido

Con Node instalado, la forma más directa de traer el repo es `degit` (copia limpia, sin historia de git):

```sh
npx -y degit itskreisler/android2apk   # o: git clone https://github.com/itskreisler/android2apk
cd android2apk
```

La *skill* vive en `.opencode/skills/android2apk/`: si abres `opencode` **dentro** de la carpeta del
repo la detecta sola, y el agente ya sabe crear, compilar y firmar APKs.

Hay dos caminos, cada uno con su **setup** y su **build**:

### A) Simple — Java o Kotlin, sin dependencias

```sh
./setup-toolchain.sh            # una vez: JDK, aapt2, d8, apksigner, android.jar (~400 MB)
./setup-kotlin.sh               # solo si el proyecto tiene .kt (~90 MB)
./build.sh examples/hello       # compila y firma → examples/hello/build/app.apk
```

### B) Con Compose — Material 3 Expressive

```sh
./setup-compose.sh                    # una vez: toolchain + Kotlin + AAR de AndroidX
./build-compose.sh examples/compose   # → examples/compose/build/app.apk
```

Cada compilación deja `build/app.apk` **y** una copia con nombre descriptivo
`<nombre>-<versión>-<abi>.apk` (p. ej. `Tareas-1.0-universal.apk`, `Compose-1.0-arm64-v8a.apk`).
El `<nombre>` sale de `name=` en `app.properties` (si falta, la carpeta del proyecto); `<abi>` es
`universal` cuando el APK no lleva código nativo.

Los proyectos de ejemplo viven en **`examples/`**.

Desde cero (Android → Alpine → opencode → primer APK):
**[docs/01-camino-simple.md](docs/01-camino-simple.md)**.

## Documentación

| Doc | Contenido |
|---|---|
| [docs/01-camino-simple.md](docs/01-camino-simple.md) | Montar Alpine desde Android (proot-distro), `apk add`, opencode y el primer APK |
| [docs/02-ejemplos.md](docs/02-ejemplos.md) | Los ejemplos (en `examples/`): `hello`, `pulse`, `notes`, `todo`, `khello`, `compose`, `neko` |
| [docs/03-kotlin.md](docs/03-kotlin.md) | Añadir Kotlin sin Gradle (etapa 1) |
| [docs/04-compose.md](docs/04-compose.md) | Jetpack Compose / Material 3 Expressive: cómo se compila (etapa 3) |
| [docs/05-neko-notes.md](docs/05-neko-notes.md) | Neko Notes: app Compose por fases (Keep + Notion), sin Room/Hilt |

> **Filosofía:** esto es el **camino simple** (Alpine + `javac`/`kotlinc` + `d8`). Si necesitas algo
> más "profesional" (Gradle, SDK completo, emulador), usa Android Studio o un `gradle` normal. Aquí
> primamos que quepa y funcione en el móvil. Compose entra porque es Kotlin puro y sus AAR se pueden
> resolver a mano; aun así, un proyecto Gradle sigue siendo lo cómodo para apps grandes.

## Reglas del proyecto (evitan los fallos habituales)

- Los paquetes empiezan por `com.klei.` para no chocar con apps reales.
- `package="…"` en `<manifest>` y `android:exported="true"` en la activity de lanzamiento.
- Tema `@android:style/Theme.DeviceDefault` (sin AppCompat).
- UI en código (`LinearLayout`, `Button`, `TextView`…) o en `res/layout/`.
- Java o Kotlin + el framework de Android: sin AndroidX ni librerías externas.
  (Kotlin necesita `./setup-kotlin.sh` una vez; AndroidX/Compose necesita `./setup-compose.sh`.)
- Sube `versionCode` en `app.properties` en cada actualización.
- **Iconos, no emojis**: para botones, pestañas y navegación usa iconos Material (`Icons.Filled.*`);
  el emoji se reserva para contenido (mascota o datos que guarda el usuario).
- **Icono propio**: no dejes el icono del sistema; añade `res/drawable/ic_launcher.xml` y apunta el
  manifest (`android:icon` + `android:roundIcon`). Pregunta al usuario si prefiere aportar una
  imagen o que el agente lo cree según la idea de la app.

Estas reglas (paquete `com.klei.*`, `android:exported`, tema del sistema, sin AndroidX salvo
Compose) las comprueba **`./lint.sh`**. Sobre iconos y emoji **no falla**: solo sugiere.

## Scripts

**Simple (setup + build)**

| Archivo | Qué hace |
|---|---|
| `setup-toolchain.sh` | **Setup simple**: instala `javac`, `aapt2`, `d8`, `apksigner`, `android.jar` (idempotente) |
| `setup-kotlin.sh` | Añade el compilador de Kotlin + `kotlin-stdlib.jar` (proyectos `.kt`) |
| `build.sh <proyecto>` | **Build simple**: `setup-toolchain` (+`setup-kotlin` si hay `.kt`) y compila, en un paso |
| `build-apk.sh <proyecto>` | El compilador en sí: Java, Kotlin y/o Compose → `<proyecto>/build/app.apk` |

**Con Compose (setup + build)**

| Archivo | Qué hace |
|---|---|
| `setup-compose.sh` | **Setup con Compose**: toolchain + Kotlin + cierre de AndroidX en `~/compose-libs/` |
| `build-compose.sh <proyecto>` | **Build con Compose**: `setup-compose` y compila, en un paso |
| `compose-deps.txt` | Lista fija de AAR/JAR de AndroidX que baja `setup-compose.sh` |
| `tools/resolve-compose-deps.py` | Regenera `compose-deps.txt` leyendo los POM de Maven |

**Extras**

| Archivo | Qué hace |
|---|---|
| `check-compose-plugin.sh` | Verifica que el plugin de Compose transforma `@Composable` (etapa 2) |
| `lint.sh` | Valida scripts (`shellcheck`), XML (`xmllint`), Python y las reglas de los proyectos |
| `.opencode/skills/android2apk/SKILL.md` | La *skill* de agente (OpenCode la descubre sola al abrir el repo): cómo crear/compilar/instalar una app |
| `AGENTS.md` | Punto de entrada para otros agentes: apunta a la skill |

Parte de este proyecto deriva de código de terceros; véase **[NOTICE](NOTICE)**
(licencia Apache-2.0).
