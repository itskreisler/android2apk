# 05 · Neko Notes (ejemplo Compose, por fases)

**Neko Notes** es el ejemplo más ambicioso del repo: una app de notas y gestión del conocimiento
que mezcla la captura rápida de **Google Keep** con la organización de páginas de **Notion**, con
estética *kawaii* (rosa, violeta y tonos pastel) sobre **Jetpack Compose + Material 3 Expressive**.

Se construyó **por fases** (la propia especificación pedía "genera primero un prototipo visual
navegable" e "implementa una fase cada vez"). Aquí se documenta qué entra en cada fase y, sobre
todo, **qué no se puede hacer en este repo y por qué**.

> **Estado:** fases 1, 2, 3 y 4 **implementadas** y el APK **compila, enlaza, dexa y firma**
> (`Neko-Notes-1.0-arm64-v8a.apk`). No hay emulador en este entorno, así que la app **no se ha
> ejecutado** (ver *Límites conocidos*).

---

## La especificación original pedía Gradle (y aquí no hay)

| Pedía | ¿Se puede aquí? | Por qué |
|---|---|---|
| **Gradle** con Kotlin DSL, catálogo `libs.versions.toml` | ❌ | Este repo compila **sin Gradle**: `aapt2` → `javac`/`kotlinc` → `d8` → zip → `apksigner`. |
| **Room** (persistencia) | ❌ | Room necesita un procesador de anotaciones (**KSP**) que genera código (`RoomDatabase_Impl`, implementaciones de DAO) en tiempo de compilación. `build-apk.sh` no ejecuta KSP. |
| **Hilt** (inyección de dependencias) | ❌ | Igual: `hilt-android-compiler` (KSP/kapt) **más** un plugin de Gradle. |
| **Navigation Compose**, **DataStore**, **Coil** | ⚠️ | Son librerías normales (se podrían añadir al cierre de `setup-compose.sh`), pero suman dependencias y tamaño. No están en el cierre actual. |
| **Jetpack Compose + Material 3 Expressive** | ✅ | Es el corazón de `examples/compose` y de este ejemplo. |
| **Kotlin, Coroutines, Flow, StateFlow** | ✅ | Están en el cierre (Kotlin + `kotlinx-coroutines`). |
| **`SharedTransitionLayout`** (transiciones compartidas) | ✅ | Está en `androidx.compose.animation` 1.12.0 (API experimental `ExperimentalSharedTransitionApi`). |
| **JUnit + pruebas de UI, validar en dispositivo** | ❌ | Aquí **no hay emulador**. Solo se puede verificar que *compila, enlaza, dexa y firma*. |

**Decisión tomada** (confirmada con el usuario): hacer una versión **Compose nativa con
persistencia JSON** (`org.json` del framework + fichero en `filesDir`), **sin Room, sin Hilt,
sin DataStore, sin Coil y sin Navigation**. Es coherente con el resto del repo
(`examples/notes` y `examples/todo` también guardan JSON a mano).

Una consecuencia visible: el cierre de AndroidX **no incluye `material-icons-core`**, así que no
hay `Icons.Filled.*`. La iconografía se resuelve con **glifos y emoji** (🏠 🗂 📅 🐾 ⭐ 🔍 ＋), que
además encajan con la estética kawaii. `d8` no reporta ninguna clase faltante en el dex resultante.

---

## Fase 1 — Base: tema, Inicio tipo Keep y editor

- **Tema kawaii** (`ui/theme/`): paleta de la especificación (`#A855F7`, `#EC4899`, `#F59EBD`,
  `#FFF8FC`, `#FFFFFF`, `#EDE9FE`, `#FCE7F3`, `#D1FAE5`, `#FEF3C7`, `#292235`), con variantes
  para **tema claro y oscuro** y `MaterialExpressiveTheme(motionScheme = MotionScheme.expressive())`.
- **Navegación**: barra inferior con 4 destinos (**Inicio, Colecciones, Calendario, Perfil**) y
  FAB de creación. Es navegación **por estado** (`rememberSaveable`), no Navigation Compose.
- **Inicio** tipo Keep: saludo, buscador, filtros (*Todas / Notas / Tareas / ⭐*), conmutador
  cuadrícula/lista, secciones *Fijadas* y *Recientes*, y tarjetas de color con título, resumen,
  etiquetas y fecha. La rejilla es **adaptable** (`GridCells.Adaptive(150.dp)`).
- **Editor**: título grande sin borde (estilo Notion), cuerpo de texto o **lista de tareas** con
  casillas, selector de **color**, **etiquetas** y conversión nota ⇄ lista.
- **Búsqueda local** sobre título, cuerpo, etiquetas e ítems de tareas.
- **Perfil**: tema **Sistema / Claro / Oscuro** en caliente, y **exportar / importar JSON**.
- **Persistencia**: `filesDir/neko.json` vía `org.json`, expuesta como `StateFlow` y guardada en
  segundo plano (`Dispatchers.IO`).

## Fase 2 — Páginas y editor de bloques (estilo Notion)

- **Páginas anidadas** (`Page.parentId`): árbol con expandir/contraer en la pestaña *Colecciones*.
- **Editor de bloques** (`PageEditorScreen`), con 12 tipos: **texto, H1, H2, H3, viñetas,
  numerada, tarea, cita, separador, enlace, imagen y código**.
- Cada bloque se puede **cambiar de tipo** (menú), **reordenar** (↑/↓) y **borrar**; hay una barra
  de inserción con todos los tipos.
- **Migas de pan** (cadena de ancestros navegable), **selector de icono** y **subpáginas**.
- **Mover páginas entre colecciones** con chips (o dejarlas *Sin colección*).
- La **imagen** guarda solo la **URL** (no hay Coil): se muestra un marcador, no se descarga.

## Fase 3 — Colecciones, calendario y bases de datos

- **Colecciones** con nombre, icono, color y descripción (crear/editar/eliminar). Al eliminar una
  colección, sus páginas pasan a *Sin colección* (no se borran).
- **Calendario** mensual (semana empieza en lunes) con navegación ◀ ▶ y marcas por día; recoge la
  **fecha límite** de notas y la fecha límite **por elemento** de tarea. Al tocar un día se listan
  sus elementos y se navega a la nota.
- **Bases de datos locales** (`DatabaseScreen`):
  - **Propiedades configurables**: título, texto, número, casilla, fecha, selección, selección
    múltiple y relación (la relación es básica: un texto).
  - **Vistas**: **tabla**, **lista** y **tablero** (agrupado por una propiedad de selección).
  - **Orden** (por propiedad, asc/desc) y **filtro** ("contiene"), y la configuración de cada
    vista se **guarda** en su `DBView`.

## Fase 4 — Calidad visual y accesibilidad

- **`SharedTransitionLayout`**: transición de geometría compartida entre la **tarjeta de una nota**
  y su **editor** (misma `key`), con `sharedBounds` (API experimental, ver *Límites*).
- **`AnimatedContent`**: entre pestañas (fade + escala) y al alternar **cuadrícula/lista**.
- **Feedback háptico** (`LocalHapticFeedback`) al crear, fijar y marcar tareas.
- **Estado de carga** con la mascota mientras se lee el JSON.
- **Accesibilidad**: `contentDescription` en botones de icono (fijar, atrás, eliminar, cambiar
  vista, borrar búsqueda…) mediante `Modifier.semantics`.
- **Estados vacíos** ilustrados en Inicio, Árbol de páginas y Bases de datos.
- **Icono de lanzamiento adaptativo** (`res/mipmap-anydpi-v26/`): fondo degradado violeta→rosa y
  cara de gatito kawaii dibujada como *vector*, con `<monochrome>` para los iconos temáticos de
  Android 13+. (Antes usaba el icono por defecto del sistema.)

---

## Estructura

```
examples/neko/
  AndroidManifest.xml                    package="com.buddyapps.neko" (label "Neko Notes")
  app.properties                         versionCode=1 versionName=1.0 minSdk=26 name=Neko Notes compose=true
  res/drawable/ic_launcher_background.xml  fondo degradado (violeta → rosa)
  res/drawable/ic_launcher_foreground.xml  gatito kawaii (cara)
  res/mipmap-anydpi-v26/ic_launcher.xml    icono adaptativo (con <monochrome> para iconos temáticos)
  res/mipmap-anydpi-v26/ic_launcher_round.xml
  java/com/buddyapps/neko/
    MainActivity.kt                      ComponentActivity → NekoApp()
    data/Note.kt                         Note, ChecklistItem, NoteFilter, ViewMode (+ JSON)
    data/Models.kt                       Block/BlockType, Page, Collection, Database (propiedades,
                                         registros, vistas) y NekoData (+ JSON)
    data/NoteStore.kt                    persistencia JSON, StateFlow, export/import
    ui/theme/Color.kt                    paleta kawaii + colores de tarjeta
    ui/theme/Theme.kt                    MaterialExpressiveTheme, claro/oscuro, ThemeMode
    ui/NekoApp.kt                        shell: SharedTransitionLayout, rutas animadas, FAB, snackbar
    ui/HomeScreen.kt                     Inicio tipo Keep
    ui/EditorScreen.kt                   editor de nota / lista de tareas
    ui/PagesScreen.kt                    Colecciones: colecciones, árbol de páginas y bases de datos
    ui/PageEditorScreen.kt               editor de bloques estilo Notion
    ui/CalendarScreen.kt                 calendario mensual
    ui/DatabaseScreen.kt                 bases de datos (tabla/lista/tablero)
    ui/OtherScreens.kt                   Perfil (tema + export/import)
    ui/Components.kt                     NoteCard, EmptyState, PillTag, avatar, carga, sharedBounds
    ui/DateUtils.kt                      utilidades de fecha + diálogo de fecha límite
```

## Compilar

```sh
./setup-compose.sh              # una vez (toolchain + Kotlin + AndroidX)
./build-compose.sh examples/neko
# → examples/neko/build/Neko-Notes-1.0-<abi>.apk
```

Con `name=Neko Notes` en `app.properties`, el nombre se sanea a `Neko-Notes`; la ABI depende de los
`.so` empaquetados (hoy solo `arm64-v8a` aporta `libandroidx.graphics.path.so`, así que el APK es
`arm64-v8a`).

## Modelo de datos

`NekoData` es la raíz que se guarda en `neko.json`: `notes`, `pages`, `collections` y `databases`.
Se lee con `opt*` (lo desconocido se ignora) y se migra un `neko.json` antiguo que fuese un array
de notas. `Note` cubre nota rápida y lista de tareas; `Page` tiene bloques y jerarquía; `Database`
tiene propiedades, registros y vistas.

## Límites conocidos

- **No hay emulador** en este entorno: el APK se verifica *compila, enlaza, dexa, firma* y que
  declara la activity de lanzamiento (`aapt2 dump badging`), pero **no se ha ejecutado**. Los
  criterios de aceptación que dependen de ejecución (persistencia al cerrar, resultados de
  búsqueda, animaciones, transición compartida) están implementados pero **sin verificar en
  dispositivo**.
- La transición compartida usa la API **experimental** `ExperimentalSharedTransitionApi`; sin
  dispositivo no se puede confirmar su comportamiento en todos los casos.
- **Multidex**: la app supera los 64K métodos, así que el APK lleva `classes.dex` **y**
  `classes2.dex` (el código de Neko vive en `classes2.dex`). `build-apk.sh` empaqueta todos los
  `classes*.dex`.
- **Sin** Room/Hilt/DataStore/Coil/Navigation (ver arriba). Persistencia en JSON. La **imagen** de
  un bloque solo guarda la URL.
- **Sin permisos** en el manifiesto: todo es local, no hay red ni cuentas (coherente con el MVP).
- Sin pruebas JUnit/instrumentación (no hay Gradle ni emulador).

## Si quieres la versión "de producción"

Con Gradle + Room + Hilt + KSP + Navigation + Coil, y validada en emulador/dispositivo, este repo
**no es el sitio**: usa Android Studio o un `gradle` normal. Aquí primamos que quepa y funcione en
el móvil sin toolchain de Gradle.
