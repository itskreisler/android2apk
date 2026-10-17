# 02 · Ejemplos del repo

Siete proyectos (en `examples/`), de menos a más.

**Simple** (Java o Kotlin, sin dependencias):

```sh
./setup-toolchain.sh          # una vez (y ./setup-kotlin.sh si hay .kt)
./build.sh <proyecto>         # compila y firma
```

**Con Compose** (etapa 3):

```sh
./setup-compose.sh            # una vez (toolchain + Kotlin + AndroidX)
./build-compose.sh examples/compose
```

El APK sale en `<proyecto>/build/app.apk`, firmado, además de una copia con nombre descriptivo
`<nombre>-<versión>-<abi>.apk` (el `<nombre>` viene de `name=` en `app.properties`).

---

## `hello` — lo mínimo (Java, UI en código)

```
examples/hello/
  AndroidManifest.xml                 package="com.buddyapps.hello"
  java/com/buddyapps/hello/MainActivity.java
  app.properties                      versionCode=1 versionName=1.0 minSdk=26
```

```sh
./build.sh examples/hello
aapt2 dump badging examples/hello/build/app.apk | grep -E "package:|launchable"
```

```
package: name='com.buddyapps.hello' versionCode='1' versionName='1.0' … compileSdkVersion='36'
launchable-activity: name='com.buddyapps.hello.MainActivity'
```

Sin `res/`: la UI se construye en Java (`TextView`, `LinearLayout`…).

---

## `pulse` — icono propio + `res/`

Añade un árbol `res/`, así que `aapt2 compile` corre y el lanzador recibe **su propio icono**:

```
examples/pulse/
  AndroidManifest.xml                 package="com.buddyapps.pulse"
  res/drawable/ic_launcher.xml        icono vectorial único (108dp)
  res/layout/activity_main.xml        layout XML real
  res/values/strings.xml              app_name / subtitle
  java/com/buddyapps/pulse/MainActivity.java   setContentView(R.layout.activity_main)
  app.properties
```

```sh
./build.sh examples/pulse
aapt2 dump badging examples/pulse/build/app.apk | grep -E "application:|package:"
```

```
package: name='com.buddyapps.pulse' versionCode='1' versionName='1.0' … compileSdkVersion='36'
application: label='Pulse' icon='res/drawable/ic_launcher.xml'
```

**Cómo darle a un APK su propio icono.** Pon un vector (o PNG) en `res/drawable/` y apunta el
manifest a él: `android:icon="@drawable/ic_launcher"` (y `android:roundIcon`). No hace falta Gradle:
`aapt2 link` lo empaqueta y `resources.arsc` guarda la referencia. Para un icono adaptativo, añade
`res/mipmap-anydpi-v26/ic_launcher.xml` que referencie `@drawable/ic_launcher_background` y
`@drawable/ic_launcher_foreground`.

---

## `notes` — una app real estilo Keep + Notion (Java)

App completa: rejilla **tipo Google Keep** de tarjetas de colores, editor **tipo Notion**, y
persistencia en JSON. Sin AndroidX ni base de datos: solo Views y `org.json`.

```
examples/notes/
  AndroidManifest.xml                 dos activities, tema Light
  res/drawable/ic_launcher.xml        su icono
  res/drawable/ic_add.xml             el glifo + del FAB
  res/drawable/fab_bg.xml             fondo circular del FAB
  res/layout/activity_main.xml        rejilla (dos columnas + FAB)
  res/layout/activity_edit.xml        título + cuerpo + fila de color + guardar
  res/values/strings.xml
  java/com/buddyapps/notes/
    Note.java            una nota (id, título, cuerpo, color, actualizado)
    NoteStore.java       carga/guarda notes.json en filesDir
    MainActivity.java    rejilla Keep: dos columnas, toca = editar, mantén = borrar
    EditActivity.java    editor Notion: título grande sin borde, cuerpo, selector de color
  app.properties
```

```sh
./build.sh examples/notes
aapt2 dump badging examples/notes/build/app.apk | grep -E "application:|launchable"
```

```
application: label='Notas' icon='res/drawable/ic_launcher.xml'
launchable-activity: name='com.buddyapps.notes.MainActivity'
```

Cómo funciona:

- **Aspecto Keep** — las tarjetas son `GradientDrawable` de esquinas redondeadas con la paleta de
  Keep (`#FFF475`, `#CCFF90`, `#CBF0F8`, `#D7AEFB`, …), en una rejilla de dos columnas
  (`col1`/`col2`), las más recientes primero.
- **Aspecto Notion** — el editor tiene un `EditText` de título grande sin borde, cuerpo plano y una
  barra superior mínima con chevron de volver y un "Eliminar" de texto.
- **Almacenamiento** — las notas son un array JSON en `filesDir/notes.json`; `onResume` recarga, así
  los cambios aparecen al instante. No hace falta `ListView`/RecyclerView: la rejilla son
  `LinearLayout` normales.

---

## `todo` — lista de tareas Keep + Notion (Java, **UI en código**)

La misma idea que `notes`, pero montada **exactamente como lo hace el repo original**: Java plano y
**toda la interfaz en código** (sin `res/layout`), con lo mínimo imprescindible.

```
examples/todo/
  AndroidManifest.xml                       package="com.buddyapps.todo" (label "Tareas")
  java/com/buddyapps/todo/MainActivity.java la app entera en un archivo
  app.properties
```

```sh
./build.sh examples/todo
aapt2 dump badging examples/todo/build/app.apk | grep -E "application:|launchable"
```

- **Keep** — rejilla de dos columnas con tarjetas de colores (`GradientDrawable`), las nuevas arriba.
- **Notion** — título grande, fondo claro y estado "Hecho/Pendiente".
- **Uso** — escribe y pulsa *Añadir*; toca una tarjeta para marcarla; mantén pulsado para borrarla.
- **Persistencia** — `filesDir/tasks.json` con `org.json`.
- **Sin `res/`** — icono del sistema y todo definido en código: el APK no lleva recursos, así que el
  paso `1/5 Resources` se salta (como en `hello`). Es el ejemplo más "puro" del camino simple.

---

## `khello` — Kotlin (etapa 1, sin Compose)

El mismo *hello world* pero en **Kotlin**, demostrando que los `.kt` compilan sin Gradle:

```
examples/khello/
  AndroidManifest.xml
  java/com/buddyapps/khello/MainActivity.kt
  app.properties
```

```sh
./setup-kotlin.sh          # una vez: compilador de Kotlin (~90 MB)
./build.sh examples/khello
```

`build-apk.sh` compila los `.kt` con `kotlinc` (`-jvm-target 1.8`) y en el paso de dex añade
`kotlin-stdlib.jar`. Es la **etapa 1** hacia Compose. Detalles en [03-kotlin.md](03-kotlin.md).

---

## `compose` — Jetpack Compose + Material 3 Expressive (etapa 3)

El snippet `MaterialExpressiveTheme(motionScheme = MotionScheme.expressive())` de verdad: Kotlin +
AndroidX, sin Gradle.

```
examples/compose/
  AndroidManifest.xml                          package="com.buddyapps.compose" (label "Compose")
  java/com/buddyapps/compose/MainActivity.kt   MaterialExpressiveTheme + un contador
  app.properties                               compose=true
```

```sh
./setup-compose.sh
./build-compose.sh examples/compose
aapt2 dump badging examples/compose/build/app.apk | grep -E "package:|native-code"
```

```
package: name='com.buddyapps.compose' versionCode='1' versionName='1.0' … compileSdkVersion='36'
# (sin native-code si no se incluyen las .so)
```

- **`compose=true`** en `app.properties` activa la rama Compose de `build-apk.sh`.
- `setup-compose.sh` baja y prepara las AAR de AndroidX (~50 MB) en `~/compose-libs/`.
- El APK pesa ~5,5 MB (el dex incluye Compose + AndroidX, y `resources.arsc` todas las traducciones).
- **ABI**: sin código nativo el APK es universal. `NATIVE_ABIS` permite empaquetar las `.so` de
  `graphics-path`: `all` (universal, por defecto), `arm64-v8a` (solo arm64), vacío (ninguna).

Cómo se resuelve el grafo de AndroidX sin Gradle, paso a paso: [04-compose.md](04-compose.md).

---

## `neko` — Neko Notes, Compose + Material 3 Expressive, por fases

El ejemplo más ambicioso: una app de notas estilo **Keep + Notion** con estética kawaii, construida
**por fases**. La especificación pedía Gradle + Room + Hilt (KSP), que este repo **no puede**
ejecutar; se ha hecho la versión **Compose nativa con persistencia JSON**.

```sh
./setup-compose.sh
./build-compose.sh examples/neko
# → examples/neko/build/Neko-Notes-1.0-<abi>.apk
```

- **Fase 1**: tema kawaii claro/oscuro, barra inferior de 4 destinos, Inicio tipo Keep (buscador,
  filtros, cuadrícula/lista, tarjetas de color), editor de nota y de lista de tareas, etiquetas,
  búsqueda local, y exportar/importar JSON.
- **Fase 2**: páginas anidadas y **editor de bloques** estilo Notion (texto, H1–H3, viñetas,
  numerada, tareas, cita, separador, enlace, imagen, código), migas de pan y mover entre colecciones.
- **Fase 3**: **colecciones** (icono/color), **calendario** mensual con fechas límite y **bases de
  datos** locales (tabla/lista/tablero, propiedades, orden y filtros).
- **Fase 4**: `SharedTransitionLayout` (tarjeta → editor), `AnimatedContent`, feedback háptico,
  estado de carga, accesibilidad y estados vacíos.
- **Acabado**: icono de lanzamiento **adaptativo** propio (gatito kawaii, `res/mipmap-anydpi-v26/`) e
  **iconos Material reales** en la interfaz (`Icons.Filled.*` / `Icons.AutoMirrored.Filled.*`, vía
  `material-icons-core` + `material-icons-extended`); el emoji queda solo para el contenido kawaii.

Por qué no hay Room/Hilt y qué se puede y no se puede verificar aquí: [05-neko-notes.md](05-neko-notes.md).
