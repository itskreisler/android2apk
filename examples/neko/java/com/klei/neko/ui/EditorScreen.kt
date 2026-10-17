package com.klei.neko.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.klei.neko.data.ChecklistItem
import com.klei.neko.data.Note
import com.klei.neko.ui.theme.NoteCardColors

/**
 * Editor de notas y listas de tareas. Las páginas con bloques viven en PageEditorScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    note: Note,
    onChange: (Note) -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    shared: SharedTransitionScope? = null,
    animated: AnimatedVisibilityScope? = null,
) {
    var tagDraft by remember { mutableStateOf("") }
    var noteDate by remember { mutableStateOf(false) }
    var itemDate by remember { mutableStateOf(-1) }

    if (noteDate) {
        DateDialog(
            initial = note.dueDate,
            onDismiss = { noteDate = false },
            onConfirm = { onChange(note.copy(dueDate = it)); noteDate = false },
        )
    }
    if (itemDate >= 0) {
        DateDialog(
            initial = note.items.getOrNull(itemDate)?.dueDate ?: 0,
            onDismiss = { itemDate = -1 },
            onConfirm = { millis ->
                onChange(note.copy(items = note.items.replaceAt(itemDate) { it.copy(dueDate = millis) }))
                itemDate = -1
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (note.isTaskList) "Lista de tareas" else "Nota") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") } },
                actions = {
                    IconButton(onClick = { onChange(note.copy(favorite = !note.favorite)) }) {
                        Icon(
                            imageVector = if (note.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = if (note.favorite) "Quitar de fijadas" else "Fijar",
                        )
                    }
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .nekoSharedBounds(shared, animated, "note-${note.id}"),
        ) {
            BasicTextField(
                value = note.title,
                onValueChange = { onChange(note.copy(title = it)) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    Box {
                        if (note.title.isEmpty()) {
                            Text("Título", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(12.dp))

            if (note.isTaskList) {
                TaskItems(note, onChange) { itemDate = it }
            } else {
                BasicTextField(
                    value = note.body,
                    onValueChange = { onChange(note.copy(body = it)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box {
                            if (note.body.isEmpty()) {
                                Text("Escribe algo bonito…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            inner()
                        }
                    },
                )
            }

            Spacer(Modifier.height(20.dp))
            TextButton(onClick = { onChange(convertType(note)) }) {
                Text(if (note.isTaskList) "Convertir en nota" else "Convertir en lista de tareas")
            }

            Spacer(Modifier.height(8.dp))
            Text("Fecha límite", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (note.dueDate > 0) {
                    PillTag(formatDay(note.dueDate), icon = Icons.Filled.CalendarMonth)
                    TextButton(onClick = { onChange(note.copy(dueDate = 0)) }) { Text("Quitar") }
                } else {
                    TextButton(onClick = { onChange(note.copy(dueDate = startOfDay(System.currentTimeMillis()))) }) { Text("Hoy") }
                    TextButton(onClick = { onChange(note.copy(dueDate = startOfDay(addDays(System.currentTimeMillis(), 1)))) }) { Text("Mañana") }
                    TextButton(onClick = { noteDate = true }) { Text("Elegir…") }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Color", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            ColorPicker(note.color) { onChange(note.copy(color = it)) }

            Spacer(Modifier.height(20.dp))
            Text("Etiquetas", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            TagRow(note.tags) { onChange(note.copy(tags = it)) }

            Spacer(Modifier.height(10.dp))
            BasicTextField(
                value = tagDraft,
                onValueChange = { tagDraft = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    Box {
                        if (tagDraft.isEmpty()) {
                            Text("Nueva etiqueta + Enter", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = {
                    val t = tagDraft.trim().trimStart('#')
                    if (t.isNotEmpty() && t !in note.tags) onChange(note.copy(tags = note.tags + t))
                    tagDraft = ""
                },
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Añadir etiqueta")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TaskItems(note: Note, onChange: (Note) -> Unit, onPickDate: (Int) -> Unit) {
    note.items.forEachIndexed { index, item ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(
                checked = item.done,
                onCheckedChange = { onChange(note.copy(items = note.items.replaceAt(index) { it.copy(done = !it.done) })) },
            )
            BasicTextField(
                value = item.text,
                onValueChange = { v -> onChange(note.copy(items = note.items.replaceAt(index) { it.copy(text = v) })) },
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    Box {
                        if (item.text.isEmpty()) {
                            Text("Elemento ${index + 1}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    }
                },
            )
            if (item.dueDate > 0) {
                TextButton(onClick = { onPickDate(index) }) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = "Cambiar fecha", modifier = Modifier.size(16.dp))
                }
            } else {
                IconButton(onClick = { onPickDate(index) }) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = "Añadir fecha", modifier = Modifier.size(16.dp))
                }
            }
            IconButton(onClick = { onChange(note.copy(items = note.items.filterIndexed { i, _ -> i != index })) }) {
                Icon(Icons.Filled.Close, contentDescription = "Eliminar elemento", modifier = Modifier.size(18.dp))
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = { onChange(note.copy(items = note.items + ChecklistItem(""))) }) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Añadir elemento")
    }
}

@Composable
private fun ColorPicker(current: Long, onPick: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NoteCardColors.forEach { c ->
            val selected = c == current
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .clickable { onPick(c) },
            )
        }
    }
}

@Composable
private fun TagRow(tags: List<String>, onChange: (List<String>) -> Unit) {
    if (tags.isEmpty()) {
        Text("Sin etiquetas todavía", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        tags.forEach { tag ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onChange(tags - tag) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("#$tag", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.Close, contentDescription = "Quitar etiqueta", modifier = Modifier.size(12.dp))
            }
        }
    }
}

private fun List<ChecklistItem>.replaceAt(index: Int, block: (ChecklistItem) -> ChecklistItem): List<ChecklistItem> =
    mapIndexed { i, item -> if (i == index) block(item) else item }

private fun convertType(note: Note): Note = if (note.isTaskList) {
    note.copy(
        isTaskList = false,
        body = note.body.ifBlank { note.items.joinToString("\n") { it.text } },
        items = emptyList(),
    )
} else {
    val lines = note.body.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    note.copy(
        isTaskList = true,
        items = (if (lines.isEmpty()) listOf("") else lines).map { ChecklistItem(it) },
        body = "",
    )
}
