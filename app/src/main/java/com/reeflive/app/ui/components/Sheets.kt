package com.reeflive.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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

/** Glass bottom sheet (UI_STYLE §6.7): 92 % opaque sheet color, 28 dp top radius, 32×4 dp kelp handle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReefSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    state: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = ReefTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = state,
        shape = ReefShapes.sheet,
        containerColor = c.sheet,
        contentColor = c.text,
        scrimColor = c.bgDeep.copy(alpha = 0.55f),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 32.dp, height = 4.dp).background(c.textTertiary, ReefShapes.pill))
        },
        content = content,
    )
}

/** Glass confirm dialog (UI_STYLE §6.7). */
@Composable
fun ReefDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismiss: String = "Cancel",
) {
    val c = ReefTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.sheet,
        shape = ReefShapes.dialog,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, color = c.text) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary) },
        confirmButton = {
            ReefButton(confirm, onConfirm, kind = if (destructive) ButtonKind.DESTRUCTIVE else ButtonKind.FILLED, compact = true)
        },
        dismissButton = { ReefButton(dismiss, onDismiss, kind = ButtonKind.OUTLINED, compact = true) },
    )
}

/** An item in [ReorderSheet]. */
data class ReorderItem(val id: String, val label: String, val visible: Boolean = true)

/**
 * Reorder (and optionally show/hide) a list with up/down buttons — accessible and reliable on every
 * device, used for dashboard tiles and the parameter list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderSheet(
    title: String,
    items: List<ReorderItem>,
    onDone: (List<ReorderItem>) -> Unit,
    onDismiss: () -> Unit,
    allowHide: Boolean = false,
) {
    val c = ReefTheme.colors
    var list by remember(items) { mutableStateOf(items) }
    ReefSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.weight(1f))
                ReefButton("Done", { onDone(list) }, compact = true)
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth().height(440.dp)) {
                itemsIndexed(list, key = { _, it -> it.id }) { i, item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        ParamGlyph(item.id, size = 18.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            item.label, style = MaterialTheme.typography.bodyLarge,
                            color = if (item.visible) c.text else c.textTertiary, modifier = Modifier.weight(1f),
                        )
                        if (allowHide) {
                            IconButton(onClick = { list = list.map { if (it.id == item.id) it.copy(visible = !it.visible) else it } }) {
                                Icon(
                                    if (item.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                    contentDescription = if (item.visible) "Hide ${item.label}" else "Show ${item.label}",
                                    tint = if (item.visible) c.textSecondary else c.textTertiary,
                                )
                            }
                        }
                        IconButton(onClick = { if (i > 0) list = list.toMutableList().apply { add(i - 1, removeAt(i)) } }, enabled = i > 0) {
                            Icon(Icons.Rounded.KeyboardArrowUp, "Move ${item.label} up", tint = if (i > 0) c.text else c.textTertiary)
                        }
                        IconButton(onClick = { if (i < list.size - 1) list = list.toMutableList().apply { add(i + 1, removeAt(i)) } }, enabled = i < list.size - 1) {
                            Icon(Icons.Rounded.KeyboardArrowDown, "Move ${item.label} down", tint = if (i < list.size - 1) c.text else c.textTertiary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
