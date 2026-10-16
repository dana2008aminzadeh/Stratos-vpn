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
 * Unit tests for [DnsttFmt].
 *
 * Covers URI parsing, public-key validation, resolver normalization and
 * parse/format round-trips.
 */
class DnsttFmtTest {

    private val pubKey = "0000111122223333444455556666777788889999aaaabbbbccccddddeeeeffff"

    @Test
    fun parse_validMinimalUri() {
        val config = DnsttFmt.parse("dnstt://$pubKey@t.example.com#test%20node")
        assertNotNull(config)
        assertEquals(EConfigType.DNSTT, config!!.configType)
        assertEquals("t.example.com", config.server)
        assertEquals(pubKey, config.publicKey)
        assertEquals("test node", config.remarks)
        assertEquals(AppConfig.DNSTT_DEFAULT_RESOLVER, config.host)
        assertEquals("53", config.serverPort)
        assertEquals(AppConfig.DNSTT_DEFAULT_MTU, config.mtu)
    }

    @Test
    fun parse_uppercaseKeyIsNormalized() {
        val config = DnsttFmt.parse("dnstt://${pubKey.uppercase()}@t.example.com")
        assertNotNull(config)
        assertEquals(pubKey, config!!.publicKey)
    }

    @Test
    fun parse_invalidPublicKeyRejected() {
        assertNull(DnsttFmt.parse("dnstt://tooshort@t.example.com"))
        assertNull(DnsttFmt.parse("dnstt://${pubKey.dropLast(1)}@t.example.com"))
        assertNull(DnsttFmt.parse("dnstt://${pubKey.dropLast(1)}z@t.example.com"))
    }

    @Test
    fun parse_missingDomainRejected() {
        assertNull(DnsttFmt.parse("dnstt://$pubKey@"))
    }

    @Test
    fun parse_resolverAndMtu() {
        val config = DnsttFmt.parse(
            "dnstt://$pubKey@t.example.com?resolver=tls%3A8.8.8.8%3A853&mtu=1400"
        )
        assertNotNull(config)
        assertEquals("tls:8.8.8.8:853", config!!.host)
        assertEquals("853", config.serverPort)
        assertEquals(1400, config.mtu)
    }

    @Test
    fun parse_dohResolverKeepsFullUrl() {
        val doh = "https://dns.google/dns-query"
        val config = DnsttFmt.parse(
            "dnstt://$pubKey@t.example.com?resolver=${UtilsEncode(doh)}"
        )
        assertNotNull(config)
        assertEquals(doh, config!!.host)
        assertEquals("443", config.serverPort)
        assertTrue(DnsttFmt.isDohResolver(config.host))
    }

    @Test
    fun normalizeResolver_variants() {
        assertEquals("udp:1.1.1.1", DnsttFmt.normalizeResolver("1.1.1.1"))
        assertEquals("udp:1.1.1.1", DnsttFmt.normalizeResolver("udp:1.1.1.1"))
        assertEquals("tls:1.1.1.1", DnsttFmt.normalizeResolver("tls:1.1.1.1"))
        assertEquals(
            "https://dns.google/dns-query",
            DnsttFmt.normalizeResolver("https://dns.google/dns-query"),
        )
        assertEquals(AppConfig.DNSTT_DEFAULT_RESOLVER, DnsttFmt.normalizeResolver(""))
    }

    @Test
    fun resolverPort_defaults() {
        assertEquals(53, DnsttFmt.resolverPort("udp:1.1.1.1"))
        assertEquals(853, DnsttFmt.resolverPort("tls:1.1.1.1"))
        assertEquals(8853, DnsttFmt.resolverPort("tls:1.1.1.1:8853"))
        assertEquals(443, DnsttFmt.resolverPort("https://dns.google/dns-query"))
    }

    @Test
    fun toUri_parseRoundTrip() {
        val config = DnsttFmt.parse(
            "dnstt://$pubKey@t.example.com?resolver=udp%3A9.9.9.9&mtu=1350#my%20tunnel"
        )
        assertNotNull(config)
        val uri = DnsttFmt.toUri(config!!)
        val back = DnsttFmt.parse("dnstt://$uri")
        assertNotNull(back)
        assertEquals(config.server, back!!.server)
        assertEquals(config.publicKey, back.publicKey)
        assertEquals(config.host, back.host)
        assertEquals(config.mtu, back.mtu)
        assertEquals(config.remarks, back.remarks)
    }

    @Test
    fun isValidPubKey_shape() {
        assertTrue(DnsttFmt.isValidPubKey(pubKey))
        assertFalse(DnsttFmt.isValidPubKey(null))
        assertFalse(DnsttFmt.isValidPubKey(""))
        assertFalse(DnsttFmt.isValidPubKey(pubKey.uppercase()))
        assertFalse(DnsttFmt.isValidPubKey("gg" + pubKey.drop(2)))
    }

    /** Percent-encodes a resolver value the way a URL builder would. */
    private fun UtilsEncode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
