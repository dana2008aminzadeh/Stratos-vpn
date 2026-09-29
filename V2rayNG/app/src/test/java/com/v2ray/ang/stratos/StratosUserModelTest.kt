package com.v2ray.ang.stratos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StratosUserModelTest {

    private fun user(
        status: StratosUserStatus = StratosUserStatus.ACTIVE,
        limit: Long = 1000,
        used: Long = 0,
        expireAt: Long = System.currentTimeMillis() + 60_000,
    ) = StratosUser("fyx1", status, limit, used, expireAt)

    @Test
    fun `canConnect only when active with time and data left`() {
        assertTrue(user().canConnect)
        assertFalse(user(status = StratosUserStatus.EXPIRED).canConnect)
        assertFalse(user(status = StratosUserStatus.DISABLED).canConnect)
        assertFalse(user(expireAt = System.currentTimeMillis() - 1000).canConnect)
        assertFalse(user(used = 1000).canConnect)
        assertFalse(user(used = 1500).canConnect)
    }

    @Test
    fun `unlimited data never blocks on volume`() {
        assertTrue(user(limit = 0, used = Long.MAX_VALUE / 2).canConnect)
        assertTrue(user(limit = -1, used = 999_999).canConnect)
    }

    @Test
    fun `remaining fraction and bytes`() {
        val u = user(limit = 1000, used = 250)
        assertEquals(750, u.remainingBytes)
        assertEquals(0.75f, u.remainingFraction, 0.0001f)

        val empty = user(limit = 1000, used = 1200)
        assertEquals(0, empty.remainingBytes)
        assertEquals(0f, empty.remainingFraction, 0.0001f)

        assertEquals(Long.MAX_VALUE, user(limit = 0).remainingBytes)
    }
}
