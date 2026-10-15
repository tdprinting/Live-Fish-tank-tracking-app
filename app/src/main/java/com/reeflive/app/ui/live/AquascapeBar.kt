package com.reeflive.app.ui.live

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.sim.DryRockShape

/** What a tap on the tank does while the aquascape builder is open. */
enum class ScapeTool { DROP, TAKE_OUT }

/**
 * The aquascape builder's bar (Tools → Build rockwork): pick a white dry rock, then tap the tank where it should go and
 * it drops in and settles on the sand or the rock under it. Turn spins the next rock, Take out removes the rock you
 * tap, Undo removes the last rock added, Empty tank clears every rock to build from scratch.
 */
@Composable
fun AquascapeBar(
    shape: DryRockShape,
    tool: ScapeTool,
    turnDeg: Int,
    onShape: (DryRockShape) -> Unit,
    onTool: (ScapeTool) -> Unit,
    onTurn: () -> Unit,
    onUndo: () -> Unit,
    onEmpty: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ReefTheme.colors
    var confirm by remember { mutableStateOf(false) }
    LivePanel(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), tint = Candy.white) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (tool == ScapeTool.DROP) "Tap the tank to drop a ${shape.label.lowercase()}" else "Tap a rock to take it out",
                style = LiveTheme.style.titleText, color = LiveTheme.style.label,
            )
            Text(
                "New dry rock is white. Over a few months in the tank it turns brown, then purple and pink like live rock.",
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (s in DryRockShape.entries) LivePill(s.label, { onShape(s); onTool(ScapeTool.DROP) }, tone = Candy.white, selected = tool == ScapeTool.DROP && s == shape)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LivePill("Turn ${turnDeg}°", onTurn, tone = Candy.cyan)
                LivePill("Take out", { onTool(if (tool == ScapeTool.TAKE_OUT) ScapeTool.DROP else ScapeTool.TAKE_OUT) }, tone = Candy.orange, selected = tool == ScapeTool.TAKE_OUT)
                LivePill("Undo", onUndo, tone = Candy.yellow)
                LivePill("Empty tank", { confirm = true }, tone = Candy.red)
                LivePill("Done", onDone, tone = Candy.green)
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Take out all the rock?") },
        text = { Text("Every rock comes out so you can build a new rockwork from scratch. Corals on them drop to the sand.") },
        confirmButton = { TextButton({ confirm = false; onEmpty() }) { Text("Empty tank") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Keep rocks") } },
    )
}
