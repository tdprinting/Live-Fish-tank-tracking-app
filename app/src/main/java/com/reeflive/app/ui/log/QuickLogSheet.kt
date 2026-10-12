package com.reeflive.app.ui.log

import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.common.TimeText
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.Dot
import com.reeflive.app.ui.components.ParamGlyph
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.components.ReefTextField
import com.reeflive.app.ui.components.statusLabel
import com.reeflive.app.ui.theme.ReefMotion
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.app.ui.theme.status
import com.reeflive.core.status.Status

data class LogChip(val id: String, val label: String, val added: Boolean)

data class LogRowUi(
    val paramId: String,
    val name: String,
    val text: String,
    val unit: String,
    val canCycleUnit: Boolean,
    val last: String?,
    val status: Status,
    val error: Boolean = false,
)

data class QuickLogUi(
    val chips: List<LogChip> = emptyList(),
    val rows: List<LogRowUi> = emptyList(),
    val takenAt: Long = 0L,
    val isNow: Boolean = true,
    val kit: String? = null,
    val kits: List<String> = emptyList(),
    val note: String = "",
    val showNote: Boolean = false,
    val saving: Boolean = false,
) {
    val canSave: Boolean get() = !saving && rows.any { it.text.isNotBlank() && !it.error }
    val filled: Int get() = rows.count { it.text.isNotBlank() && !it.error }
}

data class QuickLogActions(
    val onChip: (String) -> Unit = {},
    val onWeekly: () -> Unit = {},
    val onRemove: (String) -> Unit = {},
    val onText: (String, String) -> Unit = { _, _ -> },
    val onCycleUnit: (String) -> Unit = {},
    val onPickTime: () -> Unit = {},
    val onKit: (String?) -> Unit = {},
    val onNote: (String) -> Unit = {},
    val onToggleNote: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/**
 * Quick Log sheet body (DESIGN §5.3): date/time + kit pills, parameter chips (favorites first, "Weekly set"),
 * one row per chosen parameter with ghosted last value, unit toggle and a live status dot, then Save.
 * Next moves between rows; Done on the last row saves.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickLogContent(
    state: QuickLogUi,
    actions: QuickLogActions,
    modifier: Modifier = Modifier,
    focusParam: String? = null,
    title: String = "Log test",
    inSheet: Boolean = true,
    headerActions: @Composable RowScope.() -> Unit = {},
) {
    val c = ReefTheme.colors
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun fr(id: String) = requesters.getOrPut(id) { FocusRequester() }
    LaunchedEffect(focusParam, state.rows.size) {
        val id = focusParam ?: return@LaunchedEffect
        if (state.rows.any { it.paramId == id }) runCatching { fr(id).requestFocus() }
    }
    val outer = if (inSheet) modifier.fillMaxWidth().fillMaxHeight(0.92f) else modifier.fillMaxSize().statusBarsPadding()
    Column(outer.imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (inSheet) 0.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!inSheet) {
                IconButton(onClick = actions.onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.text) }
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.weight(1f))
            headerActions()
            if (inSheet) IconButton(onClick = actions.onClose) { Icon(Icons.Rounded.Close, "Close", tint = c.textSecondary) }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(
                icon = { Icon(Icons.Rounded.CalendarMonth, null, tint = c.primaryBright, modifier = Modifier.size(16.dp)) },
                text = if (state.isNow) "Now" else "${TimeText.dayLabel(state.takenAt)} · ${TimeText.clock(state.takenAt)}",
                onClick = actions.onPickTime,
            )
            KitPill(state.kit, state.kits, actions.onKit)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReefChip("Weekly set", selected = false, onClick = actions.onWeekly, accent = c.coral, leading = {
                    Icon(Icons.Rounded.Add, null, tint = c.coral, modifier = Modifier.size(16.dp))
                })
                state.chips.forEach { chip ->
                    ReefChip(chip.label, selected = chip.added, onClick = { actions.onChip(chip.id) }, leading = {
                        ParamGlyph(chip.id, size = 16.dp, tint = if (chip.added) c.primaryBright else c.textSecondary)
                    })
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.rows.forEachIndexed { i, row ->
                    val last = i == state.rows.size - 1
                    LogRow(
                        row = row,
                        focusRequester = fr(row.paramId),
                        imeAction = if (last) ImeAction.Done else ImeAction.Next,
                        onIme = {
                            if (last) { if (state.canSave) actions.onSave() } else runCatching { fr(state.rows[i + 1].paramId).requestFocus() }
                        },
                        actions = actions,
                    )
                }
            }
            if (state.rows.isEmpty()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Science, null, tint = c.textTertiary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Tap a parameter to add it", style = MaterialTheme.typography.bodyMedium, color = c.textTertiary)
                }
            }
            if (state.showNote) {
                Spacer(Modifier.height(12.dp))
                ReefTextField(state.note, actions.onNote, Modifier.fillMaxWidth(), label = "Note", placeholder = "Fresh reagent, after water change…", singleLine = false, minLines = 2)
            }
            Spacer(Modifier.height(16.dp))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReefButton(if (state.showNote) "Hide note" else "Add note", actions.onToggleNote, kind = ButtonKind.OUTLINED, icon = Icons.Rounded.EditNote, compact = true)
            }
            Spacer(Modifier.height(10.dp))
            ReefButton(
                text = when {
                    state.saving -> "Saving…"
                    state.filled > 1 -> "Save ${state.filled} values"
                    else -> "Save"
                },
                onClick = actions.onSave,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LogRow(row: LogRowUi, focusRequester: FocusRequester, imeAction: ImeAction, onIme: () -> Unit, actions: QuickLogActions) {
    val c = ReefTheme.colors
    val dot by animateColorAsState(
        if (row.text.isBlank() || row.error) c.unknown else c.status(row.status),
        ReefMotion.tween(ReefTheme.reduceMotion, ReefMotion.FAST), label = "dot",
    )
    Column(
        Modifier.fillMaxWidth().clip(ReefShapes.card).background(c.glass).border(1.dp, c.glassBorder, ReefShapes.card).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ParamGlyph(row.paramId, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(row.name, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
            if (row.last != null) Text("last ${row.last}", style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
            IconButton(onClick = { actions.onRemove(row.paramId) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Close, "Remove ${row.name}", tint = c.textTertiary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ReefTextField(
                value = row.text,
                onValueChange = { actions.onText(row.paramId, it) },
                modifier = Modifier.weight(1f),
                placeholder = row.last ?: "0",
                numeric = true,
                imeAction = imeAction,
                onIme = onIme,
                focusRequester = focusRequester,
                error = row.error,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .widthIn(min = 64.dp)
                    .clip(ReefShapes.field)
                    .background(c.field)
                    .border(1.dp, c.glassBorder, ReefShapes.field)
                    .clickable(enabled = row.canCycleUnit, role = Role.Button) { actions.onCycleUnit(row.paramId) }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.unit.ifEmpty { "—" }, style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                    if (row.canCycleUnit) {
                        Spacer(Modifier.width(2.dp))
                        Icon(Icons.Rounded.SwapVert, "Change unit", tint = c.textTertiary, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Dot(dot, size = 12.dp)
        }
        if (row.text.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                if (row.error) "Not a number" else statusLabel(row.status),
                style = MaterialTheme.typography.labelSmall,
                color = if (row.error) c.dangerText else c.textTertiary,
            )
        }
    }
}

@Composable
private fun Pill(icon: @Composable () -> Unit, text: String, onClick: () -> Unit) {
    val c = ReefTheme.colors
    Row(
        Modifier.heightIn(min = 36.dp).clip(ReefShapes.pill).background(c.glass).border(1.dp, c.glassBorder, ReefShapes.pill)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = c.text, maxLines = 1)
    }
}

@Composable
private fun KitPill(kit: String?, kits: List<String>, onKit: (String?) -> Unit) {
    val c = ReefTheme.colors
    var open by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    Box {
        Pill(
            icon = { Icon(Icons.Rounded.Science, null, tint = c.primaryBright, modifier = Modifier.size(16.dp)) },
            text = kit ?: "Kit",
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false; custom = false }, containerColor = c.sheet) {
            (kits + DEFAULT_KITS).distinct().take(10).forEach { k ->
                DropdownMenuItem(text = { Text(k, color = c.text) }, onClick = { onKit(k); open = false })
            }
            if (kit != null) DropdownMenuItem(text = { Text("No kit", color = c.textSecondary) }, onClick = { onKit(null); open = false })
            if (!custom) {
                DropdownMenuItem(text = { Text("Other…", color = c.primaryBright) }, onClick = { custom = true })
            } else {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).width(240.dp), verticalAlignment = Alignment.CenterVertically) {
                    ReefTextField(text, { text = it }, Modifier.weight(1f), placeholder = "Brand", imeAction = ImeAction.Done, onIme = {
                        if (text.isNotBlank()) onKit(text.trim())
                        open = false; custom = false
                    })
                }
            }
        }
    }
}

/** Common hobby kits offered before the user has a history. */
val DEFAULT_KITS = listOf("Hanna", "Salifert", "Red Sea", "API", "Nyos", "Refractometer")
