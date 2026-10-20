package com.reeflive.core.sim

import com.reeflive.core.geom.AnalyticSdf
import com.reeflive.core.geom.Ellipsoid
import com.reeflive.core.geom.Rng
import com.reeflive.core.geom.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Per-species coral health, bleaching, death, spreading and the care button (docs/research/coral_health.md). */
class CoralHealthTest {
    private fun f(v: Double) = "%.2f".format(v)

    private fun coral(speciesId: String, id: String = speciesId) =
        Coral(id, TestTank.catalog[speciesId], id, "", Vec3(40.0, 20.0, 20.0), Vec3.Y, 0.0)

    /** [days] of daylight hours (lights 10 h a day) in water [w] at light [light]. */
    private fun live(c: Coral, w: WaterState, days: Int, light: Double = 0.6) {
        repeat(days) { d ->
            for (m in 0 until 24 * 6) {
                val minute = m * 10.0
                val on = minute in 600.0..1200.0
                CoralSim.step(c, w, if (on) light else 0.0, if (on) minute - 600 else null, if (on) 1200 - minute else null, 10.0, (d * 1440 + minute) * 60, ArrayList())
            }
        }
    }

    private fun goodWater() = WaterState(tempC = 25.5, no3 = 5.0, po4 = 0.04, i = 0.06)

    @Test
    fun wellKeptCoralsStayHealthy() {
        for (id in listOf("zoanthus_sp", "sarcophyton_sp", "tubipora_musica", "rhodactis_sp", "capnella_sp")) {
            val c = coral(id)
            live(c, goodWater(), 30)
            assertTrue("$id bleach ${c.bleach}", c.bleach < 0.05)
            assertTrue("$id tissue ${c.tissue}", c.tissue > 0.97)
            assertEquals("$id need", -1, c.need)
        }
    }

    /** Heat bleaches over weeks, hardy GSP slower than the toadstool, then the bleached colony melts; cooled down it recovers. */
    @Test
    fun heatBleachesThenKillsAndCoolingLetsItRecover() {
        val hot = goodWater().copy(tempC = 31.0)
        val toad0 = coral("sarcophyton_sp"); val gsp = coral("briareum_sp")
        live(toad0, hot, 21); live(gsp, hot, 21)
        println("3 weeks at 31 C: toadstool bleach ${f(toad0.bleach)} tissue ${f(toad0.tissue)} need ${toad0.need}, GSP ${f(gsp.bleach)}")
        assertTrue("toadstool pale: ${toad0.bleach}", toad0.bleach > 0.5)
        assertTrue("GSP hardier: ${gsp.bleach} vs ${toad0.bleach}", gsp.bleach < toad0.bleach)
        assertEquals(CoralNeed.HEAT.ordinal, toad0.need)
        // left hot, it recedes and dies within a couple of months
        val dying = coral("sarcophyton_sp", "dying").also { live(it, hot, 70) }
        println("70 days at 31 C: tissue ${f(dying.tissue)}")
        assertTrue("dead: ${dying.tissue}", dying.tissue <= CoralHealth.DEAD_TISSUE)
        // caught after 2 weeks and cooled, it slowly gets its colour back
        val caught = coral("sarcophyton_sp", "caught").also { live(it, hot, 14) }
        println("2 weeks at 31 C: bleach ${f(caught.bleach)} tissue ${f(caught.tissue)}")
        val toad = caught
        val before = toad.bleach
        live(toad, goodWater(), 1)
        println("1 day cool: need ${toad.need} stress ${f(toad.stress)} tissue ${f(toad.tissue)}")
        live(toad, goodWater(), 44)
        println("45 days cool: bleach ${f(before)} -> ${f(toad.bleach)}, tissue ${f(toad.tissue)}")
        assertTrue("recovering: ${toad.bleach}", toad.bleach < before - 0.3)
        assertTrue("alive: ${toad.tissue}", toad.tissue > 0.5)
    }

    @Test
    fun tooDarkPalesOverWeeks() {
        val c = coral("zoanthus_sp")
        live(c, goodWater(), 60, light = 0.08)
        println("60 days dim: zoa bleach ${f(c.bleach)} need ${c.need}")
        assertTrue("pale in the dark: ${c.bleach}", c.bleach > 0.4)
        assertEquals(CoralNeed.LIGHT_LOW.ordinal, c.need)
    }

    /** Ultra-clean water: an unfed organ pipe starves and pales; fed it holds its colour. */
    @Test
    fun hungryInCleanWaterUnlessFed() {
        val clean = goodWater().copy(no3 = 0.0, po4 = 0.0)
        val hungry = coral("tubipora_musica", "a"); val fed = coral("tubipora_musica", "b")
        repeat(40) { hungry.fed = 0.0; fed.fed = 1.0; live(hungry, clean, 1); live(fed, clean, 1) }
        println("40 days clean: unfed bleach ${f(hungry.bleach)} need ${hungry.need}, fed ${f(fed.bleach)}")
        assertTrue(hungry.bleach > fed.bleach + 0.2)
        assertEquals(CoralNeed.HUNGRY.ordinal, hungry.need)
    }

    @Test
    fun usedUpIodineStressesLeathersMoreThanZoas() {
        val w = goodWater().copy(i = 0.0)
        val leather = coral("sinularia_sp"); val zoa = coral("zoanthus_sp")
        live(leather, w, 2); live(zoa, w, 2)
        assertTrue("leather ${leather.stress} zoa ${zoa.stress}", leather.stress > zoa.stress + 0.1)
        assertEquals(CoralNeed.IODINE.ordinal, leather.need)
    }

    /** A grown zoa clump buds a new colony on the rock next to it; a sick one does not. */
    @Test
    fun healthyColoniesSpread() {
        val rock = RockData(AnalyticSdf(listOf(Ellipsoid(Vec3(60.0, 3.0, 20.0), Vec3(40.0, 18.0, 14.0)))), AnchorSet(emptyList()))
        val p = rock.sdf.project(Vec3(60.0, 25.0, 20.0), 0.2)
        val zoa = Coral("zoa", TestTank.catalog["zoanthus_sp"], "Zoas", "coral_zoa", p, Vec3.Y, 0.0, offset = Vec3(1.0, 0.0, 0.0))
        zoa.size = 1.6; zoa.radiusCm = 8.0
        val rng = Rng(3)
        var baby: Coral? = null
        var days = 0
        while (baby == null && days < 400) { days++; baby = CoralHealth.maybeSpread(zoa, 1.0, goodWater(), rock, rng, 1440.0, 1, emptyList()) { "zoa~1" } }
        println("zoa budded after $days days at full vigour")
        val b = baby!!
        assertTrue("on the rock next to it: ${b.pos.distanceTo(zoa.pos)}", b.pos.distanceTo(zoa.pos) in 3.0..15.0)
        assertEquals("same model home", (zoa.restPos - zoa.offset).distanceTo(b.restPos - b.offset), 0.0, 1e-9)
        assertEquals("Zoas bud", b.nickname)
        assertTrue(b.size < 1.0 && b.isCopy())
        val sick = Coral("z2", zoa.species, "Z", "", p, Vec3.Y, 0.0).also { it.size = 1.6; it.bleach = 0.6 }
        repeat(400) { assertEquals(null, CoralHealth.maybeSpread(sick, 1.0, goodWater(), rock, rng, 1440.0, 1, emptyList()) { "x" }) }
    }

    private fun Coral.isCopy() = toPose().isCopy

    @Test
    fun deadColoniesAreReportedAndCleared() {
        val sim = TestTank.sim(DefaultStock.all)
        val c = sim.corals.first { it.kind == CoralKind.ZOA }
        c.tissue = 0.0
        sim.advanceAbstract(3600.0)
        val died = sim.latest.events.filterIsInstance<SimEvent.CoralDied>()
        assertTrue("$died", died.any { it.id == c.id })
        sim.advanceAbstract(2.5 * 86400)
        assertTrue("cleared", sim.corals.none { it.id == c.id })
    }

    @Test
    fun awaySummaryInPlainWords() {
        val a = coral("zoanthus_sp", "a")
        val b = coral("capnella_sp", "b")
        val before = CoralHealth.marks(listOf(a, b))
        a.tissue = 0.0; b.bleach = 0.7
        val baby = coral("capnella_sp", "b~1")
        val lines = CoralHealth.changes(before, listOf(a, b, baby))
        println(lines)
        assertTrue(lines.any { it.startsWith("a died") })
        assertTrue(lines.any { it.startsWith("b turned pale") })
        assertTrue(lines.any { it.contains("new baby colony") })
    }

    @Test
    fun careButtonDosesWithinADaysSwingAndFeeds() {
        val w = WaterState(alk = 6.5, ca = 360.0, mg = 1150.0, i = 0.01, no3 = 5.0, po4 = 0.04)
        val plan = CoralCare.plan(w, coralFed = 0.1, anemones = 1, filterFeeders = 1, crustaceans = 2, hoursSinceFeed = 48.0)
        println("plan: ${plan.words} for ${plan.price} sand dollars; ${plan.items}")
        val byKind = plan.items.associateBy { it.kind }
        assertEquals(1.0, byKind.getValue(CoralCare.Kind.ALKALINITY).amount, 1e-9)       // a day's safe swing, not all 2 dKH
        assertEquals(25.0, byKind.getValue(CoralCare.Kind.CALCIUM).amount, 1e-9)
        assertEquals(100.0, byKind.getValue(CoralCare.Kind.MAGNESIUM).amount, 1e-9)
        assertEquals(0.03, byKind.getValue(CoralCare.Kind.IODINE).amount, 1e-9)
        assertTrue(CoralCare.Kind.CORAL_FOOD in byKind && CoralCare.Kind.PHYTO in byKind && CoralCare.Kind.ANEMONE_FOOD in byKind)
        // pressed again the same day: nothing more to dose, the animals were fed
        val today = mapOf("alk" to 1.0, "ca" to 25.0, "mg" to 100.0, "i" to 0.03)
        val again = CoralCare.plan(w, 0.8, 1, 1, 2, 0.1) { id -> today[id] ?: 0.0 }
        assertTrue("again: ${again.items}", again.items.none { it.kind == CoralCare.Kind.ALKALINITY || it.kind == CoralCare.Kind.IODINE || it.kind == CoralCare.Kind.PHYTO })
        // a well kept tank needs nothing
        assertTrue(CoralCare.plan(WaterState(alk = 8.4, ca = 420.0, mg = 1320.0, i = 0.06), 0.8, 0, 0, 0, 1.0).isEmpty)
    }

    @Test
    fun careButtonInTheSim() {
        val sim = TestTank.sim(DefaultStock.all)
        sim.water.s.alk = 6.5; sim.water.s.i = 0.0
        for (c in sim.corals) c.fed = 0.0
        sim.enqueue(Command.ReefCare)
        sim.advanceAbstract(3 * 3600.0)
        println("after care: alk ${f(sim.water.s.alk)} i ${sim.water.s.i}")
        assertTrue("alk dosed: ${sim.water.s.alk}", sim.water.s.alk > 7.2)
        assertTrue("iodine dosed: ${sim.water.s.i}", sim.water.s.i > 0.02)
        assertTrue(sim.dosedLast24h("alk") > 0.9)
    }
}
