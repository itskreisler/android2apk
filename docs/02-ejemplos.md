# 02 · Ejemplos del repo

Cuatro proyectos, de menos a más. Compila cualquiera así:

```sh
./build.sh <proyecto>          # toolchain + build (recomendado)
# o, si ya corriste ./setup-toolchain.sh una vez:
./build-apk.sh <proyecto>
```

El APK sale en `<proyecto>/build/app.apk`, firmado.

---

## `hello` — lo mínimo (Java, UI en código)

```
hello/
  AndroidManifest.xml                 package="com.buddyapps.hello"
  java/com/buddyapps/hello/MainActivity.java
  app.properties                      versionCode=1 versionName=1.0 minSdk=26
```

```sh
./build.sh hello
aapt2 dump badging hello/build/app.apk | grep -E "package:|launchable"
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
pulse/
  AndroidManifest.xml                 package="com.buddyapps.pulse"
  res/drawable/ic_launcher.xml        icono vectorial único (108dp)
  res/layout/activity_main.xml        layout XML real
  res/values/strings.xml              app_name / subtitle
  java/com/buddyapps/pulse/MainActivity.java   setContentView(R.layout.activity_main)
  app.properties
```

```sh
./build.sh pulse
aapt2 dump badging pulse/build/app.apk | grep -E "application:|package:"
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
notes/
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
./build.sh notes
aapt2 dump badging notes/build/app.apk | grep -E "application:|launchable"
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

## `khello` — Kotlin (etapa 1, sin Compose)

El mismo *hello world* pero en **Kotlin**, demostrando que los `.kt` compilan sin Gradle:

```
khello/
  AndroidManifest.xml
  java/com/buddyapps/khello/MainActivity.kt
  app.properties
```

```sh
./setup-kotlin.sh          # una vez: compilador de Kotlin (~90 MB)
./build.sh khello
```

`build-apk.sh` compila los `.kt` con `kotlinc` (`-jvm-target 1.8`) y en el paso de dex añade
`kotlin-stdlib.jar`. Es la **etapa 1** hacia Compose. Detalles en [03-kotlin.md](03-kotlin.md).
