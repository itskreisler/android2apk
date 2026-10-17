package com.klei.neko.data

import org.json.JSONArray
import org.json.JSONObject

/** Un elemento de una lista de tareas. */
data class ChecklistItem(
    val text: String,
    val done: Boolean = false,
    val dueDate: Long = 0,
) {
    fun toJson(): JSONObject = JSONObject().put("text", text).put("done", done).put("dueDate", dueDate)

    companion object {
        fun fromJson(o: JSONObject) = ChecklistItem(
            o.optString("text"),
            o.optBoolean("done"),
            o.optLong("dueDate", 0),
        )
    }
}

/**
 * Una nota. Sirve para los tipos del MVP: nota rápida (body) y lista de tareas
 * (isTaskList + items). Las páginas con bloques viven en [Page].
 */
data class Note(
    val id: String,
    val title: String = "",
    val body: String = "",
    val color: Long = 0xFFFFFFFF,
    val tags: List<String> = emptyList(),
    val favorite: Boolean = false,
    val archived: Boolean = false,
    val isTaskList: Boolean = false,
    val items: List<ChecklistItem> = emptyList(),
    val dueDate: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /** Texto corto para la tarjeta. */
    val preview: String
        get() = if (isTaskList) items.joinToString(" ") { it.text } else body

    val doneCount: Int get() = items.count { it.done }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("body", body)
        .put("color", color)
        .put("tags", JSONArray(tags))
        .put("favorite", favorite)
        .put("archived", archived)
        .put("isTaskList", isTaskList)
        .put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
        .put("dueDate", dueDate)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

    companion object {
        fun fromJson(o: JSONObject): Note {
            val tags = o.optJSONArray("tags")?.let { a ->
                (0 until a.length()).map { a.optString(it) }
            } ?: emptyList()
            val items = o.optJSONArray("items")?.let { a ->
                (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(ChecklistItem::fromJson) }
            } ?: emptyList()
            return Note(
                id = o.optString("id"),
                title = o.optString("title"),
                body = o.optString("body"),
                color = o.optLong("color", 0xFFFFFFFF),
                tags = tags,
                favorite = o.optBoolean("favorite"),
                archived = o.optBoolean("archived"),
                isTaskList = o.optBoolean("isTaskList"),
                items = items,
                dueDate = o.optLong("dueDate", 0),
                createdAt = o.optLong("createdAt"),
                updatedAt = o.optLong("updatedAt"),
            )
        }
    }
}

/** Filtros de la pantalla de Inicio. */
enum class NoteFilter { ALL, NOTES, TASKS, FAVORITES }

/** Disposición de la rejilla. */
enum class ViewMode { GRID, LIST }
