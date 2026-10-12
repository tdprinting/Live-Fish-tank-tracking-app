package com.reeflive.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ReefLiveCoreTest {
    @Test
    fun greetingIsHelloReef() {
        assertEquals("Hello reef", ReefLiveCore.greeting())
    }

    @Test
    fun sixtyGallonTankIsAbout227Litres() {
        assertEquals(227.1247, ReefLiveCore.gallonsToLitres(60.0), 1e-4)
    }
}
