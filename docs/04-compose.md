# 04 · Jetpack Compose / Material 3 Expressive

La pregunta: ¿se puede usar este snippet de **Material 3 Expressive** con este pipeline?

```kotlin
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable

@Composable
fun MiAppExpressiva(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        motionScheme = MotionScheme.expressive(), // física de movimiento expresiva
        content = content
    )
}
```

**Respuesta: sí.** Está compilado de verdad en [`examples/compose/`](../examples/compose/): Kotlin +
AndroidX + Material 3, sin Gradle.

```sh
./setup-compose.sh                    # una vez (toolchain + Kotlin + AAR de AndroidX)
./build-compose.sh examples/compose   # → examples/compose/build/app.apk (~5,5 MB, firmado)
```

## Cómo se construye (dos scripts)

1. **`setup-compose.sh`** — idempotente. Corre `setup-toolchain.sh` y `setup-kotlin.sh` y, después,
   baja el *cierre* de dependencias de AndroidX listado en [`compose-deps.txt`](../compose-deps.txt) a
   `~/compose-libs/`. De cada AAR extrae su `classes.jar` y su `res/`, **fusiona** los recursos y anota
   el `package` de cada librería para generar su `R`.
2. **`build-compose.sh <proyecto>`** — `setup-compose.sh` + `build-apk.sh`. El APK queda en
   `<proyecto>/build/app.apk`.

Si ya corriste el setup, basta con `./build-apk.sh examples/compose`.

## Qué añade Compose al pipeline

El pipeline normal compila `javac`/`kotlinc` con solo `android.jar`. Compose añade tres cosas:

1. El **plugin compilador de Compose**, que reescribe las funciones `@Composable`.
2. Las **librerías AndroidX** (runtime, ui, foundation, material3…), cada una una AAR con su
   `classes.jar` **y su `res/`** (casi todo traducciones `values-*`).
3. **Dexear** todo eso junto con la app.

## Etapas (todas hechas)

| Etapa | Estado |
|---|---|
| 1. Kotlin (`kotlinc` + stdlib → `d8`) | ✅ — [03-kotlin.md](03-kotlin.md) |
| 2. Plugin compilador de Compose | ✅ — `check-compose-plugin.sh` |
| 3. Resolver AAR de AndroidX + fusionar recursos en `aapt2 link` | ✅ — `setup-compose.sh` |
| 4. Dexear Compose + AndroidX | ✅ — `build-apk.sh` (rama Compose) |

## Etapa 2 — el plugin de Compose (hecho)

Desde Kotlin 2.0, el plugin de Compose **viene dentro del compilador**: en este entorno está en
`~/kotlin/kotlinc/lib/compose-compiler-plugin.jar`. No hay que descargarlo aparte.

Se pasa a `kotlinc` con `-Xplugin`, y basta tener `compose-runtime` en el classpath (para la anotación
`@Composable`). Verifícalo con:

```sh
./check-compose-plugin.sh
```

Que compila `examples/expressive/ComposeCheck.kt` y muestra que la función ahora recibe un `Composer`:

```
public final class com.buddyapps.composecheck.ComposeCheckKt {
  public static final void Saludo(java.lang.String, androidx.compose.runtime.Composer, int);
}
OK: el plugin de Compose transformó @Composable (aparece Composer).
```

Esa firma `(String, Composer, int)` es la prueba: el plugin insertó el `Composer` y la máscara
`$changed`, es decir, la transformación de Compose ocurrió.

## Etapa 3 — las librerías AndroidX (hecho)

`setup-compose.sh` resuelve el grafo sin Gradle:

- **Versiones fijadas** en [`compose-deps.txt`](../compose-deps.txt): **88 dependencias** (69 `.aar` +
  19 `.jar`). Raíces: `androidx.compose.material3:material3-android:1.5.0-beta01` y
  `androidx.activity:activity-compose:1.8.2`; de ahí sale el tren **Compose 1.12.0**. El fichero se
  regenera con [`tools/resolve-compose-deps.py`](../tools/resolve-compose-deps.py), que recorre los
  POM de Maven.
- **Descarga**: de Google Maven y, si falla, de Maven Central, con el patrón
  `…/<grupo con / >/<artefacto>/<versión>/<artefacto>-<versión>.<aar|jar>`.
- **Recursos**: se extrae el `res/` de cada AAR y se **fusiona** en `res-merged/`. Gana el primero que
  define `(config, tipo, nombre)`; así se resuelven duplicados como `string/autofill`, presente a la
  vez en `ui` y `foundation`. Después, `aapt2 compile` una sola vez y `aapt2 link` con
  **`--extra-packages <package>`** por cada librería → genera su `R.java` y deja resolubles las
  referencias del estilo `androidx/compose/material3/R$string.*`.
- **Compilación**: `kotlinc -Xplugin=compose-compiler-plugin.jar` con **`-jvm-target 11`** (AndroidX se
  publica con target 11).
- **Dex**: `d8` con las clases de la app + `kotlin-stdlib` + los **74 `.jar`** de AndroidX (55 salen de
  AAR con `classes.jar` real + 19 `.jar` directos; se saltan los *stubs* KMP vacíos).

### Código nativo y ABI

Por defecto **el APK no lleva código nativo** (todo es dex + recursos), así que es **universal**: corre
en arm64-v8a, armeabi-v7a, x86 y x86_64. Una única AAR, `androidx.graphics:graphics-path`, trae `.so`
(`libandroidx.graphics.path.so`); `ui-graphics` la usa **solo en Android < 14** (en Android 14+ tira de
la API nativa del sistema). Nuestra app (Text/Button/Column) no la necesita.

`NATIVE_ABIS` decide qué `.so` se empaquetan en `lib/<abi>/`:

```sh
NATIVE_ABIS=all        ./setup-compose.sh   # todas las ABIs (APK universal con nativos) [por defecto]
NATIVE_ABIS=arm64-v8a  ./setup-compose.sh   # solo arm64 (el móvil típico)
NATIVE_ABIS=           ./setup-compose.sh   # ninguna → APK 100% dex, universal
```

## Etapa 4 — dexear Compose + AndroidX (hecho)

`build-apk.sh` detecta `compose=true` en `app.properties` y entonces:

- compila con `kotlinc` (no `javac`) poniendo en el classpath los 74 `.jar` de `~/compose-libs/jars/`,
- pasa `-Xplugin` y `-jvm-target 11`,
- en `3/5 Dex` añade esas mismas jars a `d8`,
- en `4/5 Package` añade `lib/<abi>/*.so` si `NATIVE_ABIS` los dejó preparados.

Resultado medido:

```
examples/compose/build/app.apk   5.578.584 bytes (~5,3 MiB)
  classes.dex       ~14,7 MB en crudo
  resources.arsc      500 KB
package=com.buddyapps.compose   minSdk 26   targetSdk 36   (firmado)
```

## Versiones: por qué `material3 1.5.0-beta01`

`MaterialExpressiveTheme` y `MotionScheme` **existen** en `material3 1.4.0`, pero están marcados
`internal`: usarlos desde una app da `error: … it is internal in file` al compilar. Pasaron a
**públicos en `1.5.0-beta01`**, la versión que fijamos. Comprobado: el snippet compila con `1.5.0-beta01`
y falla con `1.4.0`.

## Enlaces usados

Repositorios de artefactos:

- Google Maven: <https://dl.google.com/dl/android/maven2/>
- Maven Central: <https://repo1.maven.org/maven2/>

Versiones y API:

- [Releases de `compose-material3`](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- [`MaterialExpressiveTheme`](https://developer.android.com/reference/kotlin/androidx/compose/material3/MaterialExpressiveTheme.composable)
- [`MotionScheme`](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme)
- [Material 3 en Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Mapeo de versiones del BOM de Compose](https://developer.android.com/develop/ui/compose/bom/bom-mapping)
- [Releases de Kotlin](https://github.com/JetBrains/kotlin/releases) · [plugin de Compose](https://kotlinlang.org/docs/compose-compiler-migration-guide.html)

Toolchain (los baja `setup-toolchain.sh`):

- build-tools 36: <https://dl.google.com/android/repository/build-tools_r36_linux.zip>
- plataforma 36: <https://dl.google.com/android/repository/platform-36_r02.zip>
- base: [buddy-android](https://github.com/ghorbelhamdi/buddy-android) (Apache-2.0)

## Límites

Aquí **no hay Android ni emulador**: el APK de Compose se verifica solo hasta *compila, enlaza, dexa y
firma* (`aapt2 dump badging`, `apksigner verify`). No se puede probar en ejecución.

## Alternativa si de verdad quieres Compose

Un proyecto **Gradle** normal (Android Studio o `./gradlew` en un PC) resuelve el grafo, fusiona
recursos y lo dexa en una línea. Este repo es para lo otro: apps pequeñas que se compilan **en el
propio móvil**, sin dependencias y en segundos.
