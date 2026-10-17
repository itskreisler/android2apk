# 03 · Kotlin sin Gradle (etapa 1)

Por defecto el pipeline compila Java con `javac`. Añadir Kotlin fue la **etapa 1** hacia Compose
(AndroidX/Compose necesita Kotlin por narices). Aquí solo montamos el compilador y lo integramos en
`build-apk.sh`; Compose es otra etapa ([04-compose.md](04-compose.md)).

## Instalar el compilador

```sh
./setup-kotlin.sh
```

- Descarga el compilador oficial de Kotlin (~90 MB) a `~/kotlin`.
- Expone `kotlinc` (wrapper en `/usr/local/bin`).
- Deja `kotlin-stdlib.jar` listo para que `build-apk.sh` lo dexee dentro del APK.

Es idempotente y **consciente de la versión**: guarda la versión instalada en `~/kotlin/.kotlin-version`
y solo vuelve a descargar si cambia.

## Cómo compila el pipeline

El paso 2 de `build-apk.sh` ahora se llama `2/5 Java + Kotlin`:

1. Si hay `.kt` en `java/`, compila con
   `kotlinc -classpath android.jar -jvm-target 1.8 -nowarn -d "$OUT/classes" $KFILES`.
2. Compila los `.java` con `javac`, poniendo `$OUT/classes` en el classpath (para que el código Java
   pueda ver las clases Kotlin).
3. En el paso `3/5 Dex`, añade `kotlin-stdlib.jar` junto a las clases de la app al invocar `d8`.

Además, `build.sh` ejecuta `setup-kotlin.sh` automáticamente si el proyecto tiene algún `.kt`, así
que `./build.sh examples/khello` funciona sin pasos previos.

## La versión de Kotlin importa (metadata vs. `d8`)

El `d8` que usamos (R8 8.10.9, de build-tools 36) **solo entiende metadata de Kotlin hasta la 2.2.0**.
Con Kotlin 2.4.x la build **funciona**, pero `d8` imprime miles de:

```
Warning: An error occurred when parsing kotlin metadata…
Provided Metadata instance has version 2.4.0, while maximum supported version is 2.2.0.
```

Por eso fijamos **Kotlin 2.2.21** (metadata 2.2.0) en `setup-kotlin.sh`:

```sh
VER=${KOTLIN_VERSION:-2.2.21}
```

Puedes cambiarla con `KOTLIN_VERSION=… ./setup-kotlin.sh` si algún día tienes un `d8` más nuevo.

## Ejemplo `khello`

```
examples/khello/
  AndroidManifest.xml
  java/com/klei/khello/MainActivity.kt
  app.properties
```

```kotlin
package com.klei.khello

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val t = TextView(this).apply {
            text = "Hola desde Kotlin, sin Gradle"
            textSize = 22f
            gravity = Gravity.CENTER
        }
        setContentView(t)
    }
}
```

```sh
./build.sh examples/khello
# 1/5 Resources
# 2/5 Java + Kotlin
# 3/5 Dex
# 4/5 Package
# 5/5 Sign
# Built /root/android2apk/examples/khello/build/app.apk

apksigner verify examples/khello/build/app.apk && echo OK
unzip -p examples/khello/build/app.apk classes.dex | strings | grep -E "klei/khello/MainActivity|kotlin/jvm/internal/Intrinsics"
```

Nota: el APK de Kotlin pesa bastante más (~640 KB frente a ~10 KB del de Java) porque incluye
`kotlin-stdlib` dexada dentro.

## Regresión

Los ejemplos Java (`hello`, `pulse`, `notes`) siguen compilando igual: el paso de Java solo añade
`$OUT/classes` al classpath, y el de dex solo añade el stdlib si hubo `.kt`.
