package com.buddyapps.neko.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.UUID

/**
 * Persistencia local en JSON (org.json del framework). Sin Room ni KSP:
 * la spec de Neko Notes pedía Room, pero este pipeline compila sin Gradle y no
 * ejecuta procesadores de anotaciones (ver docs/05-neko-notes.md).
 *
 * Expone las notas como [StateFlow] y guarda en segundo plano (Dispatchers.IO).
 */
class NoteStore(private val context: Context) {

    private val file = File(context.filesDir, "neko.json")
    private val exportFile = File(context.filesDir, "neko-export.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    val exportPath: String get() = exportFile.absolutePath

    suspend fun load() {
        val loaded = withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext emptyList()
            runCatching { parse(file.readText()) }.getOrDefault(emptyList())
        }
        _notes.value = loaded.sortedByDescending { it.updatedAt }
    }

    fun newNote(isTaskList: Boolean = false): Note = Note(
        id = UUID.randomUUID().toString(),
        isTaskList = isTaskList,
        items = if (isTaskList) listOf(ChecklistItem("")) else emptyList(),
    )

    fun upsert(note: Note) {
        val stamp = note.copy(updatedAt = System.currentTimeMillis())
        _notes.value = (_notes.value.filterNot { it.id == stamp.id } + stamp)
            .sortedByDescending { it.updatedAt }
        persist()
    }

    fun delete(id: String) {
        _notes.value = _notes.value.filterNot { it.id == id }
        persist()
    }

    fun toggleFavorite(id: String) = update(id) { it.copy(favorite = !it.favorite) }

    fun toggleArchived(id: String) = update(id) { it.copy(archived = !it.archived) }

    fun toggleItem(noteId: String, index: Int) = update(noteId) { n ->
        n.copy(items = n.items.mapIndexed { i, item ->
            if (i == index) item.copy(done = !item.done) else item
        })
    }

    private fun update(id: String, block: (Note) -> Note) {
        val current = _notes.value.firstOrNull { it.id == id } ?: return
        val updated = block(current).copy(updatedAt = System.currentTimeMillis())
        _notes.value = _notes.value.map { if (it.id == id) updated else it }
            .sortedByDescending { it.updatedAt }
        persist()
    }

    /** Escribe todas las notas en `filesDir/neko-export.json`. Devuelve la ruta. */
    suspend fun export(): String = withContext(Dispatchers.IO) {
        exportFile.writeText(toJson(_notes.value))
        exportFile.absolutePath
    }

    /** Lee `filesDir/neko-export.json` y fusiona por id. Devuelve cuántas entraron. */
    suspend fun import(): Int = withContext(Dispatchers.IO) {
        if (!exportFile.exists()) return@withContext 0
        val incoming = runCatching { parse(exportFile.readText()) }.getOrDefault(emptyList())
        if (incoming.isEmpty()) return@withContext 0
        _notes.value = (_notes.value + incoming)
            .distinctBy { it.id }
            .sortedByDescending { it.updatedAt }
        file.writeText(toJson(_notes.value))
        incoming.size
    }

    private fun persist() {
        val snapshot = toJson(_notes.value)
        scope.launch { runCatching { file.writeText(snapshot) } }
    }

    private fun parse(text: String): List<Note> {
        val arr = JSONArray(text)
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(Note::fromJson) }
    }

    private fun toJson(list: List<Note>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }
}
