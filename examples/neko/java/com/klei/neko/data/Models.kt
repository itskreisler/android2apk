package com.klei.neko.data

import org.json.JSONArray
import org.json.JSONObject

// =====================================================================================
//  Páginas y bloques (estilo Notion)
// =====================================================================================

enum class BlockType { TEXT, H1, H2, H3, BULLET, NUMBERED, TASK, QUOTE, DIVIDER, LINK, IMAGE, CODE }

val BlockType.emoji: String
    get() = when (this) {
        BlockType.TEXT -> "¶"
        BlockType.H1 -> "H1"
        BlockType.H2 -> "H2"
        BlockType.H3 -> "H3"
        BlockType.BULLET -> "•"
        BlockType.NUMBERED -> "1."
        BlockType.TASK -> "☑"
        BlockType.QUOTE -> "❝"
        BlockType.DIVIDER -> "―"
        BlockType.LINK -> "🔗"
        BlockType.IMAGE -> "🖼"
        BlockType.CODE -> "</>"
    }

val BlockType.label: String
    get() = when (this) {
        BlockType.TEXT -> "Texto"
        BlockType.H1 -> "Encabezado 1"
        BlockType.H2 -> "Encabezado 2"
        BlockType.H3 -> "Encabezado 3"
        BlockType.BULLET -> "Viñetas"
        BlockType.NUMBERED -> "Numerada"
        BlockType.TASK -> "Tarea"
        BlockType.QUOTE -> "Cita"
        BlockType.DIVIDER -> "Separador"
        BlockType.LINK -> "Enlace"
        BlockType.IMAGE -> "Imagen"
        BlockType.CODE -> "Código"
    }

data class Block(
    val id: String,
    val type: BlockType = BlockType.TEXT,
    val text: String = "",
    val checked: Boolean = false,
    val url: String = "",
    val language: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("type", type.name)
        .put("text", text)
        .put("checked", checked)
        .put("url", url)
        .put("language", language)

    companion object {
        fun fromJson(o: JSONObject) = Block(
            id = o.optString("id"),
            type = runCatching { BlockType.valueOf(o.optString("type")) }.getOrDefault(BlockType.TEXT),
            text = o.optString("text"),
            checked = o.optBoolean("checked"),
            url = o.optString("url"),
            language = o.optString("language"),
        )
    }
}

data class Page(
    val id: String,
    val title: String = "",
    val icon: String = "📄",
    val blocks: List<Block> = emptyList(),
    val parentId: String? = null,
    val collectionId: String? = null,
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val preview: String
        get() = blocks.firstOrNull { it.text.isNotBlank() }?.text ?: ""

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("icon", icon)
        .put("blocks", JSONArray().apply { blocks.forEach { put(it.toJson()) } })
        .put("parentId", parentId ?: JSONObject.NULL)
        .put("collectionId", collectionId ?: JSONObject.NULL)
        .put("favorite", favorite)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

    companion object {
        fun fromJson(o: JSONObject): Page {
            val blocks = o.optJSONArray("blocks")?.let { a ->
                (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(Block::fromJson) }
            } ?: emptyList()
            return Page(
                id = o.optString("id"),
                title = o.optString("title"),
                icon = o.optString("icon").ifBlank { "📄" },
                blocks = blocks,
                parentId = o.optString("parentId").ifBlank { null },
                collectionId = o.optString("collectionId").ifBlank { null },
                favorite = o.optBoolean("favorite"),
                createdAt = o.optLong("createdAt"),
                updatedAt = o.optLong("updatedAt"),
            )
        }
    }
}

data class Collection(
    val id: String,
    val name: String = "Colección",
    val icon: String = "🗂",
    val color: Long = 0xFFEDE9FE,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("icon", icon)
        .put("color", color)
        .put("description", description)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

    companion object {
        fun fromJson(o: JSONObject) = Collection(
            id = o.optString("id"),
            name = o.optString("name"),
            icon = o.optString("icon").ifBlank { "🗂" },
            color = o.optLong("color", 0xFFEDE9FE),
            description = o.optString("description"),
            createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt"),
        )
    }
}

// =====================================================================================
//  Bases de datos locales
// =====================================================================================

enum class PropertyType { TITLE, TEXT, NUMBER, CHECKBOX, DATE, SELECT, MULTISELECT, RELATION }

val PropertyType.label: String
    get() = when (this) {
        PropertyType.TITLE -> "Título"
        PropertyType.TEXT -> "Texto"
        PropertyType.NUMBER -> "Número"
        PropertyType.CHECKBOX -> "Casilla"
        PropertyType.DATE -> "Fecha"
        PropertyType.SELECT -> "Selección"
        PropertyType.MULTISELECT -> "Selección múltiple"
        PropertyType.RELATION -> "Relación"
    }

data class DBProperty(
    val id: String,
    val name: String,
    val type: PropertyType,
    val options: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("type", type.name)
        .put("options", JSONArray(options))

    companion object {
        fun fromJson(o: JSONObject) = DBProperty(
            id = o.optString("id"),
            name = o.optString("name"),
            type = runCatching { PropertyType.valueOf(o.optString("type")) }.getOrDefault(PropertyType.TEXT),
            options = o.optJSONArray("options")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
        )
    }
}

data class DBRecord(
    val id: String,
    val values: Map<String, String> = emptyMap(),
) {
    fun value(propertyId: String): String = values[propertyId] ?: ""

    fun toJson(): JSONObject {
        val v = JSONObject()
        values.forEach { (k, value) -> v.put(k, value) }
        return JSONObject().put("id", id).put("values", v)
    }

    companion object {
        fun fromJson(o: JSONObject): DBRecord {
            val values = o.optJSONObject("values")?.let { jo ->
                val m = mutableMapOf<String, String>()
                val keys = jo.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    m[k] = jo.optString(k)
                }
                m
            } ?: emptyMap()
            return DBRecord(o.optString("id"), values)
        }
    }
}

enum class ViewType { TABLE, LIST, BOARD }

val ViewType.label: String
    get() = when (this) {
        ViewType.TABLE -> "Tabla"
        ViewType.LIST -> "Lista"
        ViewType.BOARD -> "Tablero"
    }

data class DBView(
    val id: String,
    val name: String = "Vista",
    val type: ViewType = ViewType.TABLE,
    val groupBy: String = "",
    val sortBy: String = "",
    val sortAsc: Boolean = true,
    val filterProperty: String = "",
    val filterValue: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("type", type.name)
        .put("groupBy", groupBy)
        .put("sortBy", sortBy)
        .put("sortAsc", sortAsc)
        .put("filterProperty", filterProperty)
        .put("filterValue", filterValue)

    companion object {
        fun fromJson(o: JSONObject) = DBView(
            id = o.optString("id"),
            name = o.optString("name"),
            type = runCatching { ViewType.valueOf(o.optString("type")) }.getOrDefault(ViewType.TABLE),
            groupBy = o.optString("groupBy"),
            sortBy = o.optString("sortBy"),
            sortAsc = o.optBoolean("sortAsc", true),
            filterProperty = o.optString("filterProperty"),
            filterValue = o.optString("filterValue"),
        )
    }
}

data class Database(
    val id: String,
    val name: String = "Base de datos",
    val icon: String = "🗃",
    val properties: List<DBProperty> = emptyList(),
    val records: List<DBRecord> = emptyList(),
    val views: List<DBView> = emptyList(),
    val collectionId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun property(id: String): DBProperty? = properties.firstOrNull { it.id == id }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("icon", icon)
        .put("properties", JSONArray().apply { properties.forEach { put(it.toJson()) } })
        .put("records", JSONArray().apply { records.forEach { put(it.toJson()) } })
        .put("views", JSONArray().apply { views.forEach { put(it.toJson()) } })
        .put("collectionId", collectionId ?: JSONObject.NULL)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

    companion object {
        fun fromJson(o: JSONObject): Database {
            fun <T> list(key: String, f: (JSONObject) -> T): List<T> =
                o.optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(f) } } ?: emptyList()
            return Database(
                id = o.optString("id"),
                name = o.optString("name"),
                icon = o.optString("icon").ifBlank { "🗃" },
                properties = list("properties", DBProperty::fromJson),
                records = list("records", DBRecord::fromJson),
                views = list("views", DBView::fromJson),
                collectionId = o.optString("collectionId").ifBlank { null },
                createdAt = o.optLong("createdAt"),
                updatedAt = o.optLong("updatedAt"),
            )
        }
    }
}

// =====================================================================================
//  Contenedor raíz (lo que se guarda en neko.json)
// =====================================================================================

data class NekoData(
    val notes: List<Note> = emptyList(),
    val pages: List<Page> = emptyList(),
    val collections: List<Collection> = emptyList(),
    val databases: List<Database> = emptyList(),
)
