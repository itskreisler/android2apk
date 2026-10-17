package com.klei.neko.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klei.neko.data.Collection
import com.klei.neko.data.Database
import com.klei.neko.data.Page
import com.klei.neko.ui.theme.NoteCardColors

private val COLLECTION_ICONS = listOf("🗂", "📁", "🌸", "⭐", "🎀", "🐱", "🧠", "📚", "🗺", "🍰", "🔧", "❤️", "🌱", "💡")

@Composable
fun CollectionsScreen(
    collections: List<Collection>,
    pages: List<Page>,
    databases: List<Database>,
    onOpenPage: (String) -> Unit,
    onCreatePage: (parentId: String?, collectionId: String?) -> Unit,
    onCreateCollection: (String, String, Long, String) -> Unit,
    onUpdateCollection: (Collection) -> Unit,
    onDeleteCollection: (String) -> Unit,
    onOpenDatabase: (String) -> Unit,
    onCreateDatabase: () -> Unit,
    onDeleteDatabase: (String) -> Unit,
) {
    var selected by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var editing by remember { mutableStateOf<Collection?>(null) }
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        CollectionDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = { name, icon, color, desc ->
                onCreateCollection(name, icon, color, desc)
                creating = false
            },
            onDelete = null,
        )
    }
    editing?.let { target ->
        CollectionDialog(
            initial = target,
            onDismiss = { editing = null },
            onSave = { name, icon, color, desc ->
                onUpdateCollection(target.copy(name = name, icon = icon, color = color, description = desc))
                editing = null
            },
            onDelete = {
                onDeleteCollection(target.id)
                if (selected == target.id) selected = ""
                editing = null
            },
        )
    }

    val roots = pages
        .filter { it.parentId == null && (selected == "" || it.collectionId == selected) }
        .sortedByDescending { it.updatedAt }
    val flat = remember(pages, roots, expanded) { flattenTree(pages, roots, expanded) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Colecciones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Nueva")
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    CollectionCard(
                        icon = "🗂",
                        name = "Todas",
                        count = pages.size,
                        color = 0xFFEDE9FE,
                        selected = selected == "",
                        onClick = { selected = "" },
                        onEdit = null,
                    )
                }
                items(collections, key = { it.id }) { c ->
                    CollectionCard(
                        icon = c.icon,
                        name = c.name,
                        count = pages.count { it.collectionId == c.id },
                        color = c.color,
                        selected = selected == c.id,
                        onClick = { selected = c.id },
                        onEdit = { editing = c },
                    )
                }
            }
        }

        item { Spacer(Modifier.height(10.dp)) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Páginas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { onCreatePage(null, selected.ifBlank { null }) }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Nueva")
                }
            }
        }
        if (flat.isEmpty()) {
            item {
                Text(
                    "Ninguna página aquí todavía. Crea una y anida subpáginas dentro.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
        items(flat, key = { it.first.id }) { (page, depth) ->
            PageRow(
                page = page,
                depth = depth,
                hasChildren = pages.any { it.parentId == page.id },
                expanded = page.id in expanded,
                onToggle = { expanded = if (page.id in expanded) expanded - page.id else expanded + page.id },
                onClick = { onOpenPage(page.id) },
                collectionName = collections.firstOrNull { it.id == page.collectionId }?.let { "${it.icon} ${it.name}" }.orEmpty(),
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bases de datos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onCreateDatabase) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Nueva")
                }
            }
        }
        if (databases.isEmpty()) {
            item {
                Text(
                    "Sin bases de datos. Crea una para organizar registros en tabla, lista o tablero.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
        items(databases, key = { it.id }) { db ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpenDatabase(db.id) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(db.icon, fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(db.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        "${db.records.size} registros · ${db.properties.size} propiedades",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onDeleteDatabase(db.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar base de datos", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PageRow(
    page: Page,
    depth: Int,
    hasChildren: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    collectionName: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (depth * 20).dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasChildren) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ArrowDropDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "Contraer" else "Expandir",
                modifier = Modifier.clickable(onClick = onToggle).padding(horizontal = 4.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Spacer(Modifier.width(20.dp))
        }
        Text(page.icon, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(page.title.ifBlank { "Sin título" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (collectionName.isNotBlank()) {
                Text(collectionName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (page.favorite) Icon(Icons.Filled.Star, contentDescription = "Fijada", modifier = Modifier.size(13.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun CollectionCard(
    icon: String,
    name: String,
    count: Int,
    color: Long,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: (() -> Unit)?,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(140.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color(color),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 3.dp else 1.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 22.sp)
                Spacer(Modifier.weight(1f))
                if (onEdit != null) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar colección", modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count páginas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CollectionDialog(
    initial: Collection?,
    onDismiss: () -> Unit,
    onSave: (String, String, Long, String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: "🗂") }
    var color by remember { mutableStateOf(initial?.color ?: 0xFFEDE9FE) }
    var desc by remember { mutableStateOf(initial?.description ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nueva colección" else "Editar colección") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text("Icono", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(COLLECTION_ICONS) { e ->
                        Text(
                            e,
                            fontSize = 22.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (e == icon) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { icon = e }
                                .padding(6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Color", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(NoteCardColors) { c ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.ifBlank { "Colección" }, icon, color, desc) }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Eliminar") }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

private fun flattenTree(pages: List<Page>, roots: List<Page>, expanded: Set<String>): List<Pair<Page, Int>> {
    val byParent = pages.groupBy { it.parentId }
    val out = mutableListOf<Pair<Page, Int>>()
    fun walk(page: Page, depth: Int) {
        out += page to depth
        if (page.id in expanded) {
            byParent[page.id]?.sortedByDescending { it.updatedAt }?.forEach { walk(it, depth + 1) }
        }
    }
    roots.forEach { walk(it, 0) }
    return out
}
