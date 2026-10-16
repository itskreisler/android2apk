package com.buddyapps.neko.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddyapps.neko.data.NoteFilter
import com.buddyapps.neko.data.NoteStore
import com.buddyapps.neko.data.ViewMode
import com.buddyapps.neko.ui.theme.NekoTheme
import com.buddyapps.neko.ui.theme.ThemeMode
import kotlinx.coroutines.launch

private data class TabSpec(val label: String, val icon: String)

private val TABS = listOf(
    TabSpec("Inicio", "🏠"),
    TabSpec("Colecciones", "🗂"),
    TabSpec("Calendario", "📅"),
    TabSpec("Perfil", "🐾"),
)

@Composable
fun NekoApp() {
    val context = LocalContext.current
    val store = remember { NoteStore(context.applicationContext) }
    val notes by store.notes.collectAsState()

    LaunchedEffect(Unit) { store.load() }

    var themeMode by rememberSaveable { mutableStateOf(ThemeMode.SYSTEM) }
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    NekoTheme(darkTheme = dark) {
        var tab by rememberSaveable { mutableStateOf(0) }
        var editingId by rememberSaveable { mutableStateOf("") }
        var query by rememberSaveable { mutableStateOf("") }
        var filter by rememberSaveable { mutableStateOf(NoteFilter.ALL) }
        var viewMode by rememberSaveable { mutableStateOf(ViewMode.GRID) }

        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val message: (String) -> Unit = { m -> scope.launch { snackbar.showSnackbar(m) } }

        val editing = notes.firstOrNull { it.id == editingId }

        if (editing != null) {
            EditorScreen(
                note = editing,
                onChange = { store.upsert(it) },
                onBack = { editingId = "" },
                onDelete = {
                    store.delete(editing.id)
                    editingId = ""
                },
            )
        } else {
            Shell(
                tab = tab,
                onTab = { tab = it },
                notes = notes,
                query = query,
                onQueryChange = { query = it },
                filter = filter,
                onFilterChange = { filter = it },
                viewMode = viewMode,
                onViewModeChange = { viewMode = it },
                onOpen = { editingId = it },
                onToggleFavorite = { store.toggleFavorite(it) },
                onToggleItem = { id, i -> store.toggleItem(id, i) },
                onCreate = {
                    val n = store.newNote()
                    store.upsert(n)
                    editingId = n.id
                },
                themeMode = themeMode,
                onThemeMode = { themeMode = it },
                onExport = { store.export() },
                onImport = { store.import() },
                exportPath = store.exportPath,
                snackbar = snackbar,
                message = message,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Shell(
    tab: Int,
    onTab: (Int) -> Unit,
    notes: List<com.buddyapps.neko.data.Note>,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: NoteFilter,
    onFilterChange: (NoteFilter) -> Unit,
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit,
    onOpen: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleItem: (String, Int) -> Unit,
    onCreate: () -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onExport: suspend () -> String,
    onImport: suspend () -> Int,
    exportPath: String,
    snackbar: SnackbarHostState,
    message: (String) -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (tab == 0) {
                            Text("🐱", fontSize = 20.sp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(TABS[tab].label, fontWeight = FontWeight.SemiBold)
                    }
                },
                actions = { if (tab == 0) NekoAvatar("N") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { onTab(i) },
                        icon = { Text(t.icon, fontSize = 18.sp) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                FloatingActionButton(
                    onClick = onCreate,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(20.dp),
                ) { Text("＋", fontSize = 24.sp) }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> HomeScreen(
                    notes = notes,
                    query = query,
                    onQueryChange = onQueryChange,
                    filter = filter,
                    onFilterChange = onFilterChange,
                    viewMode = viewMode,
                    onViewModeChange = onViewModeChange,
                    onOpen = onOpen,
                    onToggleFavorite = onToggleFavorite,
                    onToggleItem = onToggleItem,
                )

                1 -> PlaceholderScreen(
                    emoji = "🗂",
                    title = "Colecciones",
                    subtitle = "Espacios y páginas anidadas, estilo Notion",
                    bullets = listOf(
                        "Colecciones con icono, color y descripción",
                        "Páginas con jerarquía (subpáginas)",
                        "Mover páginas entre colecciones y migas de pan",
                    ),
                )

                2 -> PlaceholderScreen(
                    emoji = "📅",
                    title = "Calendario",
                    subtitle = "Notas y tareas organizadas por fecha",
                    bullets = listOf(
                        "Vista mensual con las notas del día",
                        "Fecha límite opcional en las listas de tareas",
                        "Recordatorios locales",
                    ),
                )

                else -> PerfilScreen(
                    themeMode = themeMode,
                    onThemeMode = onThemeMode,
                    noteCount = notes.size,
                    exportPath = exportPath,
                    onExport = onExport,
                    onImport = onImport,
                    onMessage = message,
                )
            }
        }
    }
}
