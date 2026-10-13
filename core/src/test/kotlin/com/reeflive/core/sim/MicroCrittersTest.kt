package com.reeflive.core.sim

import com.reeflive.core.geom.AnalyticSdf
import com.reeflive.core.geom.Ellipsoid
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** The in-tank microfauna ([CritterField]): counts follow the aggregate model's diel exposure; bodies stay on surfaces. */
class MicroCrittersTest {
    private val sdf = AnalyticSdf(listOf(
        Ellipsoid(Vec3(35.0, 10.0, 14.0), Vec3(18.0, 12.0, 9.0)),
        Ellipsoid(Vec3(85.0, 12.0, 15.0), Vec3(20.0, 14.0, 9.0)),
    ))

    /** A mature tank's microfauna after a week, stepped on to [hour] o'clock under the default lights. */
    private fun mature(hour: Double): MicrofaunaState {
        val m = MicrofaunaModel(TankConfig())
        val w = WaterState(tempC = 26.0)
        val lights = LightSchedule()
        var t = 0.0
        val end = 7 * 86400 + hour * 3600
        while (t < end) { m.step(w, 0.25, lights.intensity(t), 1.0); t += 900 }
        return m.snapshot()
    }

    private fun run(f: CritterField, s: MicrofaunaState, seconds: Double, fish: FloatArray = FloatArray(0)): List<CritterPose> {
        var out = emptyList<CritterPose>()
        var t = 0.0
        while (t < seconds) { out = f.update(s, 1.0 / 30, fishHeads = fish); t += 1.0 / 30 }
        return out
    }

    @Test fun moreCrittersAtNightThanByDay() {
        val day = mature(13.0); val night = mature(22.0)
        val f = CritterField(sdf)
        for (k in listOf(CritterKind.BRISTLEWORM, CritterKind.AMPHIPOD)) {
            assertTrue("$k should be more exposed at night", f.expected(k, night) > f.expected(k, day))
        }
        val atNight = run(CritterField(sdf), night, 60.0)
        val worms = atNight.count { it.kind == CritterKind.BRISTLEWORM }
        val pods = atNight.count { it.kind == CritterKind.AMPHIPOD }
        assertTrue("night shows bristleworms ($worms)", worms >= 1)
        assertTrue("night shows amphipods ($pods)", pods >= 1)
        val byDay = run(CritterField(sdf), day, 60.0)
        assertTrue("fewer worms by day", byDay.count { it.kind == CritterKind.BRISTLEWORM } < worms)
    }

    @Test fun crittersRetreatWhenTheLightsComeOn() {
        val f = CritterField(sdf)
        run(f, mature(22.0), 60.0)
        val before = f.count(CritterKind.BRISTLEWORM) + f.count(CritterKind.AMPHIPOD)
        assertTrue(before > 0)
        val day = mature(13.0)
        run(f, day, 120.0)
        val after = f.count(CritterKind.BRISTLEWORM) + f.count(CritterKind.AMPHIPOD)
        assertTrue("retreated: $before -> $after", after <= f.target[CritterKind.BRISTLEWORM.ordinal] + f.target[CritterKind.AMPHIPOD.ordinal])
    }

    @Test fun bodiesHaveTheirLengthAndSitOnTheirSurface() {
        val f = CritterField(sdf)
        val poses = run(f, mature(22.0), 90.0)
        assertTrue(poses.isNotEmpty())
        for (p in poses) {
            var len = 0.0
            for (i in 1 until 16) {
                val dx = p.spine[i * 3] - p.spine[i * 3 - 3]; val dy = p.spine[i * 3 + 1] - p.spine[i * 3 - 2]; val dz = p.spine[i * 3 + 2] - p.spine[i * 3 - 1]
                len += sqrt((dx * dx + dy * dy + dz * dz).toDouble())
            }
            assertEquals("${p.id} spine length", p.scale.toDouble(), len, 0.08 * p.scale + 0.02)
            for (i in 0 until 16) {
                var q = 0.0
                for (c in 0 until 4) q += (p.frames[i * 4 + c] * p.frames[i * 4 + c]).toDouble()
                assertEquals(1.0, q, 1e-3)
                assertTrue(p.spine.all { it.isFinite() })
            }
            // a skeleton shrimp stands on its rear legs: check where it grips
            val i0 = if (p.kind == CritterKind.CAPRELLID) 45 else 0
            val head = Vec3(p.spine[i0].toDouble(), p.spine[i0 + 1].toDouble(), p.spine[i0 + 2].toDouble())
            when (p.kind) {
                // mysids hover; amphipods may be mid side-swim between perches
                CritterKind.MYSID, CritterKind.AMPHIPOD, CritterKind.PLANKTON -> assertTrue("${p.id} in the water", sdf.dist(head) > -1.0 && head.y < 54)
                else -> {
                    // on rock, sand or the front glass (within a few mm of the surface)
                    val onRock = abs(sdf.dist(head)) < 0.6
                    val onSand = abs(head.y - f.sandY(head.x, head.z)) < 0.6
                    val onGlass = abs(head.z - 33.0) < 0.3
                    assertTrue("${p.id} at $head is on a surface", onRock || onSand || onGlass)
                }
            }
        }
    }

    @Test fun tentaclesPullBackFromAPassingFish() {
        val f = CritterField(sdf)
        val s = mature(13.0)
        val poses = run(f, s, 120.0)
        val tips = poses.filter { it.kind == CritterKind.TENTACLE }
        assertTrue("a mature tank shows spaghetti-worm tentacles", tips.isNotEmpty())
        fun reach(ps: List<CritterPose>) = ps.filter { it.kind == CritterKind.TENTACLE }.sumOf { p ->
            val dx = p.spine[0] - p.spine[45]; val dz = p.spine[2] - p.spine[47]
            sqrt((dx * dx + dz * dz).toDouble())
        }
        val out = reach(poses)
        val fish = FloatArray(tips.size * 3) { i -> tips[i / 3].spine[i % 3] }
        val after = run(f, s, 3.0, fish)
        assertTrue("tentacles retract (${out} -> ${reach(after)})", reach(after) < out * 0.7)
    }
}
