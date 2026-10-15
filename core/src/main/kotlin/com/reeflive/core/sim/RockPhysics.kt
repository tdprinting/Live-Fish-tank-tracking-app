package com.reeflive.core.sim

import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Loose rocks as rigid bodies (dynamic rockscape). Rocks stay put while they are supported; when animals dig the sand
 * out from under one side (the engineer goby's tunnels, the diamond goby's sifting) a rock whose centre of mass no
 * longer sits over what still holds it settles into the hole or topples, then comes to rest and sleeps again.
 *
 * Physics only runs for awake rocks. Sleeping rocks get a cheap support check (centre of mass inside the support
 * polygon of the probe points still resting on glass, sand or another rock) every [CHECK_S] of sim time, and only when
 * the sand bed has changed. A rock that already fails that check in the tank as built is treated as glued to the rock
 * it leans on (reefers epoxy or pin such pieces), so the two move as one body.
 *
 * Bodies: saturated live rock ([RHO_ROCK]) under buoyancy, added mass and quadratic drag; contacts at surface probes
 * (one per boundary cell of the rock's grid cells) against the glass, the sand (which only resists downward motion on
 * the rock's underside: grains flow away sideways) and the other rocks' distance fields, solved by sequential impulses
 * with Coulomb friction.
 */
class RockPhysics(
    val field: DynamicRockSdf,
    private val sand: SandBed,
    private val lengthCm: Double = TankDims.LENGTH_CM,
    private val widthCm: Double = TankDims.DEPTH_CM,
) {
    val bodies = ArrayList<RockBody>()
    /** Body index of each piece, −1 for a piece without cells. */
    val bodyOf = IntArray(field.pieces.size) { -1 }
    var built = false; private set
    /** Bumps whenever a rock pose changes (renderer refresh, snapshot cache). */
    var version = 0L; private set
    val anyAwake: Boolean get() = bodies.any { it.awake }
    val anyMoved: Boolean get() = bodies.any { it.moved }

    /** Rocks that came to rest since the last [drainSettled] (the sim refreshes sand, anchors and corals and reports). */
    private val settled = ArrayList<Settle>()
    class Settle(val body: RockBody, val moveCm: Double, val turnDeg: Double, val x: Double, val y: Double, val z: Double)

    private var checkAcc = CHECK_S
    private var lastSandVersion = Long.MIN_VALUE
    private var refreshAcc = 0.0
    private var stepAcc = 0.0

    // ============================================================== build
    /** Labels the field, makes one body per rock and glues rocks that would not stand alone (≈ 0.1–0.4 s, once). */
    fun build() {
        if (built) return
        field.prepare()
        bodies.clear()
        for (p in field.pieces.indices) if (field.pieceBox[p] != null) bodies += makeBody(intArrayOf(p))
        indexBodies()
        // glue: a rock that does not stand on its own in the tank as built leans on its neighbour (pinned / epoxied)
        var changed = true
        var guard = 0
        while (changed && guard++ < 32) {
            changed = false
            for (b in bodies.toList()) {
                if (b.pinned || stable(b)) continue
                val nb = mostTouched(b)
                if (nb == null) { b.pinned = true; continue }
                bodies.remove(b); bodies.remove(nb)
                bodies += makeBody((b.pieces + nb.pieces).sortedArray())
                indexBodies()
                changed = true
                break
            }
        }
        built = true
    }

    private fun indexBodies() {
        bodyOf.fill(-1)
        bodies.forEachIndexed { i, b -> b.index = i; for (p in b.pieces) bodyOf[p] = i }
    }

    private fun mostTouched(b: RockBody): RockBody? {
        val count = IntArray(bodies.size)
        for (i in 0 until b.np) { val l = b.restPiece[i]; if (l >= 0) { val j = bodyOf[l]; if (j >= 0 && j != b.index) count[j]++ } }
        var best = -1
        for (j in count.indices) if (count[j] > 0 && (best < 0 || count[j] > count[best])) best = j
        return if (best >= 0) bodies[best] else null
    }

    /** A body from its pieces' labelled cells: mass, centre, inertia and surface probes. */
    private fun makeBody(pieces: IntArray): RockBody {
        val f = field
        val inSet = BooleanArray(f.pieces.size).also { s -> pieces.forEach { s[it] = true } }
        val box = IntArray(6) { if (it < 3) Int.MAX_VALUE else -1 }
        for (p in pieces) { val bx = f.pieceBox[p]!!; for (c in 0 until 3) { box[c] = min(box[c], bx[c]); box[c + 3] = max(box[c + 3], bx[c + 3]) } }
        fun member(i: Int, j: Int, k: Int): Boolean {
            if (i < 0 || j < 0 || k < 0 || i >= f.nx || j >= f.ny || k >= f.nz) return false
            val l = f.label[f.idx(i, j, k)].toInt()
            return l >= 0 && inSet[l]
        }
        val cv = f.cell * f.cell * f.cell
        var n = 0; var sx = 0.0; var sy = 0.0; var sz = 0.0
        for (k in box[2]..box[5]) for (j in box[1]..box[4]) for (i in box[0]..box[3]) if (member(i, j, k)) {
            n++; sx += f.cx(i); sy += f.cy(j); sz += f.cz(k)
        }
        val b = RockBody(pieces, pieces.joinToString("+") { f.pieces[it].name })
        b.volume = n * cv
        b.mass = RHO_ROCK * b.volume
        // added mass of the displaced water (half a sphere's worth) for the translational inertia
        b.invMass = 1.0 / (b.mass + 0.5 * RHO_WATER * b.volume)
        b.comRest[0] = sx / n; b.comRest[1] = sy / n; b.comRest[2] = sz / n
        b.pos[0] = b.comRest[0]; b.pos[1] = b.comRest[1]; b.pos[2] = b.comRest[2]
        val ib = DoubleArray(9)
        val mc = RHO_ROCK * cv
        val own = mc * f.cell * f.cell / 6.0
        val px = ArrayList<Double>(); val py = ArrayList<Double>(); val pz = ArrayList<Double>()
        val qx = ArrayList<Double>(); val qy = ArrayList<Double>(); val qz = ArrayList<Double>()
        val rp = ArrayList<Int>()
        for (k in box[2]..box[5]) for (j in box[1]..box[4]) for (i in box[0]..box[3]) {
            if (!member(i, j, k)) continue
            val rx = f.cx(i) - b.comRest[0]; val ry = f.cy(j) - b.comRest[1]; val rz = f.cz(k) - b.comRest[2]
            ib[0] += mc * (ry * ry + rz * rz) + own; ib[4] += mc * (rx * rx + rz * rz) + own; ib[8] += mc * (rx * rx + ry * ry) + own
            ib[1] -= mc * rx * ry; ib[2] -= mc * rx * rz; ib[5] -= mc * ry * rz
            // boundary: any of the 26 neighbours outside the body; the outward normal points at the empty side
            var ex = 0.0; var ey = 0.0; var ez = 0.0; var face = false
            var touch = -1
            for (dk in -1..1) for (dj in -1..1) for (di in -1..1) {
                if (di == 0 && dj == 0 && dk == 0) continue
                if (member(i + di, j + dj, k + dk)) continue
                val w = 1.0 / sqrt((di * di + dj * dj + dk * dk).toDouble())
                ex += di * w; ey += dj * w; ez += dk * w
                if (abs(di) + abs(dj) + abs(dk) == 1) face = true
                val ii = i + di; val jj = j + dj; val kk = k + dk
                if (ii in 0 until f.nx && jj in 0 until f.ny && kk in 0 until f.nz) {
                    val l = f.label[f.idx(ii, jj, kk)].toInt()
                    if (l >= 0 && !inSet[l]) touch = l
                }
            }
            if (!face) continue
            val el = sqrt(ex * ex + ey * ey + ez * ez)
            if (el < 1e-9) continue
            px += rx; py += ry; pz += rz; qx += ex / el; qy += ey / el; qz += ez / el; rp += touch
        }
        ib[3] = ib[1]; ib[6] = ib[2]; ib[7] = ib[5]
        b.inertia = ib
        b.invInertia = Mat3.inverse(ib)
        // keep the probe count bounded: every underside probe up to MAX_BOTTOM, the rest strided to MAX_OTHER
        val bottom = px.indices.filter { qy[it] < -BOTTOM_NY }
        val other = px.indices.filter { qy[it] >= -BOTTOM_NY }
        val keep = stride(bottom, MAX_BOTTOM) + stride(other, MAX_OTHER)
        b.np = keep.size
        b.rx = DoubleArray(b.np) { px[keep[it]] }; b.ry = DoubleArray(b.np) { py[keep[it]] }; b.rz = DoubleArray(b.np) { pz[keep[it]] }
        b.nxL = DoubleArray(b.np) { qx[keep[it]] }; b.nyL = DoubleArray(b.np) { qy[keep[it]] }; b.nzL = DoubleArray(b.np) { qz[keep[it]] }
        b.restPiece = IntArray(b.np) { rp[keep[it]] }
        b.extent = sqrt(b.rx.indices.maxOfOrNull { b.rx[it] * b.rx[it] + b.ry[it] * b.ry[it] + b.rz[it] * b.rz[it] } ?: 1.0)
        return b
    }

    private fun stride(ids: List<Int>, max: Int): List<Int> {
        if (ids.size <= max) return ids
        val step = ids.size.toDouble() / max
        return List(max) { ids[(it * step).toInt()] }
    }

    // ============================================================== support check
    private val hull = Hull()
    private val supX = DoubleArray(MAX_BOTTOM + MAX_OTHER)
    private val supZ = DoubleArray(MAX_BOTTOM + MAX_OTHER)

    /** Whether [b] stands where it is: ≥ 3 support points and its centre of mass inside their polygon (with margin). */
    fun stable(b: RockBody): Boolean {
        val r = b.rot
        var n = 0; var g = 0; var sd = 0; var rk = 0
        for (i in 0 until b.np) {
            val lx = b.rx[i]; val ly = b.ry[i]; val lz = b.rz[i]
            val x = b.pos[0] + r[0] * lx + r[1] * ly + r[2] * lz
            val y = b.pos[1] + r[3] * lx + r[4] * ly + r[5] * lz
            val z = b.pos[2] + r[6] * lx + r[7] * ly + r[8] * lz
            val ny = r[3] * b.nxL[i] + r[4] * b.nyL[i] + r[5] * b.nzL[i]
            val under = y - PROBE_R
            val glass = under <= SandBed.GLASS_Y + GLASS_TOL ||
                (ny < ROCK_SUPPORT_NY && (x <= PROBE_R + GLASS_TOL || x >= lengthCm - PROBE_R - GLASS_TOL || z <= PROBE_R + GLASS_TOL || z >= widthCm - PROBE_R - GLASS_TOL))
            val onSand = !glass && ny < -BOTTOM_NY && sand.surfaceY(x.coerceIn(0.0, lengthCm), z.coerceIn(0.0, widthCm)) >= under - SAND_TOL
            val onRock = !glass && !onSand && ny < ROCK_SUPPORT_NY && rockSupport(b, i, x, y, z)
            if (glass) g++ else if (onSand) sd++ else if (onRock) rk++
            if (glass || onSand || onRock) { supX[n] = x; supZ[n] = z; n++ }
        }
        b.supports = n; b.supGlass = g; b.supSand = sd; b.supRock = rk
        if (n < 3) return false
        return hull.containsWithMargin(supX, supZ, n, b.pos[0], b.pos[2], STABLE_MARGIN)
    }

    private fun rockSupport(b: RockBody, i: Int, x: Double, y: Double, z: Double): Boolean {
        if (!b.moved) {
            val l = b.restPiece[i]
            if (l >= 0) { val j = bodyOf[l]; if (j >= 0 && j != b.index && !bodies[j].moved) return true }
        } else if (field.staticDist(x, y, z) <= CONTACT_D) return true
        for (o in bodies) {
            if (o === b || !o.moved) continue
            for (p in o.pieces) if (field.pieceDist(p, x, y, z) <= CONTACT_D) return true
        }
        return false
    }

    // ============================================================== stepping
    /**
     * Advances the rocks by [dt] sim seconds. Asleep and unchanged: one counter add. Returns true when a pose changed
     * (the caller refreshes the snapshot's rock poses).
     */
    fun step(dt: Double): Boolean {
        if (!built) return false
        checkAcc += dt
        val awake = anyAwake
        if (checkAcc >= (if (awake) CHECK_AWAKE_S else CHECK_S)) {
            checkAcc = 0.0
            if (awake || sand.version != lastSandVersion) {
                lastSandVersion = sand.version
                for (b in bodies) if (!b.awake && !b.pinned && b.cooldown <= 0.0 && !stable(b)) wake(b)
            }
        }
        for (b in bodies) if (b.cooldown > 0) b.cooldown -= dt
        if (!anyAwake) return false
        stepAcc += dt
        var subs = 0
        while (stepAcc >= H && subs < MAX_SUBSTEPS) {
            stepAcc -= H; subs++
            for (b in bodies) if (b.awake) substep(b)
        }
        if (subs >= MAX_SUBSTEPS) stepAcc = 0.0
        refreshAcc += dt
        if (refreshAcc >= REFRESH_S || !anyAwake) { refreshAcc = 0.0; field.refresh() }
        version++
        return true
    }

    /** Starts simulating [b] (lifts it out of the static rock on first move). */
    fun wake(b: RockBody) {
        if (b.awake || b.pinned) return
        if (!b.moved) { field.markMoved(b.pieces); b.moved = true }
        b.awake = true; b.sleepT = 0.0; b.awakeT = 0.0
        b.pos.copyInto(b.wakePos); b.q.copyInto(b.wakeQ)
        b.wakes++
    }

    fun drainSettled(): List<Settle> = if (settled.isEmpty()) emptyList() else settled.toList().also { settled.clear() }

    // contact scratch (one body at a time)
    private val cRx = DoubleArray(MAX_CONTACTS); private val cRy = DoubleArray(MAX_CONTACTS); private val cRz = DoubleArray(MAX_CONTACTS)
    private val cNx = DoubleArray(MAX_CONTACTS); private val cNy = DoubleArray(MAX_CONTACTS); private val cNz = DoubleArray(MAX_CONTACTS)
    private val cBias = DoubleArray(MAX_CONTACTS); private val cMu = DoubleArray(MAX_CONTACTS); private val cPen = DoubleArray(MAX_CONTACTS)
    private val cKn = DoubleArray(MAX_CONTACTS); private val cAn = DoubleArray(MAX_CONTACTS)
    private val cT1 = DoubleArray(MAX_CONTACTS * 3); private val cT2 = DoubleArray(MAX_CONTACTS * 3)
    private val cK1 = DoubleArray(MAX_CONTACTS); private val cK2 = DoubleArray(MAX_CONTACTS)
    private val cA1 = DoubleArray(MAX_CONTACTS); private val cA2 = DoubleArray(MAX_CONTACTS)
    private val iw = DoubleArray(9)
    private var nc = 0

    private fun substep(b: RockBody) {
        val r = b.rot
        Mat3.rotateInertia(r, b.invInertia, iw)
        // gravity less buoyancy, on the saturated mass; quadratic drag on a rough rock (Cd ≈ 1)
        b.v[1] -= G_CM * (1.0 - RHO_WATER / RHO_ROCK) * b.mass * b.invMass * H
        val sp = sqrt(b.v[0] * b.v[0] + b.v[1] * b.v[1] + b.v[2] * b.v[2])
        val area = 1.2 * cbrt(b.volume * b.volume)
        val kd = 0.5 * RHO_WATER * 1.0 * area * b.invMass
        val damp = 1.0 / (1.0 + kd * sp * H)
        b.v[0] *= damp; b.v[1] *= damp; b.v[2] *= damp
        val wd = 1.0 / (1.0 + ANG_DAMP * H)
        b.w[0] *= wd; b.w[1] *= wd; b.w[2] *= wd

        gather(b, r)
        for (c in 0 until nc) prepare(b, c)
        repeat(ITERATIONS) { for (c in 0 until nc) solve(b, c) }

        b.pos[0] += b.v[0] * H; b.pos[1] += b.v[1] * H; b.pos[2] += b.v[2] * H
        Quat4.integrate(b.q, b.w, H)
        Quat4.toMat(b.q, b.rot)
        publish(b)

        b.awakeT += H
        val ws = sqrt(b.w[0] * b.w[0] + b.w[1] * b.w[1] + b.w[2] * b.w[2])
        val vs = sqrt(b.v[0] * b.v[0] + b.v[1] * b.v[1] + b.v[2] * b.v[2])
        if (vs < V_SLEEP && ws < W_SLEEP) b.sleepT += H else b.sleepT = 0.0
        if (b.sleepT >= SLEEP_S || b.awakeT >= MAX_AWAKE_S) sleep(b)
    }

    private fun sleep(b: RockBody) {
        b.awake = false
        b.v.fill(0.0); b.w.fill(0.0)
        val dx = b.pos[0] - b.wakePos[0]; val dy = b.pos[1] - b.wakePos[1]; val dz = b.pos[2] - b.wakePos[2]
        val move = sqrt(dx * dx + dy * dy + dz * dz)
        val dot = abs(b.q[0] * b.wakeQ[0] + b.q[1] * b.wakeQ[1] + b.q[2] * b.wakeQ[2] + b.q[3] * b.wakeQ[3]).coerceAtMost(1.0)
        val turn = 2 * acos(dot) * 180.0 / PI
        // settled without really going anywhere: the support check was pessimistic, leave it be for a while
        if (move < 0.3 && turn < 1.0) b.cooldown = COOLDOWN_S
        field.refresh()
        settled += Settle(b, move, turn, b.pos[0], b.pos[1], b.pos[2])
    }

    /** Pushes [b]'s pose into the field for its pieces. */
    private val xfTmp = DoubleArray(12)
    private fun publish(b: RockBody) {
        val r = b.rot
        r.copyInto(xfTmp, 0, 0, 9)
        xfTmp[9] = b.pos[0] - (r[0] * b.comRest[0] + r[1] * b.comRest[1] + r[2] * b.comRest[2])
        xfTmp[10] = b.pos[1] - (r[3] * b.comRest[0] + r[4] * b.comRest[1] + r[5] * b.comRest[2])
        xfTmp[11] = b.pos[2] - (r[6] * b.comRest[0] + r[7] * b.comRest[1] + r[8] * b.comRest[2])
        for (p in b.pieces) field.setTransform(p, xfTmp)
    }

    /** Distance from (x, y, z) to everything [b] can hit but the glass and sand: static rock and the other moved rocks. */
    private fun env(b: RockBody, x: Double, y: Double, z: Double): Double {
        var d = field.staticDist(x, y, z)
        for (o in bodies) {
            if (o === b || !o.moved) continue
            for (p in o.pieces) d = min(d, field.pieceDist(p, x, y, z))
        }
        return d
    }

    private fun gather(b: RockBody, r: DoubleArray) {
        nc = 0
        for (i in 0 until b.np) {
            if (nc >= MAX_CONTACTS - 2) break
            val lx = b.rx[i]; val ly = b.ry[i]; val lz = b.rz[i]
            val wx = r[0] * lx + r[1] * ly + r[2] * lz
            val wy = r[3] * lx + r[4] * ly + r[5] * lz
            val wz = r[6] * lx + r[7] * ly + r[8] * lz
            val x = b.pos[0] + wx; val y = b.pos[1] + wy; val z = b.pos[2] + wz
            // glass bottom and walls
            if (y < SandBed.GLASS_Y + PROBE_R) add(wx, wy, wz, 0.0, 1.0, 0.0, SandBed.GLASS_Y + PROBE_R - y, MU_GLASS, true)
            if (x < PROBE_R) add(wx, wy, wz, 1.0, 0.0, 0.0, PROBE_R - x, MU_GLASS, true)
            if (x > lengthCm - PROBE_R) add(wx, wy, wz, -1.0, 0.0, 0.0, x - (lengthCm - PROBE_R), MU_GLASS, true)
            if (z < PROBE_R) add(wx, wy, wz, 0.0, 0.0, 1.0, PROBE_R - z, MU_GLASS, true)
            if (z > widthCm - PROBE_R) add(wx, wy, wz, 0.0, 0.0, -1.0, z - (widthCm - PROBE_R), MU_GLASS, true)
            // sand: holds the underside up, never pushes the rock out of it
            val ny = r[3] * b.nxL[i] + r[4] * b.nyL[i] + r[5] * b.nzL[i]
            if (ny < -BOTTOM_NY && sand.surfaceY(x.coerceIn(0.0, lengthCm), z.coerceIn(0.0, widthCm)) >= y - PROBE_R)
                add(wx, wy, wz, 0.0, 1.0, 0.0, 0.0, MU_SAND, false)
            // other rock
            val e = env(b, x, y, z)
            if (e < PROBE_R + 0.05) {
                val h = 0.5
                var gx = env(b, x + h, y, z) - env(b, x - h, y, z)
                var gy = env(b, x, y + h, z) - env(b, x, y - h, z)
                var gz = env(b, x, y, z + h) - env(b, x, y, z - h)
                val gl = sqrt(gx * gx + gy * gy + gz * gz)
                if (gl > 1e-9) { gx /= gl; gy /= gl; gz /= gl; add(wx, wy, wz, gx, gy, gz, PROBE_R - e, MU_ROCK, true) }
            }
        }
    }

    private fun add(rx: Double, ry: Double, rz: Double, nx: Double, ny: Double, nz: Double, pen: Double, mu: Double, hard: Boolean) {
        if (nc >= MAX_CONTACTS) return
        cRx[nc] = rx; cRy[nc] = ry; cRz[nc] = rz; cNx[nc] = nx; cNy[nc] = ny; cNz[nc] = nz
        cPen[nc] = pen; cMu[nc] = mu
        cBias[nc] = if (hard) min(MAX_BIAS, BETA / H * max(0.0, pen - SLOP)) else 0.0
        nc++
    }

    private fun prepare(b: RockBody, c: Int) {
        val nx = cNx[c]; val ny = cNy[c]; val nz = cNz[c]
        cKn[c] = kAlong(b, c, nx, ny, nz)
        // tangents
        val t1x: Double; val t1y: Double; val t1z: Double
        if (abs(nx) < 0.9) { val l = sqrt(nz * nz + ny * ny); t1x = 0.0; t1y = nz / l; t1z = -ny / l }
        else { val l = sqrt(nz * nz + nx * nx); t1x = -nz / l; t1y = 0.0; t1z = nx / l }
        val t2x = ny * t1z - nz * t1y; val t2y = nz * t1x - nx * t1z; val t2z = nx * t1y - ny * t1x
        cT1[c * 3] = t1x; cT1[c * 3 + 1] = t1y; cT1[c * 3 + 2] = t1z
        cT2[c * 3] = t2x; cT2[c * 3 + 1] = t2y; cT2[c * 3 + 2] = t2z
        cK1[c] = kAlong(b, c, t1x, t1y, t1z); cK2[c] = kAlong(b, c, t2x, t2y, t2z)
        cAn[c] = 0.0; cA1[c] = 0.0; cA2[c] = 0.0
    }

    /** Effective mass denominator along unit (x, y, z) at contact [c]: 1/m + d·((I⁻¹(r×d))×r). */
    private fun kAlong(b: RockBody, c: Int, x: Double, y: Double, z: Double): Double {
        val rx = cRx[c]; val ry = cRy[c]; val rz = cRz[c]
        val ax = ry * z - rz * y; val ay = rz * x - rx * z; val az = rx * y - ry * x
        val bx = iw[0] * ax + iw[1] * ay + iw[2] * az
        val by = iw[3] * ax + iw[4] * ay + iw[5] * az
        val bz = iw[6] * ax + iw[7] * ay + iw[8] * az
        val cx = by * rz - bz * ry; val cy = bz * rx - bx * rz; val cz = bx * ry - by * rx
        return b.invMass + x * cx + y * cy + z * cz
    }

    private fun relVel(b: RockBody, c: Int, x: Double, y: Double, z: Double): Double {
        val rx = cRx[c]; val ry = cRy[c]; val rz = cRz[c]
        val w = b.w
        val vx = b.v[0] + w[1] * rz - w[2] * ry
        val vy = b.v[1] + w[2] * rx - w[0] * rz
        val vz = b.v[2] + w[0] * ry - w[1] * rx
        return vx * x + vy * y + vz * z
    }

    private fun impulse(b: RockBody, c: Int, jx: Double, jy: Double, jz: Double) {
        b.v[0] += jx * b.invMass; b.v[1] += jy * b.invMass; b.v[2] += jz * b.invMass
        val rx = cRx[c]; val ry = cRy[c]; val rz = cRz[c]
        val tx = ry * jz - rz * jy; val ty = rz * jx - rx * jz; val tz = rx * jy - ry * jx
        b.w[0] += iw[0] * tx + iw[1] * ty + iw[2] * tz
        b.w[1] += iw[3] * tx + iw[4] * ty + iw[5] * tz
        b.w[2] += iw[6] * tx + iw[7] * ty + iw[8] * tz
    }

    private fun solve(b: RockBody, c: Int) {
        val nx = cNx[c]; val ny = cNy[c]; val nz = cNz[c]
        val vn = relVel(b, c, nx, ny, nz)
        var dl = (cBias[c] - vn) / cKn[c]
        val na = max(0.0, cAn[c] + dl); dl = na - cAn[c]; cAn[c] = na
        if (dl != 0.0) impulse(b, c, nx * dl, ny * dl, nz * dl)
        val lim = cMu[c] * cAn[c]
        val o = c * 3
        val v1 = relVel(b, c, cT1[o], cT1[o + 1], cT1[o + 2])
        val a1 = (cA1[c] - v1 / cK1[c]).coerceIn(-lim, lim); val d1 = a1 - cA1[c]; cA1[c] = a1
        if (d1 != 0.0) impulse(b, c, cT1[o] * d1, cT1[o + 1] * d1, cT1[o + 2] * d1)
        val v2 = relVel(b, c, cT2[o], cT2[o + 1], cT2[o + 2])
        val a2 = (cA2[c] - v2 / cK2[c]).coerceIn(-lim, lim); val d2 = a2 - cA2[c]; cA2[c] = a2
        if (d2 != 0.0) impulse(b, c, cT2[o] * d2, cT2[o + 1] * d2, cT2[o + 2] * d2)
    }

    // ============================================================== outputs
    /** Rest → world 4×4 (column-major, GL order) per piece; identity for pieces that never moved. */
    fun poseMatrices(): FloatArray {
        val out = FloatArray(field.pieces.size * 16)
        for (p in field.pieces.indices) {
            val m = field.xf[p]
            val o = p * 16
            out[o] = m[0].toFloat(); out[o + 1] = m[3].toFloat(); out[o + 2] = m[6].toFloat()
            out[o + 4] = m[1].toFloat(); out[o + 5] = m[4].toFloat(); out[o + 6] = m[7].toFloat()
            out[o + 8] = m[2].toFloat(); out[o + 9] = m[5].toFloat(); out[o + 10] = m[8].toFloat()
            out[o + 12] = m[9].toFloat(); out[o + 13] = m[10].toFloat(); out[o + 14] = m[11].toFloat(); out[o + 15] = 1f
        }
        return out
    }

    /** Moved rocks as text (piece name: quaternion xyzw, then body centre xyz), for saving the tank between launches. */
    fun encode(): String = bodies.filter { it.moved }.joinToString(";") { b ->
        field.pieces[b.pieces[0]].name + ":" + (b.q.toList() + b.pos.toList()).joinToString(",") { ((it * 1e4).toLong() / 1e4).toString() }
    }

    /** Restores [encode]d poses (the bodies must be [build]t). Unknown names are skipped. */
    fun decode(s: String) {
        if (s.isBlank()) return
        for (part in s.split(';')) {
            val name = part.substringBefore(':')
            val v = part.substringAfter(':').split(',').mapNotNull { it.toDoubleOrNull() }
            if (v.size != 7) continue
            val p = field.pieces.indexOfFirst { it.name == name }.takeIf { it >= 0 } ?: continue
            val bi = bodyOf[p].takeIf { it >= 0 } ?: continue
            val b = bodies[bi]
            if (b.pieces[0] != p) continue
            if (!b.moved) { field.markMoved(b.pieces); b.moved = true }
            for (k in 0 until 4) b.q[k] = v[k]
            Quat4.normalize(b.q)
            Quat4.toMat(b.q, b.rot)
            for (k in 0 until 3) b.pos[k] = v[4 + k]
            publish(b)
        }
        field.refresh()
        version++
    }

    companion object {
        /** Saturated live rock: aragonite (2.93 g/cm³) at ≈ 50 % porosity filled with seawater. */
        const val RHO_ROCK = 1.95
        const val RHO_WATER = 1.025
        const val G_CM = 981.0
        /** Physics substep (s) and the most substeps per [step] (high sim speeds play a fall out over several ticks). */
        const val H = 1.0 / 120.0
        const val MAX_SUBSTEPS = 60
        const val ITERATIONS = 10
        /** Probes sit on boundary cell centres, half a cell inside the surface. */
        const val PROBE_R = 0.5
        const val BOTTOM_NY = 0.35
        const val ROCK_SUPPORT_NY = 0.5
        const val GLASS_TOL = 0.6
        const val SAND_TOL = 0.3
        const val CONTACT_D = 1.1
        const val STABLE_MARGIN = 0.4
        const val MAX_BOTTOM = 320
        const val MAX_OTHER = 320
        const val MAX_CONTACTS = 240
        /** Friction: wet glass, sand (angle of repose ~30–35°), rough limestone on limestone. */
        const val MU_GLASS = 0.45
        const val MU_SAND = 0.6
        const val MU_ROCK = 0.7
        const val BETA = 0.2
        const val SLOP = 0.05
        const val MAX_BIAS = 8.0
        const val ANG_DAMP = 1.5
        const val V_SLEEP = 0.35
        const val W_SLEEP = 0.02
        const val SLEEP_S = 0.4
        const val MAX_AWAKE_S = 15.0
        /** Support checks: every 2 s of sim time while all rocks sleep (and only if the sand changed), 4×/s while one moves. */
        const val CHECK_S = 2.0
        const val CHECK_AWAKE_S = 0.25
        const val REFRESH_S = 0.1
        const val COOLDOWN_S = 120.0
    }
}

/** One rigid rock (or rocks glued together). Rest frame = world frame as built; [comRest] its centre there. */
class RockBody(val pieces: IntArray, val name: String) {
    var index = 0
    var volume = 0.0
    var mass = 0.0
    var invMass = 0.0
    val comRest = DoubleArray(3)
    var inertia = DoubleArray(9)
    var invInertia = DoubleArray(9)
    var extent = 1.0
    // probes (rest frame, relative to comRest) and their outward normals; the other piece each touches as built
    var np = 0
    var rx = DoubleArray(0); var ry = DoubleArray(0); var rz = DoubleArray(0)
    var nxL = DoubleArray(0); var nyL = DoubleArray(0); var nzL = DoubleArray(0)
    var restPiece = IntArray(0)
    // state
    val pos = DoubleArray(3)
    val q = doubleArrayOf(0.0, 0.0, 0.0, 1.0)
    val rot = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
    val v = DoubleArray(3)
    val w = DoubleArray(3)
    var awake = false
    var moved = false
    /** Never moves: does not stand on its own and touches nothing to lean on. */
    var pinned = false
    var sleepT = 0.0
    var awakeT = 0.0
    var cooldown = 0.0
    var supports = 0
    var supGlass = 0
    var supSand = 0
    var supRock = 0
    var wakes = 0
    val wakePos = DoubleArray(3)
    val wakeQ = DoubleArray(4)
}

/** 3×3 row-major helpers. */
object Mat3 {
    fun inverse(m: DoubleArray): DoubleArray {
        val a = m[0]; val b = m[1]; val c = m[2]; val d = m[3]; val e = m[4]; val f = m[5]; val g = m[6]; val h = m[7]; val i = m[8]
        val A = e * i - f * h; val B = -(d * i - f * g); val C = d * h - e * g
        val det = a * A + b * B + c * C
        if (abs(det) < 1e-12) return DoubleArray(9)
        val s = 1.0 / det
        return doubleArrayOf(
            A * s, -(b * i - c * h) * s, (b * f - c * e) * s,
            B * s, (a * i - c * g) * s, -(a * f - c * d) * s,
            C * s, -(a * h - b * g) * s, (a * e - b * d) * s,
        )
    }

    /** out = R · M · Rᵀ. */
    fun rotateInertia(r: DoubleArray, m: DoubleArray, out: DoubleArray) {
        for (i in 0 until 3) for (j in 0 until 3) {
            var s = 0.0
            for (k in 0 until 3) for (l in 0 until 3) s += r[i * 3 + k] * m[k * 3 + l] * r[j * 3 + l]
            out[i * 3 + j] = s
        }
    }
}

/** Quaternion (x, y, z, w) helpers on arrays. */
object Quat4 {
    fun integrate(q: DoubleArray, w: DoubleArray, h: Double) {
        val x = q[0]; val y = q[1]; val z = q[2]; val s = q[3]
        val hx = 0.5 * h * w[0]; val hy = 0.5 * h * w[1]; val hz = 0.5 * h * w[2]
        // q += ½ h (ω, 0) ⊗ q
        q[0] = x + hx * s + hy * z - hz * y
        q[1] = y + hy * s + hz * x - hx * z
        q[2] = z + hz * s + hx * y - hy * x
        q[3] = s - hx * x - hy * y - hz * z
        normalize(q)
    }

    fun normalize(q: DoubleArray) {
        val l = sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3])
        if (l < 1e-12) { q[0] = 0.0; q[1] = 0.0; q[2] = 0.0; q[3] = 1.0; return }
        for (k in 0 until 4) q[k] /= l
    }

    /** Row-major rotation matrix of q. */
    fun toMat(q: DoubleArray, m: DoubleArray) {
        val x = q[0]; val y = q[1]; val z = q[2]; val w = q[3]
        m[0] = 1 - 2 * (y * y + z * z); m[1] = 2 * (x * y - z * w); m[2] = 2 * (x * z + y * w)
        m[3] = 2 * (x * y + z * w); m[4] = 1 - 2 * (x * x + z * z); m[5] = 2 * (y * z - x * w)
        m[6] = 2 * (x * z - y * w); m[7] = 2 * (y * z + x * w); m[8] = 1 - 2 * (x * x + y * y)
    }
}

/** 2-D convex hull containment (scratch per instance: sims step on different threads). */
class Hull {
    private val idx = IntArray(1024)
    private val hx = DoubleArray(1024)
    private val hz = DoubleArray(1024)

    /** Whether (px, pz) lies inside the convex hull of the first [n] points, at least [margin] from every edge. */
    fun containsWithMargin(xs: DoubleArray, zs: DoubleArray, n: Int, px: Double, pz: Double, margin: Double): Boolean {
        if (n < 3) return false
        val ids = (0 until n).sortedWith(compareBy<Int>({ xs[it] }, { zs[it] }))
        var k = 0
        fun cross(o: Int, a: Int, b: Int) = (xs[a] - xs[o]) * (zs[b] - zs[o]) - (zs[a] - zs[o]) * (xs[b] - xs[o])
        for (i in ids) { while (k >= 2 && cross(idx[k - 2], idx[k - 1], i) <= 0) k--; idx[k++] = i }
        val lower = k + 1
        for (t in ids.indices.reversed().drop(1)) { val i = ids[t]; while (k >= lower && cross(idx[k - 2], idx[k - 1], i) <= 0) k--; idx[k++] = i }
        val m = k - 1
        if (m < 3) return false
        for (t in 0 until m) { hx[t] = xs[idx[t]]; hz[t] = zs[idx[t]] }
        // counter-clockwise hull: the point must be left of every edge by the margin
        for (t in 0 until m) {
            val ax = hx[t]; val az = hz[t]; val bx = hx[(t + 1) % m]; val bz = hz[(t + 1) % m]
            val ex = bx - ax; val ez = bz - az
            val l = sqrt(ex * ex + ez * ez)
            if (l < 1e-9) continue
            val side = (ex * (pz - az) - ez * (px - ax)) / l
            if (side < margin) return false
        }
        return true
    }
}
