# 01 · El camino simple: de Android a tu primer APK

La ruta "mínima" que seguimos: montar un Alpine Linux dentro de Android (sin root, con
[proot-distro](https://github.com/termux/proot-distro)), instalar lo básico, y compilar un APK con
los scripts de este repo. Si quieres una configuración más "de escritorio" (Gradle, SDK completo,
emulador), este no es el sitio: mira la nota al final.

> Todo esto funciona igual en cualquier Alpine Linux (contenedor, VM, chroot). La parte de
> proot-distro solo aplica a Android/Termux.

## 1. Instalar Alpine (desde Android, en Termux)

```sh
pkg install proot-distro        # en Termux (una vez)
pd install -n alpine https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/aarch64/alpine-minirootfs-3.24.2-aarch64.tar.gz
pd login alpine
```

- `pd` es el alias de `proot-distro`.
- `-n alpine` pone nombre a la distro.
- La URL es el *minirootfs* de Alpine 3.24 para `aarch64`.

Ya dentro de Alpine, actualiza e instala las herramientas base:

```sh
apk update -q && apk add -q bash curl jq libgcc libstdc++ ripgrep git \
  ca-certificates procps coreutils findutils nodejs npm unzip python3
```

## 2. Instalar opencode y abrir la sesión

```sh
npm i -g @opencode/cli      # o el instalador oficial de https://opencode.ai
opencode
```

Esto deja el binario en `~/.opencode/bin/opencode` (en este entorno, opencode v2.0.31).
A partir de aquí, el agente te ayuda a crear y compilar las apps.

## 3. Traer el repo y los scripts

Clona este repositorio (o usa la carpeta donde ya lo tengas) y ejecuta el setup:

```sh
git clone <URL-del-repo> android2apk   # si aún no lo tienes
cd android2apk
./setup-toolchain.sh
```

`setup-toolchain.sh` (~400 MB la primera vez, hasta 10 min) instala:

- **OpenJDK 17** y `aapt2` (de `android-build-tools@edge` de Alpine),
- `d8.jar` y `apksigner.jar` (Android build-tools 36), envueltos como scripts en `/usr/local/bin`,
- `android.jar` de la plataforma 36 en `~/android-sdk/android-36/android.jar`.

Es **idempotente**: si ya está todo, termina en segundos.

## 4. Crear y compilar un APK sencillo

Un proyecto mínimo es una carpeta con tres cosas:

```
examples/hello/
  AndroidManifest.xml      package="com.klei.hello"
  java/com/klei/hello/MainActivity.java
  app.properties           versionCode=1  versionName=1.0  minSdk=26
```

Y se compila con:

```sh
./build-apk.sh /ruta/a/hello
```

Salida:

```
1/5 Resources
2/5 Java + Kotlin
3/5 Dex
4/5 Package
5/5 Sign
Built /ruta/a/hello/build/app.apk
```

El APK queda firmado en `<proyecto>/build/app.apk`. La clave de firma se crea sola la primera vez en
`~/.klei/keys/apps.jks` (RSA 2048). **Consérvala**: las actualizaciones deben firmarse con la misma
clave.

Verificar:

```sh
aapt2 dump badging examples/hello/build/app.apk | head -6
apksigner verify --verbose examples/hello/build/app.apk
# Verified using v2 scheme …: true
# Verified using v3 scheme …: true
```

## 5. Compilar el primer ejemplo

Con la toolchain lista, compila cualquiera de los ejemplos del repo:

```sh
./build.sh examples/hello   # toolchain + build en un paso
```

El repo también trae soporte de **Kotlin** (`./setup-kotlin.sh`) y **Compose** (`./setup-compose.sh`), con ejemplos para ambos.

Sigue en **[02-ejemplos.md](02-ejemplos.md)**.

## Instalar el APK en el teléfono (opcional)

Aquí no hay Android: el APK es solo un archivo. Para instalarlo, cópialo al teléfono, o sírvelo
localmente y deja que Klei lo instale con su herramienta `install_apk`:

```sh
python3 -m http.server 8080 --directory examples/hello/build
# luego pídele a Klei: install_apk http://127.0.0.1:8080/app.apk
```

El instalador de Android y Play Protect ignoran los toques de apps de accesibilidad, así que **el
usuario tiene que pulsar Instalar/Actualizar** (a menudo con huella). Después, abre la app y comprueba
que funciona.

## ¿Y si quiero algo más profesional?

Este pipeline está pensado para ser **simple y caber en el móvil**: Java/Kotlin plano, sin AndroidX,
sin Gradle. Si tu app necesita dependencias (Compose, Room, Retrofit…), lo natural es un proyecto
Gradle normal en un PC. Ver [04-compose.md](04-compose.md) para el caso concreto de Compose.
