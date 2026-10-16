package com.v2ray.ang.fmt

import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsttFmtTest {

    private val hexKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    @Test
    fun `parse udp transport with resolver`() {
        val uri = "dnstt://tunnel.example.com?pubkey=$hexKey&transport=udp&resolver=9.9.9.9%3A53#Tunnel"
        val config = DnsttFmt.parse(uri)!!
        assertEquals(EConfigType.DNSTT, config.configType)
        assertEquals("tunnel.example.com", config.server)
        assertEquals(DnsttFmt.TRANSPORT_UDP, config.method)
        assertEquals("9.9.9.9", config.host)
        assertEquals("53", config.serverPort)
        assertEquals(hexKey, config.publicKey)
        assertEquals("Tunnel", config.remarks)
    }

    @Test
    fun `udp transport defaults resolver to cloudflare`() {
        val config = DnsttFmt.parse("dnstt://t.example.com?pubkey=$hexKey")!!
        assertEquals(DnsttFmt.DEFAULT_RESOLVER_HOST, config.host)
        assertEquals("53", config.serverPort)
        assertEquals(DnsttFmt.TRANSPORT_UDP, config.method)
    }

    @Test
    fun `parse doh transport`() {
        val config = DnsttFmt.parse("dnstt://t.example.com?pubkey=$hexKey&transport=doh&dns=https%3A%2F%2Fdns.cloudflare.com%2Fdns-query")!!
        assertEquals(DnsttFmt.TRANSPORT_DOH, config.method)
        assertEquals("https://dns.cloudflare.com/dns-query", config.path)
    }

    @Test
    fun `transport inferred from dns target scheme`() {
        val doh = DnsttFmt.parse("dnstt://t.example.com?pubkey=$hexKey&dns=https%3A%2F%2Fx%2Fdns-query")!!
        assertEquals(DnsttFmt.TRANSPORT_DOH, doh.method)
        val dot = DnsttFmt.parse("dnstt://t.example.com?pubkey=$hexKey&dns=1.1.1.1%3A853")!!
        assertEquals(DnsttFmt.TRANSPORT_DOT, dot.method)
        assertEquals("1.1.1.1:853", dot.path)
    }

    @Test
    fun `rejects missing pubkey or host`() {
        assertNull(DnsttFmt.parse("dnstt://t.example.com?transport=udp"))
        assertNull(DnsttFmt.parse("dnstt://?pubkey=$hexKey"))
        assertNull(DnsttFmt.parse("dnstt://t.example.com"))
    }

    @Test
    fun `round trip udp`() {
        val original = DnsttFmt.parse("dnstt://tunnel.example.com?pubkey=$hexKey&transport=udp&resolver=8.8.8.8%3A5353#T")!!
        val back = DnsttFmt.parse(EConfigType.DNSTT.protocolScheme + DnsttFmt.toUri(original))!!
        assertEquals(original.server, back.server)
        assertEquals(original.publicKey, back.publicKey)
        assertEquals(original.method, back.method)
        assertEquals(original.host, back.host)
        assertEquals(original.serverPort, back.serverPort)
        assertEquals(original.remarks, back.remarks)
    }

    @Test
    fun `round trip doh`() {
        val original = DnsttFmt.parse("dnstt://tunnel.example.com?pubkey=$hexKey&transport=doh&dns=https%3A%2F%2Fdns.adguard-dns.com%2Fdns-query")!!
        val back = DnsttFmt.parse(EConfigType.DNSTT.protocolScheme + DnsttFmt.toUri(original))!!
        assertEquals(original.path, back.path)
        assertEquals(DnsttFmt.TRANSPORT_DOH, back.method)
    }
}
