package com.reeflive.app.audio

import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.Mood
import com.reeflive.core.sim.OrganismStats
import com.reeflive.core.sim.SimEvent
import com.reeflive.core.sim.Symptom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TankSoundMixTest {
    @Test fun scrapeIsQuietWhenStillAndLouderForLongerStrokes() {
        assertEquals(0f, TankSoundMix.scrapeGain(0f))
        val short = TankSoundMix.scrapeGain(0.6f)
        val long = TankSoundMix.scrapeGain(3f)
        assertTrue(short > 0f && short < long && long <= 1f)
    }

    private fun day(flow: Float = 1f, share: Float = 1f, power: Boolean = true, light: Float = 1f, vol: Float = 1f) =
        TankSoundMix.ambience(flow, share, power, light, vol)

    @Test fun flowGetsLouderWithPumpFlowAndStopsWhenOff() {
        val half = day(flow = 0.5f).flow
        val normal = day(flow = 1f).flow
        val double = day(flow = 2f).flow
        assertTrue(half in 0.01f..normal && normal < double)
        assertEquals(0f, day(flow = 0f).flow)
        assertEquals(0f, day(share = 0f).flow)
        assertTrue(day(share = 0.5f).flow < normal)
        assertTrue(double <= 1f)
    }

    @Test fun hobHumAndBubblesFollowThePumpPower() {
        val on = day()
        assertTrue(on.hob > 0f && on.hum > 0f && on.bubbles > 0f)
        assertEquals(AmbienceLevels.SILENT, day(power = false))
        // the powerheads alone off: the HOB keeps trickling
        val noPowerheads = day(share = 0f)
        assertEquals(0f, noPowerheads.flow)
        assertTrue(noPowerheads.hob > 0f)
    }

    @Test fun nightIsQuieterButNotSilent() {
        val d = day(light = 1f)
        val n = day(light = 0f)
        assertEquals(d.hob * TankSoundMix.NIGHT_FLOOR, n.hob, 1e-6f)
        assertTrue(n.flow in 0.01f..d.flow)
        assertEquals(1f, TankSoundMix.nightFactor(1.5f), 1e-6f)
        assertTrue(TankSoundMix.nightFactor(0.25f) in TankSoundMix.NIGHT_FLOOR..1f)
    }

    @Test fun volumeScalesEverythingAndZeroIsSilent() {
        val full = day(vol = 1f)
        val half = day(vol = 0.5f)
        assertEquals(full.flow * 0.5f, half.flow, 1e-6f)
        assertEquals(full.hum * 0.5f, half.hum, 1e-6f)
        assertEquals(AmbienceLevels.SILENT, day(vol = 0f))
        assertEquals(full, day(vol = 3f))
    }

    @Test fun eventsMapToCues() {
        assertEquals(Cue.FEED, TankSoundMix.cueFor(SimEvent.Fed(FoodKind.PELLET, 1.0, 0.0)))
        assertEquals(Cue.ALERT, TankSoundMix.cueFor(SimEvent.Died("a", "heat", 0.0)))
        assertEquals(Cue.ALERT, TankSoundMix.cueFor(SimEvent.Eaten("eel", "a", 0.0)))
        assertEquals(Cue.CLICK, TankSoundMix.cueFor(SimEvent.Molted("shrimp", 0.0)))
        assertEquals(Cue.CLICK, TankSoundMix.cueFor(SimEvent.Cleaned("shrimp", "a", 0.0)))
        assertNull(TankSoundMix.cueFor(SimEvent.TerritoryReset(0.0)))
        assertEquals(2, TankSoundMix.pelletCount(0.1))
        assertEquals(5, TankSoundMix.pelletCount(10.0))
    }

    @Test fun limiterSpacesRepeatsPerCue() {
        val l = CueLimiter()
        assertTrue(l.tryPlay(Cue.ALERT, 1_000))
        assertFalse(l.tryPlay(Cue.ALERT, 1_000 + Cue.ALERT.minGapMs - 1))
        assertTrue(l.tryPlay(Cue.CLICK, 1_001))   // other cues are independent
        assertTrue(l.tryPlay(Cue.ALERT, 1_000 + Cue.ALERT.minGapMs))
    }

    @Test fun lossOfEquilibriumIdsSkipTheDead() {
        fun st(id: String, alive: Boolean, vararg s: Symptom) = OrganismStats(
            id, id, "sp", "Fish", 1f, 0f, 0f, Mood.CALM, "", s.toList(), null, 0f, alive, 5f, emptyList(),
        )
        val ids = TankSoundMix.loeIds(
            listOf(st("a", true, Symptom.LOSS_OF_EQUILIBRIUM), st("b", true, Symptom.LETHARGY), st("c", false, Symptom.LOSS_OF_EQUILIBRIUM)),
        )
        assertEquals(setOf("a"), ids)
    }
}
