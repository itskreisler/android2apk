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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klei.neko.data.DBProperty
import com.klei.neko.data.DBRecord
import com.klei.neko.data.DBView
import com.klei.neko.data.Database
import com.klei.neko.data.NoteStore
import com.klei.neko.data.PropertyType
import com.klei.neko.data.ViewType
import com.klei.neko.data.label
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseScreen(store: NoteStore, dbId: String, onBack: () -> Unit) {
    val data by store.data.collectAsState()
    val db = data.databases.firstOrNull { it.id == dbId }
    if (db == null) {
        LaunchedEffect(dbId) { onBack() }
        return
    }

    var viewId by remember { mutableStateOf("") }
    var addingProperty by remember { mutableStateOf(false) }

    val view = db.views.firstOrNull { it.id == viewId } ?: db.views.firstOrNull()
    val titleProp = db.properties.firstOrNull { it.type == PropertyType.TITLE } ?: db.properties.firstOrNull()

    if (addingProperty) {
        PropertyDialog(
            onDismiss = { addingProperty = false },
            onSave = { name, type, options ->
                store.addProperty(db.id, DBProperty(UUID.randomUUID().toString(), name, type, options))
                addingProperty = false
            },
        )
    }

    Scaffold(
        floatingActionButton = {
            androidx.compose.material3.FloatingActionButton(
                onClick = { store.addRecord(db.id) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = "Nuevo registro") }
        },
        topBar = {
            TopAppBar(
                title = { Row(verticalAlignment = Alignment.CenterVertically) { Text(db.icon); Spacer(Modifier.width(8.dp)); Text(db.name) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") } },
                actions = {
                    IconButton(onClick = { addingProperty = true }) { Icon(Icons.Filled.PlaylistAdd, contentDescription = "Añadir propiedad") }
                    IconButton(onClick = { store.deleteDatabase(db.id); onBack() }) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar base de datos") }
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
            // --- vistas
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                db.views.forEach { v ->
                    FilterChip(
                        selected = v.id == view?.id,
                        onClick = { viewId = v.id },
                        label = { Text("${v.name}") },
                    )
                }
                TextButton(onClick = {
                    val v = DBView(UUID.randomUUID().toString(), "Vista ${db.views.size + 1}", ViewType.TABLE)
                    store.addView(db.id, v)
                    viewId = v.id
                }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Vista")
                }
            }

            if (view != null) {
                // --- tipo de vista
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ViewType.entries.forEach { vt ->
                        FilterChip(
                            selected = view.type == vt,
                            onClick = { store.updateView(db.id, view.id) { it.copy(type = vt) } },
                            label = { Text(vt.label) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))

                // --- orden y filtro
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Orden:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    db.properties.forEach { p ->
                        FilterChip(
                            selected = view.sortBy == p.id,
                            onClick = {
                                store.updateView(db.id, view.id) {
                                    if (it.sortBy == p.id) it.copy(sortAsc = !it.sortAsc) else it.copy(sortBy = p.id, sortAsc = true)
                                }
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(p.name)
                                    if (view.sortBy == p.id) {
                                        Icon(
                                            imageVector = if (view.sortAsc) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Filtro:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    db.properties.forEach { p ->
                        FilterChip(
                            selected = view.filterProperty == p.id,
                            onClick = {
                                store.updateView(db.id, view.id) {
                                    if (it.filterProperty == p.id) it.copy(filterProperty = "") else it.copy(filterProperty = p.id)
                                }
                            },
                            label = { Text(p.name) },
                        )
                    }
                }
                if (view.filterProperty.isNotBlank()) {
                    OutlinedTextField(
                        value = view.filterValue,
                        onValueChange = { v -> store.updateView(db.id, view.id) { it.copy(filterValue = v) } },
                        placeholder = { Text("Contiene…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (view.type == ViewType.BOARD) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Agrupar por:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        db.properties.filter { it.type == PropertyType.SELECT }.forEach { p ->
                            FilterChip(
                                selected = view.groupBy == p.id,
                                onClick = { store.updateView(db.id, view.id) { it.copy(groupBy = p.id) } },
                                label = { Text(p.name) },
                            )
                        }
                        if (db.properties.none { it.type == PropertyType.SELECT }) {
                            Text("(no hay propiedades de selección)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                val records = applyView(db, view)
                when (view.type) {
                    ViewType.TABLE -> TableView(db, records, store)
                    ViewType.LIST -> ListView(db, records, store)
                    ViewType.BOARD -> BoardView(db, records, view, store)
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

private fun applyView(db: Database, view: DBView): List<DBRecord> {
    var list = db.records
    if (view.filterProperty.isNotBlank() && view.filterValue.isNotBlank()) {
        val pid = view.filterProperty
        val needle = view.filterValue
        list = list.filter { it.value(pid).contains(needle, ignoreCase = true) }
    }
    if (view.sortBy.isNotBlank()) {
        val pid = view.sortBy
        list = list.sortedBy { it.value(pid).lowercase() }
        if (!view.sortAsc) list = list.reversed()
    }
    return list
}

@Composable
private fun TableView(db: Database, records: List<DBRecord>, store: NoteStore) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column {
            Row {
                db.properties.forEach { prop ->
                    Text(
                        text = "${prop.name} · ${prop.type.label}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.width(170.dp).padding(6.dp),
                    )
                }
                Spacer(Modifier.width(40.dp))
            }
            HorizontalDivider()
            records.forEach { record ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    db.properties.forEach { prop ->
                        Box(Modifier.width(170.dp).padding(6.dp)) {
                            Cell(prop, record, db.id, store)
                        }
                    }
                    IconButton(onClick = { store.deleteRecord(db.id, record.id) }) { Icon(Icons.Filled.Close, contentDescription = "Eliminar registro", modifier = Modifier.size(16.dp)) }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ListView(db: Database, records: List<DBRecord>, store: NoteStore) {
    if (records.isEmpty()) {
        Text("Sin registros todavía. Crea uno con el botón de abajo.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        records.forEach { record ->
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp)) {
                    db.properties.forEach { prop ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                prop.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(110.dp),
                            )
                            Box(Modifier.weight(1f)) { Cell(prop, record, db.id, store) }
                        }
                    }
                    Row {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { store.deleteRecord(db.id, record.id) }) { Text("Eliminar") }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardView(db: Database, records: List<DBRecord>, view: DBView, store: NoteStore) {
    val groupProp = db.property(view.groupBy)
    if (groupProp == null) {
        Text("Elige una propiedad de selección para agrupar.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val columns = groupProp.options + "Sin valor"
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        columns.forEach { option ->
            val items = records.filter { r ->
                val v = r.value(groupProp.id)
                if (option == "Sin valor") v.isBlank() else v == option
            }
            Column(
                modifier = Modifier.width(200.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp),
            ) {
                Text("$option (${items.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                items.forEach { record ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    recordTitle(db, record),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                IconButton(onClick = { store.deleteRecord(db.id, record.id) }) { Icon(Icons.Filled.Close, contentDescription = "Eliminar registro", modifier = Modifier.size(16.dp)) }
                            }
                            TextButton(onClick = {
                                val next = groupProp.options.firstOrNull { it != record.value(groupProp.id) }
                                    ?: groupProp.options.firstOrNull() ?: ""
                                store.setRecordValue(db.id, record.id, groupProp.id, next)
                            }) { Text("Mover") }
                        }
                    }
                }
            }
        }
    }
}

private fun recordTitle(db: Database, record: DBRecord): String {
    val titleProp = db.properties.firstOrNull { it.type == PropertyType.TITLE } ?: db.properties.firstOrNull()
    val value = titleProp?.let { record.value(it.id) }.orEmpty()
    return value.ifBlank { "(sin título)" }
}

@Composable
private fun Cell(prop: DBProperty, record: DBRecord, dbId: String, store: NoteStore) {
    val value = record.value(prop.id)
    val set: (String) -> Unit = { v -> store.setRecordValue(dbId, record.id, prop.id, v) }
    when (prop.type) {
        PropertyType.TITLE, PropertyType.TEXT -> SimpleField(value, "…", set)
        PropertyType.NUMBER -> SimpleField(value.filter { it.isDigit() || it == '.' || it == '-' }, "0", { set(it) })
        PropertyType.RELATION -> SimpleField(value, "relación…", set)
        PropertyType.DATE -> {
            var showDate by remember { mutableStateOf(false) }
            TextButton(onClick = { showDate = true }) {
                Text(if (value.isBlank()) "—" else formatDay(value.toLongOrNull() ?: 0L))
            }
            if (showDate) {
                DateDialog(
                    initial = value.toLongOrNull() ?: 0L,
                    onDismiss = { showDate = false },
                    onConfirm = { millis -> set(millis.toString()); showDate = false },
                )
            }
        }
        PropertyType.CHECKBOX -> Checkbox(checked = value == "true", onCheckedChange = { set(it.toString()) })
        PropertyType.SELECT -> {
            if (prop.options.isEmpty()) {
                SimpleField(value, "opción", set)
            } else {
                TextButton(onClick = {
                    val idx = prop.options.indexOf(value)
                    set(prop.options[(idx + 1) % prop.options.size])
                }) { Text(value.ifBlank { "—" }) }
            }
        }
        PropertyType.MULTISELECT -> SimpleField(value, "a, b, c", set)
    }
}

@Composable
private fun SimpleField(value: String, placeholder: String, onValue: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                inner()
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PropertyDialog(onDismiss: () -> Unit, onSave: (String, PropertyType, List<String>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(PropertyType.TEXT) }
    var options by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva propiedad") },
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
                Text("Tipo", style = MaterialTheme.typography.labelMedium)
                Column {
                    PropertyType.entries.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pair.forEach { pt ->
                                FilterChip(
                                    selected = type == pt,
                                    onClick = { type = pt },
                                    label = { Text(pt.label, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                if (type == PropertyType.SELECT || type == PropertyType.MULTISELECT) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = options,
                        onValueChange = { options = it },
                        label = { Text("Opciones (separadas por comas)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val opts = if (type == PropertyType.SELECT || type == PropertyType.MULTISELECT) {
                    options.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                } else {
                    emptyList()
                }
                onSave(name.ifBlank { "Propiedad" }, type, opts)
            }) { Text("Añadir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
