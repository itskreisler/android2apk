package com.klei.neko.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.klei.neko.data.Note
import com.klei.neko.data.NoteFilter
import com.klei.neko.data.ViewMode

@Composable
fun HomeScreen(
    notes: List<Note>,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: NoteFilter,
    onFilterChange: (NoteFilter) -> Unit,
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit,
    onOpen: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleItem: (String, Int) -> Unit,
    shared: SharedTransitionScope? = null,
    animated: AnimatedVisibilityScope? = null,
) {
    val visible = remember(notes, query, filter) { filterNotes(notes, query, filter) }
    val favorites = visible.filter { it.favorite }
    val others = visible.filterNot { it.favorite }

    AnimatedContent(targetState = viewMode, label = "viewMode") { mode ->
        LazyVerticalGrid(
            columns = if (mode == ViewMode.GRID) GridCells.Adaptive(150.dp) else GridCells.Fixed(1),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { WelcomeHeader() }
            item(span = { GridItemSpan(maxLineSpan) }) { SearchField(query, onQueryChange) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                FilterRow(filter, onFilterChange, viewMode, onViewModeChange)
            }

            if (visible.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        emoji = if (query.isBlank()) "🐱" else "🐾",
                        title = if (query.isBlank()) "Neko está esperando" else "Sin resultados",
                        subtitle = if (query.isBlank()) {
                            "Crea tu primera nota con el botón de abajo"
                        } else {
                            "Prueba con otra palabra o cambia el filtro"
                        },
                    )
                }
            } else {
                if (favorites.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Fijadas") }
                    items(favorites, key = { it.id }) { note ->
                        CardFor(note, onOpen, onToggleFavorite, onToggleItem, shared, animated)
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(if (favorites.isEmpty()) "Recientes" else "Todas")
                }
                items(others, key = { it.id }) { note ->
                    CardFor(note, onOpen, onToggleFavorite, onToggleItem, shared, animated)
                }
            }
        }
    }
}

@Composable
private fun CardFor(
    note: Note,
    onOpen: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleItem: (String, Int) -> Unit,
    shared: SharedTransitionScope?,
    animated: AnimatedVisibilityScope?,
) {
    NoteCard(
        note = note,
        onClick = { onOpen(note.id) },
        onToggleFavorite = { onToggleFavorite(note.id) },
        onToggleItem = { onToggleItem(note.id, it) },
        shared = shared,
        animated = animated,
    )
}

private fun filterNotes(notes: List<Note>, query: String, filter: NoteFilter): List<Note> {
    val q = query.trim().lowercase()
    return notes.asSequence()
        .filter { !it.archived }
        .filter {
            when (filter) {
                NoteFilter.ALL -> true
                NoteFilter.NOTES -> !it.isTaskList
                NoteFilter.TASKS -> it.isTaskList
                NoteFilter.FAVORITES -> it.favorite
            }
        }
        .filter {
            q.isEmpty() ||
                it.title.lowercase().contains(q) ||
                it.body.lowercase().contains(q) ||
                it.tags.any { tag -> tag.lowercase().contains(q) } ||
                it.items.any { item -> item.text.lowercase().contains(q) }
        }
        .toList()
}

@Composable
private fun WelcomeHeader() {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(
            text = "Hola 🐱",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "¿Qué se te ocurre hoy?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Buscar notas, tareas, etiquetas…") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.semantics { contentDescription = "Borrar búsqueda" },
                ) { Icon(Icons.Filled.Close, contentDescription = null) }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    filter: NoteFilter,
    onFilterChange: (NoteFilter) -> Unit,
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filter == NoteFilter.ALL,
                onClick = { onFilterChange(NoteFilter.ALL) },
                label = { Text("Todas") },
            )
            FilterChip(
                selected = filter == NoteFilter.NOTES,
                onClick = { onFilterChange(NoteFilter.NOTES) },
                label = { Text("Notas") },
            )
            FilterChip(
                selected = filter == NoteFilter.TASKS,
                onClick = { onFilterChange(NoteFilter.TASKS) },
                label = { Text("Tareas") },
            )
            FilterChip(
                selected = filter == NoteFilter.FAVORITES,
                onClick = { onFilterChange(NoteFilter.FAVORITES) },
                label = { Icon(Icons.Filled.Star, contentDescription = "Favoritas", modifier = Modifier.size(16.dp)) },
            )
        }
        IconButton(
            onClick = {
                onViewModeChange(if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
            },
            modifier = Modifier.semantics {
                contentDescription = if (viewMode == ViewMode.GRID) "Ver como lista" else "Ver como cuadrícula"
            },
        ) {
            Icon(
                imageVector = if (viewMode == ViewMode.GRID) {
                    Icons.Filled.GridView
                } else {
                    Icons.AutoMirrored.Filled.ViewList
                },
                contentDescription = null,
            )
        }
    }
}
