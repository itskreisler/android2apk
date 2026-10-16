# 05 · Neko Notes (ejemplo Compose, por fases)

**Neko Notes** es un ejemplo más ambicioso que `compose`: una app de notas y gestión del
conocimiento que mezcla la captura rápida de **Google Keep** con la organización de páginas
de **Notion**, con estética *kawaii* (rosa, violeta y tonos pastel) sobre **Jetpack Compose +
Material 3 Expressive**.

Se construye **por fases** (la propia especificación pedía "genera primero un prototipo visual
navegable" e "implementa una fase cada vez"). Aquí se documenta qué entra en cada fase y, sobre
todo, **qué no se puede hacer en este repo y por qué**.

---

## La especificación original pedía Gradle (y aquí no hay)

La especificación de Neko Notes pedía, entre otras cosas:

| Pedía | ¿Se puede aquí? | Por qué |
|---|---|---|
| **Gradle** con Kotlin DSL, catálogo `libs.versions.toml` | ❌ | Este repo compila **sin Gradle**: `aapt2` → `javac`/`kotlinc` → `d8` → zip → `apksigner`. |
| **Room** (persistencia) | ❌ | Room necesita un procesador de anotaciones (**KSP**) que genera código (`RoomDatabase_Impl`, implementaciones de DAO) en tiempo de compilación. `build-apk.sh` no ejecuta KSP. |
| **Hilt** (inyección de dependencias) | ❌ | Igual: `hilt-android-compiler` (KSP/kapt) **más** un plugin de Gradle. |
| **Navigation Compose**, **DataStore**, **Coil** | ⚠️ | Son librerías normales (se podrían añadir al cierre de `setup-compose.sh`), pero suman dependencias y tamaño. No están en el cierre actual. |
| **Jetpack Compose + Material 3 Expressive** | ✅ | Ya funciona: es el corazón de `examples/compose` y de este ejemplo. |
| **Kotlin, Coroutines, Flow, StateFlow** | ✅ | Están en el cierre (Kotlin + `kotlinx-coroutines`). |
| **JUnit + pruebas de UI, validar en dispositivo** | ❌ | Aquí **no hay emulador**. Solo se puede verificar que *compila, enlaza, dexa y firma*. |

**Decisión tomada** (confirmada con el usuario): hacer una versión **Compose nativa con
persistencia JSON** (`org.json` del framework + fichero en `filesDir`), **sin Room, sin Hilt,
sin DataStore, sin Coil y sin Navigation**. Esto es coherente con el resto del repo
(`examples/notes` y `examples/todo` también guardan JSON a mano).

> Si algún día quieres la versión "de producción" con Gradle + Room + Hilt + KSP, este repo
> **no es el sitio**: usa Android Studio o un `gradle` normal. Aquí primamos que quepa y
> funcione en el móvil.

Una consecuencia visible: el cierre de AndroidX **no incluye `material-icons-core`**, así que no
hay `Icons.Filled.*`. La iconografía se resuelve con **glifos y emoji** (🏠 🗂 📅 🐾 ⭐ 🔍 ＋), que
además encajan con la estética kawaii.

---

## Fase 1 — lo que ya está

Prototipo visual navegable con datos reales:

- **Tema kawaii** (`ui/theme/`): paleta de la especificación (`#A855F7`, `#EC4899`, `#F59EBD`,
  `#FFF8FC`, `#FFFFFF`, `#EDE9FE`, `#FCE7F3`, `#D1FAE5`, `#FEF3C7`, `#292235`), con variantes
  para **tema claro y oscuro** y `MaterialExpressiveTheme(motionScheme = MotionScheme.expressive())`.
- **Navegación**: barra inferior con 4 destinos (**Inicio, Colecciones, Calendario, Perfil**) y
  FAB de creación. Es navegación **por estado** (`rememberSaveable`), no Navigation Compose.
- **Inicio** tipo Keep: saludo, buscador, filtros (*Todas / Notas / Tareas / ⭐*), conmutador
  cuadrícula/lista, secciones *Fijadas* y *Recientes*, y tarjetas de color con título, resumen,
  etiquetas y fecha. La rejilla es **adaptable** (`GridCells.Adaptive(150.dp)` → dos columnas en
  teléfono); el modo lista es la misma rejilla con `GridCells.Fixed(1)`.
- **Editor**: título grande sin borde (estilo Notion), cuerpo de texto o **lista de tareas** con
  casillas, selector de **color**, **etiquetas** y conversión nota ⇄ lista.
- **Búsqueda local** sobre título, cuerpo, etiquetas e ítems de tareas.
- **Perfil**: tema **Sistema / Claro / Oscuro** en caliente, y **exportar / importar JSON**.
- **Persistencia**: `filesDir/neko.json` vía `org.json`, expuesta como `StateFlow<List<Note>>` y
  guardada en segundo plano (`Dispatchers.IO`).

## Fases siguientes (pendientes)

- **Fase 2 — Páginas y bloques**: páginas anidadas, editor de bloques (texto, H1–H3, viñetas,
  numerada, tareas, cita, separador, enlace, imagen, código), migas de pan, mover páginas entre
  colecciones.
- **Fase 3 — Colecciones, calendario y bases de datos**: colecciones con icono/color, vista
  mensual con fecha límite, y una primera versión de bases de datos locales (tabla, lista,
  tablero) con propiedades.
- **Fase 4 — Calidad visual**: `AnimatedContent`/`AnimatedVisibility` y `SharedTransitionLayout`
  entre tarjeta y detalle, feedback háptico, estados vacíos ilustrados y accesibilidad.

Nada de esto se marca como terminado: las pestañas *Colecciones* y *Calendario* muestran una
**pantalla honesta de "llega en una fase siguiente"**, sin botones decorativos sin
comportamiento.

---

## Estructura

```
examples/neko/
  AndroidManifest.xml                    package="com.buddyapps.neko" (label "Neko Notes")
  app.properties                         versionCode=1 versionName=1.0 minSdk=26 name=Neko Notes compose=true
  java/com/buddyapps/neko/
    MainActivity.kt                      ComponentActivity → NekoApp()
    data/Note.kt                         Note, ChecklistItem, NoteFilter, ViewMode (+ JSON)
    data/NoteStore.kt                    persistencia JSON, StateFlow, export/import
    ui/theme/Color.kt                    paleta kawaii + colores de tarjeta
    ui/theme/Theme.kt                    MaterialExpressiveTheme, claro/oscuro, ThemeMode
    ui/NekoApp.kt                        shell: navegación, tema, FAB, snackbar
    ui/HomeScreen.kt                     Inicio tipo Keep
    ui/EditorScreen.kt                   editor de nota / lista de tareas
    ui/OtherScreens.kt                   Colecciones/Calendario (placeholder) y Perfil
    ui/Components.kt                     NoteCard, EmptyState, PillTag, avatar…
```

## Compilar

```sh
./setup-compose.sh              # una vez (toolchain + Kotlin + AndroidX)
./build-compose.sh examples/neko
# → examples/neko/build/Neko-Notes-1.0-<abi>.apk
```

Con `name=Neko Notes` en `app.properties`, el nombre se sanea a `Neko-Notes`; la ABI depende de
`NATIVE_ABIS` (por defecto `all` → `arm64-v8a` en el ejemplo, o `universal` si no hay `.so`).

## Modelo de datos (Fase 1)

`Note` cubre nota rápida y lista de tareas. Las páginas, bloques, colecciones y bases de datos
llegan en fases posteriores; el modelo se ha diseñado para poder añadirlos sin romper el JSON
(se ignora lo que no se conoce al leer: `opt*`).

## Límites conocidos

- **No hay emulador** en este entorno: el APK se verifica *compila, enlaza, dexa, firma y arranca
  la activity de lanzamiento* (según `badging`), pero **no se ha ejecutado**. Los criterios de
  aceptación que dependen de ejecución (que las notas sobrevivan al cierre, que la búsqueda
  devuelva resultados, etc.) están implementados pero **sin verificar en dispositivo**.
- Sin **Room/Hilt/DataStore/Coil/Navigation** (ver arriba). Persistencia en JSON.
- Sin pruebas JUnit/instrumentación (no hay Gradle ni emulador).
