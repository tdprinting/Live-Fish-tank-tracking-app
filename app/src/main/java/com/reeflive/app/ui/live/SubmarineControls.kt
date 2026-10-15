package com.reeflive.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reeflive.core.sim.SubControls
import com.reeflive.core.sim.SubWatch
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
    fun view(ui: SubUi) = if (ui.inside) "Inside: on" else "Inside"
    fun light(ui: SubUi) = if (ui.light) "Light" else "Light off"
    fun mode(m: SubWatch) = when (m) { SubWatch.HOVER -> "Hover"; SubWatch.CIRCLE -> "Circle"; SubWatch.ALONGSIDE -> "Swim along" }
    const val IDLE = "Tap a fish, or › for the nearest"
    fun stick(watching: Boolean) = if (watching) "Swing round · closer" else "Drift & steer"
    const val EXIT = "✕ Leave sub"
}

private val SUB_MODES = SubWatch.entries.toTypedArray()

/**
 * The submarine's controls, built for fish watching (UI_STYLE big friendly targets): a big "Leave sub" exit and the
 * reactions top left; small option pills (Invisible, Inside, Light) under them; when a fish is being watched
 * ([watchName] non-null, the sub glides over and keeps it company in the sim) the card names it; ‹ › always hop to the
 * next fish, Hover / Circle / Swim along and Let go. A yellow one-thumb stick bottom left (drift and steer, or swing
 * round the fish and move closer while watching) and labelled Up / Down buttons bottom right. Every control sends at
 * once; the stick and buttons send [onSteer] only when the input changes.
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
    watchName: String? = null,
    watchMode: SubWatch = SubWatch.HOVER,
    onWatchMode: (SubWatch) -> Unit = {},
    onLetGo: () -> Unit = {},
    onNextFish: (Int) -> Unit = {},
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
    val watching = watchName != null

    // ---- top: exit, reactions, options, and the watch card
    Column(
        Modifier
            .align(Alignment.TopStart)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(top = 12.dp, start = 12.dp, end = 12.dp)
            .widthIn(max = 520.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .background(Candy.red, RoundedCornerShape(24.dp))
                    .border(2.dp, Candy.white.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
                    .clickable(onClickLabel = "Leave the submarine", onClick = onDone)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) { Text(SubmarineWords.EXIT, color = Candy.white, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            Text(
                SubmarineWords.info(ui, curious, wary),
                color = Candy.white, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.background(Color(0xCC061327), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LivePill(SubmarineWords.ghost(ui), { onChange(ui.copy(visible = !ui.visible)) }, tone = Candy.purple, selected = !ui.visible)
            LivePill(SubmarineWords.view(ui), { onChange(ui.copy(inside = !ui.inside)) }, tone = Candy.cyan, selected = ui.inside)
            LivePill(SubmarineWords.light(ui), { onChange(ui.copy(light = !ui.light)) }, tone = Candy.yellow, selected = ui.light)
        }
        // always there: idle it offers ‹ › to pick a fish (or tap one); watching it names the fish and its modes
        run {
            Column(
                Modifier
                    .background(Color(0xDD061327), RoundedCornerShape(18.dp))
                    .border(2.dp, Candy.yellow.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LivePill("‹", { onNextFish(-1) }, tone = Candy.white, selected = false)
                    Text(
                        if (watchName != null) "Watching $watchName" else SubmarineWords.IDLE, color = Candy.white, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    LivePill("›", { onNextFish(1) }, tone = Candy.white, selected = false)
                }
                if (watching) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (m in SUB_MODES) LivePill(SubmarineWords.mode(m), { onWatchMode(m) }, tone = Candy.green, selected = m == watchMode)
                    LivePill("Let go", onLetGo, tone = Candy.orange, selected = false)
                }
            }
        }
    }

    // ---- one-thumb stick: drift and steer (watching: swing round the fish, up = closer)
    val radius = 70.dp
    Column(
        Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 20.dp, bottom = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(radius * 2)
                .background(Color(0x99061327), CircleShape)
                .border(2.dp, Candy.white.copy(alpha = 0.35f), CircleShape)
                .semantics { contentDescription = if (watching) "Swing round the fish, up to go closer" else "Drift and steer the submarine" }
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
            val knob = 28.dp
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset((joyX * 42.dp.toPx()).roundToInt(), (joyY * 42.dp.toPx()).roundToInt()) }
                    .size(knob * 2)
                    .background(Candy.yellow, CircleShape),
            )
        }
        Text(SubmarineWords.stick(watching), color = Candy.white, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }

    // ---- up / down
    Column(
        Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (v in UP_DOWN) {
            Column(
                Modifier
                    .size(width = 76.dp, height = 70.dp)
                    .background(if (rise == v) Candy.cyan.copy(alpha = 0.55f) else Color(0x99061327), RoundedCornerShape(22.dp))
                    .border(2.dp, Candy.white.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                    .semantics { contentDescription = if (v > 0) "Go up" else "Go down" }
                    .pointerInput(v) {
                        awaitEachGesture {
                            awaitFirstDown().consume()
                            rise = v; send()
                            waitForUpOrCancellation()
                            rise = 0f; send()
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(if (v > 0) "▲" else "▼", color = Candy.white, fontSize = 22.sp)
                Text(if (v > 0) "Up" else "Down", color = Candy.white, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private val UP_DOWN = floatArrayOf(1f, -1f)
