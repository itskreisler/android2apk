package com.klei.neko.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Medianoche del día que contiene [millis]. */
fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun toMillis(year: Int, month1to12: Int, day: Int): Long = Calendar.getInstance().apply {
    set(Calendar.YEAR, year)
    set(Calendar.MONTH, month1to12 - 1)
    set(Calendar.DAY_OF_MONTH, day)
    set(Calendar.HOUR_OF_DAY, 9)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun addDays(millis: Long, days: Int): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    add(Calendar.DAY_OF_MONTH, days)
}.timeInMillis

fun formatDay(millis: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))

@Composable
fun DateDialog(initial: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { if (initial > 0) timeInMillis = initial }
    var year by remember { mutableStateOf(cal.get(Calendar.YEAR).toString()) }
    var month by remember { mutableStateOf((cal.get(Calendar.MONTH) + 1).toString()) }
    var day by remember { mutableStateOf(cal.get(Calendar.DAY_OF_MONTH).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fecha límite") },
        text = {
            Row {
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it.filter(Char::isDigit).take(4) },
                    label = { Text("Año") },
                    singleLine = true,
                    modifier = Modifier.weight(1.4f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = month,
                    onValueChange = { month = it.filter(Char::isDigit).take(2) },
                    label = { Text("Mes") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = day,
                    onValueChange = { day = it.filter(Char::isDigit).take(2) },
                    label = { Text("Día") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val y = year.toIntOrNull() ?: 2026
                val m = (month.toIntOrNull() ?: 1).coerceIn(1, 12)
                val d = (day.toIntOrNull() ?: 1).coerceIn(1, 31)
                onConfirm(toMillis(y, m, d))
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
