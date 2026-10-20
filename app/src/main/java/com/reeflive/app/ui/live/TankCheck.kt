package com.reeflive.app.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reeflive.app.ui.components.GlyphBadge
import com.reeflive.app.ui.components.ReefGlyph
import com.reeflive.app.ui.components.ReefGlyphs
import com.reeflive.core.sim.CareFix
import com.reeflive.core.sim.CoralCare
import com.reeflive.core.sim.CoralHealth
import com.reeflive.core.sim.HungerRow
import com.reeflive.core.sim.TankDoctor

/**
 * The Tank Health card: the water at a glance, who is hungry (a bar per animal and one button that feeds every hungry
 * one its favourite food, core HungryFeed), then the tank check's fixes ([TankCheckBody]).
 */
@Composable
internal fun TankHealthBody(
    readouts: List<WaterReadout>,
    hunger: List<HungerRow>,
    onFeedHungry: () -> Unit,
    fixes: List<CareFix>,
    busy: Int,
    tone: Color,
    onFix: (CareFix) -> Unit,
    onFixAll: () -> Unit,
    close: () -> Unit,
    corals: List<CoralHealth.Row> = emptyList(),
    care: CoralCare.Plan? = null,
    onReefCare: (() -> Unit)? = null,
) {
    if (readouts.isNotEmpty()) WaterStrip(readouts)
    LiveSectionTitle("Hungry tummies")
    val hungry = hunger.count { it.hungry }
    if (hungry > 0) {
        LiveTile(ReefGlyphs.feedTin, if (hungry == 1) "Feed the hungry one" else "Feed all $hungry hungry ones", Candy.orange,
            onClick = { onFeedHungry(); close() }, modifier = Modifier.fillMaxWidth(), sub = "Each gets its favourite food, dropped right above it")
    } else if (hunger.isNotEmpty()) {
        LiveHelp("Everyone is full. Check back in a few hours.")
    } else {
        LiveHelp("No animals yet. Add some from Add life.")
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (r in hunger) {
            StatBar(r.name + r.food?.let { " · likes ${it.label.lowercase()}" }.orEmpty(), r.hunger, higherIsBetter = false)
        }
    }
    CoralSection(corals, care, onReefCare, close)
    LiveSectionTitle("Water & gear")
    TankCheckBody(fixes, busy, tone, onFix, onFixAll, close)
}

/**
 * Corals & inverts: a health bar per coral with its main trouble (core CoralHealth), and in My tank one button that
 * gives corals and inverts what they need now (core CoralCare: doses, iodine, coral food, phyto, anemone food).
 */
@Composable
private fun CoralSection(corals: List<CoralHealth.Row>, care: CoralCare.Plan?, onReefCare: (() -> Unit)?, close: () -> Unit) {
    LiveSectionTitle("Corals & inverts")
    if (onReefCare != null && care != null) {
        if (!care.isEmpty) {
            LiveTile(ReefGlyphs.branch, "Give corals and inverts what they need", Candy.green,
                onClick = { onReefCare(); close() }, modifier = Modifier.fillMaxWidth(), sub = "Adds " + care.words + " (${care.price} sand dollars)")
        } else LiveHelp("Corals and inverts have everything they need right now.")
    }
    if (corals.isEmpty()) { LiveHelp("No corals yet. Add some from Add life."); return }
    val pale = corals.count { it.pale }
    if (pale > 0) LiveHelp(if (pale == 1) "One coral is turning pale (bleaching). Fix what bothers it and it can get its colour back." else "$pale corals are turning pale (bleaching). Fix what bothers them and they can get their colour back.")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (r in corals) StatBar(r.name + r.need?.let { " · ${it.words}" }.orEmpty(), r.health.toFloat(), higherIsBetter = true)
    }
}

/**
 * The "Fix tank" card: what the tank check (core TankDoctor) says the tank needs, most urgent first, each with its own
 * Fix button, and "Fix everything" on top. Each fix then plays in the 3D tank (render CareScene). An empty list says
 * the tank looks great, so the bubble always visibly does something.
 */
@Composable
internal fun TankCheckBody(
    fixes: List<CareFix>,
    busy: Int,
    tone: Color,
    onFix: (CareFix) -> Unit,
    onFixAll: () -> Unit,
    close: () -> Unit,
) {
    val s = LiveTheme.style
    if (fixes.isEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphBadge(ReefGlyphs.heart, Candy.green, size = 48.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(if (busy > 0) "Fixing..." else "Your tank looks great!", style = s.titleText, color = s.label)
                LiveHelp(if (busy > 0) "Watch the tank: the fixes are going in one by one." else "The water and the gear are all in good shape. Check again after a few days.")
            }
        }
        return
    }
    LiveHelp(if (fixes.size == 1) "The tank needs one thing." else "The tank needs ${fixes.size} things. Fix them one by one or all at once.")
    if (fixes.size > 1) {
        LiveTile(ReefGlyphs.waveSparkle, "Fix everything", tone, onClick = { onFixAll(); close() }, modifier = Modifier.fillMaxWidth(), sub = "${fixes.size} fixes")
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (f in fixes) FixRow(f, tone) { onFix(f); if (fixes.size == 1) close() }
    }
}

@Composable
private fun FixRow(f: CareFix, tone: Color, onFix: () -> Unit) {
    val s = LiveTheme.style
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GlyphBadge(fixGlyph(f.kind), if (f.urgent) Candy.red else tone, size = 44.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(f.title, style = s.buttonText.copy(fontSize = 14.sp), color = s.label)
            Text(f.why, style = MaterialTheme.typography.bodySmall, color = s.labelDim)
        }
        Spacer(Modifier.width(8.dp))
        LivePill("Fix", onFix, Modifier.padding(start = 2.dp), tone = if (f.urgent) Candy.red else tone)
    }
}

/** The picture beside each fix. */
fun fixGlyph(k: TankDoctor.Kind): ReefGlyph = when (k) {
    TankDoctor.Kind.EMPTY_SKIMMER -> ReefGlyphs.skimmer
    TankDoctor.Kind.SWAP_SOCK -> ReefGlyphs.sock
    TankDoctor.Kind.FRESH_CARBON -> ReefGlyphs.carbon
    TankDoctor.Kind.WATER_CHANGE -> ReefGlyphs.bucket
    TankDoctor.Kind.TOP_OFF -> ReefGlyphs.droplet
    TankDoctor.Kind.LOWER_SALT -> ReefGlyphs.dropMinus
    TankDoctor.Kind.RAISE_SALT -> ReefGlyphs.dropPlus
    TankDoctor.Kind.DOSE_MG, TankDoctor.Kind.DOSE_CA, TankDoctor.Kind.DOSE_ALK -> ReefGlyphs.bottle
    TankDoctor.Kind.HEATER -> ReefGlyphs.flame
    TankDoctor.Kind.PUMPS -> ReefGlyphs.powerhead
    TankDoctor.Kind.FEED_CORALS -> ReefGlyphs.feedTin
}
