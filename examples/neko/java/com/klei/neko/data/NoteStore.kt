package com.klei.neko.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Persistencia local en JSON (org.json del framework). Sin Room ni KSP: la especificación
 * de Neko Notes pedía Room, pero este pipeline compila sin Gradle y no ejecuta procesadores
 * de anotaciones (ver docs/05-neko-notes.md).
 *
 * Guarda un único `filesDir/neko.json` con notas, páginas, colecciones y bases de datos.
 * Expone el estado como [StateFlow] y escribe en segundo plano (Dispatchers.IO).
 */
class NoteStore(private val context: Context) {

    private val file = File(context.filesDir, "neko.json")
    private val exportFile = File(context.filesDir, "neko-export.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _data = MutableStateFlow(NekoData())
    val data: StateFlow<NekoData> = _data.asStateFlow()

    val notes: StateFlow<List<Note>> = _data.map { it.notes }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val pages: StateFlow<List<Page>> = _data.map { it.pages }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val collections: StateFlow<List<Collection>> = _data.map { it.collections }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val databases: StateFlow<List<Database>> = _data.map { it.databases }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val exportPath: String get() = exportFile.absolutePath

    // ------------------------------------------------------------------ carga / guardado

    suspend fun load() {
        val loaded = withContext(Dispatchers.IO) { readFile(file) }
        _data.value = loaded
    }

    private fun mutate(block: (NekoData) -> NekoData) {
        val next = block(_data.value)
        _data.value = next
        val snapshot = toJson(next)
        scope.launch { runCatching { file.writeText(snapshot) } }
    }

    private fun readFile(f: File): NekoData {
        if (!f.exists()) return NekoData()
        return runCatching { parseData(f.readText()) }.getOrDefault(NekoData())
    }

    private fun parseData(text: String): NekoData {
        val trimmed = text.trim()
        if (trimmed.startsWith("[")) { // formato antiguo: array de notas
            val arr = JSONArray(trimmed)
            return NekoData(notes = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(Note::fromJson) })
        }
        val o = JSONObject(trimmed)
        fun <T> list(key: String, f: (JSONObject) -> T): List<T> =
            o.optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(f) } } ?: emptyList()
        return NekoData(
            notes = list("notes", Note::fromJson),
            pages = list("pages", Page::fromJson),
            collections = list("collections", Collection::fromJson),
            databases = list("databases", Database::fromJson),
        )
    }

    private fun toJson(d: NekoData): String = JSONObject()
        .put("notes", arr(d.notes) { it.toJson() })
        .put("pages", arr(d.pages) { it.toJson() })
        .put("collections", arr(d.collections) { it.toJson() })
        .put("databases", arr(d.databases) { it.toJson() })
        .toString()

    private fun <T> arr(list: List<T>, f: (T) -> JSONObject) = JSONArray().apply { list.forEach { put(f(it)) } }

    // ------------------------------------------------------------------ notas

    fun newNote(isTaskList: Boolean = false): Note = Note(
        id = UUID.randomUUID().toString(),
        isTaskList = isTaskList,
        items = if (isTaskList) listOf(ChecklistItem("")) else emptyList(),
    )

    fun upsert(note: Note) = mutate { d ->
        val stamp = note.copy(updatedAt = System.currentTimeMillis())
        d.copy(notes = (d.notes.filterNot { it.id == stamp.id } + stamp).sortedByDescending { it.updatedAt })
    }

    fun delete(id: String) = mutate { d -> d.copy(notes = d.notes.filterNot { it.id == id }) }

    fun toggleFavorite(id: String) = updateNote(id) { it.copy(favorite = !it.favorite) }

    fun toggleArchived(id: String) = updateNote(id) { it.copy(archived = !it.archived) }

    fun toggleItem(noteId: String, index: Int) = updateNote(noteId) { n ->
        n.copy(items = n.items.mapIndexed { i, item -> if (i == index) item.copy(done = !item.done) else item })
    }

    private fun updateNote(id: String, f: (Note) -> Note) = mutate { d ->
        d.copy(notes = d.notes.map { if (it.id == id) f(it).copy(updatedAt = System.currentTimeMillis()) else it }
            .sortedByDescending { it.updatedAt })
    }

    // ------------------------------------------------------------------ páginas

    fun newPage(parentId: String? = null, collectionId: String? = null): Page = Page(
        id = UUID.randomUUID().toString(),
        parentId = parentId,
        collectionId = collectionId,
        blocks = listOf(Block(id = UUID.randomUUID().toString(), type = BlockType.TEXT)),
    )

    fun upsertPage(page: Page) = mutate { d ->
        val stamp = page.copy(updatedAt = System.currentTimeMillis())
        d.copy(pages = (d.pages.filterNot { it.id == stamp.id } + stamp).sortedByDescending { it.updatedAt })
    }

    fun deletePage(id: String) = mutate { d ->
        val doomed = mutableSetOf(id)
        var grew = true
        while (grew) {
            grew = false
            d.pages.forEach { if (it.parentId in doomed && doomed.add(it.id)) grew = true }
        }
        d.copy(pages = d.pages.filterNot { it.id in doomed })
    }

    fun movePage(id: String, collectionId: String?) = updatePage(id) { it.copy(collectionId = collectionId) }

    private fun updatePage(id: String, f: (Page) -> Page) = mutate { d ->
        d.copy(pages = d.pages.map { if (it.id == id) f(it).copy(updatedAt = System.currentTimeMillis()) else it })
    }

    fun addBlock(pageId: String, type: BlockType) = updatePage(pageId) { p ->
        p.copy(blocks = p.blocks + Block(id = UUID.randomUUID().toString(), type = type))
    }

    fun updateBlock(pageId: String, blockId: String, f: (Block) -> Block) = updatePage(pageId) { p ->
        p.copy(blocks = p.blocks.map { if (it.id == blockId) f(it) else it })
    }

    fun deleteBlock(pageId: String, blockId: String) = updatePage(pageId) { p ->
        p.copy(blocks = p.blocks.filterNot { it.id == blockId })
    }

    fun moveBlock(pageId: String, index: Int, delta: Int) = updatePage(pageId) { p ->
        val target = index + delta
        if (target < 0 || target >= p.blocks.size) {
            p
        } else {
            val list = p.blocks.toMutableList()
            list.add(target, list.removeAt(index))
            p.copy(blocks = list)
        }
    }

    // ------------------------------------------------------------------ colecciones

    fun newCollection(name: String = "Nueva colección", icon: String = "🗂", color: Long = 0xFFEDE9FE): Collection =
        Collection(id = UUID.randomUUID().toString(), name = name, icon = icon, color = color)

    fun upsertCollection(c: Collection) = mutate { d ->
        val stamp = c.copy(updatedAt = System.currentTimeMillis())
        d.copy(collections = (d.collections.filterNot { it.id == stamp.id } + stamp).sortedByDescending { it.updatedAt })
    }

    fun deleteCollection(id: String) = mutate { d ->
        d.copy(
            collections = d.collections.filterNot { it.id == id },
            pages = d.pages.map { if (it.collectionId == id) it.copy(collectionId = null) else it },
        )
    }

    // ------------------------------------------------------------------ bases de datos

    fun newDatabase(name: String = "Nueva base de datos"): Database = Database(
        id = UUID.randomUUID().toString(),
        name = name,
        properties = listOf(
            DBProperty(UUID.randomUUID().toString(), "Título", PropertyType.TITLE),
            DBProperty(UUID.randomUUID().toString(), "Hecho", PropertyType.CHECKBOX),
        ),
        views = listOf(DBView(id = UUID.randomUUID().toString(), name = "Tabla", type = ViewType.TABLE)),
    )

    fun upsertDatabase(db: Database) = mutate { d ->
        val stamp = db.copy(updatedAt = System.currentTimeMillis())
        d.copy(databases = (d.databases.filterNot { it.id == stamp.id } + stamp).sortedByDescending { it.updatedAt })
    }

    fun deleteDatabase(id: String) = mutate { d -> d.copy(databases = d.databases.filterNot { it.id == id }) }

    private fun updateDatabase(id: String, f: (Database) -> Database) = mutate { d ->
        d.copy(databases = d.databases.map { if (it.id == id) f(it).copy(updatedAt = System.currentTimeMillis()) else it })
    }

    fun addProperty(dbId: String, property: DBProperty) = updateDatabase(dbId) { it.copy(properties = it.properties + property) }

    fun addRecord(dbId: String) = updateDatabase(dbId) { it.copy(records = it.records + DBRecord(id = UUID.randomUUID().toString())) }

    fun deleteRecord(dbId: String, recordId: String) = updateDatabase(dbId) { db ->
        db.copy(records = db.records.filterNot { it.id == recordId })
    }

    fun setRecordValue(dbId: String, recordId: String, propertyId: String, value: String) = updateDatabase(dbId) { db ->
        db.copy(records = db.records.map { if (it.id == recordId) it.copy(values = it.values + (propertyId to value)) else it })
    }

    fun addView(dbId: String, view: DBView) = updateDatabase(dbId) { it.copy(views = it.views + view) }

    fun updateView(dbId: String, viewId: String, f: (DBView) -> DBView) = updateDatabase(dbId) { db ->
        db.copy(views = db.views.map { if (it.id == viewId) f(it) else it })
    }

    // ------------------------------------------------------------------ export / import

    suspend fun export(): String = withContext(Dispatchers.IO) {
        exportFile.writeText(toJson(_data.value))
        exportFile.absolutePath
    }

    suspend fun import(): Int = withContext(Dispatchers.IO) {
        if (!exportFile.exists()) return@withContext 0
        val incoming = runCatching { parseData(exportFile.readText()) }.getOrDefault(NekoData())
        val cur = _data.value
        val merged = NekoData(
            notes = (cur.notes + incoming.notes).distinctBy { it.id }.sortedByDescending { it.updatedAt },
            pages = (cur.pages + incoming.pages).distinctBy { it.id },
            collections = (cur.collections + incoming.collections).distinctBy { it.id },
            databases = (cur.databases + incoming.databases).distinctBy { it.id },
        )
        _data.value = merged
        file.writeText(toJson(merged))
        incoming.notes.size + incoming.pages.size + incoming.collections.size + incoming.databases.size
    }
}
