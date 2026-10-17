package com.klei.neko.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klei.neko.data.NekoData
import com.klei.neko.data.NoteFilter
import com.klei.neko.data.NoteStore
import com.klei.neko.data.ViewMode
import com.klei.neko.ui.theme.NekoTheme
import com.klei.neko.ui.theme.ThemeMode
import kotlinx.coroutines.launch

private data class TabSpec(val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabSpec("Inicio", Icons.Filled.Home),
    TabSpec("Colecciones", Icons.Filled.Folder),
    TabSpec("Calendario", Icons.Filled.CalendarMonth),
    TabSpec("Perfil", Icons.Filled.Pets),
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NekoApp() {
    val context = LocalContext.current
    val store = remember { NoteStore(context.applicationContext) }
    val data by store.data.collectAsState()
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        store.load()
        loaded = true
    }

    var themeMode by rememberSaveable { mutableStateOf(ThemeMode.SYSTEM) }
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    NekoTheme(darkTheme = dark) {
        if (!loaded) {
            NekoLoading()
            return@NekoTheme
        }

        var tab by rememberSaveable { mutableStateOf(0) }
        var route by rememberSaveable { mutableStateOf("") }

        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val message: (String) -> Unit = { m -> scope.launch { snackbar.showSnackbar(m) } }

        SharedTransitionLayout {
            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(initialScale = 0.97f)) togetherWith
                        (fadeOut(tween(160)) + scaleOut(targetScale = 0.98f))
                },
                label = "route",
            ) { target ->
                val avScope: AnimatedVisibilityScope = this
                when {
                    target.startsWith("note:") -> {
                        val id = target.removePrefix("note:")
                        val note = data.notes.firstOrNull { it.id == id }
                        if (note == null) {
                            LaunchedEffect(id) { route = "" }
                        } else {
                            EditorScreen(
                                note = note,
                                onChange = { store.upsert(it) },
                                onBack = { route = "" },
                                onDelete = { store.delete(id); route = "" },
                                shared = this@SharedTransitionLayout,
                                animated = avScope,
                            )
                        }
                    }

                    target.startsWith("page:") -> PageEditorScreen(
                        store = store,
                        pageId = target.removePrefix("page:"),
                        onBack = { route = "" },
                        onOpenPage = { route = "page:$it" },
                    )

                    target.startsWith("db:") -> DatabaseScreen(
                        store = store,
                        dbId = target.removePrefix("db:"),
                        onBack = { route = "" },
                    )

                    else -> Shell(
                        store = store,
                        data = data,
                        tab = tab,
                        onTab = { tab = it },
                        onOpenNote = { route = "note:$it" },
                        onOpenPage = { route = "page:$it" },
                        onOpenDatabase = { route = "db:$it" },
                        themeMode = themeMode,
                        onThemeMode = { themeMode = it },
                        snackbar = snackbar,
                        message = message,
                        shared = this@SharedTransitionLayout,
                        animated = avScope,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Shell(
    store: NoteStore,
    data: NekoData,
    tab: Int,
    onTab: (Int) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenPage: (String) -> Unit,
    onOpenDatabase: (String) -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    snackbar: SnackbarHostState,
    message: (String) -> Unit,
    shared: SharedTransitionScope,
    animated: AnimatedVisibilityScope,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(NoteFilter.ALL) }
    var viewMode by rememberSaveable { mutableStateOf(ViewMode.GRID) }
    val haptics = LocalHapticFeedback.current

    val fab: @Composable () -> Unit = {
        when (tab) {
            0 -> FloatingActionButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    val n = store.newNote()
                    store.upsert(n)
                    onOpenNote(n.id)
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = "Nueva nota") }

            1 -> FloatingActionButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    val p = store.newPage()
                    store.upsertPage(p)
                    onOpenPage(p.id)
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = "Nueva página") }
        }
    }

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
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = fab,
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> HomeScreen(
                    notes = data.notes,
                    query = query,
                    onQueryChange = { query = it },
                    filter = filter,
                    onFilterChange = { filter = it },
                    viewMode = viewMode,
                    onViewModeChange = { viewMode = it },
                    onOpen = onOpenNote,
                    onToggleFavorite = { store.toggleFavorite(it) },
                    onToggleItem = { id, i -> store.toggleItem(id, i) },
                    shared = shared,
                    animated = animated,
                )

                1 -> CollectionsScreen(
                    collections = data.collections,
                    pages = data.pages,
                    databases = data.databases,
                    onOpenPage = onOpenPage,
                    onCreatePage = { parentId, collectionId ->
                        val p = store.newPage(parentId, collectionId)
                        store.upsertPage(p)
                        onOpenPage(p.id)
                    },
                    onCreateCollection = { name, icon, color, description ->
                        store.upsertCollection(store.newCollection(name, icon, color).copy(description = description))
                    },
                    onUpdateCollection = { store.upsertCollection(it) },
                    onDeleteCollection = { store.deleteCollection(it) },
                    onOpenDatabase = onOpenDatabase,
                    onCreateDatabase = {
                        val d = store.newDatabase()
                        store.upsertDatabase(d)
                        onOpenDatabase(d.id)
                    },
                    onDeleteDatabase = { store.deleteDatabase(it) },
                )

                2 -> CalendarScreen(store = store, onOpenNote = onOpenNote)

                else -> PerfilScreen(
                    themeMode = themeMode,
                    onThemeMode = onThemeMode,
                    noteCount = data.notes.size,
                    pageCount = data.pages.size,
                    databaseCount = data.databases.size,
                    exportPath = store.exportPath,
                    onExport = { store.export() },
                    onImport = { store.import() },
                    onMessage = message,
                )
            }
        }
    }
}
