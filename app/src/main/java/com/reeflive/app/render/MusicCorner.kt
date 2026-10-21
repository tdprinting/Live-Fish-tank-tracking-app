package com.reeflive.app.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The music corner left of the tank (tools/blender/music_corner.py): a record stand holding every radio record
 * (core RadioStation) and the radio on the side table. Positions are world cm in the tank frame and do not move with
 * the tank's size (every tank's left glass is at x = 0). Keep in step with music_corner.py.
 */
object MusicCornerLayout {
    const val MODEL = "models/music_corner.glb"
    const val RECORDS = 16
    const val DISC_R = 7.5f
    private const val COLS = 4
    private const val ROWS = 4
    private const val COL_X0 = -117.75f
    private const val COL_PITCH = 16.5f
    private const val ROW_Y0 = -70f
    private const val ROW_DY = 18f
    private const val ROW_Z0 = -14f
    private const val ROW_DZ = -6f
    /** The records lean back this far (rad) against the stand. */
    const val LEAN = 0.17f

    /** Record [i]'s centre (x, y, z) at rest: song 0 top left, four to a row. */
    fun disc(i: Int, out: FloatArray) {
        val col = i % COLS
        val row = ROWS - 1 - i / COLS
        val railY = ROW_Y0 + ROW_DY * row
        val railZ = ROW_Z0 + ROW_DZ * row
        out[0] = COL_X0 + COL_PITCH * col
        out[1] = railY + 0.25f + DISC_R * cos(LEAN)
        out[2] = railZ - 2.1f - DISC_R * sin(LEAN)
    }

    /** The radio's volume and tuning knobs: centre of the front face (x, y, z) and radius. */
    val VOLUME = floatArrayOf(-20.5f, -16f, 28f, 2.8f)
    val TUNE = floatArrayOf(-27f, -16f, 28f, 2.3f)
    /** The dial's needle: x at the first and last station, y, z. */
    const val NEEDLE_X0 = -28.8f
    const val NEEDLE_X1 = -18.7f
    const val NEEDLE_Y = -8.6f
    const val NEEDLE_Z = 28.06f
    /** Tap boxes (min xyz, max xyz): the stand, and the radio with the table under it. */
    val STAND_BOX = floatArrayOf(-128f, -82f, -45f, -58f, 0f, -8f)
    val RADIO_BOX = floatArrayOf(-48f, -24f, 14f, -16f, -4f, 29.6f)
    val TABLE_BOX = floatArrayOf(-50f, -82f, 5f, -14f, -24f, 39f)
    /** The music corner view ([Camera.lookAtMusic]): aim point, and the half width / height it keeps on screen. */
    val VIEW_AIM = floatArrayOf(-62f, -54f, 4f)
    const val VIEW_HALF_W = 80f
    const val VIEW_HALF_H = 62f
}

/** What the hosts tell the renderer about the music corner, and what it hears back. Any thread writes. */
class MusicCornerControl {
    /** The song playing (RadioStation index), or -1 with the radio off. */
    @Volatile var playing: Int = -1
    /** Records that can be played (bit i = song i: free or bought); the rest stand dimmed. */
    @Volatile var ownedMask: Int = 0
    /** Music volume 0..1 (the knob's angle while nobody is turning it). */
    @Volatile var volume: Float = 0.6f
    /** A locked record pulled forward to show its price (the host's buy bar), or -1. */
    @Volatile var selected: Int = -1
    /** A record was tapped (song index). */
    @Volatile var onRecord: ((Int) -> Unit)? = null
    /** The volume knob turned: the new volume and whether the finger lifted. */
    @Volatile var onVolume: ((Float, Boolean) -> Unit)? = null
    /** The tuning knob was tapped: on to the next song. */
    @Volatile var onTune: (() -> Unit)? = null
    /** The radio itself was tapped: music on / off. */
    @Volatile var onPower: (() -> Unit)? = null
    /** The camera went to the music corner (true) or back to the tank (false). */
    @Volatile var onView: ((Boolean) -> Unit)? = null
    @Volatile internal var openRequest: Boolean? = null

    /** Glides the camera to the music corner ([on]) or back to the tank. */
    fun open(on: Boolean) { openRequest = on }
}

/**
 * GL-thread side of the music corner: draws the stand, the records (the one playing pulled out and turning, locked
 * ones dimmed), the radio with its lit dial, knobs and needle, and answers taps. Static, about 30 draw calls, no
 * per-frame allocation.
 */
internal class MusicCornerScene {
    private var model: GpuModel? = null
    private var discs: Array<Array<DrawPart>> = emptyArray()
    private var fixed: Array<DrawPart> = emptyArray()
    private var tune: Array<DrawPart> = emptyArray()
    private var volume: Array<DrawPart> = emptyArray()
    private var needle: Array<DrawPart> = emptyArray()
    private val mat = FloatArray(16)
    private val c = FloatArray(3)
    private val box = FloatArray(6)
    private val pop = FloatArray(MusicCornerLayout.RECORDS)
    private var spin = 0f
    private var tuneTurn = 0f
    private var tuneGoal = 0f
    private var needleX = Float.NaN
    private var knob = 0.6f
    /** The knob's angle follows the finger while it turns it (not the host's volume, which catches up after). */
    var turning = false
    var turningVolume = 0.6f

    fun ready(assets: (String) -> GpuModel?): Boolean {
        val m = assets(MusicCornerLayout.MODEL) ?: return false
        if (m !== model) {
            model = m
            discs = Array(MusicCornerLayout.RECORDS) { i ->
                val name = if (i < 10) "Disc0$i" else "Disc$i"
                m.parts.filter { it.node.name == name }.toTypedArray()
            }
            fixed = m.parts.filter { it.node.name == "Stand" || it.node.name == "Radio" }.toTypedArray()
            tune = m.parts.filter { it.node.name == "KnobTune" }.toTypedArray()
            volume = m.parts.filter { it.node.name == "KnobVolume" }.toTypedArray()
            needle = m.parts.filter { it.node.name == "Needle" }.toTypedArray()
        }
        return true
    }

    /** Eases the records, knobs and needle toward [ctl]'s state. */
    fun update(ctl: MusicCornerControl, dt: Float) {
        val playing = ctl.playing
        val selected = ctl.selected
        for (i in pop.indices) {
            val goal = if (i == playing || i == selected) 1f else 0f
            pop[i] += (goal - pop[i]) * min(1f, dt * 7f)
        }
        if (playing >= 0) spin = (spin + dt * SPIN) % (2f * PI.toFloat())
        tuneTurn += (tuneGoal - tuneTurn) * min(1f, dt * 10f)
        val station = if (playing >= 0) playing else 0
        val nx = MusicCornerLayout.NEEDLE_X0 + (MusicCornerLayout.NEEDLE_X1 - MusicCornerLayout.NEEDLE_X0) * station / (MusicCornerLayout.RECORDS - 1)
        needleX = if (needleX.isNaN()) nx else needleX + (nx - needleX) * min(1f, dt * 4f)
        val v = if (turning) turningVolume else ctl.volume
        knob += (v - knob) * min(1f, dt * 14f)
    }

    /** The tuning knob clicks round a notch. */
    fun clickTune() { tuneGoal -= 2f * PI.toFloat() / 12f }

    /** Draws everything opaque with the room program (its room uniforms already set by HomeRoomScene.draw). */
    fun draw(p: ShaderProgram, white: Texture, ctl: MusicCornerControl): Int {
        if (model == null) return 0
        var calls = 0
        p.set1f("uGrain", 0f)
        p.set1f("uGlowOn", 0f)
        p.set1f("uKind", 0f)
        val on = ctl.playing >= 0
        for (part in fixed) {
            part.material.apply(p, white)
            if (part.material.src.name == "dial_face" && on) p.set3f("uEmissive", 0.55f, 0.36f, 0.12f)
            p.set1f("uGrain", if ("wood" in part.material.src.name) 1f else 0f)
            p.setMat4("uModel", part.node.world)
            part.mesh.draw()
            calls++
        }
        p.set1f("uGrain", 0f)
        val owned = ctl.ownedMask
        for (i in discs.indices) {
            MusicCornerLayout.disc(i, c)
            val k = smooth(pop[i])
            val lean = MusicCornerLayout.LEAN * (1f - 0.6f * k)
            // pulled up out of the groove and toward the viewer
            c[1] += k * 3.5f
            c[2] += k * 4f
            place(c[0], c[1], c[2], -lean, if (i == ctl.playing) spin else 0f)
            val locked = owned and (1 shl i) == 0
            for (part in discs[i]) {
                part.material.apply(p, white)
                // a record not bought yet stands greyed out (linear: about half as bright, and a little blue)
                if (locked) {
                    val b = part.material.src.baseColor
                    p.set4f("uBaseColor", b[0] * 0.15f, b[1] * 0.16f, b[2] * 0.19f, 1f)
                }
                p.setMat4("uModel", mat)
                part.mesh.draw()
                calls++
            }
        }
        val vol = MusicCornerLayout.VOLUME
        calls += knob(p, white, volume, vol, (0.75f - 1.5f * knob) * PI.toFloat())
        calls += knob(p, white, tune, MusicCornerLayout.TUNE, tuneTurn)
        place(needleX, MusicCornerLayout.NEEDLE_Y, MusicCornerLayout.NEEDLE_Z, 0f, 0f)
        for (part in needle) {
            part.material.apply(p, white)
            p.setMat4("uModel", mat)
            part.mesh.draw()
            calls++
        }
        return calls
    }

    private fun knob(p: ShaderProgram, white: Texture, parts: Array<DrawPart>, at: FloatArray, turn: Float): Int {
        place(at[0], at[1], at[2], 0f, turn)
        for (part in parts) {
            part.material.apply(p, white)
            p.setMat4("uModel", mat)
            part.mesh.draw()
        }
        return parts.size
    }

    /** [mat] = translate(x, y, z) · rotX(tilt) · rotZ(turn). */
    private fun place(x: Float, y: Float, z: Float, tilt: Float, turn: Float) {
        val ct = cos(tilt); val st = sin(tilt)
        val cf = cos(turn); val sf = sin(turn)
        mat[0] = cf; mat[1] = ct * sf; mat[2] = st * sf; mat[3] = 0f
        mat[4] = -sf; mat[5] = ct * cf; mat[6] = st * cf; mat[7] = 0f
        mat[8] = 0f; mat[9] = -st; mat[10] = ct; mat[11] = 0f
        mat[12] = x; mat[13] = y; mat[14] = z; mat[15] = 1f
    }

    /** What a ray from [o] along [d] hits first: a record (0..15), [TUNE], [VOLUME], [RADIO], or [NONE]. */
    fun pick(o: FloatArray, d: FloatArray, slack: Float): Int {
        var best = NONE
        var bestT = Float.MAX_VALUE
        for (i in 0 until MusicCornerLayout.RECORDS) {
            MusicCornerLayout.disc(i, c)
            val r = MusicCornerLayout.DISC_R
            box[0] = c[0] - r; box[1] = c[1] - r; box[2] = c[2] - 2f
            box[3] = c[0] + r; box[4] = c[1] + r; box[5] = c[2] + 2f
            val t = SupplyShelfScene.rayBox(o, d, box, slack * 0.3f) ?: continue
            if (t < bestT) { bestT = t; best = i }
        }
        val knobT = knobHit(o, d, MusicCornerLayout.VOLUME, slack)
        if (knobT != null && knobT < bestT + 1f) { bestT = knobT; best = VOLUME }
        val tuneT = knobHit(o, d, MusicCornerLayout.TUNE, slack * 0.6f)
        if (tuneT != null && tuneT < bestT + 1f) { bestT = tuneT; best = TUNE }
        if (best == NONE) SupplyShelfScene.rayBox(o, d, MusicCornerLayout.RADIO_BOX, slack * 0.3f)?.let { best = RADIO }
        return best
    }

    private fun knobHit(o: FloatArray, d: FloatArray, k: FloatArray, slack: Float): Float? {
        val r = k[3] + slack * 0.4f
        box[0] = k[0] - r; box[1] = k[1] - r; box[2] = k[2] - 0.5f
        box[3] = k[0] + r; box[4] = k[1] + r; box[5] = k[2] + 2f
        return SupplyShelfScene.rayBox(o, d, box, 0f)
    }

    companion object {
        const val NONE = -1
        const val TUNE = -2
        const val VOLUME = -3
        const val RADIO = -4
        /** A turning record, rad/s (a slow, calm turn). */
        private const val SPIN = 1.6f
        private val BOXES = arrayOf(MusicCornerLayout.STAND_BOX, MusicCornerLayout.RADIO_BOX, MusicCornerLayout.TABLE_BOX)

        private fun smooth(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

        /** Distance along the ray to the music corner (the stand, the radio or its table), or null. */
        fun hit(o: FloatArray, d: FloatArray): Float? {
            var t: Float? = null
            for (b in BOXES) {
                val h = SupplyShelfScene.rayBox(o, d, b, 1f) ?: continue
                if (t == null || h < t) t = h
            }
            return t
        }
    }
}
