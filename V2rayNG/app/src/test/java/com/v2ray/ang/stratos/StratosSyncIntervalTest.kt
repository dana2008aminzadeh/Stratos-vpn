package com.v2ray.ang.stratos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StratosSyncIntervalTest {

    @Test
    fun `foreground sync keeps the fast cadence`() {
        assertEquals(2 * 60 * 1000L, StratosSync.nextDelayMs(interactive = true))
    }

    @Test
    fun `background sync stretches to save battery`() {
        assertEquals(5 * 60 * 1000L, StratosSync.nextDelayMs(interactive = false))
    }

    @Test
    fun `idle delay never goes below the foreground delay`() {
        assertTrue(StratosSync.nextDelayMs(false) >= StratosSync.nextDelayMs(true))
    }
}
