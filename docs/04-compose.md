# 04 · Jetpack Compose / Material 3 Expressive

La pregunta: ¿se puede usar este snippet de Material 3 Expressive con este pipeline?

```kotlin
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable

@Composable
fun MiAppExpressiva(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(motionScheme = MotionScheme.expressive(), content = content)
}
```

**Con el pipeline actual, no.** El snippet es Kotlin + AndroidX, y `build-apk.sh` compila solo con
`javac`/`kotlinc` y `android.jar` en el classpath. El archivo queda en `expressive/Expressive.kt`.

Lo que comprobamos:

| Prueba | Resultado |
|---|---|
| `javac Expressive.kt` | `error: Class names, 'Expressive.kt', are only accepted if annotation processing is explicitly requested` |
| Compilador de Kotlin en Alpine | ninguno (`apk search kotlin` → solo `kotlin-language-server`)¹ |
| `androidx` en el classpath | nada (solo `android.jar`) |
| ¿Existe la API? | sí — `material3-android:1.4.0` contiene `MaterialExpressiveTheme` y `MotionScheme.expressive()` |

¹ Ya resuelto en la etapa 1: ahora usamos el compilador oficial de Kotlin ([03-kotlin.md](03-kotlin.md)).

## Qué haría falta

Compose necesita, como mínimo:

1. Un **compilador de Kotlin** — ✅ hecho (etapa 1).
2. El **plugin compilador de Compose** (el `compose-compiler`/`kotlin-compose` plugin), que transforma
   las funciones `@Composable` en bytecode. `kotlinc` solo no basta.
3A. Las **AAR de AndroidX Compose** y todas sus dependencias transitivas. Solo `material3` arrastra
   `runtime`, `foundation`, `ui-text`, `foundation-layout`, `graphics-shapes`, `material3-*`,
   `kotlin-stdlib`, …
3B. **Fusionar recursos y manifest de las AAR** en la llamada a `aapt2 link` (colors, dimens, layouts,
   `AndroidManifest` de cada librería).
4. **Dexear todo eso** junto con la app.

Eso ya es un *build system*: es justo el punto donde Gradle (o un bootstrap propio grande) se vuelve
inevitable. Por eso el repo se queda en el "camino simple" y no promete Compose.

## Etapas

| Etapa | Estado |
|---|---|
| 1. Kotlin (`kotlinc` + stdlib → `d8`) | ✅ hecho — [03-kotlin.md](03-kotlin.md) |
| 2. Plugin compilador de Compose | ⏳ pendiente |
| 3. Resolver AAR de AndroidX + fusionar recursos/manifest en `aapt2 link` | ⏳ pendiente |
| 4. Dexear Compose + AndroidX | ⏳ pendiente |

## Alternativa si de verdad quieres Compose

Usa Gradle (Android Studio o `./gradlew` en un PC). Ahí Compose funciona con dos líneas en el
`build.gradle`. Este repo es para lo otro: apps pequeñas que se compilan **en el propio móvil**, sin
dependencias y en segundos.
