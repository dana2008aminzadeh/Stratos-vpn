package com.v2ray.ang.fmt

import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WireguardFmtTest {

    @Test
    fun `parse wireguard uri`() {
        val uri = "wireguard://cHJpdmF0ZWtleQ%3D%3D@1.2.3.4:51820?publickey=dXBieQ%3D%3D&address=172.16.0.2%2F32&mtu=1280#WG"
        val config = WireguardFmt.parse(uri)!!
        assertEquals(EConfigType.WIREGUARD, config.configType)
        assertEquals("1.2.3.4", config.server)
        assertEquals("51820", config.serverPort)
        assertEquals("cHJpdmF0ZWtleQ==", config.secretKey)
        assertEquals("dXBieQ==", config.publicKey)
        assertEquals("172.16.0.2/32", config.localAddress)
        assertEquals(1280, config.mtu)
        assertEquals("WG", config.remarks)
    }

    @Test
    fun `round trip wireguard uri`() {
        val original = WireguardFmt.parse("wireguard://c2VjcmV0@1.2.3.4:51820?publickey=dXBsa2V5&address=172.16.0.2%2F32#WG")!!
        val back = WireguardFmt.parse(EConfigType.WIREGUARD.protocolScheme + WireguardFmt.toUri(original))!!
        assertEquals(original.server, back.server)
        assertEquals(original.serverPort, back.serverPort)
        assertEquals(original.secretKey, back.secretKey)
        assertEquals(original.publicKey, back.publicKey)
        assertEquals(original.remarks, back.remarks)
    }

    @Test
    fun `parse wireguard conf file`() {
        val conf = """
            [Interface]
            PrivateKey = YWJj
            Address = 172.16.0.2/32
            DNS = 1.1.1.1
            MTU = 1420

            [Peer]
            PublicKey = ZGVm
            Endpoint = wg.example.com:51820
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()
        val config = WireguardFmt.parseWireguardConfFile(conf)
        assertEquals("YWJj", config.secretKey)
        assertEquals("ZGVm", config.publicKey)
        assertEquals("wg.example.com", config.server)
        assertEquals("51820", config.serverPort)
        assertNotNull(config)
    }
}
