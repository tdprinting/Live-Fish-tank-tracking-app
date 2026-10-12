package com.reeflive.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Pick a date then a time. Quick choices ("Now", "This morning", "Yesterday evening") cover the usual case of
 * logging a test done a little earlier. Returns epoch millis in the device zone.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DateTimeDialog(initial: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit, allowFuture: Boolean = false) {
    val c = ReefTheme.colors
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(initial).atZone(zone)
    var step by remember { mutableStateOf(0) }
    var date by remember { mutableStateOf(start.toLocalDate()) }
    val today = LocalDate.now(zone)
    val colors = DatePickerDefaults.colors(
        containerColor = c.sheet,
        titleContentColor = c.textSecondary,
        headlineContentColor = c.text,
        weekdayContentColor = c.textSecondary,
        dayContentColor = c.text,
        selectedDayContainerColor = c.primary,
        selectedDayContentColor = c.bgDeep,
        todayContentColor = c.primaryBright,
        todayDateBorderColor = c.primaryBright,
    )
    if (step == 0) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            colors = colors,
            shape = ReefShapes.dialog,
            confirmButton = {
                ReefButton("Next", {
                    state.selectedDateMillis?.let { ms ->
                        val picked = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        date = if (!allowFuture && picked.isAfter(today)) today else picked
                    }
                    step = 1
                }, compact = true)
            },
            dismissButton = { ReefButton("Cancel", onDismiss, kind = ButtonKind.OUTLINED, compact = true) },
        ) {
            Column {
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReefChip("Now", false, { onPick(System.currentTimeMillis()) })
                    ReefChip("This morning", false, { onPick(today.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()) })
                    ReefChip("Yesterday eve", false, { onPick(today.minusDays(1).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()) })
                }
                DatePicker(state = state, colors = colors, showModeToggle = false)
            }
        }
    } else {
        val tState = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = c.sheet,
            shape = ReefShapes.dialog,
            title = { Text("Time", style = MaterialTheme.typography.headlineSmall, color = c.text) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    TimePicker(
                        state = tState,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = c.glass,
                            selectorColor = c.primary,
                            containerColor = c.sheet,
                            clockDialSelectedContentColor = c.bgDeep,
                            clockDialUnselectedContentColor = c.text,
                            timeSelectorSelectedContainerColor = c.primary.copy(alpha = 0.3f),
                            timeSelectorUnselectedContainerColor = c.glass,
                            timeSelectorSelectedContentColor = c.text,
                            timeSelectorUnselectedContentColor = c.textSecondary,
                        ),
                    )
                }
            },
            confirmButton = {
                ReefButton("Set", {
                    var ms = date.atTime(LocalTime.of(tState.hour, tState.minute)).atZone(zone).toInstant().toEpochMilli()
                    if (!allowFuture) ms = minOf(ms, System.currentTimeMillis())
                    onPick(ms)
                }, compact = true)
            },
            dismissButton = { ReefButton("Back", { step = 0 }, kind = ButtonKind.OUTLINED, compact = true) },
        )
    }
}
