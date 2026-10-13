package com.reeflive.app.ui.live

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.reeflive.core.sim.MicroGroup
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The microscope's animals (research 10 §5.3-5.4), drawn as they look under a stereo microscope at 10x and a
 * compound microscope at 40x. Every sprite is drawn in body lengths: [drawMicroSprite]'s caller has put the origin on
 * the animal, turned +x to its heading (head or front end at +0.5) and scaled one unit to its length on screen, so
 * stroke widths are in body lengths too. Top-down sprites use y for left/right; side-view ones (amphipod, skeleton
 * shrimp, mysid, ostracod) use y for down and are mirrored when they face left so they never swim upside down.
 *
 * Level of detail follows the size on screen: a silhouette below [DETAIL_PX], the anatomy above it, and setae, eggs,
 * granules and other fine structure from [FINE_PX]. A scratch [P] is reused so the frame loop allocates little.
 */

/** Draws [m] in body-length units with its head at +x; [l] is its length on screen, px. */
internal fun DrawScope.drawMicroSprite(m: Mote, l: Float, t: Float, look: Look) {
    val lod = when { l >= FINE_PX -> 2; l >= DETAIL_PX -> 1; else -> 0 }
    val px = 1f / l
    if (m.nauplius) {
        nauplius(m, t, look, lod, px)
        return
    }
    when (m.group) {
        MicroGroup.HARPACTICOID -> harpacticoid(m, t, look, lod, px)
        MicroGroup.CALANOID -> calanoid(m, t, look, lod, px)
        MicroGroup.CYCLOPOID -> cyclopoid(m, t, look, lod, px)
        MicroGroup.GAMMARID -> mirrored(cos(m.heading) < 0f) { gammarid(m, t, look, lod, px) }
        MicroGroup.CAPRELLID -> mirrored(m.r1 > 0.5f) { caprellid(m, t, look, lod, px) }
        MicroGroup.MYSID -> mirrored(cos(m.heading) < 0f) { mysid(m, t, look, lod, px) }
        MicroGroup.OSTRACOD -> mirrored(cos(m.heading) < 0f) { ostracod(m, t, look, lod, px) }
        MicroGroup.ISOPOD -> isopod(m, t, look, lod, px)
        MicroGroup.ROTIFER -> rotifer(m, t, look, lod, px)
        MicroGroup.CILIATE -> if (isVorticella(m)) vorticella(m, t, look, lod, px) else euplotes(m, t, look, lod, px)
        MicroGroup.PHYTO -> if (isNanno(m)) nannochloropsis(look, lod, px) else tetraselmis(m, t, look, lod, px)
        MicroGroup.BRISTLEWORM -> bristleworm(m, t, look, lod, px)
        MicroGroup.MICROSNAIL -> microsnail(m, t, look, lod, px)
        MicroGroup.SPAGHETTI_WORM -> spaghettiWorm(m, t, look, lod, px)
        MicroGroup.FORAM -> if (m.r1 < 0.5f) homotrema(m, t, look, lod, px) else peneroplis(m, t, look, lod, px)
        MicroGroup.FLATWORM -> flatworm(m, t, look, lod, px)
    }
}

/** Ciliates on a surface: about a third are Vorticella bells on their stalks, which stay put (§5.3). */
internal fun isVorticella(m: Mote): Boolean = m.group == MicroGroup.CILIATE && m.gait == Gait.STEP && m.r1 > 0.65f

/** Phyto cells: Nannochloropsis (2-3 µm, no flagella, Brownian only) beside the swimming Tetraselmis. */
internal fun isNanno(m: Mote): Boolean = m.group == MicroGroup.PHYTO && m.r1 > 0.6f

// ================================================================== copepods

// Harpacticoid (Tisbe, Tigriopus) from above, head to tail: cephalosome, pedigerous somites 1-4, then the narrow
// urosome (P5 somite, genital double-somite, two abdominal somites and the anal somite). Centre, length, width.
private val HARP_X = floatArrayOf(0.35f, 0.195f, 0.11f, 0.03f, -0.045f, -0.11f, -0.18f, -0.25f, -0.305f, -0.355f)
private val HARP_L = floatArrayOf(0.27f, 0.085f, 0.085f, 0.08f, 0.075f, 0.06f, 0.085f, 0.06f, 0.055f, 0.05f)
private val HARP_W = floatArrayOf(0.27f, 0.265f, 0.25f, 0.23f, 0.205f, 0.16f, 0.155f, 0.135f, 0.12f, 0.11f)

/** Harpacticoid: a tapering cylinder that flexes as it crawls, short antennules, forked furca, red naupliar eye. */
private fun DrawScope.harpacticoid(m: Mote, t: Float, look: Look, lod: Int, px: Float) {
    val base = look.body(m.group)
    // Tigriopus is red-orange with astaxanthin, brightest along the gut; Tisbe is nearly transparent (research 16 §1.3)
    val tigriopus = m.r1 < 0.4f
    val body = when {
        look.night -> base
        tigriopus -> mix(base, TIGRIOPUS, 0.6f)
        else -> fade(base, 0.7f)
    }
    val edge = edgeOf(body, look)
    val limb = limbOf(body, look)
    val walking = m.gait == Gait.CRAWL && m.state == 0
    // the body flexes side to side at ~3 Hz while it crawls (§5.3)
    val flex = if (walking) 0.06f * sin(t * 3f * TWO_PI + m.r1 * 6f) else 0f
    val gravid = m.r2 < 0.5f * look.breeding(m.group)
    val ty = bendY(-0.42f, flex)
    if (lod == 0) {
        bar(body, 0.05f, 0f, -0.4f, ty, 0.12f)
        for (s in PM) bar(limb, -0.4f, ty, -0.56f, ty + s * 0.07f, maxOf(px, 0.03f))
        if (gravid) ov(EGG, -0.26f, bendY(-0.26f, flex), 0.22f, 0.18f)
        ov(body, 0.22f, 0f, 0.62f, 0.27f)
        for (s in PM) bar(limb, 0.46f, s * 0.08f, 0.58f, s * 0.2f, maxOf(px, 0.03f))
        dot(EYE_RED, 0.42f, 0f, 0.04f)
        return
    }
    val seta = setaOf(body, look)
    val hair = maxOf(px, 0.01f)
    // furca: two short caudal rami, each with a pair of long terminal setae
    for (s in PM) {
        val ry = ty + s * 0.035f
        bar(body, -0.37f, ry, -0.44f, ry + s * 0.005f, 0.026f)
        bar(seta, -0.44f, ry, -0.68f, ry + s * 0.09f, hair)
        bar(seta, -0.44f, ry, -0.57f, ry + s * 0.03f, hair)
        if (lod == 2) bar(seta, -0.42f, ry + s * 0.01f, -0.49f, ry + s * 0.07f, hair)
    }
    // swimming legs 1-4: only their tips show past the body, stepping in a metachronal wave
    for (i in 0 until 4) {
        val x = HARP_X[i] - HARP_L[i] * 0.3f
        val y = bendY(x, flex)
        val sw = if (walking) 0.035f * sin(t * 5f * TWO_PI - i * 0.9f) else 0f
        for (s in PM) bar(limb, x, y + s * HARP_W[i] * 0.4f, x - 0.05f + sw, y + s * (HARP_W[i] * 0.5f + 0.035f), 0.018f)
    }
    // the female's single egg sac rides under the urosome and shows past it on both sides
    if (gravid) {
        val ey = bendY(-0.27f, flex)
        ov(EGG, -0.27f, ey, 0.25f, 0.2f)
        if (lod == 2) eggCluster(-0.27f, ey, 3, 3, 0.07f, 0.055f, 0.026f)
    }
    somites(HARP_X, HARP_L, HARP_W, body, edge, flex, px)
    val gut = if (tigriopus && !look.night) fade(TIGRIOPUS_GUT, 0.8f) else fade(look.gut, 0.6f)
    bar(gut, 0.3f, 0f, 0f, bendY(0f, flex), 0.045f)
    bar(gut, 0f, bendY(0f, flex), -0.3f, bendY(-0.3f, flex), 0.035f)
    dot(fade(OIL, 0.8f), 0.16f, 0.055f, 0.024f)
    dot(fade(OIL, 0.8f), 0.07f, -0.045f, 0.019f)
    // rostrum
    P.reset()
    P.moveTo(0.5f, -0.04f)
    P.lineTo(0.56f, 0f)
    P.lineTo(0.5f, 0.04f)
    P.close()
    drawPath(P, body)
    // short 8-segment antennules splayed forward, flicking as it feels its way; the antennae just behind them
    val flick = if (walking) 0.15f * sin(t * 4f + m.r2 * 6f) else 0f
    val n = if (lod == 2) 8 else 4
    for (s in PM) {
        jointed(limb, 0.47f, s * 0.08f, s * (0.8f + flick), n, 0.22f / n, s * 0.07f, 0.022f, seta, if (lod == 2) 0.03f else 0f, s)
        jointed(limb, 0.43f, s * 0.11f, s * 1.35f, 2, 0.045f, -s * 0.4f, 0.018f)
    }
    dot(EYE_RED, 0.43f, 0f, 0.028f)
    dot(GLINT, 0.437f, -0.008f, 0.008f)
}

private val CAL_UX = floatArrayOf(-0.12f, -0.19f, -0.255f, -0.31f)
private val CAL_UL = floatArrayOf(0.075f, 0.06f, 0.055f, 0.05f)
private val CAL_UW = floatArrayOf(0.115f, 0.09f, 0.08f, 0.075f)
/** Segment lines across the calanoid prosome (cephalosome | pedigers 1-4, the 4th and 5th fused). */
private val CAL_SEG = floatArrayOf(0.26f, 0.15f, 0.07f, 0.0f)

/**
 * Calanoid (Parvocalanus): glassy oval prosome with an orange lipid droplet and a blunt rostrum, slender 4-somite
 * urosome, and antennules as long as the body held out at right angles. They snap back along the body for a hop and spread again within ~0.3 s.
 */
private fun DrawScope.calanoid(m: Mote, t: Float, look: Look, lod: Int, px: Float) {
    val body = look.body(m.group)
    // the cuticle is glass; the orange is mostly the lipid store
    val glass = if (look.night) body else fade(body, 0.5f)
    val edge = edgeOf(body, look)
    val limb = limbOf(body, look)
    val a = 1.68f + 1.1f * exp(-m.since * 8f)
    if (lod == 0) {
        bar(glass, -0.05f, 0f, -0.38f, 0f, 0.08f)
        ov(glass, 0.19f, 0f, 0.62f, 0.32f)
        dot(OIL, 0.12f, 0f, 0.07f)
        for (s in PM) bar(limb, 0.42f, s * 0.1f, 0.42f + cos(a) * 0.9f, s * (0.1f + sin(a) * 0.9f), maxOf(px, 0.02f))
        dot(EYE_RED, 0.43f, 0f, 0.035f)
        return
    }
    val seta = setaOf(body, look)
    val hair = maxOf(px, 0.008f)
    // furca with fanned plumose setae
    for (s in PM) {
        bar(glass, -0.34f, s * 0.022f, -0.4f, s * 0.03f, 0.022f)
        for (k in 0 until 4) bar(seta, -0.4f, s * 0.03f, -0.58f - 0.02f * k, s * (0.025f + 0.04f * k), hair)
    }
    if (m.since < 0.3f) {
        // the hop: swimming legs 1-5 sweep back in turn
        for (i in 0 until 5) {
            val x = 0.22f - i * 0.065f
            val ang = 0.6f + 2f * (m.since * 6f - i * 0.12f).coerceIn(0f, 1f)
            for (s in PM) bar(limb, x, s * 0.13f, x + cos(ang) * 0.12f, s * (0.13f + sin(ang) * 0.1f), 0.018f)
        }
    } else {
        // feeding current: the mouthparts vibrate too fast to see, a faint fan beside the head
        val blur = fade(limb, 0.35f)
        val v = 1.35f + 0.35f * sin(t * 40f)
        for (s in PM) {
            bar(blur, 0.36f, s * 0.13f, 0.36f + cos(1f) * 0.1f, s * (0.13f + sin(1f) * 0.1f), 0.014f)
            bar(blur, 0.36f, s * 0.13f, 0.36f + cos(1.7f) * 0.1f, s * (0.13f + sin(1.7f) * 0.1f), 0.014f)
            bar(limb, 0.36f, s * 0.13f, 0.36f + cos(v) * 0.1f, s * (0.13f + sin(v) * 0.1f), 0.014f)
        }
    }
    somites(CAL_UX, CAL_UL, CAL_UW, glass, edge, 0f, px)
    // prosome: one smooth oval with its segment lines faint through the glass
    val st = Stroke(width = maxOf(px, 0.012f))
    ov(glass, 0.19f, 0f, 0.64f, 0.34f)
    drawOval(edge, Offset(0.19f - 0.32f, -0.17f), Size(0.64f, 0.34f), style = st)
    val faint = fade(edge, 0.45f)
    for (bx in CAL_SEG) {
        val u = (bx - 0.19f) / 0.32f
        val hw = 0.16f * sqrt((1f - u * u).coerceAtLeast(0f))
        drawArc(faint, 90f, 180f, false, Offset(bx, -hw), Size(0.06f, 2f * hw), style = st)
    }
    bar(fade(look.gut, 0.55f), 0.34f, 0f, -0.3f, 0f, 0.035f)
    // many Parvocalanus carry a purple patch between the somites of legs 2 and 3 (research 16 §2.3)
    if (m.r2 > 0.4f && !look.night) ov(PURPLE, 0.11f, 0f, 0.05f, 0.2f)
    dot(OIL, 0.1f, 0f, 0.065f)
    dot(GLINT, 0.118f, -0.022f, 0.016f)
    val n = if (lod == 2) 10 else 5
    for (s in PM) {
        val tip = jointed(limb, 0.44f, s * 0.12f, s * a, n, 0.8f / n, s * 0.035f, 0.02f, seta, if (lod == 2) 0.045f else 0f, s)
        if (lod == 2) {
            val ea = s * (a + 0.035f * n)
            bar(seta, tip.x, tip.y, tip.x + cos(ea + s * 0.3f) * 0.12f, tip.y + sin(ea + s * 0.3f) * 0.12f, hair)
            bar(seta, tip.x, tip.y, tip.x + cos(ea - s * 0.2f) * 0.1f, tip.y + sin(ea - s * 0.2f) * 0.1f, hair)
        }
    }
    dot(EYE_RED, 0.44f, 0f, 0.03f)
}

private val CYC_X = floatArrayOf(0.29f, 0.13f, 0.05f, -0.025f, -0.09f, -0.16f, -0.225f, -0.275f, -0.32f)
private val CYC_L = floatArrayOf(0.36f, 0.1f, 0.09f, 0.08f, 0.055f, 0.09f, 0.055f, 0.05f, 0.045f)
private val CYC_W = floatArrayOf(0.4f, 0.33f, 0.27f, 0.21f, 0.13f, 0.14f, 0.1f, 0.09f, 0.085f)

/** Cyclopoid: pear-shaped prosome, antennules about half the body, two egg sacs that trail and swing after a hop. */
private fun DrawScope.cyclopoid(m: Mote, t: Float, look: Look, lod: Int, px: Float) {
    val body = look.body(m.group)
    val edge = edgeOf(body, look)
    val limb = limbOf(body, look)
    val gravid = m.r2 < 0.5f * look.breeding(m.group)
    // the sacs lag the hop and swing back into line
    val swing = 14f * exp(-m.since * 4f) * sin(m.since * 16f)
    if (lod == 0) {
        bar(body, 0f, 0f, -0.38f, 0f, 0.09f)
        if (gravid) for (s in PM) ov(EGG, -0.3f, s * 0.13f, 0.3f, 0.13f)
        ov(body, 0.24f, 0f, 0.55f, 0.38f)
        for (s in PM) bar(limb, 0.45f, s * 0.12f, 0.6f, s * 0.36f, maxOf(px, 0.025f))
        dot(EYE_RED, 0.44f, 0f, 0.04f)
        return
    }
    val seta = setaOf(body, look)
    val hair = maxOf(px, 0.009f)
    for (s in PM) {
        bar(body, -0.35f, s * 0.025f, -0.41f, s * 0.035f, 0.024f)
        bar(seta, -0.41f, s * 0.035f, -0.62f, s * 0.1f, hair)
        bar(seta, -0.41f, s * 0.03f, -0.56f, s * 0.045f, hair)
    }
    if (gravid) for (s in PM) {
        rotate(s * (-24f + swing), Offset(-0.15f, s * 0.06f)) {
            ov(EGG, -0.3f, s * 0.13f, 0.32f, 0.14f)
            if (lod == 2) eggCluster(-0.3f, s * 0.13f, 4, 2, 0.07f, 0.06f, 0.028f)
        }
    }
    somites(CYC_X, CYC_L, CYC_W, body, edge, 0f, px)
    bar(fade(look.gut, 0.6f), 0.36f, 0f, -0.25f, 0f, 0.04f)
    dot(fade(OIL, 0.7f), 0.2f, 0.07f, 0.025f)
    val n = if (lod == 2) 8 else 4
    val flick = 0.1f * sin(t * 2f + m.r1 * 6f)
    for (s in PM) {
        jointed(limb, 0.46f, s * 0.13f, s * (0.75f + flick), n, 0.5f / n, s * 0.12f, 0.022f, seta, if (lod == 2) 0.035f else 0f, s)
        jointed(limb, 0.42f, s * 0.15f, s * 1.4f, 2, 0.05f, -s * 0.3f, 0.018f)
    }
    dot(EYE_RED, 0.45f, 0f, 0.032f)
    dot(GLINT, 0.457f, -0.009f, 0.009f)
}

// nauplius limbs: antennule (uniramous), antenna and mandible (both biramous): base x, base y, length, rest angle
private val NAUP_LX = floatArrayOf(0.28f, 0.14f, 0f)
private val NAUP_LY = floatArrayOf(0.14f, 0.22f, 0.2f)
private val NAUP_LEN = floatArrayOf(0.4f, 0.55f, 0.42f)
private val NAUP_A = floatArrayOf(0.7f, 1.2f, 1.7f)

/** Nauplius: a pear-shaped shield, median red eye, three limb pairs beating fast in a metachronal wave. */
private fun DrawScope.nauplius(m: Mote, t: Float, look: Look, lod: Int, px: Float) {
    val body = look.body(m.group)
    val limb = limbOf(body, look)
    val seta = setaOf(body, look)
    val beat = 0.3f + 0.3f * exp(-m.since * 4f)
    val w = maxOf(px, if (lod == 0) 0.05f else 0.028f)
    for (i in 0 until 3) {
        for (s in PM) {
            val bx = NAUP_LX[i]
            val by = s * NAUP_LY[i]
            val a = s * (NAUP_A[i] + beat * sin(t * 6f * TWO_PI - i * 1.2f + m.r1 * 6f))
            val ex = bx + cos(a) * NAUP_LEN[i]
            val ey = by + sin(a) * NAUP_LEN[i]
            bar(limb, bx, by, ex, ey, w)
            if (lod > 0) {
                if (i > 0) {
                    // the second ramus
                    val a2 = a - s * 0.4f
                    bar(limb, bx + cos(a) * 0.12f, by + sin(a) * 0.12f, bx + cos(a2) * NAUP_LEN[i] * 0.8f, by + sin(a2) * NAUP_LEN[i] * 0.8f, w * 0.7f)
                }
                for (k in -1..1) {
                    val sa = a + k * 0.25f
                    bar(seta, ex, ey, ex + cos(sa) * 0.18f, ey + sin(sa) * 0.18f, maxOf(px, 0.012f))
                }
            }
        }
    }
    if (lod == 0) {
        ov(body, 0f, 0f, 0.8f, 0.58f)
        dot(EYE_RED, 0.28f, 0f, 0.06f)
        return
    }
    for (s in PM) bar(limb, -0.4f, s * 0.035f, -0.52f, s * 0.07f, w * 0.6f)  // caudal spines
    P.reset()
    P.moveTo(0.42f, 0f)
    P.cubicTo(0.42f, 0.3f, 0.05f, 0.34f, -0.15f, 0.22f)
    P.cubicTo(-0.28f, 0.14f, -0.38f, 0.08f, -0.42f, 0.04f)
    P.lineTo(-0.42f, -0.04f)
    P.cubicTo(-0.38f, -0.08f, -0.28f, -0.14f, -0.15f, -0.22f)
    P.cubicTo(0.05f, -0.34f, 0.42f, -0.3f, 0.42f, 0f)
    P.close()
    drawPath(P, body)
    drawPath(P, edgeOf(body, look), style = Stroke(width = maxOf(px, 0.015f)))
    // yolk and gut
    ov(fade(look.gut, 0.45f), -0.02f, 0f, 0.36f, 0.24f)
    dot(EYE_RED, 0.3f, 0f, 0.055f)
}

/** Draws copepod somites tail first, each an oval with its hind margin inked; [flex] bends the urosome sideways. */
private fun DrawScope.somites(xs: FloatArray, ls: FloatArray, ws: FloatArray, body: Color, edge: Color, flex: Float, px: Float) {
    val st = Stroke(width = maxOf(px, 0.012f))
    for (i in xs.indices.reversed()) {
        val len = ls[i] * 1.3f
        val w = ws[i]
        val tl = Offset(xs[i] - len / 2f, bendY(xs[i], flex) - w / 2f)
        drawOval(body, tl, Size(len, w))
        if (i == 0) drawOval(edge, tl, Size(len, w), style = st) else drawArc(edge, 105f, 150f, false, tl, Size(len, w), style = st)
    }
}

/** Sideways offset of a copepod's body at [x] when it flexes: the urosome swings, the head holds its course. */
private fun bendY(x: Float, flex: Float): Float {
    val u = (0.3f - x).coerceAtLeast(0f)
    return flex * u * u * 4f
}

/** A cluster of [nx] by [ny] eggs centred on (x, y), granular at high power. */
private fun DrawScope.eggCluster(x: Float, y: Float, nx: Int, ny: Int, dx: Float, dy: Float, r: Float) {
    val egg = shade(EGG, 0.8f)
    val shine = fade(Color.White, 0.25f)
    for (i in 0 until nx) for (j in 0 until ny) {
        val ex = x + (i - (nx - 1) / 2f) * dx
        val ey = y + (j - (ny - 1) / 2f) * dy
        dot(egg, ex, ey, r)
        dot(shine, ex - r * 0.3f, ey - r * 0.3f, r * 0.3f)
    }
}

// ================================================================== peracarids

// Gammarid, head to tail: head, pereonites 1-7, pleonites 1-3, urosomites 1-3, telson. Relative length and depth.
private val AMP_LEN = floatArrayOf(0.1f, 0.075f, 0.075f, 0.075f, 0.08f, 0.085f, 0.085f, 0.085f, 0.09f, 0.09f, 0.085f, 0.05f, 0.045f, 0.04f, 0.03f)
private val AMP_DEPTH = floatArrayOf(0.13f, 0.15f, 0.16f, 0.17f, 0.18f, 0.18f, 0.18f, 0.175f, 0.18f, 0.17f, 0.15f, 0.1f, 0.085f, 0.07f, 0.04f)
/** Where each gammarid segment starts along the body, 0 (head) to 1 (telson). */
private val AMP_S: FloatArray = FloatArray(AMP_LEN.size + 1).also { s ->
    val total = AMP_LEN.sum()
    var acc = 0f
    for (i in AMP_LEN.indices) {
        s[i] = acc / total
        acc += AMP_LEN[i]
    }
    s[AMP_LEN.size] = 1f
}
/** Coxal plate depth per pereonite (index 1-7): big shields on 1-4, smaller on 5-7. */
private val COX_H = floatArrayOf(0f, 0.1f, 0.105f, 0.11f, 0.115f, 0.075f, 0.06f, 0.05f)

/** Gammarid dorsal line: a bowed back with the pleon curling under. */
private fun ampX(s: Float): Float {
    val u = (s - 0.72f).coerceAtLeast(0f)
    return 0.48f - 0.98f * s + 0.4f * u * u
}

private fun ampY(s: Float): Float {
    val u = (s - 0.68f).coerceAtLeast(0f)
    return -0.1f - 0.08f * sin(PI_F * s) + 0.9f * u * u
}

/**
 * Gammarid amphipod from the side: laterally compressed and bowed, head with a kidney eye and two pairs of antennae,
 * seven pereonites with coxal plates, two subchelate gnathopods and five walking legs (3-4 forward, 5-7 back) cycling
 * at 5-8 Hz, three pairs of pleopods (a blur when it swims) and three pairs of uropods.
 */
private fun DrawScope.gammarid(m: Mote, t: Float, look: Look, lod: Int, px: Float) {
    val body = look.body(m.group)
    val edge = edgeOf(body, look)
    val limb = limbOf(body, look)
    val swim = m.state == 1
    val clock = if (m.state == 0) t * 6.5f * TWO_PI else m.r1 * 20f
    val wave = 0.12f * sin(t * 3f + m.r1 * 6f)
    val hs = AMP_S[1] * 0.5f
    val hx = ampX(hs)
    val hy = ampY(hs)
    if (lod == 0) {
        val w = maxOf(px, 0.025f)
        bar(limb, hx + 0.04f, hy + 0.02f, hx + 0.4f, hy - 0.15f + wave * 0.3f, w)
        bar(limb, hx + 0.03f, hy + 0.07f, hx + 0.33f, hy + 0.04f - wave * 0.3f, w)
        for (j in 2..6 step 2) {
            val sm = (AMP_S[j] + AMP_S[j + 1]) / 2f
            val bx = ampX(sm)
            val by = ampY(sm) + AMP_DEPTH[j]
            bar(limb, bx, by, bx + (if (j < 5) 0.05f else -0.07f) + 0.03f * cos(clock - j), by + 0.15f, w)
        }
        for (i in AMP_LEN.indices.reversed()) {
            val sm = (AMP_S[i] + AMP_S[i + 1]) / 2f
            val len = (ampX(AMP_S[i]) - ampX(AMP_S[i + 1])) * 1.5f
            drawOval(body, Offset(ampX(sm) - len / 2f, ampY(sm)), Size(len, AMP_DEPTH[i]))
        }
        dot(EYE_DARK, hx, hy + 0.05f, 0.03f)
        return
    }
    val seta = setaOf(body, look)
    val w = maxOf(px, 0.016f)
    val st = Stroke(width = maxOf(px, 0.01f))
    // the far side's legs, half a cycle out of step and in shadow
    val far = shade(limb, 0.7f)
    for (j in 1..7) {
        val sm = (AMP_S[j] + AMP_S[j + 1]) / 2f
        ampLeg(j, ampX(sm) + 0.012f, ampY(sm) + AMP_DEPTH[j] - 0.03f + COX_H[j] * 0.8f, clock + PI_F, swim, far, 0, w)
    }
    // antennae: A1 above (longer, with a small accessory flagellum), A2 below with a stout peduncle
    val n1 = if (lod == 2) 12 else 6
    val n2 = if (lod == 2) 9 else 5
    val sl = if (lod == 2) 0.02f else 0f
    val e1 = jointed(limb, hx + 0.04f, hy + 0.025f, -0.55f + wave, 3, 0.035f, 0.05f, 0.03f)
    jointed(limb, e1.x, e1.y, -0.4f + wave, n1, 0.3f / n1, 0.03f, 0.018f, seta, sl, -1f)
    if (lod == 2) jointed(limb, e1.x, e1.y, -0.15f + wave, 3, 0.02f, 0f, 0.01f)
    val e2 = jointed(limb, hx + 0.03f, hy + 0.075f, -0.05f - wave * 0.5f, 3, 0.04f, 0.04f, 0.035f)
    jointed(limb, e2.x, e2.y, 0.07f - wave * 0.5f, n2, 0.24f / n2, 0.06f, 0.016f, seta, sl, 1f)
    // pleopods: a slow ventilating beat, a blur when it swims
    val amp = if (swim) 0.7f else 0.25f
    val freq = if (swim) 9f else 2f
    for (i in 8..10) {
        val sm = (AMP_S[i] + AMP_S[i + 1]) / 2f
        val bx = ampX(sm)
        val by = ampY(sm) + AMP_DEPTH[i] - 0.01f
        if (swim) {
            val blur = fade(limb, 0.25f)
            for (b in PM) bar(blur, bx, by, bx + cos(1.45f + b * amp) * 0.11f, by + sin(1.45f + b * amp) * 0.11f, 0.03f)
        }
        val ang = 1.45f + amp * sin(t * freq * TWO_PI - (i - 8) * 0.9f)
        val ex = bx + cos(ang) * 0.05f
        val ey = by + sin(ang) * 0.05f
        bar(limb, bx, by, ex, ey, w)
        for (r in PM) {
            val ra = ang + r * 0.18f
            val fx = ex + cos(ra) * 0.06f
            val fy = ey + sin(ra) * 0.06f
            bar(limb, ex, ey, fx, fy, w * 0.6f)
            if (lod == 2) bar(seta, fx, fy, fx + cos(ra + 0.5f) * 0.03f, fy + sin(ra + 0.5f) * 0.03f, maxOf(px, 0.005f))
        }
    }
    // uropods 1-3, each a peduncle and two spiny rami; uropod 3 with long setae
    for (i in 11..13) {
        val sm = (AMP_S[i] + AMP_S[i + 1]) / 2f
        val bx = ampX(sm)
        val by = ampY(sm) + AMP_DEPTH[i] * 0.7f
        val len = when (i) { 11 -> 0.12f; 12 -> 0.09f; else -> 0.075f }
        val ua = 2.55f + 0.1f * (i - 11)
        val ex = bx + cos(ua) * len * 0.45f
        val ey = by + sin(ua) * len * 0.45f
        bar(limb, bx, by, ex, ey, w * 1.3f)
        for (r in PM) {
            val ra = ua + r * 0.12f
            val fx = ex + cos(ra) * len * 0.6f
            val fy = ey + sin(ra) * len * 0.6f
            bar(limb, ex, ey, fx, fy, w * 0.8f)
            if (lod == 2) {
                val sa = ra + r * 0.6f
                val sLen = if (i == 13) 0.05f else 0.015f
                bar(seta, fx, fy, fx + cos(sa) * sLen, fy + sin(sa) * sLen, maxOf(px, 0.005f))
                bar(seta, (ex + fx) / 2f, (ey + fy) / 2f, (ex + fx) / 2f + cos(sa) * 0.015f, (ey + fy) / 2f + sin(sa) * 0.015f, maxOf(px, 0.005f))
            }
        }
    }
    // body: segments tail first so each tergite overlaps the one behind it
    for (i in AMP_LEN.indices.reversed()) {
        val sm = (AMP_S[i] + AMP_S[i + 1]) / 2f
        val len = (ampX(AMP_S[i]) - ampX(AMP_S[i + 1])) * 1.4f
        val d = AMP_DEPTH[i]
        val tl = Offset(ampX(sm) - len / 2f, ampY(sm))
        drawOval(body, tl, Size(len, d))
        if (i == 0) drawOval(edge, tl, Size(len, d), style = st) else drawArc(edge, 100f, 160f, false, tl, Size(len, d), style = st)
    }
    // the gut through the translucent side, and the shine along the back in reflected light
    val gut = fade(look.gut, if (look.bright) 0.6f else 0.35f)
    val shine = fade(mix(body, Color.White, 0.4f), 0.5f)
    for (i in 1 until 10) {
        val a0 = (AMP_S[i] + AMP_S[i + 1]) / 2f
        val a1 = (AMP_S[i + 1] + AMP_S[i + 2]) / 2f
        bar(gut, ampX(a0), ampY(a0) + AMP_DEPTH[i] * 0.45f, ampX(a1), ampY(a1) + AMP_DEPTH[i + 1] * 0.45f, 0.035f)
        if (!look.bright) bar(shine, ampX(a0), ampY(a0) + 0.015f, ampX(a1), ampY(a1) + 0.015f, 0.012f)
    }
    // reef gammarids often carry discrete white spots on the pereon (research 16 §4.3)
    if (lod == 2 && m.r2 > 0.5f && !look.bright) {
        val spot = fade(Color(0xFFF4F1E6), 0.85f)
        for (j in 2..7 step 2) {
            val sm = (AMP_S[j] + AMP_S[j + 1]) / 2f
            dot(spot, ampX(sm), ampY(sm) + AMP_DEPTH[j] * 0.3f, 0.016f)
        }
    }
    for (j in 1..7) {
        val sm = (AMP_S[j] + AMP_S[j + 1]) / 2f
        ampLeg(j, ampX(sm), ampY(sm) + AMP_DEPTH[j] - 0.02f + COX_H[j] * 0.85f, clock, swim, limb, lod, w)
    }
    // a brooding female's eggs show between the coxae
    if (m.r2 < 0.5f * look.breeding(m.group)) {
        val sm = AMP_S[4]
        ov(EGG, ampX(sm), ampY(sm) + AMP_DEPTH[3] + 0.05f, 0.24f, 0.08f)
        if (lod == 2) eggCluster(ampX(sm), ampY(sm) + AMP_DEPTH[3] + 0.05f, 5, 2, 0.04f, 0.035f, 0.017f)
    }
    // coxal plates, the last first so each overlaps the one behind
    val coxa = shade(body, 0.92f)
    for (j in 7 downTo 1) {
        val sm = (AMP_S[j] + AMP_S[j + 1]) / 2f
        val len = (ampX(AMP_S[j]) - ampX(AMP_S[j + 1])) * 1.2f
        val tl = Offset(ampX(sm) - len / 2f - 0.005f, ampY(sm) + AMP_DEPTH[j] - 0.025f)
        drawOval(coxa, tl, Size(len, COX_H[j]))
        drawOval(edge, tl, Size(len, COX_H[j]), style = st)
    }
    // telson: a small cleft flap
    val ts = (AMP_S[14] + 1f) / 2f
    for (r in PM) ov(body, ampX(ts) - 0.012f, ampY(ts) + 0.02f + r * 0.006f, 0.035f, 0.016f)
    // kidney-shaped eye: a dark oval bitten by the head
    val ex = hx - 0.005f
    val ey = hy + 0.05f
    ov(EYE_DARK, ex, ey, 0.034f, 0.058f)
    dot(body, ex + 0.014f, ey + 0.016f, 0.011f)
    dot(GLINT, ex - 0.006f, ey - 0.015f, 0.006f)
}

/** One gammarid pereopod [j] (1-7) hanging from its coxa at (bx, by), stepping on [clock]. */
private fun DrawScope.ampLeg(j: Int, bx: Float, by: Float, clock: Float, swim: Boolean, c: Color, lod: Int, w: Float) {
    val p = clock - j * 0.9f
    if (j <= 2) {
        // gnathopods: basis down, short carpus forward, swollen propodus with the dactyl folded on its palm
        val g = if (j == 2) 1.25f else 1f
        val kx = bx + 0.008f * sin(clock * 0.3f + j)
        val ky = by + 0.065f * g
        bar(c, bx, by, kx, ky, w * 1.2f)
        bar(c, kx, ky, kx + 0.035f * g, ky + 0.022f * g, w)
        ov(c, kx + 0.07f * g, ky + 0.025f * g, 0.075f * g, 0.042f * g)
        bar(c, kx + 0.105f * g, ky + 0.02f * g, kx + 0.06f * g, ky + 0.048f * g, w * 0.6f)
        return
    }
    val dx = if (swim) 0f else 0.03f * cos(p)
    val lift = if (swim) 0.05f else 0.022f * sin(p).coerceAtLeast(0f)
    val fwd = j <= 4
    val sx = if (fwd) 1f else -1f
    val k = 0.9f + 0.06f * (j - 3)
    if (!fwd) ov(c, bx - 0.015f, by + 0.035f, 0.05f, 0.075f)  // the expanded basis of pereopods 5-7
    val kx = bx + sx * 0.035f * k
    val ky = by + 0.08f * k - lift * 0.5f
    val fx = bx + sx * 0.06f * k + dx
    val fy = by + 0.17f * k - lift
    bar(c, bx, by, kx, ky, w)
    bar(c, kx, ky, fx, fy, w * 0.8f)
    bar(c, fx, fy, fx + sx * 0.018f, fy + 0.008f, w * 0.5f)
    if (lod == 2) bar(c, kx, ky, kx - sx * 0.015f, ky + 0.012f, w * 0.35f)
}

/** Skeleton shrimp body segments from the clinging rear: pereonites 7..1 and head; boundaries and thickness. */
private val CAP_B = floatArrayOf(0f, 0.06f, 0.13f, 0.23f, 0.41f, 0.59f, 0.8f, 1f)
private val CAP_W = floatArrayOf(0.035f, 0.035f, 0.035f, 0.03f, 0.03f, 0.042f, 0.045f)

/**
 * Skeleton shrimp from the side, anchored at its rear (the origin) by three pairs of clinging legs: a jointed stick
 * with gills on pereonites 3-4, a mantis-like second gnathopod that snaps every few seconds, and two pairs of antennae.
 */
private fun DrawScope.caprellid(m: Mote, t: Float, look: Look, lod: Int, px: Float) {
    val body = look.body(m.group)
    val edge = edgeOf(body, look)
    val limb = limbOf(body, look)
    val snap = (t + m.r1 * 7f) % (2f + 8f * m.r2) < 0.15f
    val wave = 0.1f * sin(t * 2f + m.r2 * 6f)
    if (lod == 0) {
        val w = maxOf(px, 0.03f)
        bar(body, 0f, 0f, 0.95f, 0f, maxOf(px, 0.045f))
        dot(body, 0.95f, 0f, 0.04f)
        val ga = if (snap) -0.1f else 0.5f
        bar(limb, 0.64f, 0.02f, 0.7f, 0.1f, w)
        bar(limb, 0.7f, 0.1f, 0.7f + cos(ga) * 0.15f, 0.1f - sin(ga) * 0.05f, w)
        bar(limb, 0.98f, -0.01f, 1.25f, -0.1f + wave, w)
        for (x in CAP_LEG_X) bar(limb, x, 0f, x - 0.05f, 0.1f, w)
        return
    }
    val seta = setaOf(body, look)
    val w = maxOf(px, 0.012f)
    val st = Stroke(width = maxOf(px, 0.008f))
    // clinging legs (pereopods 5-7): far side first, then near, each ending in a hooked dactyl on the perch
    val far = shade(limb, 0.7f)
    for (pass in 0..1) {
        val c = if (pass == 0) far else limb
        val o = if (pass == 0) 0.015f else 0f
        for (k in 0 until 3) {
            val bx = CAP_LEG_X[k] + o
            val kx = bx + 0.02f
            val fx = bx - 0.06f
            bar(c, bx, 0.02f - o, kx, 0.07f - o, w * 1.3f)
            bar(c, kx, 0.07f - o, fx, 0.1f - o, w)
            bar(c, fx, 0.1f - o, fx - 0.02f, 0.085f - o, w * 0.6f)
        }
    }
    // antennae: A1 long and jointed, A2 shorter with a fringe of swimming setae
    val e1 = jointed(limb, 0.99f, capY(0.99f) - 0.015f, -0.35f + wave, 3, 0.05f, 0.04f, 0.02f)
    jointed(limb, e1.x, e1.y, -0.2f + wave, if (lod == 2) 8 else 4, if (lod == 2) 0.02f else 0.04f, 0.04f, 0.012f)
    jointed(limb, 0.985f, capY(0.985f) + 0.012f, 0.25f - wave * 0.5f, 3, 0.045f, 0.1f, 0.016f, seta, if (lod == 2) 0.025f else 0f, 1f)
    // body: thin articulated stick, slightly bowed
    for (i in 0 until 7) bar(body, CAP_B[i], capY(CAP_B[i]), CAP_B[i + 1], capY(CAP_B[i + 1]), CAP_W[i])
    bar(body, -0.03f, 0f, 0f, 0f, 0.02f)  // the vestigial abdomen
    for (i in 1 until 7) {
        val x = CAP_B[i]
        bar(edge, x, capY(x) - CAP_W[i] * 0.5f, x, capY(x) + CAP_W[i] * 0.5f, maxOf(px, 0.006f))
    }
    // dark bands across the pereon, the usual camouflage (research 16 §5.3)
    val band = fade(shade(body, 0.45f), 0.8f)
    for (i in 3..5) {
        val x = (CAP_B[i] + CAP_B[i + 1]) / 2f
        bar(band, x - 0.02f, capY(x), x + 0.02f, capY(x), CAP_W[i] * 1.05f)
    }
    ov(body, 0.94f, capY(0.94f), 0.12f, 0.07f)
    drawOval(edge, Offset(0.88f, capY(0.94f) - 0.035f), Size(0.12f, 0.07f), style = st)
    dot(EYE_DARK, 0.965f, capY(0.965f) - 0.01f, 0.013f)
    // gills: two translucent sacs hanging from pereonites 3 and 4
    val gill = fade(mix(body, Color.White, 0.35f), 0.6f)
    for (gx in CAP_GILL_X) {
        ov(gill, gx, 0.055f, 0.075f, 0.04f)
        drawOval(edge, Offset(gx - 0.0375f, 0.035f), Size(0.075f, 0.04f), style = st)
    }
    if (m.r2 < 0.5f * look.breeding(m.group)) {
        ov(EGG, 0.41f, 0.06f, 0.12f, 0.07f)
        if (lod == 2) eggCluster(0.41f, 0.06f, 3, 2, 0.03f, 0.025f, 0.012f)
    }
    // gnathopod 1: small, held under the head
    bar(limb, 0.84f, 0.025f, 0.86f, 0.06f, w)
    ov(limb, 0.895f, 0.065f, 0.06f, 0.025f)
    // gnathopod 2: raised like a mantis, the dactyl open until it strikes
    bar(limb, 0.64f, 0.03f, 0.7f, 0.1f, w * 1.5f)
    rotate(if (snap) -25f else -8f, Offset(0.7f, 0.1f)) {
        ov(limb, 0.8f, 0.09f, 0.2f, 0.055f)
        drawOval(edge, Offset(0.7f, 0.0625f), Size(0.2f, 0.055f), style = st)
        if (lod == 2) for (k in 0 until 4) {
            val x = 0.73f + k * 0.035f
            bar(edge, x, 0.115f, x - 0.008f, 0.131f, maxOf(px, 0.006f))
        }
        val da = if (snap) PI_F else PI_F - 0.6f
        bar(limb, 0.895f, 0.095f, 0.895f + cos(da) * 0.15f, 0.095f + sin(da) * 0.15f, w * 0.8f)
    }
}

/** Clinging legs (pereopods 5-7) and the gills (pereonites 3-4) along the skeleton shrimp. */
private val CAP_LEG_X = floatArrayOf(0.18f, 0.095f, 0.03f)
private val CAP_GILL_X = floatArrayOf(0.5f, 0.32f)

private fun capY(x: Float): Float = -0.025f * sin(PI_F * x)

private val MYS_X = floatArrayOf(-0.02f, -0.1f, -0.17f, -0.24f, -0.31f, -0.38f)
private val MYS_L = floatArrayOf(0.08f, 0.075f, 0.07f, 0.07f, 0.07f, 0.075f)
private val MYS_D = floatArrayOf(0.12f, 0.11f, 0.1f, 0.09f, 0.08f, 0.065f)

/**
 * Mysid from the side: glassy, carapace over the thorax, big stalked eyes, an antennal scale, eight pairs of feathery
 * thoracic exopods fluttering to hold station, and a tail fan with a statocyst on each inner uropod.
 */
private fun DrawScope.mysid(m: Mote, t: Float, look: Look, lod: Int, px: Float) {
    val body = look.body(m.group)
    val glass = if (look.bright) body else fade(body, 0.6f)
    val edge = edgeOf(body, look)
    val limb = limbOf(body, look)
    val eye = if (look.night) EYE_GLOW else EYE_DARK
    if (lod == 0) {
        ov(glass, 0f, 0f, 0.9f, 0.13f)
        for (s in PM) bar(glass, -0.42f, 0f, -0.56f, s * 0.05f, maxOf(px, 0.04f))
        for (s in PM) dot(eye, 0.47f, s * 0.06f, 0.04f)   // two stalked eyes
        for (s in PM) dot(EYE_DARK, -0.44f, s * 0.015f, maxOf(px * 0.8f, 0.012f))
        return
    }
    val seta = setaOf(body, look)
    val hair = maxOf(px, 0.006f)
    val st = Stroke(width = maxOf(px, 0.008f))
    // A2: a long flagellum sweeping down and back under the body
    val sway = 0.06f * sin(t * 1.3f + m.r1 * 6f)
    val e2 = jointed(limb, 0.47f, 0f, 0.2f, 2, 0.03f, 0f, 0.016f)
    jointed(limb, e2.x, e2.y, 0.15f + sway, if (lod == 2) 12 else 6, if (lod == 2) 0.05f else 0.1f, if (lod == 2) 0.17f else 0.34f, 0.01f)
    // thoracic limbs: short endopods and the feathery exopods beating in a metachronal wave at ~6 Hz
    for (i in 0 until 8) {
        val x = 0.38f - i * 0.045f
        jointed(limb, x, 0.055f, 1.35f, 3, 0.035f, 0.3f, 0.012f)
        val ang = 2f + 0.55f * sin(t * 6f * TWO_PI - i * 0.8f + m.r1 * 6f)
        val ex = x + cos(ang) * 0.12f
        val ey = 0.055f + sin(ang) * 0.12f
        if (lod == 1) bar(fade(limb, 0.3f), x, 0.055f, x + cos(ang + 0.4f) * 0.11f, 0.055f + sin(ang + 0.4f) * 0.11f, 0.02f)
        bar(limb, x, 0.055f, ex, ey, 0.012f)
        if (lod == 2) for (k in 1..3) {
            val fx = x + cos(ang) * 0.03f * k
            val fy = 0.055f + sin(ang) * 0.03f * k
            bar(seta, fx, fy, fx + cos(ang + 1f) * 0.03f, fy + sin(ang + 1f) * 0.03f, hair)
            bar(seta, fx, fy, fx + cos(ang - 1f) * 0.03f, fy + sin(ang - 1f) * 0.03f, hair)
        }
    }
    // small pleopods under the abdomen
    for (i in 0 until 5) bar(limb, MYS_X[i], 0.04f, MYS_X[i] - 0.025f, 0.075f, 0.01f)
    // tail fan: outer exopods, inner endopods each carrying a statocyst (a clear vesicle with a dark statolith)
    for (s in PM) {
        rotate(s * 26f, Offset(-0.41f, 0f)) {
            ov(glass, -0.495f, 0f, 0.17f, 0.045f)
            drawOval(edge, Offset(-0.58f, -0.0225f), Size(0.17f, 0.045f), style = st)
            if (lod == 2) for (k in 0 until 4) {
                val x = -0.5f - k * 0.02f
                bar(seta, x, 0.02f, x - 0.015f, 0.04f, hair)
            }
        }
        rotate(s * 12f, Offset(-0.41f, 0f)) {
            ov(glass, -0.485f, 0f, 0.15f, 0.04f)
            drawOval(edge, Offset(-0.56f, -0.02f), Size(0.15f, 0.04f), style = st)
            dot(fade(Color.White, 0.55f), -0.435f, 0f, 0.014f)
            dot(EYE_DARK, -0.435f, 0f, 0.007f)
        }
    }
    P.reset()
    P.moveTo(-0.41f, -0.02f)
    P.lineTo(-0.53f, -0.008f)
    P.lineTo(-0.53f, 0.008f)
    P.lineTo(-0.41f, 0.02f)
    P.close()
    drawPath(P, glass)
    drawPath(P, edge, style = st)
    // abdomen, tail first
    for (i in MYS_X.indices.reversed()) {
        val len = MYS_L[i] * 1.3f
        val tl = Offset(MYS_X[i] - len / 2f, -0.005f - MYS_D[i] / 2f)
        drawOval(glass, tl, Size(len, MYS_D[i]))
        drawArc(edge, 100f, 160f, false, tl, Size(len, MYS_D[i]), style = st)
    }
    // a brooding female's marsupium under the rear of the thorax
    if (m.r2 < 0.5f * look.breeding(m.group)) {
        ov(fade(EGG, 0.8f), 0.08f, 0.07f, 0.16f, 0.08f)
        if (lod == 2) eggCluster(0.08f, 0.07f, 4, 2, 0.032f, 0.03f, 0.013f)
    }
    // carapace and rostrum
    ov(glass, 0.22f, -0.01f, 0.5f, 0.15f)
    drawOval(edge, Offset(-0.03f, -0.085f), Size(0.5f, 0.15f), style = st)
    P.reset()
    P.moveTo(0.44f, -0.07f)
    P.lineTo(0.5f, -0.05f)
    P.lineTo(0.44f, -0.04f)
    P.close()
    drawPath(P, glass)
    bar(fade(look.gut, 0.5f), 0.3f, -0.01f, -0.38f, -0.005f, 0.025f)
    if (lod == 2) {
        // star-shaped melanophores along the ventral abdomen
        val mel = Color(0xCC3A2A20)
        for (x in MYS_X) {
            dot(mel, x, 0.03f, 0.008f)
            for (k in 0 until 4) {
                val a = k * 1.6f + x * 10f
                bar(mel, x, 0.03f, x + cos(a) * 0.018f, 0.03f + sin(a) * 0.018f, hair)
            }
        }
    }
    // antennal scale: a flat setose blade forward of the head
    ov(glass, 0.56f, 0f, 0.16f, 0.03f)
    drawOval(edge, Offset(0.48f, -0.015f), Size(0.16f, 0.03f), style = st)
    if (lod == 2) for (k in 0 until 5) {
        val x = 0.52f + k * 0.02f
        bar(seta, x, 0.015f, x + 0.01f, 0.03f, hair)
    }
    // antennules: a short peduncle and two flagella
    val e1 = jointed(limb, 0.47f, -0.05f, -0.35f, 3, 0.03f, 0f, 0.016f)
    jointed(limb, e1.x, e1.y, -0.25f + sway, 6, 0.05f, 0.02f, 0.01f)
    jointed(limb, e1.x, e1.y, -0.05f - sway, 5, 0.045f, 0.03f, 0.01f)
    // stalked eyes, which shine in a torch beam
    bar(glass, 0.43f, -0.045f, 0.49f, -0.075f, 0.025f)
    if (look.night) dot(fade(EYE_GLOW, 0.3f), 0.5f, -0.08f, 0.07f)
    dot(eye, 0.5f, -0.08f, 0.04f)
    if (!look.night) dot(GLINT, 0.49f, -0.09f, 0.01f)
}

/** Ostracod from the side: a bivalved bean, antennae rowing out of the gape while it moves, shut tight at rest. */
private fun DrawScope.ostracod(m: Mote, t: Float, look: Look, lod: Int, px: Float) {
    val body = look.body(m.group)
    val edge = edgeOf(body, look)
    val limb = limbOf(body, look)
    val active = m.gait == Gait.HOP || m.state == 0
    // swimmers tumble as they row
    val wob = if (m.gait == Gait.HOP) 10f * sin(t * 2f + m.r1 * 6f) else 0f
    rotate(wob, Offset.Zero) {
        if (active) {
            val row = 0.5f * sin(t * 5f * TWO_PI + m.r1 * 6f)
            val w = maxOf(px, 0.025f)
            // A1 up and forward, A2 rowing with its fan of natatory setae, walking legs and furca from the gape
            jointed(limb, 0.44f, -0.1f, -0.6f + row * 0.3f, if (lod == 0) 1 else 4, if (lod == 0) 0.22f else 0.055f, 0.1f, w, setaOf(body, look), if (lod == 2) 0.03f else 0f, -1f)
            val a2 = jointed(limb, 0.45f, 0.06f, 0.15f + row, if (lod == 0) 1 else 3, if (lod == 0) 0.18f else 0.06f, 0.1f, w)
            if (lod > 0) for (k in 0 until 4) {
                val sa = 0.15f + row + 0.3f + k * 0.15f
                bar(setaOf(body, look), a2.x, a2.y, a2.x + cos(sa) * 0.2f, a2.y + sin(sa) * 0.2f, maxOf(px, 0.008f))
            }
            if (lod > 0) {
                for (k in 0 until 2) {
                    val bx = 0.1f - k * 0.15f
                    val step = 0.03f * sin(t * 8f - k * 1.5f)
                    bar(limb, bx, 0.2f, bx + 0.03f + step, 0.31f, w * 0.8f)
                }
                bar(limb, -0.28f, 0.2f, -0.33f, 0.28f, w * 0.7f)  // furca
            }
        }
        ostracodShell()
        drawPath(P, body)
        val st = Stroke(width = maxOf(px, 0.015f))
        drawPath(P, edge, style = st)
        if (lod > 0) {
            // the calcified inner lamella shows as a second rim inside the margin
            withTransform({ scale(0.86f, 0.8f, Offset(0f, -0.02f)) }) {
                drawPath(P, fade(mix(body, Color.White, 0.35f), 0.5f), style = st)
            }
            if (!look.bright) ov(fade(Color.White, 0.22f), 0.1f, -0.14f, 0.35f, 0.08f)
            val scar = fade(shade(body, 0.6f), 0.8f)
            for (k in 0 until 5) dot(scar, -0.04f + (k % 2) * 0.035f, -0.02f + k * 0.018f, 0.014f)
            if (lod == 2) {
                // pitted valve surface
                val pit = fade(edge, 0.25f)
                for (k in 0 until 14) dot(pit, -0.35f + 0.7f * h01(k, m.r1), -0.2f + 0.36f * h01(k + 31, m.r2), 0.01f)
            }
            // podocopids have a small median naupliar eye, not the big black eyes of cypridinids
            dot(OSTRACOD_EYE, 0.32f, -0.09f, 0.013f)
        }
    }
}

/** The ostracod's bean: convex dorsal margin, straight ventral one, into [P]. */
private fun ostracodShell() {
    P.reset()
    P.moveTo(0.5f, 0f)
    P.cubicTo(0.48f, -0.3f, -0.3f, -0.36f, -0.49f, -0.04f)
    P.cubicTo(-0.53f, 0.12f, -0.4f, 0.25f, -0.15f, 0.24f)
    P.cubicTo(0.05f, 0.23f, 0.1f, 0.17f, 0.25f, 0.2f)
    P.cubicTo(0.42f, 0.22f, 0.52f, 0.12f, 0.5f, 0f)
    P.close()
}

private val ISO_X = floatArrayOf(0.34f, 0.26f, 0.18f, 0.1f, 0.02f, -0.06f, -0.14f)
private val ISO_W = floatArrayOf(0.36f, 0.4f, 0.42f, 0.42f, 0.41f, 0.38f, 0.34f)
private val ISO_PX = floatArrayOf(-0.2f, -0.225f, -0.25f, -0.275f, -0.3f)
private val ISO_PW = floatArrayOf(0.3f, 0.29f, 0.27f, 0.25f, 0.23f)

/**
 * Isopod (cirolanid-like) from above: flattened oval, head with lateral eyes, 7 pereonites with pointed epimera, 5
 * short pleonites, a shield-shaped pleotelson flanked by the uropods, and 7 leg pairs walking in a metachronal wave.
 */
private fun DrawScope.isopod(m: Mote, t: Float, look: Look, lod: Int, px: Float) {
    val body = look.body(m.group)
    val edge = edgeOf(body, look)
    val limb = limbOf(body, look)
    val walking = m.state == 0
    val clock = if (walking) t * 6f * TWO_PI else m.r1 * 20f
    val flick = 0.12f * sin(t * 2.5f + m.r1 * 6f)
    val w = maxOf(px, 0.018f)
    // legs under the margins: 1-3 reach forward, 4-7 back
    for (i in 0 until 7) {
        val hw = ISO_W[i] / 2f
        val fwd = if (i < 3) 0.05f else -0.05f
        val dx = if (walking) 0.03f * cos(clock - i * 0.8f) else 0f
        for (s in PM) {
            val sdx = if (s > 0f) dx else -dx
            bar(limb, ISO_X[i], s * hw * 0.8f, ISO_X[i] + fwd * 0.5f, s * (hw + 0.05f), w)
            bar(limb, ISO_X[i] + fwd * 0.5f, s * (hw + 0.05f), ISO_X[i] + fwd + sdx, s * (hw + 0.1f), w * 0.8f)
        }
    }
    // antennae: A1 short, A2 long and curving back past the sides
    for (s in PM) {
        jointed(limb, 0.5f, s * 0.05f, s * 0.45f, if (lod == 2) 3 else 2, 0.12f / (if (lod == 2) 3 else 2), 0f, w)
        val e2 = jointed(limb, 0.49f, s * 0.08f, s * (0.7f + flick), 3, 0.045f, s * 0.15f, w * 1.2f)
        jointed(limb, e2.x, e2.y, s * (1.1f + flick), if (lod == 2) 8 else 4, if (lod == 2) 0.035f else 0.07f, s * 0.1f, w * 0.8f)
    }
    // uropods: endopod and exopod blades either side of the pleotelson
    for (s in PM) {
        ov(body, -0.33f, s * 0.12f, 0.06f, 0.04f)
        ov(shade(body, 0.9f), -0.43f, s * 0.165f, 0.14f, 0.045f)
        ov(body, -0.44f, s * 0.12f, 0.15f, 0.05f)
    }
    if (lod == 0) {
        ov(body, 0.05f, 0f, 0.95f, 0.42f)
        val seg = fade(edge, 0.6f)
        for (i in 1..5) bar(seg, 0.4f - i * 0.13f, -0.17f, 0.4f - i * 0.13f, 0.17f, maxOf(px, 0.02f))
        for (s in PM) dot(EYE_DARK, 0.44f, s * 0.1f, 0.03f)
        return
    }
    val st = Stroke(width = maxOf(px, 0.012f))
    // pleotelson
    P.reset()
    P.moveTo(-0.3f, -0.12f)
    P.cubicTo(-0.38f, -0.13f, -0.47f, -0.08f, -0.5f, 0f)
    P.cubicTo(-0.47f, 0.08f, -0.38f, 0.13f, -0.3f, 0.12f)
    P.close()
    drawPath(P, body)
    drawPath(P, edge, style = st)
    // pleonites, short with pointed sides
    for (i in ISO_PX.indices.reversed()) {
        val tl = Offset(ISO_PX[i] - 0.025f, -ISO_PW[i] / 2f)
        drawOval(body, tl, Size(0.05f, ISO_PW[i]))
        drawArc(edge, 100f, 160f, false, tl, Size(0.05f, ISO_PW[i]), style = st)
    }
    // pereonites, the hind ones first so each overlaps the next; epimera as backward points at the corners
    for (i in ISO_X.indices.reversed()) {
        val hw = ISO_W[i] / 2f
        if (i > 0 && lod == 2) for (s in PM) {
            P.reset()
            P.moveTo(ISO_X[i] + 0.03f, s * (hw - 0.03f))
            P.lineTo(ISO_X[i] - 0.06f, s * (hw + 0.01f))
            P.lineTo(ISO_X[i] - 0.02f, s * (hw - 0.05f))
            P.close()
            drawPath(P, body)
            drawPath(P, edge, style = st)
        }
        val tl = Offset(ISO_X[i] - 0.06f, -hw)
        drawOval(body, tl, Size(0.12f, 2f * hw))
        drawArc(edge, 100f, 160f, false, tl, Size(0.12f, 2f * hw), style = st)
    }
    if (lod == 2) {
        // chromatophores: dark stellate spots in rows across each pereonite
        val spot = fade(shade(body, 0.5f), 0.7f)
        for (i in ISO_X.indices) for (k in 0 until 4) {
            dot(spot, ISO_X[i] - 0.01f + 0.02f * h01(i * 4 + k, m.r1), (k - 1.5f) * ISO_W[i] * 0.2f, 0.012f)
        }
    }
    // head with lateral compound eyes
    ov(body, 0.44f, 0f, 0.14f, 0.26f)
    drawOval(edge, Offset(0.37f, -0.13f), Size(0.14f, 0.26f), style = st)
    for (s in PM) {
        ov(EYE_DARK, 0.44f, s * 0.105f, 0.06f, 0.035f)
        dot(GLINT, 0.45f, s * 0.1f, 0.007f)
    }
}

// ================================================================== rotifers and protists

/**
 * Brachionus from above: a glassy lorica with six anterior spines, the corona's ciliary wreaths beating in a
 * metachronal wave (the "wheel"), the mastax jaws grinding behind it, stomach, red eyespot, and an annulated foot
 * ending in two toes. Females carry an egg at the foot.
 */
private fun DrawScope.rotifer(m: Mote, t: Float, look: Look, lod: Int, px: Float) {
    val body = look.body(m.group)
    val glass = fade(body, 0.6f)
    val edge = edgeOf(body, look)
    val hl = look.highlight
    // foot: four annuli swinging gently, two toes
    val fs = 0.03f * sin(t * 2f + m.r1 * 6f)
    val fst = Stroke(width = maxOf(px, 0.01f))
    for (k in 0 until 4) {
        val fx = -0.34f - k * 0.05f
        val fh = 0.06f - k * 0.008f
        ov(glass, fx, fs * k / 3f, 0.06f, fh)
        if (lod > 0) drawOval(edge, Offset(fx - 0.03f, fs * k / 3f - fh / 2f), Size(0.06f, fh), style = fst)
    }
    for (s in PM) bar(edge, -0.49f, fs, -0.56f, fs + s * 0.02f, maxOf(px, 0.012f))
    if (m.r2 < 0.5f * look.breeding(m.group)) {
        ov(EGG, -0.4f, 0.17f, 0.2f, 0.14f)
        if (lod == 2) eggCluster(-0.4f, 0.17f, 3, 2, 0.04f, 0.04f, 0.015f)
    }
    // corona: the head's two ciliated lobes
    ov(glass, 0.33f, 0f, 0.16f, 0.34f)
    val nc = when (lod) { 0 -> 5; 1 -> 9; else -> 14 }
    for (s in PM) {
        val cx = 0.4f
        val cy = s * 0.1f
        ov(glass, cx, cy, 0.12f, 0.17f)
        for (k in 0 until nc) {
            val th = -1.75f + 3.5f * k / (nc - 1)
            val rx = cx + cos(th) * 0.06f
            val ry = cy + sin(th) * 0.085f
            // a wave of cilia running round each wreath, opposite ways on the two lobes
            val ph = th * 3f - t * 5f * s
            val dir = th + 0.6f * sin(ph)
            val len = 0.045f * (0.75f + 0.25f * cos(ph))
            val c = if (sin(ph) > 0.7f) hl else fade(hl, 0.45f)
            bar(c, rx, ry, rx + cos(dir) * len, ry + sin(dir) * len, maxOf(px, 0.008f))
        }
    }
    // lorica: anterior margin with lateral, intermediate and median spine pairs either side of a median sinus
    P.reset()
    P.moveTo(0.22f, -0.24f)
    P.lineTo(0.32f, -0.22f)
    P.lineTo(0.25f, -0.16f)
    P.lineTo(0.31f, -0.12f)
    P.lineTo(0.25f, -0.07f)
    P.lineTo(0.34f, -0.03f)
    P.lineTo(0.27f, 0f)
    P.lineTo(0.34f, 0.03f)
    P.lineTo(0.25f, 0.07f)
    P.lineTo(0.31f, 0.12f)
    P.lineTo(0.25f, 0.16f)
    P.lineTo(0.32f, 0.22f)
    P.lineTo(0.22f, 0.24f)
    P.cubicTo(0.08f, 0.3f, -0.12f, 0.3f, -0.26f, 0.16f)
    P.cubicTo(-0.31f, 0.11f, -0.32f, 0.07f, -0.32f, 0.05f)
    P.lineTo(-0.32f, -0.05f)
    P.cubicTo(-0.32f, -0.07f, -0.31f, -0.11f, -0.26f, -0.16f)
    P.cubicTo(-0.12f, -0.3f, 0.08f, -0.3f, 0.22f, -0.24f)
    P.close()
    drawPath(P, glass)
    drawPath(P, edge, style = Stroke(width = maxOf(px, 0.012f)))
    // stomach and intestine (gut colour follows the diet), gastric glands, vitellarium
    ov(fade(look.gut, 0.55f), -0.03f, 0f, 0.26f, 0.2f)
    bar(fade(look.gut, 0.45f), -0.1f, 0f, -0.25f, 0f, 0.05f)
    if (lod > 0) {
        for (s in PM) ov(fade(look.gut, 0.35f), 0.09f, s * 0.075f, 0.06f, 0.05f)
        ov(fade(shade(body, 0.75f), 0.5f), -0.02f, 0.14f, 0.28f, 0.06f)
    }
    // mastax: the muscular pharynx and its trophi, opening and closing as it grinds
    ov(fade(shade(body, 0.7f), 0.7f), 0.14f, 0f, 0.11f, 0.09f)
    if (lod > 0) {
        val open = 0.5f + 0.5f * sin(t * 4f * TWO_PI)
        for (s in PM) bar(edge, 0.1f, s * 0.005f, 0.17f, s * (0.01f + 0.025f * open), maxOf(px, 0.012f))
        bar(edge, 0.1f, 0f, 0.08f, 0f, maxOf(px, 0.012f))
    }
    dot(EYE_RED, 0.24f, 0f, 0.018f)
}

/**
 * Euplotes from above: a stiff oval with dorsal ridges, the adoral zone of membranelles round the front and left side,
 * nine stout frontoventral cirri it walks on, five transverse and two caudal cirri at the rear, two left marginal ones,
 * a C-shaped macronucleus and a contractile vacuole that fills and empties (research 16 §10.1).
 */
private fun DrawScope.euplotes(m: Mote, t: Float, look: Look, lod: Int, px: Float) {
    val body = look.body(m.group)
    val cell = fade(body, 0.7f)
    val edge = edgeOf(body, look)
    val cirrus = fade(edge, 0.75f)
    val hl = look.highlight
    val w = maxOf(px, 0.03f)
    // transverse and caudal cirri reach out behind; frontoventral cirri step under the front
    for (k in 0 until 5) {
        val y = -0.12f + k * 0.06f
        val a = PI_F + 0.15f * sin(t * 8f + k)
        bar(cirrus, -0.28f, y, -0.28f + cos(a) * 0.25f, y + sin(a) * 0.25f + y * 0.3f, w)
    }
    if (lod > 0) {
        // two caudal cirri at the rear right, two marginal cirri on the left edge
        for (k in 0 until 2) bar(cirrus, -0.4f, 0.13f + k * 0.06f, -0.6f, 0.17f + k * 0.1f, maxOf(px, 0.012f))
        for (k in 0 until 2) bar(cirrus, -0.1f - k * 0.15f, -0.3f, -0.16f - k * 0.15f, -0.4f, maxOf(px, 0.015f))
    }
    ov(cell, 0f, 0f, 0.92f, 0.66f)
    if (lod == 0) {
        for (k in 0 until 10) {
            val a = k * TWO_PI / 10f
            val f = 0.5f + 0.5f * sin(t * 12f + k)
            dot(fade(hl, 0.5f * f), cos(a) * 0.46f, sin(a) * 0.33f, 0.03f)
        }
        return
    }
    val st = Stroke(width = maxOf(px, 0.012f))
    drawOval(edge, Offset(-0.46f, -0.33f), Size(0.92f, 0.66f), style = st)
    // dorsal ridges
    val ridge = fade(edge, 0.3f)
    for (k in 1..3) {
        val h = 0.66f * k / 4f
        drawArc(ridge, 200f, 140f, false, Offset(-0.43f, -h / 2f), Size(0.86f, h), style = st)
        drawArc(ridge, 20f, 140f, false, Offset(-0.43f, -h / 2f), Size(0.86f, h), style = st)
    }
    // adoral zone of membranelles: a band round the front and down the left, beating in a wave
    drawArc(fade(shade(body, 0.6f), 0.7f), 210f, 170f, false, Offset(-0.4f, -0.29f), Size(0.8f, 0.58f), style = Stroke(width = 0.05f))
    val nm = if (lod == 2) 16 else 9
    for (k in 0 until nm) {
        val th = (210f + 170f * k / (nm - 1)) / DEG_F
        val x = cos(th) * 0.4f
        val y = sin(th) * 0.29f
        val f = sin(t * 20f - k * 0.9f)
        bar(if (f > 0.5f) hl else fade(hl, 0.4f), x, y, x * (0.82f + 0.06f * f), y * (0.82f + 0.06f * f), maxOf(px, 0.012f))
    }
    // frontoventral cirri: stout tufts it walks on
    for (k in 0 until 9) {
        val x = 0.12f + 0.24f * h01(k, 0.37f)
        val y = -0.04f + 0.24f * h01(k + 11, 0.37f)
        val a = 0.3f + 0.3f * sin(t * 8f + k * 1.3f)
        bar(cirrus, x, y, x + cos(a) * 0.08f, y + sin(a) * 0.08f, maxOf(px, 0.025f))
    }
    // C-shaped macronucleus, micronucleus
    drawArc(fade(shade(body, 0.55f), 0.5f), 110f, 250f, false, Offset(-0.28f, -0.2f), Size(0.56f, 0.4f), style = Stroke(width = 0.06f))
    dot(fade(shade(body, 0.5f), 0.6f), 0.18f, -0.12f, 0.025f)
    // food vacuoles and the contractile vacuole (systole every ~6 s)
    val fv = fade(look.gut, 0.6f)
    for (k in 0 until 4) dot(fv, -0.2f + 0.4f * h01(k, m.r1), -0.15f + 0.3f * h01(k + 7, m.r1), 0.035f)
    val cv = 0.07f * fr((t + m.r2 * 6f) / 6f)
    dot(fade(Color.White, 0.35f), -0.3f, 0.17f, cv)
    drawCircle(fade(edge, 0.5f), cv, Offset(-0.3f, 0.17f), style = st)
}

/**
 * Vorticella: an inverted bell on a stalk from its holdfast (the origin). The oral cilia shimmer round the peristome;
 * every 8-20 s the spasmoneme fires, the stalk coils to a fifth of its length and the bell balls up, then it
 * re-extends over ~2 s (§5.3, research 16 §10.3).
 */
private fun DrawScope.vorticella(m: Mote, t: Float, look: Look, lod: Int, px: Float) {
    val body = look.body(m.group)
    val cell = fade(body, 0.7f)
    val edge = edgeOf(body, look)
    val cyc = 8f + 12f * m.r2
    val p = fr((t + m.r1 * 13f) / cyc) * cyc
    val ext = if (p < 2f) smooth(p / 2f) else 1f
    val coil = 1f - ext
    val sl = 0.62f * (0.2f + 0.8f * ext)
    val nod = 0.03f * sin(t * 0.8f + m.r1 * 6f)
    // stalk: straight when extended, a tight zigzag helix when contracted; the spasmoneme a dark core
    P.reset()
    P.moveTo(0f, 0f)
    val ns = if (lod == 0) 8 else 24
    for (k in 1..ns) {
        val u = k.toFloat() / ns
        P.lineTo(u * sl, 0.05f * coil * sin(u * 40f) + nod * u)
    }
    drawPath(P, fade(mix(body, Color.White, 0.3f), 0.8f), style = Stroke(width = maxOf(px, 0.018f)))
    if (lod > 0) drawPath(P, fade(shade(body, 0.5f), 0.7f), style = Stroke(width = maxOf(px * 0.6f, 0.007f)))
    val bx = sl
    val by = nod
    val bl = 0.36f * (0.75f + 0.25f * ext)
    val bw = 0.1f + 0.06f * ext
    val lip = 0.05f * ext
    // the bell: narrow at the scopula, flaring to the peristomial lip, the disc domed between
    P.reset()
    P.moveTo(bx, by - 0.025f)
    P.cubicTo(bx + bl * 0.4f, by - 0.04f, bx + bl * 0.7f, by - bw, bx + bl, by - bw)
    P.lineTo(bx + bl + 0.015f, by - bw - 0.01f * ext)
    P.cubicTo(bx + bl + lip, by - bw * 0.4f, bx + bl + lip, by + bw * 0.4f, bx + bl + 0.015f, by + bw + 0.01f * ext)
    P.lineTo(bx + bl, by + bw)
    P.cubicTo(bx + bl * 0.7f, by + bw, bx + bl * 0.4f, by + 0.04f, bx, by + 0.025f)
    P.close()
    drawPath(P, cell)
    if (lod == 0) return
    val st = Stroke(width = maxOf(px, 0.01f))
    drawPath(P, edge, style = st)
    // oral cilia, only while the bell is open
    if (ext > 0.6f) {
        val hl = look.highlight
        val nc = if (lod == 2) 12 else 7
        for (k in 0 until nc) {
            val y = by - bw + 2f * bw * k / (nc - 1)
            val x = bx + bl + 0.02f
            val a = 0.5f * sin(t * 18f - k * 0.8f) - 0.6f * (y - by) / bw
            val f = sin(t * 18f - k * 0.8f)
            bar(if (f > 0.6f) hl else fade(hl, 0.45f), x, y, x + cos(a) * 0.06f, y + sin(a) * 0.06f, maxOf(px, 0.008f))
        }
    }
    // long curved macronucleus, food vacuoles, contractile vacuole near the lip
    drawArc(fade(shade(body, 0.55f), 0.5f), 160f, 220f, false, Offset(bx + bl * 0.3f, by - bw * 0.55f), Size(bl * 0.55f, bw * 1.1f), style = Stroke(width = 0.035f))
    val fv = fade(look.gut, 0.6f)
    for (k in 0 until 3) dot(fv, bx + bl * (0.35f + 0.4f * h01(k, m.r2)), by + bw * (0.6f * h01(k + 5, m.r2) - 0.3f), 0.025f)
    dot(fade(Color.White, 0.35f), bx + bl * 0.8f, by + bw * 0.5f, 0.03f * fr((t + m.r1 * 4f) / 4f))
}

/** Tetraselmis: an ovoid cell in a thin theca, four flagella (shorter than the cell) from an apical pit, cup chloroplast, pyrenoid, stigma. */
private fun DrawScope.tetraselmis(m: Mote, t: Float, look: Look, lod: Int, px: Float) {
    val body = look.body(MicroGroup.PHYTO)
    if (lod == 0) {
        ov(body, 0f, 0f, 0.9f, 0.66f)
        return
    }
    val edge = edgeOf(body, look)
    val st = Stroke(width = maxOf(px, 0.015f))
    // flagella, two pairs beating breaststroke
    val fl = fade(edgeOf(body, look), 0.7f)
    for (k in 0 until 4) {
        val s = if (k < 2) -1f else 1f
        val beat = 0.8f * sin(t * 12f + (k % 2) * 0.5f + m.r1 * 6f)
        val a = s * (0.45f + 0.3f * (k % 2) + beat)
        val c = cos(a)
        val sn = sin(a)
        P.reset()
        P.moveTo(0.42f, s * 0.02f)
        P.cubicTo(0.42f + c * 0.23f - sn * 0.05f, s * 0.02f + sn * 0.23f + c * 0.05f, 0.42f + c * 0.47f + sn * 0.05f, s * 0.02f + sn * 0.47f - c * 0.05f, 0.42f + c * 0.7f, s * 0.02f + sn * 0.7f)
        drawPath(P, fl, style = st)
    }
    // theca and cytoplasm, then the green cup open at the front
    val cyto = fade(mix(body, Color.White, 0.5f), 0.6f)
    ov(cyto, 0f, 0f, 0.9f, 0.66f)
    ov(body, -0.04f, 0f, 0.78f, 0.58f)
    ov(cyto, 0.3f, 0f, 0.3f, 0.28f)
    drawOval(edge, Offset(-0.45f, -0.33f), Size(0.9f, 0.66f), style = st)
    // pyrenoid with its starch sheath, and the red stigma
    dot(fade(Color.White, 0.5f), -0.12f, 0f, 0.1f)
    drawCircle(fade(edge, 0.7f), 0.1f, Offset(-0.12f, 0f), style = st)
    dot(EYE_RED, 0.05f, 0.24f, 0.04f)
}

/** Nannochloropsis: a 2-3 µm yellow-green sphere with one parietal chloroplast and no flagella. */
private fun DrawScope.nannochloropsis(look: Look, lod: Int, px: Float) {
    val body = mix(look.body(MicroGroup.PHYTO), NANNO, 0.6f)
    dot(fade(mix(body, Color.White, 0.4f), 0.8f), 0f, 0f, 0.18f)
    if (lod > 0) {
        drawArc(body, 120f, 240f, false, Offset(-0.15f, -0.15f), Size(0.3f, 0.3f), style = Stroke(width = 0.08f))
        drawCircle(edgeOf(body, look), 0.18f, Offset.Zero, style = Stroke(width = maxOf(px, 0.02f)))
    } else {
        dot(body, 0f, 0f, 0.12f)
    }
}

// ================================================================== worms, snails, forams, flatworms

/** The bristleworm's sideways body wave at [s] (0 head, 1 tail), travelling tail-ward with phase [ph]. */
private fun wormY(s: Float, ph: Float): Float = 0.03f * sin(TWO_PI * 1.4f * s - ph) * (0.3f + 0.7f * s)

/**
 * Bristleworm (an amphinomid like Eurythoe) from above: a long chain of segments, each with parapodia bearing white
 * glassy chaetae and red branchial tufts; caruncle and eyes on the head. The body wave and the parapodial stroke both
 * travel tail-ward at ~1.5 Hz (metachronal), so the bristles ripple as it glides.
 */
private fun DrawScope.bristleworm(m: Mote, t: Float, look: Look, lod: Int, px: Float) {
    val body = look.body(m.group)
    val n = when (lod) { 0 -> 14; 1 -> 24; else -> 38 }
    val ph = t * 1.5f * TWO_PI + m.r1 * 6f
    val chaeta = if (look.bright) INK_SETA else fade(CHAETA, if (look.night) 0.9f else 0.75f)
    val gill = if (look.night) fade(GILL_RED, 0.7f) else GILL_RED
    val edge = edgeOf(body, look)
    val dorsal = fade(shade(body, 0.7f), 0.7f)
    val ds = 1f / n
    val hair = maxOf(px * 0.7f, 0.0025f)
    val nc = when (lod) { 0 -> 1; 1 -> 3; else -> 5 }
    for (i in n - 1 downTo 0) {
        val s = (i + 0.5f) * ds
        val x = 0.5f - s
        val y = wormY(s, ph)
        val dy = wormY(s + 0.01f, ph) - wormY(s - 0.01f, ph)
        val tl = sqrt(0.0004f + dy * dy)
        // unit normal to the axis
        val nx = -dy / tl
        val ny = -0.02f / tl
        val wf = minOf(1f, 0.55f + s * 6f, (1f - s) * 5f + 0.15f)
        val hw = 0.033f * wf
        val stroke = 0.45f * sin(ph * 1.3f - i * 0.55f)
        for (side in PM) {
            val bx = x + nx * side * hw
            val by = y + ny * side * hw
            // the chaetal fan, swept fore and aft with the parapodial stroke
            for (k in 0 until nc) {
                val a = stroke + (k - (nc - 1) / 2f) * 0.22f
                val ca = cos(a)
                val sa = sin(a)
                val vx = (nx * ca - ny * sa) * side
                val vy = (nx * sa + ny * ca) * side
                val len = (0.04f + 0.012f * (k % 2)) * wf
                bar(chaeta, bx, by, bx + vx * len, by + vy * len, hair)
            }
            if (lod > 0) bar(body, x, y, bx + nx * side * 0.012f, by + ny * side * 0.012f, 0.014f * wf)  // parapodium
        }
        drawOval(body, Offset(x - ds * 0.8f, y - hw), Size(ds * 1.6f, 2f * hw))
        if (lod > 0) {
            bar(fade(edge, 0.5f), x - ds * 0.5f + nx * hw, y + ny * hw, x - ds * 0.5f - nx * hw, y - ny * hw, hair)
            bar(dorsal, x + ds * 0.5f, y, x - ds * 0.5f, y, hw * 0.25f)
            // red branchial tufts at the notopodia, from the second chaetiger back
            if (i >= 1 && s < 0.95f) for (side in PM) {
                val gx = x + nx * side * hw * 0.8f
                val gy = y + ny * side * hw * 0.8f
                dot(gill, gx, gy, 0.009f * wf)
                if (lod == 2) {
                    dot(gill, gx - 0.006f, gy + ny * side * 0.006f, 0.006f * wf)
                    dot(gill, gx + 0.005f, gy + ny * side * 0.007f, 0.006f * wf)
                }
            }
        }
    }
    val y0 = wormY(0f, ph)
    if (lod > 0) {
        // prostomium with five short antennae and two pairs of eyes; the caruncle lies back over the first segments
        val limb = limbOf(body, look)
        for (k in -2..2) bar(limb, 0.505f, y0 + k * 0.006f, 0.535f, y0 + k * 0.012f, hair * 1.5f)
        ov(body, 0.5f, y0, 0.04f, 0.035f)
        for (s in PM) {
            dot(EYE_DARK, 0.505f, y0 + s * 0.009f, 0.003f)
            dot(EYE_DARK, 0.492f, y0 + s * 0.011f, 0.003f)
        }
        val car = mix(body, Color(0xFFF0D8C8), 0.5f)
        ov(car, 0.46f, wormY(0.04f, ph), 0.075f, 0.022f)
        if (lod == 2) for (k in 0 until 4) {
            val cx = 0.435f + k * 0.015f
            bar(fade(edge, 0.6f), cx, wormY(0.04f, ph) - 0.008f, cx, wormY(0.04f, ph) + 0.008f, hair)
        }
        // pygidium with its anal cirri
        val ye = wormY(1f, ph)
        for (s in PM) bar(limb, -0.5f, ye, -0.525f, ye + s * 0.01f, hair * 1.5f)
    } else {
        dot(shade(body, 0.75f), 0.5f, y0, 0.03f)
    }
}

/**
 * Micro-snail (a trochoid like Collonista) from above: the coiled shell with its sutures, spiral bands and growth
 * lines, over a translucent foot with epipodial tentacles; head with two cephalic tentacles and eyes at their bases.
 */
private fun DrawScope.microsnail(m: Mote, t: Float, look: Look, lod: Int, px: Float) {
    val body = look.body(m.group)
    val flesh = fade(mix(body, Color.White, 0.55f), 0.85f)
    val edge = edgeOf(body, look)
    val crawling = m.state == 0
    val sway = 0.15f * sin(t * 1.5f + m.r1 * 6f)
    val w = maxOf(px, 0.03f)
    ov(flesh, 0.02f, 0f, 0.9f, 0.42f)
    if (lod > 0) for (s in PM) for (k in 0 until 3) {
        val x = 0.1f - k * 0.18f
        bar(flesh, x, s * 0.19f, x - 0.04f, s * (0.27f + 0.02f * sin(t * 2f + k)), maxOf(px, 0.015f))
    }
    // pedal waves running back along the sole as it glides
    if (lod > 0 && crawling) {
        val wave = fade(edge, 0.25f)
        for (k in 0 until 3) {
            val x = 0.42f - fr(k / 3f + t * 0.4f) * 0.2f
            bar(wave, x, -0.15f, x, 0.15f, maxOf(px, 0.01f))
        }
    }
    ov(flesh, 0.44f, 0f, 0.12f, 0.12f)
    for (s in PM) {
        jointed(flesh, 0.42f, s * 0.06f, s * 0.5f + sway, if (lod == 0) 1 else 3, if (lod == 0) 0.22f else 0.075f, s * 0.08f, w)
        dot(EYE_DARK, 0.41f, s * 0.1f, 0.018f)
    }
    // operculum on the back of the foot
    ov(fade(Color(0xFFB08A50), 0.85f), -0.44f, 0f, 0.07f, 0.09f)
    if (lod == 0) {
        dot(body, -0.06f, 0f, 0.34f)
        drawCircle(fade(edge, 0.7f), 0.17f, Offset(-0.09f, 0f), style = Stroke(width = maxOf(px, 0.03f)))
        return
    }
    // shell: a logarithmic spiral expanding ~2.2x a turn; aperture to the front right
    val st = Stroke(width = maxOf(px, 0.012f))
    val cx = -0.06f
    val tmax = SNAIL_TURNS * TWO_PI
    val rot = 0.3f - tmax
    val no = if (lod == 2) 40 else 24
    P.reset()
    for (k in 0..no) {
        val th = tmax - TWO_PI + TWO_PI * k / no
        val r = snailR(th, tmax)
        val x = cx + cos(th + rot) * r
        val y = sin(th + rot) * r * 0.95f
        if (k == 0) P.moveTo(x, y) else P.lineTo(x, y)
    }
    P.close()
    drawPath(P, body)
    drawPath(P, edge, style = st)
    // suture: the same spiral one whorl in, down to the apex
    P.reset()
    val ni = if (lod == 2) 48 else 28
    val th0 = 1.2f
    for (k in 0..ni) {
        val th = th0 + (tmax - TWO_PI - th0) * k / ni
        val r = snailR(th, tmax)
        val x = cx + cos(th + rot) * r
        val y = sin(th + rot) * r * 0.95f
        if (k == 0) P.moveTo(x, y) else P.lineTo(x, y)
    }
    drawPath(P, edge, style = st)
    dot(fade(Color.White, 0.35f), cx, 0f, 0.035f)  // the worn apex
    if (lod == 2) {
        // a spiral band of dark spots on the body whorl, and axial growth lines
        val spot = fade(shade(body, 0.55f), 0.8f)
        val growth = fade(edge, 0.35f)
        for (k in 0 until 12) {
            val th = tmax - TWO_PI + TWO_PI * (k + 0.5f) / 12f
            val ro = snailR(th, tmax)
            val ri = snailR(th - TWO_PI, tmax)
            val rm = ri + (ro - ri) * 0.55f
            dot(spot, cx + cos(th + rot) * rm, sin(th + rot) * rm * 0.95f, 0.02f)
            bar(growth, cx + cos(th + rot) * ri, sin(th + rot) * ri * 0.95f, cx + cos(th + rot) * ro, sin(th + rot) * ro * 0.95f, maxOf(px, 0.006f))
        }
    }
    if (!look.bright) ov(fade(Color.White, 0.2f), -0.12f, -0.12f, 0.2f, 0.08f)
}

private const val SNAIL_TURNS = 3.2f
/** Whorl expansion: ln(2.2) / 2π. */
private const val SNAIL_B = 0.1255f

private fun snailR(th: Float, tmax: Float): Float = 0.36f * exp(SNAIL_B * (th - tmax))

/**
 * Spaghetti worm (a terebellid) at its burrow mouth (the origin): long buccal tentacles spread over the rock, each with
 * a ciliary groove carrying food particles back to the mouth; red branchiae in the tube mouth of cemented grains.
 */
private fun DrawScope.spaghettiWorm(m: Mote, t: Float, look: Look, lod: Int, px: Float) {
    val body = look.body(m.group)
    // the tentacles are bluish white, often banded purple (research 16 §14.1)
    val tent = if (look.night) body else mix(body, TENTACLE_BLUE, 0.5f)
    val n = when (lod) { 0 -> 6; 1 -> 10; else -> 16 }
    val st = Stroke(width = maxOf(px * 1.2f, 0.012f), cap = StrokeCap.Round)
    val groove = Stroke(width = maxOf(px * 0.6f, 0.004f), cap = StrokeCap.Round)
    val grooveC = shade(tent, 0.72f)
    for (k in 0 until n) {
        val h = h01(k, m.r1)
        val a = (k + 0.4f * h) * TWO_PI / n + m.r1 * 6f
        // each tentacle creeps out along the surface and draws back again
        val reach = 0.5f * (0.55f + 0.25f * h + 0.2f * sin(t * 0.3f + k * 1.7f + m.r2 * 6f))
        val ca = cos(a)
        val sa = sin(a)
        val b1 = 0.18f * reach * sin(k * 2.3f + t * 0.2f)
        val b2 = -0.22f * reach * sin(k * 1.1f + t * 0.15f + h * 4f)
        val x0 = ca * 0.04f
        val y0 = sa * 0.04f
        val x1 = ca * reach * 0.35f - sa * b1
        val y1 = sa * reach * 0.35f + ca * b1
        val x2 = ca * reach * 0.7f - sa * b2
        val y2 = sa * reach * 0.7f + ca * b2
        val x3 = ca * reach
        val y3 = sa * reach
        P.reset()
        P.moveTo(x0, y0)
        P.cubicTo(x1, y1, x2, y2, x3, y3)
        drawPath(P, tent, style = st)
        if (lod > 0) {
            drawPath(P, grooveC, style = groove)
            if (lod == 2 && !look.night) for (b in QUARTERS) dot(PURPLE, bez(b, x0, x1, x2, x3), bez(b, y0, y1, y2, y3), 0.009f)
            for (j in 0 until 3) {
                // particles ride the groove toward the mouth
                val u = fr(j / 3f + h - t * 0.06f)
                dot(DETRITUS, bez(u, x0, x1, x2, x3), bez(u, y0, y1, y2, y3), 0.008f)
            }
        }
    }
    if (lod > 0) {
        for (k in 0 until 3) {
            val a = k * 2.1f + m.r2 * 6f
            for (j in 0 until 3) dot(GILL_RED, cos(a) * (0.02f + j * 0.012f), sin(a) * (0.02f + j * 0.012f) + (j - 1) * 0.006f, 0.008f)
        }
        val grain = Color(0xFFCDBB98)
        for (k in 0 until 10) {
            val a = k * TWO_PI / 10f
            dot(grain, cos(a) * 0.06f, sin(a) * 0.06f, 0.012f + 0.006f * h01(k, m.r2))
        }
    }
    dot(shade(body, 0.6f), 0f, 0f, 0.035f)
}

/**
 * Homotrema rubrum: an encrusting red mound with knobby lobes and white pores. At high power the granular reticulopodia
 * spread from it with particles streaming both ways.
 */
private fun DrawScope.homotrema(m: Mote, t: Float, look: Look, lod: Int, px: Float) {
    val body = look.body(m.group)
    if (look.bright) reticulopodia(m, t, look, lod, px, 0f, TWO_PI, 0.36f)
    val np = 28
    for (k in 0 until np) {
        val th = k * TWO_PI / np
        val r = 0.38f * (1f + 0.12f * sin(5f * th + m.r1 * 20f) + 0.07f * sin(9f * th + m.r2 * 20f) + 0.05f * sin(3f * th))
        XS[k] = cos(th) * r
        YS[k] = sin(th) * r
    }
    smoothClosed(np)
    drawPath(P, body)
    if (lod == 0) {
        dot(mix(body, Color.White, 0.15f), 0f, 0f, 0.15f)
        return
    }
    drawPath(P, shade(body, 0.6f), style = Stroke(width = maxOf(px, 0.015f)))
    // knobs and the paler crown
    val knob = mix(body, Color.White, 0.12f)
    for (k in 0 until 5) {
        val a = k * 1.3f + m.r1 * 6f
        dot(knob, cos(a) * 0.24f, sin(a) * 0.24f, 0.07f)
        dot(fade(Color.White, 0.25f), cos(a) * 0.24f - 0.02f, sin(a) * 0.24f - 0.02f, 0.02f)
    }
    dot(knob, 0f, 0f, 0.14f)
    // pores
    val pore = fade(Color.White, 0.7f)
    for (k in 0 until 18) {
        val a = TWO_PI * h01(k, m.r1)
        val r = 0.32f * sqrt(h01(k + 40, m.r2))
        dot(pore, cos(a) * r, sin(a) * r, 0.012f)
    }
}

/** Peneroplis log spiral: radius at angle [th]. */
private fun penR(th: Float): Float = 0.05f * exp(0.22f * th)

/** Angle at which the Peneroplis spiral reaches its rim (0.48). */
private const val PEN_TMAX = 10.28f

/**
 * Peneroplis: a porcelaneous planispiral test, its chambers flaring as they grow, with radial septa, fine striae and a
 * row of apertural pores; reticulopodia stream out of the apertural face at high power.
 */
private fun DrawScope.peneroplis(m: Mote, t: Float, look: Look, lod: Int, px: Float) {
    val body = mix(look.body(m.group), PORCELAIN, 0.75f)
    val edge = edgeOf(body, look)
    val rot = m.r2 * TWO_PI
    val apDir = PEN_TMAX + rot
    if (look.bright) reticulopodia(m, t, look, lod, px, apDir - 1.2f, 2.4f, 0.4f)
    val no = if (lod == 0) 16 else 36
    P.reset()
    for (k in 0..no) {
        val th = PEN_TMAX - TWO_PI + TWO_PI * k / no
        val r = penR(th)
        val x = cos(th + rot) * r
        val y = sin(th + rot) * r
        if (k == 0) P.moveTo(x, y) else P.lineTo(x, y)
    }
    P.close()
    drawPath(P, body)
    if (lod == 0) {
        drawCircle(fade(edge, 0.6f), 0.12f, Offset.Zero, style = Stroke(width = maxOf(px, 0.03f)))
        return
    }
    val st = Stroke(width = maxOf(px, 0.01f))
    drawPath(P, edge, style = st)
    // septa: from the previous whorl out to the rim, one per chamber
    var th = PEN_TMAX
    while (th > 1.2f) {
        val ro = penR(th)
        val ri = if (th > TWO_PI) penR(th - TWO_PI) else 0.02f
        bar(edge, cos(th + rot) * ri, sin(th + rot) * ri, cos(th + rot) * ro, sin(th + rot) * ro, maxOf(px, 0.008f))
        th -= 0.42f
    }
    // the previous whorl's suture
    P.reset()
    val ni = 24
    for (k in 0..ni) {
        val a = 1.2f + (PEN_TMAX - TWO_PI - 1.2f) * k / ni
        val r = penR(a)
        if (k == 0) P.moveTo(cos(a + rot) * r, sin(a + rot) * r) else P.lineTo(cos(a + rot) * r, sin(a + rot) * r)
    }
    drawPath(P, edge, style = st)
    if (lod == 2) {
        // fine striae running with the spiral
        val stria = fade(edge, 0.3f)
        for (f in QUARTERS) {
            P.reset()
            for (k in 0..24) {
                val a = PEN_TMAX - TWO_PI + 0.2f + (TWO_PI - 0.2f) * k / 24f
                val r = penR(a - TWO_PI) + f * (penR(a) - penR(a - TWO_PI))
                if (k == 0) P.moveTo(cos(a + rot) * r, sin(a + rot) * r) else P.lineTo(cos(a + rot) * r, sin(a + rot) * r)
            }
            drawPath(P, stria, style = Stroke(width = maxOf(px * 0.7f, 0.004f)))
        }
    }
    // apertural pores along the last septum
    val pore = fade(shade(body, 0.5f), 0.8f)
    val r0 = penR(PEN_TMAX - TWO_PI)
    val r1 = penR(PEN_TMAX)
    for (k in 1..6) {
        val r = r0 + (r1 - r0) * k / 7f
        dot(pore, cos(apDir) * r - sin(apDir) * 0.012f, sin(apDir) * r + cos(apDir) * 0.012f, 0.01f)
    }
    dot(fade(shade(body, 0.8f), 0.8f), cos(rot) * 0.05f, sin(rot) * 0.05f, 0.035f)  // proloculus
    if (!look.bright) ov(fade(Color.White, 0.3f), -0.1f, -0.12f, 0.2f, 0.08f)
}

/**
 * A foram's reticulopodia: fine anastomosing threads fanning over [from]..[from]+[span] from radius [r0], branching
 * once, with granules streaming out and back along them (bidirectional cytoplasmic streaming).
 */
private fun DrawScope.reticulopodia(m: Mote, t: Float, look: Look, lod: Int, px: Float, from: Float, span: Float, r0: Float) {
    val thread = if (look.bright) INK_SETA else fade(Color.White, 0.3f)
    val granule = if (look.bright) INK_LIMB else fade(Color.White, 0.6f)
    val st = Stroke(width = maxOf(px * 0.8f, 0.004f))
    val nr = if (lod == 2) 14 else 8
    for (k in 0 until nr) {
        val h = h01(k, m.r1)
        val a = from + span * (k + 0.5f * h) / nr
        val len = 0.6f + 0.5f * h
        val ca = cos(a)
        val sa = sin(a)
        val bend = 0.15f * sin(k * 1.9f + t * 0.1f)
        val x0 = ca * r0
        val y0 = sa * r0
        val x1 = ca * (r0 + len * 0.35f) - sa * bend
        val y1 = sa * (r0 + len * 0.35f) + ca * bend
        val x2 = ca * (r0 + len * 0.7f) + sa * bend
        val y2 = sa * (r0 + len * 0.7f) - ca * bend
        val x3 = ca * (r0 + len)
        val y3 = sa * (r0 + len)
        P.reset()
        P.moveTo(x0, y0)
        P.cubicTo(x1, y1, x2, y2, x3, y3)
        // a side branch from midway, joining its neighbour's
        val mx = bez(0.5f, x0, x1, x2, x3)
        val my = bez(0.5f, y0, y1, y2, y3)
        val ba = a + (if (k % 2 == 0) 0.35f else -0.35f)
        P.moveTo(mx, my)
        P.lineTo(mx + cos(ba) * len * 0.4f, my + sin(ba) * len * 0.4f)
        drawPath(P, thread, style = st)
        val dir = if (k % 2 == 0) 1f else -1f
        for (j in 0 until 3) {
            val u = fr(j / 3f + h * 7f + dir * t * 0.05f)
            dot(granule, bez(u, x0, x1, x2, x3), bez(u, y0, y1, y2, y3), 0.008f)
        }
    }
}

// Convolutriloba retrogemma outline, upper half from the front to the middle lobe: a rounded front, then lateral and
// median posterior lobes either side of the notches
private val FW_X = floatArrayOf(0.5f, 0.47f, 0.38f, 0.24f, 0.06f, -0.12f, -0.28f, -0.4f, -0.5f, -0.46f, -0.38f, -0.45f, -0.49f)
private val FW_Y = floatArrayOf(0f, 0.1f, 0.19f, 0.26f, 0.3f, 0.3f, 0.27f, 0.24f, 0.22f, 0.14f, 0.09f, 0.05f, 0f)

/**
 * Red planaria (the acoel Convolutriloba retrogemma): flat and rust-red from its symbiotic algae, with a paler rim, a
 * three-lobed hind end and a pale statocyst at the front. It glides on cilia with a ripple running back along its edge.
 */
private fun DrawScope.flatworm(m: Mote, t: Float, look: Look, lod: Int, px: Float) {
    val body = look.body(m.group)
    if (lod == 0) {
        ov(body, 0.02f, 0f, 0.95f, 0.56f)
        for (s in PM) ov(body, -0.4f, s * 0.17f, 0.2f, 0.12f)
        return
    }
    val ripple = if (m.state == 0) 1f else 0.3f
    val half = FW_X.size
    var q = 0
    for (k in 0 until half) {
        XS[q] = FW_X[k]
        YS[q] = FW_Y[k] * (1f + 0.04f * ripple * sin(q * 1.1f - t * 4f))
        q++
    }
    for (k in half - 2 downTo 1) {
        XS[q] = FW_X[k]
        YS[q] = -FW_Y[k] * (1f + 0.04f * ripple * sin(q * 1.1f - t * 4f))
        q++
    }
    smoothClosed(q)
    drawPath(P, body)
    drawPath(P, fade(mix(body, Color(0xFFE08A6A), 0.7f), 0.8f), style = Stroke(width = 0.035f))
    // the symbionts mottle it darker; a few pale spots
    val mottle = fade(shade(body, 0.72f), 0.6f)
    val nm = if (lod == 2) 28 else 14
    for (k in 0 until nm) {
        val x = -0.32f + 0.7f * h01(k, m.r1)
        val y = (h01(k + 50, m.r2) - 0.5f) * 0.42f
        dot(mottle, x, y, 0.022f)
    }
    if (lod == 2) {
        val pale = fade(Color.White, 0.3f)
        for (k in 0 until 6) dot(pale, -0.25f + 0.55f * h01(k + 90, m.r1), (h01(k + 70, m.r2) - 0.5f) * 0.3f, 0.01f)
    }
    // statocyst with its statolith
    dot(fade(Color.White, 0.75f), 0.36f, 0f, 0.03f)
    dot(shade(body, 0.5f), 0.36f, 0f, 0.012f)
}

// ================================================================== helpers

/** Scratch path shared by the sprites (the draw pass runs on the main thread only). */
private val P = Path()
/** Scratch outline points for [smoothClosed]. */
private val XS = FloatArray(64)
private val YS = FloatArray(64)

/** Fills [P] with a closed Catmull-Rom curve through the first [n] points of [XS], [YS]. */
private fun smoothClosed(n: Int) {
    P.reset()
    P.moveTo(XS[0], YS[0])
    for (i in 0 until n) {
        val i0 = (i - 1 + n) % n
        val i2 = (i + 1) % n
        val i3 = (i + 2) % n
        P.cubicTo(
            XS[i] + (XS[i2] - XS[i0]) / 6f, YS[i] + (YS[i2] - YS[i0]) / 6f,
            XS[i2] - (XS[i3] - XS[i]) / 6f, YS[i2] - (YS[i3] - YS[i]) / 6f,
            XS[i2], YS[i2],
        )
    }
    P.close()
}

/**
 * A jointed appendage from (x, y): [n] articles of length [len], starting at angle [a] and turning [bend] at each
 * joint, thinning toward the tip. With [setaLen] > 0 each joint carries a bristle angled toward [side]. Returns the tip.
 */
private fun DrawScope.jointed(
    c: Color, x: Float, y: Float, a: Float, n: Int, len: Float, bend: Float, w: Float,
    setaC: Color = c, setaLen: Float = 0f, side: Float = 1f,
): Offset {
    var x0 = x
    var y0 = y
    var ang = a
    for (k in 0 until n) {
        val x1 = x0 + cos(ang) * len
        val y1 = y0 + sin(ang) * len
        drawLine(c, Offset(x0, y0), Offset(x1, y1), strokeWidth = w * (1f - 0.45f * k / n), cap = StrokeCap.Round)
        if (setaLen > 0f) {
            val sa = ang + side * 0.8f
            drawLine(setaC, Offset(x1, y1), Offset(x1 + cos(sa) * setaLen, y1 + sin(sa) * setaLen), strokeWidth = w * 0.3f)
        }
        x0 = x1
        y0 = y1
        ang += bend
    }
    return Offset(x0, y0)
}

private fun DrawScope.bar(c: Color, x1: Float, y1: Float, x2: Float, y2: Float, w: Float) =
    drawLine(c, Offset(x1, y1), Offset(x2, y2), strokeWidth = w, cap = StrokeCap.Round)

private fun DrawScope.ov(c: Color, cx: Float, cy: Float, w: Float, h: Float) = drawOval(c, Offset(cx - w / 2f, cy - h / 2f), Size(w, h))

private fun DrawScope.dot(c: Color, x: Float, y: Float, r: Float) = drawCircle(c, r, Offset(x, y))

/** Draws [block] flipped top to bottom (a side-view animal heading left). */
private inline fun DrawScope.mirrored(flip: Boolean, block: DrawScope.() -> Unit) {
    if (flip) withTransform({ scale(1f, -1f, Offset.Zero) }, block) else block()
}

/** Outline ink: dark under the bright-field lamp, a darker shade of the body in reflected light. */
private fun edgeOf(body: Color, look: Look): Color = look.outline?.copy(alpha = 0.75f) ?: shade(body, 0.55f)

private fun limbOf(body: Color, look: Look): Color = if (look.bright) INK_LIMB else shade(body, 0.8f)

private fun setaOf(body: Color, look: Look): Color = if (look.bright) INK_SETA else fade(mix(body, Color.White, 0.45f), 0.6f)

private fun shade(c: Color, k: Float): Color = Color(c.red * k, c.green * k, c.blue * k, c.alpha)

private fun mix(a: Color, b: Color, f: Float): Color =
    Color(a.red + (b.red - a.red) * f, a.green + (b.green - a.green) * f, a.blue + (b.blue - a.blue) * f, a.alpha)

private fun fade(c: Color, k: Float): Color = c.copy(alpha = c.alpha * k)

/** One coordinate of a cubic Bézier at [u]. */
private fun bez(u: Float, a: Float, b: Float, c: Float, d: Float): Float {
    val v = 1f - u
    return v * v * v * a + 3f * v * v * u * b + 3f * v * u * u * c + u * u * u * d
}

private fun fr(v: Float): Float = v - floor(v)

private fun smooth(x: Float): Float = x * x * (3f - 2f * x)

/** Stable 0..1 pseudo-random for fixed per-animal detail (spots, pores, tentacle lengths). */
private fun h01(i: Int, seed: Float): Float = fr(sin(i * 12.9898f + seed * 78.233f) * 43758.547f)

private val PM = floatArrayOf(-1f, 1f)
private val QUARTERS = floatArrayOf(0.25f, 0.5f, 0.75f)
private const val TWO_PI = 6.2831855f
private const val PI_F = 3.1415927f
private const val DEG_F = 57.29578f
/** Sprites at least this long on screen (px) draw their anatomy; shorter ones a silhouette. */
private const val DETAIL_PX = 24f
/** From this length (px) the fine structure too: setae, eggs, granules, spots. */
private const val FINE_PX = 64f

private val EYE_RED = Color(0xFFB0302A)
private val EYE_DARK = Color(0xFF1E1A16)
private val OSTRACOD_EYE = Color(0xFF5A2A20)
private val EYE_GLOW = Color(0xFFFFF4C8)
private val GLINT = Color(0x99FFFFFF)
/** Egg sacs and broods: grey-brown and granular (§5.4). */
private val EGG = Color(0xFF8A7A5C)
private val OIL = Color(0xFFF08A2C)
private val TIGRIOPUS = Color(0xFFD9502A)
private val TIGRIOPUS_GUT = Color(0xFFD9542B)
private val PURPLE = Color(0x997A3A7E)
private val TENTACLE_BLUE = Color(0xFFDCE6F2)
private val GILL_RED = Color(0xFFC0392B)
private val CHAETA = Color(0xFFF6F1E7)
private val NANNO = Color(0xFFB5C93A)
private val DETRITUS = Color(0xFF8B6B45)
private val PORCELAIN = Color(0xFFEADFC6)
private val INK_LIMB = Color(0x8C2E2A24)
private val INK_SETA = Color(0x592E2A24)
