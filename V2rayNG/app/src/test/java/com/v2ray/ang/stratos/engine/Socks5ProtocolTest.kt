package com.v2ray.ang.stratos.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Socks5ProtocolTest {

    private fun connectIpv4(host: ByteArray, port: Int): ByteArray = byteArrayOf(
        0x05, 0x01, 0x00, 0x01,
        host[0], host[1], host[2], host[3],
        ((port shr 8) and 0xFF).toByte(), (port and 0xFF).toByte(),
    )

    @Test
    fun parseIpv4Connect() {
        val data = connectIpv4(byteArrayOf(127.toByte(), 0, 0, 1), 8080)
        val req = Socks5Protocol.parseRequest(data)
        assertNotNull(req)
        assertEquals(Socks5Protocol.CMD_CONNECT, req!!.command)
        assertEquals("127.0.0.1", req.host)
        assertEquals(8080, req.port)
    }

    @Test
    fun parseDomainConnect() {
        val domain = "example.com"
        val out = ByteArray(7 + domain.length)
        out[0] = 0x05
        out[1] = 0x01
        out[2] = 0x00
        out[3] = 0x03
        out[4] = domain.length.toByte()
        domain.toByteArray().copyInto(out, 5)
        out[5 + domain.length] = 0x01
        out[6 + domain.length] = 0xBB.toByte()
        val req = Socks5Protocol.parseRequest(out)
        assertNotNull(req)
        assertEquals("example.com", req!!.host)
        assertEquals(443, req.port)
    }

    @Test
    fun parseIpv6Connect() {
        val out = ByteArray(22)
        out[0] = 0x05
        out[1] = 0x01
        out[2] = 0x00
        out[3] = 0x04
        out[19] = 0x01
        out[20] = 0x00
        out[21] = 0x50
        val req = Socks5Protocol.parseRequest(out)
        assertNotNull(req)
        assertEquals("0:0:0:0:0:0:0:1", req!!.host)
        assertEquals(80, req.port)
    }

    @Test
    fun rejectWrongVersion() {
        val data = connectIpv4(byteArrayOf(1, 2, 3, 4), 80)
        data[0] = 0x04
        assertNull(Socks5Protocol.parseRequest(data))
    }

    @Test
    fun rejectTruncated() {
        val data = connectIpv4(byteArrayOf(1, 2, 3, 4), 80)
        assertNull(Socks5Protocol.parseRequest(data, 9))
    }

    @Test
    fun rejectUnknownAddressType() {
        val data = connectIpv4(byteArrayOf(1, 2, 3, 4), 80)
        data[3] = 0x09
        assertNull(Socks5Protocol.parseRequest(data))
    }

    @Test
    fun udpAssociateIsParsedAsCommand() {
        val data = connectIpv4(byteArrayOf(0, 0, 0, 0), 0)
        data[1] = Socks5Protocol.CMD_UDP_ASSOCIATE.toByte()
        val req = Socks5Protocol.parseRequest(data)
        assertNotNull(req)
        assertEquals(Socks5Protocol.CMD_UDP_ASSOCIATE, req!!.command)
    }

    @Test
    fun replyLayout() {
        val reply = Socks5Protocol.buildReply(Socks5Protocol.REP_SUCCESS)
        assertEquals(10, reply.size)
        assertEquals(0x05, reply[0].toInt() and 0xFF)
        assertEquals(Socks5Protocol.REP_SUCCESS, reply[1].toInt() and 0xFF)
        assertEquals(0x01, reply[3].toInt() and 0xFF)
    }

    @Test
    fun greetingReplySelectsNoAuth() {
        val reply = Socks5Protocol.buildGreetingReply()
        assertEquals(2, reply.size)
        assertEquals(0x05, reply[0].toInt() and 0xFF)
        assertEquals(0x00, reply[1].toInt() and 0xFF)
    }

    @Test
    fun greetingAcceptsNoAuth() {
        val greeting = byteArrayOf(0x05, 0x02, 0x02, 0x00)
        assertTrue(Socks5Protocol.greetingOffersNoAuth(greeting))
        val noAuthMissing = byteArrayOf(0x05, 0x01, 0x02)
        assertFalse(Socks5Protocol.greetingOffersNoAuth(noAuthMissing))
    }
}
