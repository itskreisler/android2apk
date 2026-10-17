package com.klei.neko.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klei.neko.data.Block
import com.klei.neko.data.BlockType
import com.klei.neko.data.NoteStore
import com.klei.neko.data.Page
import com.klei.neko.data.emoji
import com.klei.neko.data.label

private val ICONS = listOf("📄", "📝", "📌", "🌱", "🌸", "⭐", "🎀", "🐱", "🧠", "💡", "📚", "🗺", "🎧", "🍰", "🔧", "❤️")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
    store: NoteStore,
    pageId: String,
    onBack: () -> Unit,
    onOpenPage: (String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val data by store.data.collectAsState()
    val page = data.pages.firstOrNull { it.id == pageId }
    if (page == null) {
        LaunchedEffect(pageId) { onBack() }
        return
    }

    var iconMenu by remember { mutableStateOf(false) }
    val chain = remember(page, data.pages) { ancestorsOf(page, data.pages) }
    val children = data.pages.filter { it.parentId == page.id }.sortedByDescending { it.updatedAt }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        chain.forEach { ancestor ->
                            Text(
                                text = ancestor.title.ifBlank { "Sin título" },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { onOpenPage(ancestor.id) },
                            )
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                        Text(page.title.ifBlank { "Sin título" }, style = MaterialTheme.typography.labelLarge)
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "Atrás" },
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") }
                },
                actions = {
                    IconButton(onClick = { store.upsertPage(page.copy(favorite = !page.favorite)) }) {
                        Icon(
                            imageVector = if (page.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = if (page.favorite) "Quitar de fijadas" else "Fijar",
                        )
                    }
                    IconButton(
                        onClick = { store.deletePage(page.id); onBack() },
                        modifier = Modifier.semantics { contentDescription = "Eliminar página" },
                    ) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar página") }
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Text(
                        text = page.icon,
                        fontSize = 34.sp,
                        modifier = Modifier.clickable { iconMenu = true },
                    )
                    DropdownMenu(expanded = iconMenu, onDismissRequest = { iconMenu = false }) {
                        ICONS.forEach { emoji ->
                            DropdownMenuItem(
                                text = { Text("$emoji   ${emojiLabel(emoji)}") },
                                onClick = { store.upsertPage(page.copy(icon = emoji)); iconMenu = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = page.title,
                    onValueChange = { store.upsertPage(page.copy(title = it)) },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box {
                            if (page.title.isEmpty()) {
                                Text("Sin título", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            inner()
                        }
                    },
                )
            }

            Spacer(Modifier.height(16.dp))

            page.blocks.forEachIndexed { index, block ->
                BlockRow(
                    block = block,
                    index = index,
                    count = page.blocks.size,
                    pageId = page.id,
                    store = store,
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("Añadir bloque", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BlockType.entries.forEach { type ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            store.addBlock(page.id, type)
                        },
                        label = { Text("${type.emoji} ${type.label}") },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Subpáginas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val child = store.newPage(parentId = page.id, collectionId = page.collectionId)
                    store.upsertPage(child)
                    onOpenPage(child.id)
                }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Subpágina")
                }
            }
            if (children.isEmpty()) {
                Text("Sin subpáginas todavía", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                children.forEach { child ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenPage(child.id) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(child.icon, fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(child.title.ifBlank { "Sin título" }, style = MaterialTheme.typography.bodyLarge)
                            if (child.preview.isNotBlank()) {
                                Text(
                                    child.preview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Colección", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = page.collectionId == null,
                    onClick = { store.movePage(page.id, null) },
                    label = { Text("Sin colección") },
                )
                data.collections.forEach { c ->
                    FilterChip(
                        selected = page.collectionId == c.id,
                        onClick = { store.movePage(page.id, c.id) },
                        label = { Text("${c.icon} ${c.name}") },
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun BlockRow(block: Block, index: Int, count: Int, pageId: String, store: NoteStore) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(30.dp)) {
                    Text(block.type.emoji, fontSize = 12.sp)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    BlockType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text("${type.emoji}  ${type.label}") },
                            onClick = {
                                store.updateBlock(pageId, block.id) { it.copy(type = type) }
                                menu = false
                            },
                        )
                    }
                }
            }
            if (index > 0) {
                IconButton(onClick = { store.moveBlock(pageId, index, -1) }, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Subir bloque", modifier = Modifier.size(18.dp))
                }
            }
            if (index < count - 1) {
                IconButton(onClick = { store.moveBlock(pageId, index, 1) }, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Bajar bloque", modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = { store.deleteBlock(pageId, block.id) }, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Eliminar bloque", modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(4.dp))
        Box(Modifier.weight(1f).padding(vertical = 4.dp)) {
            BlockContent(block, index, pageId, store)
        }
    }
}

@Composable
private fun BlockContent(block: Block, index: Int, pageId: String, store: NoteStore) {
    val onText: (String) -> Unit = { s -> store.updateBlock(pageId, block.id) { it.copy(text = s) } }
    when (block.type) {
        BlockType.TEXT -> PlainBlock(block.text, MaterialTheme.typography.bodyLarge, "Texto", onText)
        BlockType.H1 -> PlainBlock(block.text, MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), "Encabezado 1", onText)
        BlockType.H2 -> PlainBlock(block.text, MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), "Encabezado 2", onText)
        BlockType.H3 -> PlainBlock(block.text, MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), "Encabezado 3", onText)
        BlockType.BULLET -> PrefixedBlock("•", block.text, onText)
        BlockType.NUMBERED -> PrefixedBlock("${index + 1}.", block.text, onText)
        BlockType.TASK -> Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = block.checked, onCheckedChange = { c -> store.updateBlock(pageId, block.id) { it.copy(checked = c) } })
            PlainBlock(
                text = block.text,
                style = MaterialTheme.typography.bodyLarge,
                placeholder = "Tarea",
                onText = onText,
            )
        }
        BlockType.QUOTE -> Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(3.dp).heightIn(min = 24.dp).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                PlainBlock(block.text, MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic), "Cita", onText)
            }
        }
        BlockType.DIVIDER -> HorizontalDivider(Modifier.padding(vertical = 10.dp))
        BlockType.LINK -> Column {
            PlainBlock(block.text, MaterialTheme.typography.bodyLarge, "Texto del enlace", onText)
            PlainBlock(block.url, MaterialTheme.typography.bodyMedium, "https://…", { u -> store.updateBlock(pageId, block.id) { it.copy(url = u) } })
        }
        BlockType.IMAGE -> Column {
            Surface(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(30.dp))
                        Text(
                            "Imagen por URL (sin Coil: solo se guarda la dirección)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            PlainBlock(block.url, MaterialTheme.typography.bodyMedium, "https://… (URL de la imagen)", { u -> store.updateBlock(pageId, block.id) { it.copy(url = u) } })
        }
        BlockType.CODE -> Column {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(Modifier.padding(10.dp)) {
                    PlainBlock(
                        text = block.text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        placeholder = "código…",
                        onText = onText,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            PlainBlock(
                block.language,
                MaterialTheme.typography.bodySmall,
                "lenguaje (kotlin, sh…)",
                { l -> store.updateBlock(pageId, block.id) { it.copy(language = l) } },
            )
        }
    }
}

@Composable
private fun PlainBlock(text: String, style: TextStyle, placeholder: String, onText: (String) -> Unit) {
    BasicTextField(
        value = text,
        onValueChange = onText,
        modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp),
        textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Box {
                if (text.isEmpty()) {
                    Text(placeholder, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                inner()
            }
        },
    )
}

@Composable
private fun PrefixedBlock(prefix: String, text: String, onText: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            prefix,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(26.dp),
        )
        Box(Modifier.weight(1f)) {
            PlainBlock(text, MaterialTheme.typography.bodyLarge, "Elemento", onText)
        }
    }
}

private fun ancestorsOf(page: Page, pages: List<Page>): List<Page> {
    val out = ArrayDeque<Page>()
    var current: Page? = page
    val seen = mutableSetOf<String>()
    while (current != null && seen.add(current.id)) {
        current = current.parentId?.let { id -> pages.firstOrNull { it.id == id } }
        if (current != null) out.addFirst(current)
    }
    return out.toList()
}

private fun emojiLabel(emoji: String): String = when (emoji) {
    "📄" -> "Página"
    "📝" -> "Notas"
    "📌" -> "Fijado"
    "🌱" -> "Idea"
    "🌸" -> "Bonito"
    "⭐" -> "Favorito"
    "🎀" -> "Regalo"
    "🐱" -> "Neko"
    "🧠" -> "Cerebro"
    "💡" -> "Idea"
    "📚" -> "Libro"
    "🗺" -> "Mapa"
    "🎧" -> "Música"
    "🍰" -> "Receta"
    "🔧" -> "Proyecto"
    else -> "Corazón"
}
