package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LightwayFmt].
 *
 * Covers URI parsing, defaults, port hopping, obfuscation and round-trips.
 */
class LightwayFmtTest {

    @Test
    fun parse_validMinimalUri() {
        val config = LightwayFmt.parse("lightway://secret@lw.example.com:443#de%20node")
        assertNotNull(config)
        assertEquals(EConfigType.LIGHTWAY, config!!.configType)
        assertEquals("lw.example.com", config.server)
        assertEquals("443", config.serverPort)
        assertEquals("secret", config.password)
        assertEquals("de node", config.remarks)
        assertEquals(AppConfig.LIGHTWAY_PROTO_UDP, config.network)
        assertEquals(AppConfig.LIGHTWAY_CIPHER_AES, config.method)
        assertEquals(AppConfig.LIGHTWAY_DEFAULT_MTU, config.mtu)
        assertFalse(config.insecure == true)
    }

    @Test
    fun parse_fullUri() {
        val config = LightwayFmt.parse(
            "lightway://pw@lw.example.com:51820" +
                    "?proto=tcp&cipher=chacha20-poly1305&sni=cdn.example.com" +
                    "&insecure=1&obfs=hide-me&mport=20000-20100:3&phi=30s&mtu=1400#office"
        )
        assertNotNull(config)
        val c = config!!
        assertEquals("lw.example.com", c.server)
        assertEquals("51820", c.serverPort)
        assertEquals("pw", c.password)
        assertEquals(AppConfig.LIGHTWAY_PROTO_TCP, c.network)
        assertEquals(AppConfig.LIGHTWAY_CIPHER_CHACHA, c.method)
        assertEquals("cdn.example.com", c.sni)
        assertTrue(c.insecure == true)
        assertEquals("hide-me", c.obfsPassword)
        assertEquals("20000-20100:3", c.portHopping)
        assertEquals("30s", c.portHoppingInterval)
        assertEquals(1400, c.mtu)
        assertEquals("office", c.remarks)
    }

    @Test
    fun parse_missingHostOrPortRejected() {
        assertNull(LightwayFmt.parse("lightway://pw@:443"))
        assertNull(LightwayFmt.parse("lightway://pw@lw.example.com"))
    }

    @Test
    fun parse_unknownCipherFallsBackToAes() {
        val config = LightwayFmt.parse("lightway://pw@lw.example.com:443?cipher=bogus")
        assertNotNull(config)
        assertEquals(AppConfig.LIGHTWAY_CIPHER_AES, config!!.method)
    }

    @Test
    fun parse_passwordWithSpecialCharacters() {
        val config = LightwayFmt.parse("lightway://p%40ss%3Aw@lw.example.com:443")
        assertNotNull(config)
        assertEquals("p@ss:w", config!!.password)
    }

    @Test
    fun toUri_parseRoundTrip() {
        val config = LightwayFmt.parse(
            "lightway://pw@lw.example.com:51820" +
                    "?proto=tcp&cipher=chacha20-poly1305&sni=cdn.example.com" +
                    "&insecure=1&obfs=hide-me&mport=20000-20100:3&mtu=1400#office"
        )
        assertNotNull(config)
        val uri = LightwayFmt.toUri(config!!)
        val back = LightwayFmt.parse("lightway://$uri")
        assertNotNull(back)
        val b = back!!
        assertEquals(config.server, b.server)
        assertEquals(config.serverPort, b.serverPort)
        assertEquals(config.password, b.password)
        assertEquals(config.network, b.network)
        assertEquals(config.method, b.method)
        assertEquals(config.sni, b.sni)
        assertEquals(config.insecure, b.insecure)
        assertEquals(config.obfsPassword, b.obfsPassword)
        assertEquals(config.portHopping, b.portHopping)
        assertEquals(config.mtu, b.mtu)
        assertEquals(config.remarks, b.remarks)
    }

    @Test
    fun isComplete_requiresPassword() {
        val config = LightwayFmt.parse("lightway://pw@lw.example.com:443")
        assertNotNull(config)
        assertTrue(LightwayFmt.isComplete(config!!))

        val noPw = LightwayFmt.parse("lightway://@lw.example.com:443")
        assertNotNull(noPw)
        assertFalse(LightwayFmt.isComplete(noPw!!))
    }
}
