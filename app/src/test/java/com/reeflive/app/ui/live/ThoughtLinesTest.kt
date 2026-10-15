package com.reeflive.app.ui.live

import com.reeflive.core.sim.Mood
import com.reeflive.core.sim.OrganismStats
import com.reeflive.core.sim.Thought
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThoughtLinesTest {
    private fun t(now: String, key: String = "host|", next: String = "") = Thought(now = now, next = next, feeling = "Feeling: happy.", mood = "happy", bubble = "Hi!", planKey = key, interest = 2)

    @Test fun aLineIsHeldUnlessThePlanChanges() {
        val h = ThoughtHold()
        assertEquals("A", h.update("nemo", t("A"), 0).now)
        // same plan, new words too soon: the old line stays
        assertEquals("A", h.update("nemo", t("B"), 500).now)
        assertEquals("B", h.update("nemo", t("B"), 1600).now)
        // a new plan shows at once
        assertEquals("C", h.update("nemo", t("C", key = "feed|"), 1700).now)
        // another animal shows at once
        assertEquals("D", h.update("dory", t("D", key = "feed|"), 1800).now)
    }

    private fun stats(id: String, th: Thought) = OrganismStats(
        id, id, "sp", "Fish", 1f, 0.2f, 0f, Mood.CALM, "Hosting", emptyList(), null, 0f, true, 5f, emptyList(), thought = th,
    )

    @Test fun atMostThreeBubblesThatFadeInAndOut() {
        val p = ThoughtBubblePicker()
        val animals = (0 until 8).map { stats("a$it", t("x", key = "k$it")) }
        val place: (String) -> FloatArray? = { id -> floatArrayOf(id.removePrefix("a").toInt() * 400f, 300f, 10f) }
        var most = 0
        var ms = 0L
        while (ms < 20_000L) {
            val shown = p.update(animals, ms, place)
            most = maxOf(most, shown.size)
            assertTrue(shown.all { it.alpha in 0f..1f })
            ms += 16
        }
        assertTrue("never more than three: $most", most in 1..ThoughtBubblePicker.MAX)
        // a bubble starts faint (fade in)
        val q = ThoughtBubblePicker()
        val first = q.update(animals, 0L, place)
        assertTrue(first.all { it.alpha < 0.1f })
    }
}
