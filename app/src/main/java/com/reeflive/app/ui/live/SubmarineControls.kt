package com.reeflive.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reeflive.core.sim.SubControls
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Submarine mode for the live screen (LiveTankViewModel.submarine): out, seen by the fish, inside view, headlight. */
data class SubUi(val on: Boolean = false, val visible: Boolean = true, val inside: Boolean = false, val light: Boolean = true)

/** What the sub's options say and how the fish are taking it (shared wording with web/site/live.js). */
object SubmarineWords {
    fun info(ui: SubUi, curious: Int?, wary: Int?): String = when {
        !ui.visible -> "The fish cannot see you"
        curious != null && wary != null -> "$curious curious · $wary shy"
        else -> "Submarine"
    }
    fun ghost(ui: SubUi) = if (ui.visible) "Invisible" else "Invisible: on"
    fun view(ui: SubUi) = if (ui.inside) "Outside view" else "Inside view"
    fun light(ui: SubUi) = if (ui.light) "Light on" else "Light off"
}

/**
 * The submarine's controls over the tank (UI_STYLE big friendly targets): a yellow steering stick bottom left
 * (up = forward, sideways = turn), up / down buttons bottom right, and a bar on top with Invisible, Inside view, Light
 * and Done. Every control sends at once; the stick and buttons send [onSteer] only when the input changes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoxScope.SubmarineControls(
    ui: SubUi,
    curious: Int?,
    wary: Int?,
    onSteer: (SubControls) -> Unit,
    onChange: (SubUi) -> Unit,
    onDone: () -> Unit,
) {
    val steer by rememberUpdatedState(onSteer)
    var joyX by remember { mutableFloatStateOf(0f) }
    var joyY by remember { mutableFloatStateOf(0f) }
    var rise by remember { mutableFloatStateOf(0f) }
    var sent by remember { mutableStateOf(SubControls.IDLE) }
    fun send() {
        val c = SubControls(thrust = (-joyY).toDouble(), turn = joyX.toDouble(), rise = rise.toDouble())
        if (c != sent) { sent = c; steer(c) }
    }

    // ---- top bar
    FlowRow(
        Modifier
            .align(Alignment.TopCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(top = 12.dp, start = 12.dp, end = 12.dp)
            .background(Color(0xCC061327), RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            SubmarineWords.info(ui, curious, wary), color = Candy.white, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
        LivePill(SubmarineWords.ghost(ui), { onChange(ui.copy(visible = !ui.visible)) }, tone = Candy.purple, selected = !ui.visible)
        LivePill(SubmarineWords.view(ui), { onChange(ui.copy(inside = !ui.inside)) }, tone = Candy.cyan, selected = ui.inside)
        LivePill(SubmarineWords.light(ui), { onChange(ui.copy(light = !ui.light)) }, tone = Candy.yellow, selected = ui.light)
        LivePill("Done", onDone, tone = Candy.green)
    }

    // ---- steering stick
    val radius = 66.dp
    Box(
        Modifier
            .align(Alignment.BottomStart)
            .navigationBarsPadding()
            .padding(start = 20.dp, bottom = 28.dp)
            .size(radius * 2)
            .background(Color(0x99061327), CircleShape)
            .border(2.dp, Candy.white.copy(alpha = 0.35f), CircleShape)
            .semantics { contentDescription = "Steer the submarine" }
            .pointerInput(Unit) {
                val r = size.width / 2f
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun at(x: Float, y: Float) {
                        var dx = (x - r) / (r - 14f); var dy = (y - r) / (r - 14f)
                        val l = sqrt(dx * dx + dy * dy)
                        if (l > 1f) { dx /= l; dy /= l }
                        joyX = if (abs(dx) < 0.12f) 0f else dx
                        joyY = if (abs(dy) < 0.12f) 0f else dy
                        send()
                    }
                    at(down.position.x, down.position.y)
                    down.consume()
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        at(ch.position.x, ch.position.y)
                        ch.consume()
                    }
                    joyX = 0f; joyY = 0f; send()
                }
            },
    ) {
        val knob = 26.dp
        Box(
            Modifier
                .align(Alignment.Center)
                .offset { IntOffset((joyX * 40.dp.toPx()).roundToInt(), (joyY * 40.dp.toPx()).roundToInt()) }
                .size(knob * 2)
                .background(Candy.yellow, CircleShape),
        )
    }

    // ---- up / down
    Column(
        Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        for ((label, v) in listOf("▲" to 1f, "▼" to -1f)) {
            Box(
                Modifier
                    .size(60.dp)
                    .background(if (rise == v) Candy.cyan.copy(alpha = 0.55f) else Color(0x99061327), CircleShape)
                    .border(2.dp, Candy.white.copy(alpha = 0.35f), CircleShape)
                    .semantics { contentDescription = if (v > 0) "Go up" else "Go down" }
                    .pointerInput(v) {
                        awaitEachGesture {
                            awaitFirstDown().consume()
                            rise = v; send()
                            waitForUpOrCancellation()
                            rise = 0f; send()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Text(label, color = Candy.white, fontSize = 22.sp) }
        }
    }
}
