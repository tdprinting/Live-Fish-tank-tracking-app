package com.reeflive.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.GlassIconButton
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.StatusPill
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefLiveTheme
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.status.Status
import kotlin.math.abs
import kotlin.math.roundToInt

/** Selected-fish card: names, live health/stress/hunger bars, behaviour line, relationships, Follow. */
@Composable
fun FishCard(
    state: FishCardState,
    onFollow: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ReefTheme.colors
    GlassCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(state.name, style = MaterialTheme.typography.headlineSmall, color = c.text, maxLines = 1)
                Text(state.speciesName, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1)
            }
            StatusPill(moodStatus(state), label = state.mood)
            Spacer(Modifier.width(4.dp))
            GlassIconButton(Icons.Rounded.Close, "Close fish card", onClose)
        }
        Spacer(Modifier.height(10.dp))
        Text(state.behavior, style = MaterialTheme.typography.bodyMedium, color = c.text)
        if (state.symptoms.isNotEmpty()) {
            Text(state.symptoms.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = c.cautionText)
        }
        Spacer(Modifier.height(12.dp))
        StatBar("Health", state.health, higherIsBetter = true)
        Spacer(Modifier.height(8.dp))
        StatBar("Stress", state.stress, higherIsBetter = false)
        Spacer(Modifier.height(8.dp))
        StatBar("Hunger", state.hunger, higherIsBetter = false)

        if (state.relationships.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Relationships", style = MaterialTheme.typography.titleMedium, color = c.text)
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.relationships.forEach { RelationshipRow(it) }
            }
        }
        Spacer(Modifier.height(14.dp))
        ReefButton("Follow", onFollow, Modifier.fillMaxWidth(), kind = ButtonKind.FILLED, icon = Icons.Rounded.CenterFocusStrong, enabled = state.alive)
    }
}

private fun moodStatus(s: FishCardState): Status = when {
    !s.alive || s.health < 0.35f -> Status.DANGER
    s.stress > 0.5f || s.health < 0.6f -> Status.CAUTION
    else -> Status.GOOD
}

/** Labelled 0..1 bar with a percent readout; fill colour reflects whether the level is good or bad. */
@Composable
fun StatBar(label: String, value: Float, higherIsBetter: Boolean, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    val v = value.coerceIn(0f, 1f)
    val goodness = if (higherIsBetter) v else 1f - v
    val fill = when {
        goodness >= 0.6f -> c.good
        goodness >= 0.35f -> c.caution
        else -> c.danger
    }
    val pct = (v * 100).roundToInt()
    Column(modifier.fillMaxWidth().semantics { contentDescription = "$label $pct percent" }) {
        Row {
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
            Text("$pct%", style = MaterialTheme.typography.labelMedium, color = c.text)
        }
        Spacer(Modifier.height(3.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(ReefShapes.pill).background(c.glassHi)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(v).clip(ReefShapes.pill).background(fill))
        }
    }
}

@Composable
private fun RelationshipRow(r: RelationshipUi) {
    val c = ReefTheme.colors
    val tone: Color = when {
        r.affinity >= 0.3f -> c.good
        r.affinity <= -0.3f -> c.danger
        else -> c.unknown
    }
    val tonePill = when {
        r.affinity >= 0.3f -> Status.GOOD
        r.affinity <= -0.3f -> Status.DANGER
        else -> Status.UNKNOWN
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(r.otherName, style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 1)
            Text(
                if (r.note.isBlank()) r.otherSpecies else "${r.otherSpecies} · ${r.note}",
                style = MaterialTheme.typography.labelSmall, color = c.textSecondary, maxLines = 1,
            )
        }
        // mini affinity meter: centre = neutral, right = fond, left = hostile
        Box(Modifier.width(56.dp).height(6.dp).clip(ReefShapes.pill).background(c.glassHi), contentAlignment = Alignment.CenterStart) {
            val half = abs(r.affinity).coerceIn(0f, 1f) * 0.5f
            val start = if (r.affinity >= 0f) 0.5f else 0.5f - half
            Box(Modifier.padding(start = (56f * start).dp).width((56f * half).dp).fillMaxHeight().background(tone))
        }
        Spacer(Modifier.width(8.dp))
        StatusPill(tonePill, label = r.kind.label)
    }
}

// ---------------------------------------------------------------- previews

private val previewFish = FishCardState(
    id = "f1", speciesName = "Ocellaris clownfish", name = "Pixel",
    health = 0.82f, stress = 0.28f, hunger = 0.64f, mood = "Hungry",
    behavior = "Hovering in the anemone, watching the surface for food",
    relationships = listOf(
        RelationshipUi("Coral", "Ocellaris clownfish", RelationKind.MATE, 0.9f, "pair bonded"),
        RelationshipUi("Kiwi", "Yellow tang", RelationKind.NEUTRAL, 0.0f),
        RelationshipUi("Falco", "Hawkfish", RelationKind.RIVAL, -0.7f, "chased twice today"),
    ),
)

@Preview(name = "FishCard · Abyss", widthDp = 393, heightDp = 640, showBackground = true)
@Composable
private fun FishCardDarkPreview() = ReefLiveTheme(darkTheme = true, reduceMotion = true) {
    Box(Modifier.background(ReefTheme.colors.bg).padding(16.dp)) { FishCard(previewFish, {}, {}) }
}

@Preview(name = "FishCard · Lagoon", widthDp = 393, heightDp = 640, showBackground = true)
@Composable
private fun FishCardLightPreview() = ReefLiveTheme(darkTheme = false, reduceMotion = true) {
    Box(Modifier.background(ReefTheme.colors.bg).padding(16.dp)) { FishCard(previewFish.copy(stress = 0.7f, health = 0.4f), {}, {}) }
}
