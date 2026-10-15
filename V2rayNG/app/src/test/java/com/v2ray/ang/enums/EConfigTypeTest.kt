package com.v2ray.ang.enums

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EConfigTypeTest {

    @Test
    fun `all config type values are unique`() {
        val values = EConfigType.entries.map { it.value }
        assertEquals(values.size, values.toSet().size)
    }

    @Test
    fun `relay and engine protocols have distinct schemes`() {
        assertNotEquals(EConfigType.SSH.protocolScheme, EConfigType.DNSTT.protocolScheme)
        assertNotEquals(EConfigType.DNSTT.protocolScheme, EConfigType.LIGHTWAY.protocolScheme)
        assertEquals("ssh://", EConfigType.SSH.protocolScheme)
        assertEquals("dnstt://", EConfigType.DNSTT.protocolScheme)
        assertEquals("lightway://", EConfigType.LIGHTWAY.protocolScheme)
    }

    @Test
    fun `fromInt resolves the new protocols`() {
        assertEquals(EConfigType.SSH, EConfigType.fromInt(11))
        assertEquals(EConfigType.DNSTT, EConfigType.fromInt(12))
        assertEquals(EConfigType.LIGHTWAY, EConfigType.fromInt(13))
        assertNotNull(EConfigType.fromInt(EConfigType.WIREGUARD.value))
    }
}
