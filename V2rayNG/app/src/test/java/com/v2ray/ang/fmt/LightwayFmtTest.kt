package com.v2ray.ang.fmt

import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LightwayFmtTest {

    @Test
    fun `parse full uri`() {
        val ca = "-----BEGIN CERTIFICATE-----\\nMIIB\\n-----END CERTIFICATE-----"
        val uri = "lightway://alice:s3cret@lw.example.com:27690?dn=lw.example.com&mode=tcp&cipher=aes256&ca=${com.v2ray.ang.util.Utils.encodeURIComponent(ca)}#LW"
        val config = LightwayFmt.parse(uri)!!
        assertEquals(EConfigType.LIGHTWAY, config.configType)
        assertEquals("lw.example.com", config.server)
        assertEquals("27690", config.serverPort)
        assertEquals("alice", config.username)
        assertEquals("s3cret", config.password)
        assertEquals("lw.example.com", config.sni)
        assertEquals("tcp", config.network)
        assertEquals("aes256", config.method)
        assertEquals(ca, config.path)
        assertEquals("LW", config.remarks)
    }

    @Test
    fun `defaults port mode and cipher`() {
        val config = LightwayFmt.parse("lightway://u:p@lw.example")!!
        assertEquals(LightwayFmt.DEFAULT_PORT, config.serverPort)
        assertEquals(LightwayFmt.DEFAULT_MODE, config.network)
        assertEquals(LightwayFmt.DEFAULT_CIPHER, config.method)
        assertEquals("none", config.remarks)
    }

    @Test
    fun `token auth without user`() {
        val config = LightwayFmt.parse("lightway://lw.example:27690?token=abc.def")!!
        assertEquals("abc.def", config.publicKey)
        assertNull(config.username)
    }

    @Test
    fun `rejects profile without any credentials`() {
        assertNull(LightwayFmt.parse("lightway://lw.example"))
    }

    @Test
    fun `round trip preserves the profile`() {
        val original = LightwayFmt.parse("lightway://alice:s3cret@lw.example.com:27690?dn=lw.example.com&mode=udp&cipher=aes128#LW")!!
        val back = LightwayFmt.parse(EConfigType.LIGHTWAY.protocolScheme + LightwayFmt.toUri(original))!!
        assertEquals(original.server, back.server)
        assertEquals(original.serverPort, back.serverPort)
        assertEquals(original.username, back.username)
        assertEquals(original.password, back.password)
        assertEquals(original.sni, back.sni)
        assertEquals(original.network, back.network)
        assertEquals(original.method, back.method)
        assertEquals(original.remarks, back.remarks)
    }
}
