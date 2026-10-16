package com.v2ray.ang.relay

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Socks5ProtocolTest {

    @Test
    fun `greeting with no-auth is accepted`() {
        val greeting = byteArrayOf(0x05, 0x02, 0x00, 0x02)
        assertEquals(Socks5Protocol.METHOD_NO_AUTH, Socks5Protocol.chooseMethod(greeting))
        assertEquals(4, Socks5Protocol.greetingLength(greeting))
    }

    @Test
    fun `greeting without no-auth is rejected`() {
        assertEquals(
            Socks5Protocol.METHOD_UNACCEPTABLE,
            Socks5Protocol.chooseMethod(byteArrayOf(0x05, 0x01, 0x02)),
        )
    }

    @Test
    fun `malformed greeting is rejected`() {
        assertEquals(Socks5Protocol.METHOD_UNACCEPTABLE, Socks5Protocol.chooseMethod(byteArrayOf(0x04, 0x01, 0x00)))
        assertEquals(Socks5Protocol.METHOD_UNACCEPTABLE, Socks5Protocol.chooseMethod(byteArrayOf(0x05, 0x05, 0x00)))
        assertEquals(-1, Socks5Protocol.greetingLength(byteArrayOf(0x04, 0x01)))
    }

    @Test
    fun `connect request with domain target`() {
        val domain = "example.com"
        val request = byteArrayOf(
            0x05, 0x01, 0x00, 0x03, domain.length.toByte(),
        ) + domain.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0x01, 0xBB.toByte()) // 443
        assertEquals(7 + domain.length, Socks5Protocol.requestLength(request.copyOf(5)))
        val target = Socks5Protocol.parseConnectRequest(request)!!
        assertEquals("example.com", target.host)
        assertEquals(443, target.port)
    }

    @Test
    fun `connect request with ipv4 target`() {
        val request = byteArrayOf(
            0x05, 0x01, 0x00, 0x01,
            10, 0, 0, 1,
            0x1F, 0x90.toByte(), // 8080
        )
        assertEquals(10, Socks5Protocol.requestLength(request))
        val target = Socks5Protocol.parseConnectRequest(request)!!
        assertEquals("10.0.0.1", target.host)
        assertEquals(8080, target.port)
    }

    @Test
    fun `connect request with ipv6 target`() {
        val addr = ByteArray(16)
        addr[15] = 0x01 // ::1
        val request = byteArrayOf(0x05, 0x01, 0x00, 0x04) + addr + byteArrayOf(0x00, 0x35) // 53
        assertEquals(22, Socks5Protocol.requestLength(request))
        val target = Socks5Protocol.parseConnectRequest(request)!!
        assertTrue(target.host.endsWith("1"))
        assertEquals(53, target.port)
    }

    @Test
    fun `rejects unsupported commands and truncated frames`() {
        val connect = byteArrayOf(0x05, 0x01, 0x00, 0x01, 1, 2, 3, 4, 0, 80)
        val bindCmd = connect.clone().also { it[1] = 0x02 }
        assertNull(Socks5Protocol.parseConnectRequest(bindCmd))
        assertNull(Socks5Protocol.parseConnectRequest(connect.copyOf(6)))
        assertNull(Socks5Protocol.parseConnectRequest(byteArrayOf(0x04, 0x01, 0x00, 0x01, 1, 2, 3, 4, 0, 80)))
        assertNull(Socks5Protocol.parseConnectRequest(byteArrayOf(0x05, 0x01, 0x00, 0x09)))
        // port 0 is invalid
        assertNull(Socks5Protocol.parseConnectRequest(connect.clone().also { it[8] = 0; it[9] = 0 }))
    }

    @Test
    fun `reply message shape`() {
        val reply = Socks5Protocol.buildReply(Socks5Protocol.REPLY_SUCCESS, "1.2.3.4", 443)
        assertArrayEquals(
            byteArrayOf(0x05, 0x00, 0x00, 0x01, 1, 2, 3, 4, 0x01, 0xBB.toByte()),
            reply,
        )
        val fail = Socks5Protocol.buildReply(Socks5Protocol.REPLY_CONNECTION_REFUSED)
        assertEquals(10, fail.size)
        assertEquals(0x05.toByte(), fail[0])
        assertEquals(Socks5Protocol.REPLY_CONNECTION_REFUSED, fail[1])
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
