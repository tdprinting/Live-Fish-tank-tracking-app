package com.reeflive.core.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReefFactsTest {
    @Test fun noFactRepeatsUntilAllWereShown() {
        val n = ReefFacts.all.size
        assertTrue("a sizable list: $n", n >= 50)
        val start = 20_380L
        assertEquals(n, (0 until n).map { ReefFacts.nth(start + it) }.toSet().size)
    }

    @Test fun factsAreShortAndUnique() {
        assertEquals(ReefFacts.all.size, ReefFacts.all.map { it.text }.toSet().size)
        ReefFacts.all.forEach { assertTrue(it.text, it.text.length in 30..170) }
        assertTrue(ReefFacts.all.count { it.kind == ReefFacts.Kind.TIP } >= 20)
        assertTrue(ReefFacts.all.count { it.kind == ReefFacts.Kind.CREATURE } >= 20)
    }
}
