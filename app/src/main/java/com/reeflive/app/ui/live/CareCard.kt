package com.reeflive.app.ui.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.reeflive.core.custom.economy.EconomyView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * My tank's money on screen (docs/ECONOMY.md §7): the sand-dollar chip in the HUD and the Care card it opens, with
 * today's chores, the bonuses, the level bar and the goals. The web page draws the same in site/care.js.
 */

/** Sand dollars and level for the HUD's top row; a dot when a chore or the happy bonus is waiting. */
@Composable
fun MoneyChip(e: EconomyView, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LivePill(
        "${e.coins}", onClick, modifier, tone = Candy.yellow,
        leading = { Canvas(Modifier.size(22.dp)) { drawSandDollar() } },
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
                        Text(if (t.done) "✓" else "○", style = s.titleText, color = if (t.done) Candy.green else s.label, modifier = Modifier.width(28.dp))
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
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { LivePill("Close", onDismiss, tone = Candy.white) }
        }
    }
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
