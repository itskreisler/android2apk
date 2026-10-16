# 04 · Jetpack Compose / Material 3 Expressive

La pregunta: ¿se puede usar este snippet de **Material 3 Expressive** con este pipeline?

```kotlin
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable

@Composable
fun MiAppExpressiva(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        motionScheme = MotionScheme.expressive(), // Activa la física de movimiento expresiva
        content = content
    )
}
```

## Qué dice la documentación

- [`MaterialExpressiveTheme`](https://developer.android.com/reference/kotlin/androidx/compose/material3/MaterialExpressiveTheme.composable)
  — `fun MaterialExpressiveTheme(colorScheme: ColorScheme? = null, motionScheme: MotionScheme? = null, shapes: Shapes? = null, typography: Typography? = null, content: @Composable () -> Unit)`.
- [`MotionScheme.expressive()`](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme)
  — "Material's recommended motion scheme for prominent UI elements and hero interactions".
- [Material 3 en Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) ·
  [paquete `androidx.compose.material3`](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary).

La referencia marca `MotionScheme` como "Añadido en **1.5.0-beta01**" (ahí pasó a estable), pero el
artefacto **`material3-android:1.4.0` ya incluye** `MaterialExpressiveTheme` y `MotionScheme`
(verificado abajo). Usamos 1.4.0.

## Diferencia entre Java y Compose

El pipeline normal compila `javac`/`kotlinc` con solo `android.jar`. Compose añade tres cosas:

1. El **plugin compilador de Compose**, que reescribe las funciones `@Composable`.
2. Las **librerías AndroidX** (runtime, ui, foundation, material3…), cada una una AAR con su `classes.jar`
   **y su `res/`** (material3 ~175 entradas, ui ~176, foundation ~173 — casi todas `values-*`).
3. **Dexear** todo eso junto con la app.

## Etapas

| Etapa | Estado |
|---|---|
| 1. Kotlin (`kotlinc` + stdlib → `d8`) | ✅ hecho — [03-kotlin.md](03-kotlin.md) |
| 2. Plugin compilador de Compose | ✅ hecho (abajo) |
| 3. Resolver AAR de AndroidX + fusionar recursos/manifest en `aapt2 link` | ⏳ en curso |
| 4. Dexear Compose + AndroidX | ⏳ pendiente |

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

## Etapa 3 — las librerías AndroidX (el muro)

Aquí está lo grande. Para compilar y ejecutar el snippet hacen falta las AAR de AndroidX **y toda su
clausura transitiva**. `material3-android:1.4.0` declara, entre otras:

```
androidx.compose.runtime:runtime-android:1.9.0
androidx.compose.ui:ui-android:1.8.2
androidx.compose.ui:ui-text-android:1.8.2
androidx.compose.ui:ui-util-android:1.8.2
androidx.compose.foundation:foundation-android:1.8.1
androidx.compose.foundation:foundation-layout-android:1.8.1
androidx.compose.animation:animation-core-android:1.8.1
androidx.compose.material:material-ripple-android:1.8.1
androidx.activity:activity-compose:1.8.2
androidx.lifecycle:lifecycle-common-java8:2.6.1
androidx.collection:collection-jvm:1.5.0
androidx.annotation:annotation-jvm / annotation-experimental
…
```

Y esas, a su vez, tiran de `lifecycle-*`, `core`, `savedstate`, `kotlinx-coroutines`, `profileinstaller`,
`tracing`, `autofill`, `emoji2`… Resolver ese grafo con sus conflictos de versión es, literalmente,
**el trabajo que hace Gradle**. Reimplementarlo a mano (un resolutor de POMs) es posible pero frágil.

Además:

- **Recursos**: hay que fusionar el `res/` de cada AAR (casi todo traducciones `values-*`) y fijar los
  IDs con `aapt2 link --stable-ids` (a partir de los `R.txt` de las AAR), porque el `classes.jar` de
  cada librería ya trae sus IDs incrustados.
- **Manifest**: fusionar el `<manifest>` de cada AAR (sobre todo `minSdk`).

Nota honesta: aquí **no hay Android ni emulador**, así que aunque consigamos un APK de Compose firmado,
solo podremos verificar que *compila, enlaza, dexa y firma*. No podemos probar en ejecución.

## Alternativa si de verdad quieres Compose

Un proyecto **Gradle** normal (Android Studio o `./gradlew` en un PC) resuelve el grafo, fusiona
recursos y lo dexa en una línea. Este repo es para lo otro: apps pequeñas que se compilan **en el
propio móvil**, sin dependencias y en segundos.
