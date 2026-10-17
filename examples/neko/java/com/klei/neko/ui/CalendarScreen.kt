package com.klei.neko.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klei.neko.data.NoteStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val WEEKDAYS = listOf("L", "M", "X", "J", "V", "S", "D")

private data class CalEntry(
    val noteId: String,
    val label: String,
    val task: Boolean,
    val done: Boolean,
)

@Composable
fun CalendarScreen(store: NoteStore, onOpenNote: (String) -> Unit) {
    val data by store.data.collectAsState()
    val today = remember { startOfDay(System.currentTimeMillis()) }
    var anchor by remember { mutableStateOf(today) }
    var selected by remember { mutableStateOf(today) }

    val entries: Map<Long, List<CalEntry>> = remember(data.notes) {
        val map = mutableMapOf<Long, MutableList<CalEntry>>()
        data.notes.forEach { note ->
            if (note.dueDate > 0) {
                map.getOrPut(startOfDay(note.dueDate)) { mutableListOf() }
                    .add(CalEntry(note.id, note.title.ifBlank { "(sin título)" }, false, false))
            }
            note.items.forEach { item ->
                if (item.dueDate > 0) {
                    map.getOrPut(startOfDay(item.dueDate)) { mutableListOf() }
                        .add(CalEntry(note.id, "${note.title.ifBlank { "Tareas" }} · ${item.text.ifBlank { "elemento" }}", true, item.done))
                }
            }
        }
        map
    }

    val cal = remember(anchor) {
        Calendar.getInstance().apply {
            timeInMillis = anchor
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    val monthTitle = remember(anchor) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(anchor))
    }
    val firstWeekday = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // lunes = 0
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) + 1
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val cells = buildList<Long?> {
        repeat(firstWeekday) { add(null) }
        for (day in 1..daysInMonth) add(toMillis(year, month, day))
        while (size % 7 != 0) add(null)
    }
    val weeks = cells.chunked(7)
    val dayEntries = entries[selected].orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { anchor = shiftMonth(anchor, -1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mes anterior") }
                Text(
                    text = monthTitle.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { anchor = shiftMonth(anchor, 1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mes siguiente") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth()) {
                WEEKDAYS.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        items(weeks) { week ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                week.forEach { dayMillis ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (dayMillis == null) {
                            Spacer(Modifier.size(40.dp))
                        } else {
                            val isSelected = dayMillis == selected
                            val isToday = dayMillis == today
                            val count = entries[dayMillis]?.size ?: 0
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .border(
                                        width = if (isToday) 2.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape,
                                    )
                                    .clickable { selected = dayMillis },
                            ) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = dayOfMonth(dayMillis).toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                )
                                if (count > 0) {
                                    Text(
                                        text = "•$count",
                                        fontSize = 9.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                text = formatDay(selected),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
        }
        if (dayEntries.isEmpty()) {
            item {
                Text(
                    "Nada para este día. Añade una fecha límite a una nota o a un elemento de tarea.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(dayEntries) { entry ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpenNote(entry.noteId) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when {
                        !entry.task -> Icons.Filled.EventNote
                        entry.done -> Icons.Filled.CheckBox
                        else -> Icons.Filled.CheckBoxOutlineBlank
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun shiftMonth(anchor: Long, delta: Int): Long = Calendar.getInstance().apply {
    timeInMillis = anchor
    set(Calendar.DAY_OF_MONTH, 1)
    add(Calendar.MONTH, delta)
}.timeInMillis

private fun dayOfMonth(millis: Long): Int = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_MONTH)
