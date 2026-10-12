package com.reeflive.app.ui.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.components.GlassIconButton
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.sim.MicroAction
import com.reeflive.core.sim.MicroCount
import com.reeflive.core.sim.MicroGroup
import com.reeflive.core.sim.MicroScene
import com.reeflive.core.sim.MicroView
import com.reeflive.core.sim.MicroZoom
import com.reeflive.core.sim.MicrofaunaState
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The microscope (research 10 §5): a close-up of one spot in the tank at 1x (naked eye / torch), 10x (hand lens) or
 * 40x (low-power microscope, bright-field). Counts per field come from [MicroView.sample] on the sim's microfauna
 * densities, so they follow day/night, doses and predators; the animals themselves are a small particle system with
 * the §5.3 motion primitives (hop-and-sink, crawl, scuttle and side-swim, sway, hover, glide). The remainder above the
 * 300-sprite cap shimmers as a texture, and a legend lists the tank's populations. [onAction] (sandbox only) adds the
 * pod / phyto doses and pest treatments.
 */
@Composable
fun MicroscopeView(
    state: MicrofaunaState,
    simTimeSec: Double,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onAction: ((MicroAction) -> Unit)? = null,
) {
    val c = ReefTheme.colors
    var scene by rememberSaveable { mutableStateOf(MicroScene.ROCK) }
    var zoom by rememberSaveable { mutableStateOf(MicroZoom.X10) }
    // one seed per spot and sim hour: reopening the same view shows the same animals
    val hour = (simTimeSec / 3600.0).toLong()
    val counts = remember(state, scene, zoom, hour) {
        MicroView.sample(state, scene, zoom, seed = hour * 1009L + scene.ordinal * 131L + zoom.ordinal * 7L)
    }
    val cells = MicroView.phytoCells(state, zoom)
    val look = Look(
        night = state.night && zoom != MicroZoom.X40, bright = zoom == MicroZoom.X40,
        greenGut = state.phytoPerMl > GREEN_GUT_CELLS, breeding = state.breeding,
    )
    val haze = MicroView.phytoHaze(state, zoom) * (if (scene == MicroScene.WATER) 0.7f else 0.35f)
    // what the shimmer layer stands for: the animals expected in view but not drawn
    val remainder = counts.sumOf { (it.expected - it.sprites).coerceAtLeast(0.0) }
    val shimmer = min(SHIMMER_CAP.toDouble(), remainder * 0.1).toInt()

    val latestCounts by rememberUpdatedState(counts)
    val latestCells by rememberUpdatedState(cells)
    val swarm = remember { Swarm() }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                last = now
                swarm.sync(latestCounts, latestCells)
                swarm.step(dt, time)
                time += dt
            }
        }
    }

    Column(
        modifier.fillMaxSize().background(c.bg).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Microscope", style = MaterialTheme.typography.titleMedium, color = c.text)
                Text(
                    "${zoom.label} · ${fieldLabel(zoom)} field · " +
                        (if (zoom == MicroZoom.X40) "bright-field lamp" else if (state.night) "night, torch" else "day"),
                    style = MaterialTheme.typography.labelMedium, color = c.textSecondary,
                )
            }
            GlassIconButton(Icons.Rounded.Close, "Close microscope", onClose)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MicroScene.entries.forEach { s -> ReefChip(text = s.label, selected = s == scene, onClick = { scene = s }) }
            Spacer(Modifier.width(4.dp))
            MicroZoom.entries.forEach { z -> ReefChip(text = z.label, selected = z == zoom, onClick = { zoom = z }, accent = c.coral) }
        }
        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(1f).clip(ReefShapes.card).border(1.dp, c.glassBorder, ReefShapes.card),
        ) {
            // keep the field's area at the §5.2 value whatever the view's shape, so counts per field stay honest
            val aspect = if (maxWidth.value > 0f) (maxHeight / maxWidth).coerceIn(0.2f, 4f) else NOMINAL_ASPECT
            val widthMm = sqrt(zoom.fieldCm2.toFloat() * 100f / aspect)
            val barMm = SCALE_BARS_MM.lastOrNull { it <= widthMm * 0.25f } ?: SCALE_BARS_MM.first()
            val ink = if (look.bright) BRIGHT_INK else Color.White.copy(alpha = 0.85f)
            Canvas(Modifier.fillMaxSize()) {
                val t = time
                swarm.field(widthMm, aspect, scene)
                val px = size.width / widthMm
                drawBackdrop(scene, look, px, t)
                if (haze > 0f) drawRect(PHYTO_GREEN.copy(alpha = haze))
                if (look.night) {
                    // lights out: the torch beam picks out a pool of rock, the rest falls away to black
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.85f)),
                            center = Offset(size.width * 0.45f, size.height * 0.45f), radius = size.minDimension * 0.9f,
                        ),
                    )
                }
                for (m in swarm.motes) drawMote(m, px, t, look)
                drawShimmer(shimmer, t, look)
                val y0 = size.height - 10.dp.toPx()
                val x0 = 12.dp.toPx()
                drawLine(ink, Offset(x0, y0), Offset(x0 + barMm * px, y0), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            Text(
                scaleLabel(barMm), style = MaterialTheme.typography.labelSmall, color = ink,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 16.dp),
            )
        }
        MicroLegend(state, counts, zoom, Modifier.fillMaxWidth())
        onAction?.let { act ->
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MicroAction.entries.forEach { a -> ReefChip(text = a.label, selected = false, onClick = { act(a) }, accent = c.coral) }
            }
        }
    }
}

/** Populations in the tank, what this field shows of each, and the §6.2 step 6 readouts. */
@Composable
private fun MicroLegend(state: MicrofaunaState, counts: List<MicroCount>, zoom: MicroZoom, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    GlassCard(modifier, contentPadding = PaddingValues(12.dp)) {
        Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val groups = MicroGroup.entries
                .filter { it != MicroGroup.PHYTO && state.present[it.ordinal] && state.total(it) >= 1.0 }
                .sortedByDescending { state.total(it) }
            if (groups.isEmpty()) {
                Text(
                    "Nothing living on the rock yet. Dry rock needs live rock, a frag or a bottle of pods to seed it.",
                    style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                )
            }
            for (g in groups) {
                val mc = counts.firstOrNull { it.group == g }
                val here = when {
                    !MicroView.resolvable(g, zoom) -> "too small"
                    !MicroView.fits(g, zoom) -> "too big"
                    mc == null -> "0 here"
                    mc.expected >= 2.0 * maxOf(mc.sprites, 1) -> "${mc.sprites} of ~${compact(mc.expected)} here"
                    else -> "${mc.sprites + mc.nauplii} here"
                }
                val trend = state.trendPerDay[g.ordinal] * 100.0
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(ReefShapes.pill).background(groupColor(g)))
                    Text(g.plural, style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f))
                    Text(here, style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
                    Text(compact(state.total(g)), style = MaterialTheme.typography.labelLarge, color = c.text)
                    Text(
                        if (abs(trend) < 0.5) "steady" else String.format(Locale.US, "%+.0f%%/d", trend),
                        style = MaterialTheme.typography.labelMedium,
                        color = when { abs(trend) < 0.5 -> c.textTertiary; trend > 0 -> c.goodText; else -> c.cautionText },
                    )
                }
            }
            val info = MaterialTheme.typography.labelMedium
            Text(
                "Torch count after lights-out: ~${compact(state.torchPer100Cm2)} pods per 100 cm² of glass",
                style = info, color = c.textSecondary,
            )
            val born = state.podsBornPerDay
            val eaten = state.podsEatenPerDay
            Text(
                "Copepods born ${compact(born)}/day · eaten ${compact(eaten)}/day",
                style = info, color = if (eaten > born * 1.1 && eaten > 1.0) c.cautionText else c.textSecondary,
            )
            Text(
                if (state.phytoPerMl >= 1e3) "Phytoplankton ${compact(state.phytoPerMl)} cells/mL" else "Water clear of phytoplankton",
                style = info, color = c.textSecondary,
            )
            if (state.coralCapturePerDay >= 1.0) {
                Text(
                    "Corals catch ~${compact(state.coralCapturePerDay)} prey/day" + (if (state.coralFed) " · feeding bonus on" else ""),
                    style = info, color = c.textSecondary,
                )
            }
            if (state.refugiumL > 0.0) Text("Refugium: ${compact(state.refugiumL)} L of chaeto", style = info, color = c.textSecondary)
            if (state.toxin) {
                Text(
                    "Flatworm die-off is releasing toxin: run fresh carbon and change water",
                    style = info, color = c.dangerText,
                )
            }
        }
    }
}

// ================================================================== particles

/** How a group moves in the field (§5.3). */
private enum class Gait { HOP, CRAWL, STEP, AMPHIPOD, SWAY, HOVER, GLIDE, STATIC, CELL }

private fun gaitOf(g: MicroGroup, nauplius: Boolean, scene: MicroScene): Gait = when {
    nauplius -> Gait.HOP
    g == MicroGroup.PHYTO -> Gait.CELL
    g == MicroGroup.CALANOID || g == MicroGroup.CYCLOPOID -> Gait.HOP
    // benthic pods caught in the water column swim in hops like the planktonic ones
    g == MicroGroup.HARPACTICOID || g == MicroGroup.OSTRACOD -> if (scene == MicroScene.WATER) Gait.HOP else Gait.CRAWL
    g == MicroGroup.ISOPOD || g == MicroGroup.MICROSNAIL || g == MicroGroup.FLATWORM -> Gait.CRAWL
    g == MicroGroup.CILIATE -> if (scene == MicroScene.WATER) Gait.GLIDE else Gait.STEP
    g == MicroGroup.GAMMARID -> Gait.AMPHIPOD
    g == MicroGroup.CAPRELLID -> Gait.SWAY
    g == MicroGroup.MYSID -> Gait.HOVER
    g == MicroGroup.ROTIFER || g == MicroGroup.BRISTLEWORM -> Gait.GLIDE
    else -> Gait.STATIC
}

/** Surface crawling speed, mm/s (§5.3). */
private fun crawlSpeed(g: MicroGroup): Float = when (g) {
    MicroGroup.HARPACTICOID, MicroGroup.OSTRACOD, MicroGroup.MICROSNAIL -> 2f
    MicroGroup.ISOPOD -> 5f
    MicroGroup.FLATWORM -> 0.75f
    else -> 1f
}

/** Free gliding speed, mm/s (§5.3). */
private fun glideSpeed(g: MicroGroup): Float = when (g) {
    MicroGroup.ROTIFER -> 0.6f
    MicroGroup.CILIATE -> 0.4f
    MicroGroup.BRISTLEWORM -> 12f
    else -> 1f
}

/** One animal on screen. Position in mm from the field's top-left; [r1]/[r2] are fixed per-animal randoms. */
private class Mote(val group: MicroGroup, val nauplius: Boolean, val gait: Gait, val r1: Float, val r2: Float) {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var heading = 0f
    var turn = 0f
    var timer = 0f
    var state = 0
    /** Seconds since the last hop (antennule flare) or the leg-cycle clock. */
    var since = 1f
    /** Anchor (sway, static) or offset in the school (hover), mm. */
    var ax = 0f
    var ay = 0f

    val lengthMm: Float
        get() = when {
            nauplius -> MicroView.NAUPLIUS_MM.toFloat()
            group == MicroGroup.PHYTO -> TETRASELMIS_MM
            else -> group.sizeMm.toFloat()
        }
}

/**
 * The animals in view, kept in step with the sampled counts: when a count rises one animal swims in, when it falls
 * the newest leave, so a recount never reshuffles the whole field. Touched only from the frame loop and the draw pass
 * (both on the main thread); the Canvas redraws because it reads the frame clock.
 */
private class Swarm {
    val motes = ArrayList<Mote>()
    private val rnd = Random(11)
    private var w = 300f
    private var h = 170f
    private var scene = MicroScene.ROCK
    private var sx = 0f
    private var sy = 0f

    /** Field size in mm for this view; a new scene or zoom starts a fresh field. */
    fun field(widthMm: Float, aspect: Float, s: MicroScene) {
        val nh = widthMm * aspect
        if (s != scene || abs(widthMm - w) > w * 0.25f) {
            motes.clear()
            scene = s
        } else if (widthMm != w || nh != h) {
            val kx = widthMm / w
            val ky = nh / h
            for (m in motes) { m.x *= kx; m.y *= ky; m.ax *= kx; m.ay *= ky }
        }
        w = widthMm
        h = nh
    }

    fun sync(counts: List<MicroCount>, cells: Int) {
        for (g in MicroGroup.entries) {
            val mc = counts.firstOrNull { it.group == g }
            match(g, false, if (g == MicroGroup.PHYTO) cells else mc?.sprites ?: 0)
            match(g, true, mc?.nauplii ?: 0)
        }
    }

    private fun match(g: MicroGroup, nauplius: Boolean, n: Int) {
        var have = 0
        for (m in motes) if (m.group == g && m.nauplius == nauplius) have++
        var i = motes.size - 1
        while (have > n && i >= 0) {
            val m = motes[i]
            if (m.group == g && m.nauplius == nauplius) { motes.removeAt(i); have-- }
            i--
        }
        while (have < n) { motes += spawn(g, nauplius); have++ }
    }

    private fun spawn(g: MicroGroup, nauplius: Boolean): Mote {
        val m = Mote(g, nauplius, gaitOf(g, nauplius, scene), rnd.nextFloat(), rnd.nextFloat())
        m.x = rnd.nextFloat() * w
        m.y = rnd.nextFloat() * h
        m.ax = m.x
        m.ay = m.y
        m.heading = rnd.nextFloat() * TAU
        m.timer = rnd.nextFloat() * 2f
        if (m.gait == Gait.HOVER) {
            // a loose school, 1-2 body lengths apart
            val spread = min(w * 0.6f, 60f)
            m.ax = (rnd.nextFloat() - 0.5f) * spread
            m.ay = (rnd.nextFloat() - 0.5f) * spread * 0.5f
        }
        return m
    }

    fun step(dt: Float, t: Float) {
        if (dt <= 0f) return
        // the mysid school drifts on a slow Lissajous path
        sx = w * (0.5f + 0.25f * sin(t * 0.07f))
        sy = h * (0.5f + 0.2f * sin(t * 0.05f + 1f))
        val decay = exp(-HOP_DECAY * dt)
        for (m in motes) {
            when (m.gait) {
                Gait.HOP -> hop(m, dt)
                Gait.CRAWL -> crawl(m, dt)
                Gait.STEP -> {
                    // Euplotes: a jerky crawl in 0.1-0.5 mm steps with right-angle turns
                    m.timer -= dt
                    if (m.timer <= 0f) {
                        m.timer = 0.3f + 0.7f * rnd.nextFloat()
                        if (rnd.nextFloat() < 0.5f) m.heading += if (rnd.nextBoolean()) HALF_PI else -HALF_PI
                        impulse(m, m.heading, 0.1f + 0.4f * rnd.nextFloat())
                    }
                }
                Gait.AMPHIPOD -> amphipod(m, dt)
                Gait.SWAY -> {
                    // anchored, swaying with the flow at 0.5-1 Hz
                    m.heading = -HALF_PI + (m.r1 - 0.5f) * 0.8f + 0.35f * sin(TAU * (0.5f + 0.5f * m.r2) * t + m.r1 * 6f)
                }
                Gait.HOVER -> {
                    val k = min(1f, 1.5f * dt)
                    m.x += (sx + m.ax - m.x) * k
                    m.y += (sy + m.ay - m.y) * k
                    m.heading = PI.toFloat()   // nose into the current
                }
                Gait.GLIDE -> {
                    m.heading += sin(t * 0.4f + m.r1 * 6f) * 0.6f * dt
                    val v = glideSpeed(m.group) * (0.6f + 0.8f * m.r2)
                    m.x += cos(m.heading) * v * dt
                    m.y += sin(m.heading) * v * dt
                    m.since += dt
                }
                Gait.CELL -> {
                    // Tetraselmis: a straight run, then an abrupt new direction
                    m.timer -= dt
                    if (m.timer <= 0f) { m.timer = 0.5f + 1.5f * rnd.nextFloat(); m.heading = rnd.nextFloat() * TAU }
                    val v = 0.1f + 0.2f * m.r1
                    m.x += cos(m.heading) * v * dt
                    m.y += sin(m.heading) * v * dt
                }
                Gait.STATIC -> Unit
            }
            m.x += m.vx * dt
            m.y += m.vy * dt
            m.vx *= decay
            m.vy *= decay
            if (m.gait != Gait.HOVER && m.gait != Gait.SWAY && m.gait != Gait.STATIC) {
                m.x = wrap(m.x, w)
                m.y = wrap(m.y, h)
            }
        }
    }

    /** Copepods and nauplii: a hop of 1-3 body lengths that decays fast, then a slow sink in open water. */
    private fun hop(m: Mote, dt: Float) {
        m.timer -= dt
        m.since += dt
        if (m.timer <= 0f) {
            val bl = if (m.nauplius) 1f else 1f + 2f * rnd.nextFloat()
            val a = if (scene == MicroScene.WATER) -HALF_PI + (rnd.nextFloat() - 0.5f) * 2.6f else rnd.nextFloat() * TAU
            impulse(m, a, bl * m.lengthMm)
            m.heading = a
            m.since = 0f
            m.timer = when {
                m.nauplius -> 0.3f + 0.7f * rnd.nextFloat()
                m.group == MicroGroup.CYCLOPOID -> 1f + 4f * rnd.nextFloat()
                else -> 0.5f + 1.5f * rnd.nextFloat()
            }
        }
        if (scene == MicroScene.WATER) m.y += SINK_MM_S * dt
    }

    /** Surface random walk with feeding pauses; harpacticoids now and then swim-hop 2-5 mm (a shadow passing). */
    private fun crawl(m: Mote, dt: Float) {
        m.timer -= dt
        if (m.timer <= 0f) {
            m.state = 1 - m.state
            m.timer = if (m.state == 1) 0.5f + 2.5f * rnd.nextFloat() else 1f + 3f * rnd.nextFloat()
            if (m.state == 0) m.heading += (rnd.nextFloat() - 0.5f) * 2f
        }
        if (m.state == 0) {
            m.heading += (rnd.nextFloat() - 0.5f) * 3f * dt
            val v = crawlSpeed(m.group) * (0.6f + 0.8f * m.r1)
            m.x += cos(m.heading) * v * dt
            m.y += sin(m.heading) * v * dt
        }
        if (m.group == MicroGroup.HARPACTICOID && rnd.nextFloat() < 0.05f * dt) impulse(m, m.heading, 2f + 3f * rnd.nextFloat())
    }

    /** Gammarids: scuttle (5-20 mm/s), side-swim bouts (30-80 mm/s on a curving path) and pauses. */
    private fun amphipod(m: Mote, dt: Float) {
        m.timer -= dt
        m.since += dt
        if (m.timer <= 0f) {
            val p = rnd.nextFloat()
            m.state = when { p < 0.3f -> 1; p < 0.7f -> 2; else -> 0 }
            m.timer = 0.5f + (if (m.state == 1) 2.5f else 1.5f) * rnd.nextFloat()
            m.turn = (rnd.nextFloat() - 0.5f) * 1.6f
            if (m.state != 2) m.heading += (rnd.nextFloat() - 0.5f) * 2f
        }
        val v = when (m.state) { 0 -> 5f + 15f * m.r1; 1 -> 30f + 50f * m.r2; else -> 0f }
        if (m.state == 0) m.heading += (rnd.nextFloat() - 0.5f) * 4f * dt
        if (m.state == 1) m.heading += m.turn * dt
        m.x += cos(m.heading) * v * dt
        m.y += sin(m.heading) * v * dt
    }

    /** A velocity kick that travels [distMm] before the exponential drag stops it. */
    private fun impulse(m: Mote, angle: Float, distMm: Float) {
        m.vx = cos(angle) * distMm * HOP_DECAY
        m.vy = sin(angle) * distMm * HOP_DECAY
    }
}

// ================================================================== drawing

/** Colour scheme for the current light (§5.4): daylight macro, torch at night, or 40x bright-field. */
private class Look(val night: Boolean, val bright: Boolean, greenGut: Boolean, private val breeding: DoubleArray) {
    val outline: Color? = if (bright) BRIGHT_INK else null
    /** Gut band: brown on film and detritus, green after a phyto dose. */
    val gut: Color = if (greenGut) Color(0xFF4F8F3A) else Color(0xFF6B4A2A)
    val highlight: Color = if (bright) BRIGHT_INK else Color(0xFFF2F7FF)

    fun breeding(g: MicroGroup): Float = if (g.ordinal < breeding.size) breeding[g.ordinal].toFloat() else 0f

    fun body(g: MicroGroup): Color {
        val base = groupColor(g)
        return when {
            bright -> base.copy(alpha = 0.45f)                       // translucent under transmitted light
            night && g.isCrustacean && g.sizeMm < 1.5 -> NIGHT_SPECK // pods as white-blue specks in the beam
            night -> base.copy(alpha = 0.8f)
            else -> base
        }
    }
}

/** Daylight colour per group (§5.4). */
private fun groupColor(g: MicroGroup): Color = when (g) {
    MicroGroup.HARPACTICOID -> Color(0xFFD9A56B)
    MicroGroup.CALANOID -> Color(0xFFE5743F)
    MicroGroup.CYCLOPOID -> Color(0xFFCDB89A)
    MicroGroup.GAMMARID -> Color(0xFF8C8455)
    MicroGroup.CAPRELLID -> Color(0xFFB89A7A)
    MicroGroup.MYSID -> Color(0xFFE6D9C2)
    MicroGroup.OSTRACOD -> Color(0xFFC9B88A)
    MicroGroup.ISOPOD -> Color(0xFF8E8A86)
    MicroGroup.ROTIFER -> Color(0xFFDDE6E0)
    MicroGroup.CILIATE -> Color(0xFFCFE3E8)
    MicroGroup.PHYTO -> PHYTO_GREEN
    MicroGroup.BRISTLEWORM -> Color(0xFFD08A66)
    MicroGroup.MICROSNAIL -> Color(0xFFA8957E)
    MicroGroup.SPAGHETTI_WORM -> Color(0xFFE8CDB4)
    MicroGroup.FORAM -> Color(0xFFD9576A)
    MicroGroup.FLATWORM -> Color(0xFFB5482F)
}

/** Fixed texture points for the rock, glass, sand and water backdrops (fractions of the view, a size and a tone). */
private class Grain(val x: Float, val y: Float, val r: Float, val tone: Int)

private val GRAINS: List<Grain> = Random(5).let { rnd -> List(180) { Grain(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat(), rnd.nextInt(3)) } }

private fun DrawScope.drawBackdrop(scene: MicroScene, look: Look, px: Float, t: Float) {
    val w = size.width
    val h = size.height
    val md = size.minDimension
    if (look.bright) {
        drawRect(Color(0xFFE8E3D3))
        when (scene) {
            MicroScene.SAND -> for (i in 0 until 24) {
                val g = GRAINS[i]
                val c = Offset(g.x * w, g.y * h)
                val r = (0.2f + 0.3f * g.r) * px
                drawCircle(Color(0xFFD9CCAA).copy(alpha = 0.6f), r, c)
                drawCircle(BRIGHT_INK.copy(alpha = 0.35f), r, c, style = Stroke(width = 1.5f))
            }
            MicroScene.WATER -> Unit
            else -> for (i in 0 until 90) {
                // the diatom film: golden rods of 20-50 µm
                val g = GRAINS[i]
                val c = Offset(g.x * w, g.y * h)
                val l = (0.02f + 0.03f * g.r) * px
                val a = g.tone * 1.1f + g.r * 3f
                drawLine(Color(0xFFB48A3C).copy(alpha = 0.6f), c, Offset(c.x + cos(a) * l, c.y + sin(a) * l), strokeWidth = (0.006f * px).coerceAtLeast(1.5f), cap = StrokeCap.Round)
            }
        }
        // the round edge of a microscope field
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xFF1B1A17).copy(alpha = 0.85f)), center = center, radius = size.maxDimension * 0.62f))
        return
    }
    when (scene) {
        MicroScene.ROCK -> {
            drawRect(Color(0xFF4E3F4A))
            for (g in GRAINS) {
                val c = Offset(g.x * w, g.y * h)
                when (g.tone) {
                    0 -> drawCircle(Color(0xFFB0657F).copy(alpha = 0.5f), (0.04f + 0.08f * g.r) * md, c)   // coralline
                    1 -> drawCircle(Color(0xFF9C7A45).copy(alpha = 0.35f), (0.01f + 0.02f * g.r) * md, c)  // diatom film
                    else -> drawCircle(Color(0xFF2C2229).copy(alpha = 0.6f), (0.008f + 0.015f * g.r) * md, c) // pores
                }
            }
        }
        MicroScene.GLASS -> {
            drawRect(Color(0xFF0F2C36))
            for (g in GRAINS) drawCircle(Color(0xFFB48A3C).copy(alpha = 0.10f + 0.08f * g.tone), (0.004f + 0.01f * g.r) * md, Offset(g.x * w, g.y * h))
        }
        MicroScene.SAND -> {
            drawRect(Color(0xFFC9B68F))
            for (g in GRAINS) {
                val tone = when (g.tone) { 0 -> Color(0xFFE6D8B8); 1 -> Color(0xFFB09A70); else -> Color(0xFFD4C4A0) }
                drawCircle(tone, ((0.25f + 0.25f * g.r) * px).coerceAtLeast(1f), Offset(g.x * w, g.y * h))
            }
        }
        MicroScene.WATER -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF12406A), Color(0xFF061A2E))))
            // marine snow sinking slowly through the focus
            for (g in GRAINS) {
                val y = frac(g.y + t * 0.004f * (0.5f + g.r)) * h
                drawCircle(Color.White.copy(alpha = 0.12f + 0.1f * g.r), 1f + 1.5f * g.r, Offset(g.x * w, y))
            }
        }
    }
}

/** The animals above the sprite cap, as flickering specks whose density follows the uncapped count (§5.2). */
private fun DrawScope.drawShimmer(n: Int, t: Float, look: Look) {
    if (n <= 0) return
    val col = when { look.night -> NIGHT_SPECK; look.bright -> BRIGHT_INK; else -> Color(0xFFE2C9A0) }
    for (i in 0 until n) {
        val gx = hash(i * 2 + 1)
        val gy = hash(i * 2 + 2)
        val x = frac(gx + t * 0.004f * (gy - 0.5f)) * size.width
        val y = frac(gy + t * 0.003f * (gx - 0.5f)) * size.height
        val a = 0.12f + 0.3f * (0.5f + 0.5f * sin(t * (2f + 3f * gy) + i))
        drawCircle(col.copy(alpha = a), 1.3f, Offset(x, y))
    }
}

private fun DrawScope.drawMote(m: Mote, px: Float, t: Float, look: Look) {
    val l = m.lengthMm * px
    var cx = m.x * px
    var cy = m.y * px
    if (m.gait == Gait.HOVER) {
        // mysids hold station with a 1-2 mm jitter at 2-3 Hz
        cx += sin(t * 2.5f * TAU + m.r1 * 6f) * 1.2f * px
        cy += cos(t * 2.2f * TAU + m.r2 * 6f) * 0.8f * px
    }
    val body = look.body(m.group)
    val c = Offset(cx, cy)
    if (l < DOT_PX) {
        // below ~8 px an animal is a speck; at night the torch makes pods glint
        val r = (l * 0.4f).coerceAtLeast(1.3f)
        if (look.night && m.group.isCrustacean) drawCircle(body.copy(alpha = 0.25f), r * 2.6f, c)
        drawCircle(body, r, c)
        return
    }
    val sw = (l * 0.04f).coerceAtLeast(1f)
    rotate(m.heading * DEG, c) {
        when {
            m.nauplius -> nauplius(m, cx, cy, l, sw, body, look, t)
            m.group.isCopepod -> copepod(m, cx, cy, l, sw, body, look)
            else -> when (m.group) {
                MicroGroup.GAMMARID -> amphipod(m, cx, cy, l, sw, body, t)
                MicroGroup.CAPRELLID -> caprellid(m, cx, cy, l, sw, body, t)
                MicroGroup.MYSID -> mysid(cx, cy, l, sw, body, look)
                MicroGroup.OSTRACOD -> {
                    blob(body, look.outline, cx, cy, l, 0.65f * l, sw)
                    drawLine(Color.Black.copy(alpha = 0.25f), Offset(cx - 0.3f * l, cy - 0.2f * l), Offset(cx + 0.3f * l, cy - 0.2f * l), strokeWidth = sw)
                    // antennae flick out only while it runs
                    if (m.state == 0) for (s in SIDES) {
                        drawLine(body, Offset(cx + 0.45f * l, cy), Offset(cx + 0.7f * l, cy + s * 0.15f * l), strokeWidth = sw)
                    }
                }
                MicroGroup.ISOPOD -> {
                    blob(body, look.outline, cx, cy, l, 0.5f * l, sw)
                    for (i in 1..5) {
                        val x = cx - 0.4f * l + i * 0.13f * l
                        drawLine(Color.Black.copy(alpha = 0.25f), Offset(x, cy - 0.2f * l), Offset(x, cy + 0.2f * l), strokeWidth = sw)
                    }
                    for (s in SIDES) drawLine(body, Offset(cx + 0.48f * l, cy), Offset(cx + 0.7f * l, cy + s * 0.18f * l), strokeWidth = sw)
                }
                MicroGroup.ROTIFER -> rotifer(cx, cy, l, sw, body, look, t)
                MicroGroup.CILIATE -> {
                    blob(body.copy(alpha = 0.6f), look.outline, cx, cy, l, 0.7f * l, sw)
                    // the cilia shimmer round the rim
                    for (k in 0 until 12) {
                        val a = k * TAU / 12f
                        val f = 0.5f + 0.5f * sin(t * 12f + k)
                        drawCircle(look.highlight.copy(alpha = 0.5f * f), 0.03f * l, Offset(cx + cos(a) * 0.52f * l, cy + sin(a) * 0.37f * l))
                    }
                }
                MicroGroup.BRISTLEWORM -> bristleworm(cx, cy, l, body, t)
                MicroGroup.MICROSNAIL -> {
                    val shell = Color.Black.copy(alpha = 0.3f)
                    for (s in SIDES) drawLine(Color(0xFFD8CBB8), Offset(cx + 0.3f * l, cy + s * 0.1f * l), Offset(cx + 0.6f * l, cy + s * 0.22f * l), strokeWidth = sw)
                    drawCircle(body, 0.4f * l, c)
                    drawCircle(shell, 0.25f * l, Offset(cx - 0.03f * l, cy), style = Stroke(width = sw))
                    drawCircle(shell, 0.1f * l, Offset(cx - 0.06f * l, cy - 0.03f * l), style = Stroke(width = sw))
                }
                MicroGroup.SPAGHETTI_WORM -> {
                    // tentacles spread over the surface, slowly extending and drawing back
                    for (k in 0 until 6) {
                        val a = k * TAU / 6f + m.r1 * 6f
                        val reach = l * (0.35f + 0.25f * sin(t * 0.3f + k * 1.7f + m.r2 * 6f))
                        val wig = 0.05f * l * sin(t + k)
                        val mid = Offset(cx + cos(a) * reach * 0.5f - sin(a) * wig, cy + sin(a) * reach * 0.5f + cos(a) * wig)
                        val end = Offset(cx + cos(a) * reach, cy + sin(a) * reach)
                        drawLine(body, c, mid, strokeWidth = sw)
                        drawLine(body, mid, end, strokeWidth = sw)
                    }
                    drawCircle(body, 0.05f * l, c)
                }
                MicroGroup.FORAM -> {
                    // at 40x the pseudopod net radiates from the test
                    if (look.bright) for (k in 0 until 24) {
                        val a = k * TAU / 24f + m.r1
                        val r = 0.9f * l * (0.7f + 0.3f * sin(t * 0.5f + k))
                        drawLine(body.copy(alpha = 0.25f), c, Offset(cx + cos(a) * r, cy + sin(a) * r), strokeWidth = sw * 0.6f)
                    }
                    drawCircle(body, 0.4f * l, c)
                    drawCircle(Color.White.copy(alpha = 0.25f), 0.25f * l, c, style = Stroke(width = sw))
                    drawCircle(Color.Black.copy(alpha = 0.3f), 0.08f * l, c)
                }
                MicroGroup.FLATWORM -> {
                    blob(body, null, cx, cy, l, 0.6f * l, sw)
                    drawOval(Color(0xFFE08A6A), Offset(cx - 0.5f * l, cy - 0.3f * l), Size(l, 0.6f * l), style = Stroke(width = sw))
                    for (s in SIDES) drawCircle(Color(0xFF2A1410), 0.04f * l, Offset(cx + 0.3f * l, cy + s * 0.08f * l))
                }
                else -> blob(body, look.outline, cx, cy, l, 0.6f * l, sw)
            }
        }
    }
}

/** Copepod seen from above, head at +x: prosome, urosome and furca, gut band, egg sacs and antennules. */
private fun DrawScope.copepod(m: Mote, cx: Float, cy: Float, l: Float, sw: Float, body: Color, look: Look) {
    val g = m.group
    val slim = if (g == MicroGroup.HARPACTICOID) 0.24f else 0.34f
    // harpacticoids flex the body at ~3 Hz while they crawl
    val flex = if (g == MicroGroup.HARPACTICOID && m.state == 0) sin(m.x * 7f + m.y * 5f) * 0.08f * l else 0f
    val tail = Offset(cx - 0.5f * l, cy + flex)
    drawLine(body, Offset(cx - 0.1f * l, cy), tail, strokeWidth = 0.08f * l, cap = StrokeCap.Round)
    for (s in SIDES) drawLine(body, tail, Offset(tail.x - 0.1f * l, tail.y + s * 0.06f * l), strokeWidth = sw)
    // egg sacs: paired on cyclopoids, single on harpacticoids; the share of females carrying follows the breeding index
    if (g != MicroGroup.CALANOID && m.r2 < 0.5f * look.breeding(g)) {
        val sac = Color(0xFF8A7A5C)
        if (g == MicroGroup.CYCLOPOID) {
            for (s in SIDES) drawOval(sac, Offset(cx - 0.5f * l, cy + s * 0.16f * l - 0.07f * l), Size(0.3f * l, 0.14f * l))
        } else {
            drawOval(sac, Offset(cx - 0.42f * l, cy - 0.08f * l), Size(0.25f * l, 0.16f * l))
        }
    }
    blob(body, look.outline, cx + 0.15f * l, cy, 0.7f * l, slim * l, sw)
    blob(look.gut, null, cx + 0.1f * l, cy, 0.35f * l, slim * 0.35f * l, 0f)
    // antennules: long on calanoids and flared back on a hop, short on harpacticoids
    val reach = l * (when (g) { MicroGroup.CALANOID -> 0.9f; MicroGroup.CYCLOPOID -> 0.4f; else -> 0.15f })
    val angle = if (g == MicroGroup.CALANOID && m.since < 0.2f) 2.3f else 1.25f
    val head = Offset(cx + 0.48f * l, cy)
    for (s in SIDES) drawLine(body, head, Offset(head.x + cos(angle) * reach, head.y + s * sin(angle) * reach), strokeWidth = sw)
    drawCircle(Color(0xFFB0302A), 0.035f * l, Offset(head.x - 0.04f * l, cy))
}

/** Nauplius: a small oval with three flickering limb pairs (§5.3). */
private fun DrawScope.nauplius(m: Mote, cx: Float, cy: Float, l: Float, sw: Float, body: Color, look: Look, t: Float) {
    for (i in 0 until 3) for (s in SIDES) {
        val a = 0.9f + 0.35f * i + sin(t * 6f * TAU + i * 1.3f + m.r1 * 6f) * 0.35f
        val base = Offset(cx + (0.2f - 0.15f * i) * l, cy)
        drawLine(body, base, Offset(base.x + cos(a) * 0.7f * l, base.y + s * sin(a) * 0.7f * l), strokeWidth = sw)
    }
    blob(body, look.outline, cx, cy, 0.8f * l, 0.6f * l, sw)
    drawCircle(Color(0xFFB0302A), 0.06f * l, Offset(cx + 0.25f * l, cy))
}

/** Gammarid from the side: a bowed row of segments, cycling legs, antennae and an eye. */
private fun DrawScope.amphipod(m: Mote, cx: Float, cy: Float, l: Float, sw: Float, body: Color, t: Float) {
    val n = 7
    val legPhase = if (m.state == 0) m.since * 6.5f * TAU else 0f
    for (i in 1..5) {
        val u = i / (n - 1f)
        val base = Offset(cx + (u - 0.5f) * 0.9f * l, cy - (0.25f - (u - 0.5f) * (u - 0.5f)) * 0.6f * l)
        drawLine(body.copy(alpha = 0.8f), base, Offset(base.x + sin(legPhase + i) * 0.06f * l, base.y + 0.2f * l), strokeWidth = sw)
    }
    if (m.state == 1) blob(body.copy(alpha = 0.25f), null, cx - 0.1f * l, cy + 0.12f * l, 0.5f * l, 0.2f * l, 0f)   // pleopod blur
    var head = Offset(cx, cy)
    for (i in 0 until n) {
        val u = i / (n - 1f)
        val p = Offset(cx + (u - 0.5f) * 0.9f * l, cy - (0.25f - (u - 0.5f) * (u - 0.5f)) * 0.6f * l)
        drawCircle(body, l * (0.06f + 0.05f * sin(u * PI.toFloat())), p)
        head = p
    }
    val wave = 0.04f * l * sin(t * 3f + m.r1 * 6f)
    drawLine(body, head, Offset(head.x + 0.35f * l, head.y - 0.15f * l + wave), strokeWidth = sw)
    drawLine(body, head, Offset(head.x + 0.3f * l, head.y - 0.04f * l - wave), strokeWidth = sw)
    drawCircle(Color(0xFF1E1A16), 0.03f * l, Offset(head.x - 0.01f * l, head.y - 0.02f * l))
}

/** Skeleton shrimp: anchored at its base (the pivot), a jointed stick with gnathopods that snap every few seconds. */
private fun DrawScope.caprellid(m: Mote, cx: Float, cy: Float, l: Float, sw: Float, body: Color, t: Float) {
    drawLine(body, Offset(cx, cy), Offset(cx + l, cy), strokeWidth = (0.05f * l).coerceAtLeast(1f), cap = StrokeCap.Round)
    for (i in 1..4) drawCircle(body, 0.035f * l, Offset(cx + l * i / 5f, cy))
    val snap = (t + m.r1 * 7f) % (2f + 8f * m.r2) < 0.15f
    val a = if (snap) 0.3f else 0.9f
    val g0 = Offset(cx + 0.8f * l, cy)
    for (s in SIDES) {
        drawLine(body, g0, Offset(g0.x + cos(a) * 0.18f * l, g0.y + s * sin(a) * 0.18f * l), strokeWidth = sw * 1.5f)
        drawLine(body, Offset(cx + l, cy), Offset(cx + 1.2f * l, cy + s * 0.06f * l), strokeWidth = sw)
    }
}

/** Mysid: a glassy body, tail fan, statocysts and stalked eyes that shine in the torch. */
private fun DrawScope.mysid(cx: Float, cy: Float, l: Float, sw: Float, body: Color, look: Look) {
    for (s in SIDES) drawLine(body, Offset(cx - 0.45f * l, cy), Offset(cx - 0.62f * l, cy + s * 0.08f * l), strokeWidth = sw * 1.5f)
    blob(body.copy(alpha = 0.55f), look.outline, cx, cy, l, 0.22f * l, sw)
    for (s in SIDES) drawCircle(Color(0xFF3A3A3A), 0.025f * l, Offset(cx - 0.4f * l, cy + s * 0.04f * l))
    val eye = if (look.night) Color(0xFFFFF4C8) else Color(0xFF1E1A16)
    for (s in SIDES) {
        drawLine(body, Offset(cx + 0.45f * l, cy), Offset(cx + 0.55f * l, cy + s * 0.08f * l), strokeWidth = sw)
        drawCircle(eye, 0.04f * l, Offset(cx + 0.56f * l, cy + s * 0.08f * l))
    }
}

/** Rotifer: body, foot, and the corona as two wheels whose highlights run round (the "wheel" illusion). */
private fun DrawScope.rotifer(cx: Float, cy: Float, l: Float, sw: Float, body: Color, look: Look, t: Float) {
    drawLine(body, Offset(cx - 0.35f * l, cy), Offset(cx - 0.55f * l, cy), strokeWidth = sw * 2f, cap = StrokeCap.Round)
    blob(body.copy(alpha = 0.7f), look.outline, cx, cy, 0.8f * l, 0.38f * l, sw)
    blob(look.gut, null, cx - 0.02f * l, cy, 0.3f * l, 0.14f * l, 0f)
    for (s in SIDES) {
        val wc = Offset(cx + 0.42f * l, cy + s * 0.12f * l)
        drawCircle(body.copy(alpha = 0.5f), 0.11f * l, wc, style = Stroke(width = sw))
        for (k in 0 until 5) {
            val a = t * 1.5f * TAU * s + k * TAU / 5f
            drawCircle(look.highlight, 0.025f * l, Offset(wc.x + cos(a) * 0.11f * l, wc.y + sin(a) * 0.11f * l))
        }
    }
}

/** Bristleworm: a chain of segments with a parapodial wave travelling tail-ward at ~1.5 Hz, bristles on each side. */
private fun DrawScope.bristleworm(cx: Float, cy: Float, l: Float, body: Color, t: Float) {
    val n = 14
    val r = 0.035f * l
    val bristle = Color(0xFFF2E6D8).copy(alpha = 0.6f)
    for (i in 0 until n) {
        val u = i / (n - 1f)
        val x = cx + (u - 0.5f) * l
        val y = cy + sin(u * 2f * TAU + t * 1.5f * TAU) * 0.03f * l
        for (s in SIDES) drawLine(bristle, Offset(x, y + s * r), Offset(x - 0.02f * l, y + s * r * 2.2f), strokeWidth = 1f)
        drawCircle(if (i == n - 1) Color(0xFF8A4A35) else body, r, Offset(x, y))
    }
}

/** Filled oval centred on (cx, cy), with an optional outline (bright-field). */
private fun DrawScope.blob(color: Color, outline: Color?, cx: Float, cy: Float, w: Float, h: Float, sw: Float) {
    drawOval(color, Offset(cx - w / 2f, cy - h / 2f), Size(w, h))
    if (outline != null && sw > 0f) drawOval(outline, Offset(cx - w / 2f, cy - h / 2f), Size(w, h), style = Stroke(width = sw))
}

// ================================================================== helpers

private fun wrap(v: Float, max: Float): Float = if (max <= 0f) v else v - floor(v / max) * max

private fun frac(v: Float): Float = v - floor(v)

/** Cheap stable hash to 0..1 for the shimmer specks. */
private fun hash(i: Int): Float = frac(sin(i * 12.9898f) * 43758.547f)

/** 12, 1.2k, 34k, 1.2M. */
private fun compact(v: Double): String = when {
    v >= 1e9 -> String.format(Locale.US, "%.1fB", v / 1e9)
    v >= 1e6 -> String.format(Locale.US, "%.1fM", v / 1e6)
    v >= 1e4 -> String.format(Locale.US, "%.0fk", v / 1e3)
    v >= 1e3 -> String.format(Locale.US, "%.1fk", v / 1e3)
    v >= 10 -> String.format(Locale.US, "%.0f", v)
    else -> String.format(Locale.US, "%.1f", v)
}

private fun fieldLabel(z: MicroZoom): String =
    if (z.edgeMm >= 10) String.format(Locale.US, "%.0f cm", z.edgeMm / 10) else String.format(Locale.US, "%.1f mm", z.edgeMm)

private fun scaleLabel(mm: Float): String = when {
    mm >= 10f -> String.format(Locale.US, "%.0f cm", mm / 10f)
    mm >= 1f -> String.format(Locale.US, "%.0f mm", mm)
    else -> String.format(Locale.US, "%.0f µm", mm * 1000f)
}

private val TAU = (2 * PI).toFloat()
private val HALF_PI = (PI / 2).toFloat()
private val DEG = (180 / PI).toFloat()
/** Hop drag, 1/s: a hop covers v/HOP_DECAY and is over in ~0.3 s. */
private const val HOP_DECAY = 8f
/** Sinking between hops in open water, mm/s (§5.3: 0.5-1). */
private const val SINK_MM_S = 0.7f
/** Tetraselmis cell, mm. */
private const val TETRASELMIS_MM = 0.01f
/** Below this length on screen (px) an animal draws as a speck. */
private const val DOT_PX = 8f
private const val SHIMMER_CAP = 240
/** Gut colour turns green above this phyto density (cells/mL). */
private const val GREEN_GUT_CELLS = 5e4
/** The §5.2 fields are 30 × 17 cm and their scaled copies. */
private const val NOMINAL_ASPECT = 0.567f
private val SCALE_BARS_MM = floatArrayOf(0.1f, 0.2f, 0.5f, 1f, 2f, 5f, 10f, 20f, 50f)
private val SIDES = floatArrayOf(-1f, 1f)
private val PHYTO_GREEN = Color(0xFF4CAF50)
private val NIGHT_SPECK = Color(0xFFDDEBFF)
private val BRIGHT_INK = Color(0xFF2E2A24)
