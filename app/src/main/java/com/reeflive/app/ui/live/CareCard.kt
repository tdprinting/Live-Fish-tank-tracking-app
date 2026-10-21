package com.reeflive.app.ui.live

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.custom.economy.EconomyView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * My tank's money on screen (docs/ECONOMY.md §7): the sand-dollar chip in the HUD and the Care card it opens, with
 * today's chores, the bonuses, the level bar, the goals and the reef journal. The web page draws the same in site/care.js.
 */

/**
 * Sand dollars and level for the HUD's top row; a dot when a chore or the happy bonus is waiting. The number counts
 * up as money comes in and the chip gulps when a coin burst lands in it ([RewardFxState]).
 */
@Composable
fun MoneyChip(e: EconomyView, onClick: () -> Unit, fx: RewardFxState, modifier: Modifier = Modifier) {
    val reduce = ReefTheme.reduceMotion
    val shown by animateIntAsState(e.coins, if (reduce) tween(0) else tween(900, delayMillis = 700), label = "coins")
    val gulp = remember { Animatable(0f) }
    LaunchedEffect(fx.landed) {
        if (fx.landed != 0L && !reduce) { gulp.snapTo(1f); gulp.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 380f)) }
    }
    LivePill(
        "$shown", onClick,
        modifier.rewardTarget(fx).graphicsLayer { val k = 1f + 0.18f * gulp.value; scaleX = k; scaleY = 2f - k },
        tone = Candy.yellow,
        leading = { Canvas(Modifier.size(22.dp).graphicsLayer { rotationZ = gulp.value * 40f }) { drawSandDollar() } },
        trailing = {
            Text("★${e.level}", style = LiveTheme.style.buttonText.copy(fontSize = 13.sp), color = Candy.navy)
            if (e.attention) Box(Modifier.size(9.dp).clip(CircleShape).background(Candy.red))
        },
    )
}

/** A sand dollar: a pale disc with the five-petal flower in the middle. */
fun DrawScope.drawSandDollar() {
    val r = size.minDimension / 2
    val c = center
    drawCircle(Color(0xFFF3E3C3), r * 0.92f, c)
    drawCircle(Candy.navy, r * 0.92f, c, style = Stroke(r * 0.14f))
    for (i in 0 until 5) {
        val a = (i * 72.0 - 90.0) * PI / 180
        val tip = Offset(c.x + (cos(a) * r * 0.55).toFloat(), c.y + (sin(a) * r * 0.55).toFloat())
        drawLine(Color(0xFFB8925A), c, tip, strokeWidth = r * 0.16f)
    }
    drawCircle(Color(0xFFB8925A), r * 0.12f, c)
}

/** The Care card: what to do today and what it pays, the bonuses, the level and the goals. */
@Composable
fun CareCard(e: EconomyView, onCollect: () -> Unit, onDismiss: () -> Unit) {
    val s = LiveTheme.style
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 440.dp)
                .heightIn(max = 640.dp)
                .livePanel(s)
                .background(s.panel.copy(alpha = 0.92f))
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(40.dp)) { drawSandDollar() }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${e.coins} sand dollars", style = s.titleText, color = s.label)
                    Text("Take care of your tank to earn more", style = MaterialTheme.typography.bodySmall, color = s.labelDim)
                }
            }
            LevelBar(e)

            LiveSectionTitle("Today's jobs")
            e.tasks.forEach { t ->
                LivePanel(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp), tint = if (t.done) Candy.green else null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // a done job's tick pops in with a bounce
                        val pop by animateFloatAsState(if (t.done) 1f else 0f, spring(dampingRatio = 0.35f, stiffness = 300f), label = "tick")
                        Text(
                            if (t.done) "✓" else "○", style = s.titleText, color = if (t.done) Candy.green else s.label,
                            modifier = Modifier.width(28.dp).graphicsLayer { val k = 1f + 0.5f * pop * (1f - pop) * 4f; scaleX = k; scaleY = k },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(t.task.label, style = s.buttonText.copy(fontSize = 15.sp), color = s.label)
                            if (!t.done) Text(t.task.hint, style = MaterialTheme.typography.bodySmall, color = s.labelDim)
                        }
                        Text("+${t.pay}", style = s.buttonText.copy(fontSize = 15.sp), color = Candy.yellow)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (e.allDonePaid) "All done today! +${e.allDoneBonus} paid" else "Finish them all: +${e.allDoneBonus} more",
                    style = MaterialTheme.typography.bodyMedium, color = s.label, modifier = Modifier.weight(1f),
                )
            }
            if (e.streak > 0 || e.multiplier > 1.0) {
                Text(
                    "🔥 ${e.streak} day${if (e.streak == 1) "" else "s"} in a row" +
                        if (e.multiplier > 1.0) " · pay +${((e.multiplier - 1) * 100).toInt()} %" else "",
                    style = MaterialTheme.typography.bodyMedium, color = Candy.orange,
                )
            }

            LiveSectionTitle("Happy tank bonus")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        e.happyPaid -> "Collected today. Come back tomorrow!"
                        e.happyBonus > 0 -> "Your healthy animals earned ${e.happyBonus} today."
                        else -> "Healthy animals and corals earn a bonus every day."
                    },
                    style = MaterialTheme.typography.bodyMedium, color = s.label, modifier = Modifier.weight(1f),
                )
                if (!e.happyPaid && e.happyBonus > 0) LivePill("Collect", onCollect, tone = Candy.green)
            }

            LiveSectionTitle("Goals")
            e.goals.forEach { g ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (g.done) "★" else "☆", color = if (g.done) Candy.yellow else s.labelDim, modifier = Modifier.width(24.dp))
                    Text(g.goal.label, style = MaterialTheme.typography.bodyMedium, color = if (g.done) s.labelDim else s.label, modifier = Modifier.weight(1f))
                    Text("+${g.goal.reward}", style = MaterialTheme.typography.labelMedium, color = if (g.done) s.labelDim else Candy.yellow)
                }
            }
            ReefJournal(e)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { LivePill("Close", onDismiss, tone = Candy.white) }
        }
    }
}

/**
 * The reef journal: the reef moments found so far (tap one to read its fact again; ones still to find show "???" and a
 * hint) and every kind of animal this tank has kept.
 */
@Composable
private fun ReefJournal(e: EconomyView) {
    val s = LiveTheme.style
    val j = e.journal
    var openId by remember { mutableStateOf<String?>(null) }
    LiveSectionTitle("Reef journal · ${j.moments.count { it.found }} of ${j.moments.size} moments")
    j.moments.forEach { m ->
        Column(Modifier.fillMaxWidth().clickable(enabled = m.found) { openId = if (openId == m.moment.id) null else m.moment.id }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (m.found) "✨" else "?", color = if (m.found) Candy.yellow else s.labelDim, modifier = Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (m.found) m.moment.title else "???", style = MaterialTheme.typography.bodyMedium, color = if (m.found) s.label else s.labelDim)
                    if (!m.found) Text(m.moment.hint, style = MaterialTheme.typography.bodySmall, color = s.labelDim)
                }
                Text("+${m.moment.reward}", style = MaterialTheme.typography.labelMedium, color = if (m.found) s.labelDim else Candy.yellow)
            }
            if (m.found && openId == m.moment.id) {
                Text(m.moment.fact, style = MaterialTheme.typography.bodySmall, color = s.label, modifier = Modifier.padding(start = 24.dp, top = 2.dp))
            }
        }
    }
    if (j.kept.isNotEmpty()) {
        Text(
            "Animals you've kept (${j.kept.size} kinds): ${j.kept.joinToString(", ")}",
            style = MaterialTheme.typography.bodySmall, color = s.labelDim,
        )
    }
    Text("Fish birthdays at 1 week, 1 month, 100 days and 1 year pay a little too.", style = MaterialTheme.typography.bodySmall, color = s.labelDim)
}

@Composable
private fun LevelBar(e: EconomyView) {
    val s = LiveTheme.style
    val f = if (e.levelNeed <= 0) 1f else (e.levelStars.toFloat() / e.levelNeed).coerceIn(0f, 1f)
    Column {
        Text(
            if (e.levelNeed > 0) "Level ${e.level} · ${e.levelNeed - e.levelStars} stars to level ${e.level + 1}" else "Level ${e.level} · top level!",
            style = s.buttonText, color = s.label,
        )
        Spacer(Modifier.height(4.dp))
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(Candy.navy.copy(alpha = 0.55f), cornerRadius = r)
            drawRoundRect(Candy.yellow, size = Size(size.width * f.coerceAtLeast(0.03f), size.height), cornerRadius = r)
            drawRoundRect(Candy.navy, cornerRadius = r, style = Stroke(2f))
        }
    }
}
