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
 * Unit tests for [SshFmt].
 *
 * Covers password and key authentication URIs, host-key fingerprints,
 * userinfo edge cases and parse/format round-trips.
 */
class SshFmtTest {

    private val samplePem = "-----BEGIN OPENSSH PRIVATE KEY-----\nb3BlbnNzaC1rZXktdjEAAAAA\n-----END OPENSSH PRIVATE KEY-----"

    @Test
    fun parse_passwordAuth() {
        val config = SshFmt.parse("ssh://alice:hunter2@ssh.example.com:2222#dev%20box")
        assertNotNull(config)
        assertEquals(EConfigType.SSH, config!!.configType)
        assertEquals("ssh.example.com", config.server)
        assertEquals("2222", config.serverPort)
        assertEquals("alice", config.username)
        assertEquals("hunter2", config.password)
        assertEquals("dev box", config.remarks)
        assertEquals(AppConfig.SSH_AUTH_PASSWORD, config.method)
    }

    @Test
    fun parse_defaultPortIs22() {
        val config = SshFmt.parse("ssh://alice:hunter2@ssh.example.com")
        assertNotNull(config)
        assertEquals(AppConfig.SSH_DEFAULT_PORT, config!!.serverPort)
    }

    @Test
    fun parse_passwordWithColonSurvives() {
        val config = SshFmt.parse("ssh://alice:pa%3Ass@ssh.example.com:22")
        assertNotNull(config)
        assertEquals("alice", config!!.username)
        assertEquals("pa:ss", config.password)
        assertEquals(AppConfig.SSH_AUTH_PASSWORD, config.method)
    }

    @Test
    fun parse_keyAuth() {
        val encoded = SshFmt.encodePrivateKey(samplePem)
        val config = SshFmt.parse(
            "ssh://bob@ssh.example.com:22" +
                    "?auth=key&key=${java.net.URLEncoder.encode(encoded, "UTF-8")}" +
                    "&hostkey=SHA256%3AAbCdEf#jump"
        )
        assertNotNull(config)
        val c = config!!
        assertEquals("bob", c.username)
        assertEquals(AppConfig.SSH_AUTH_KEY, c.method)
        assertEquals(samplePem, c.secretKey)
        assertEquals("SHA256:AbCdEf", c.publicKey)
        assertNull(c.password)
    }

    @Test
    fun parse_insecureFlag() {
        val config = SshFmt.parse("ssh://alice:pw@ssh.example.com:22?insecure=1")
        assertNotNull(config)
        assertTrue(config!!.insecure == true)
    }

    @Test
    fun parse_missingHostRejected() {
        assertNull(SshFmt.parse("ssh://alice:pw@:22"))
    }

    @Test
    fun toUri_parseRoundTrip_password() {
        val config = SshFmt.parse("ssh://alice:hunter2@ssh.example.com:2222#dev%20box")
        assertNotNull(config)
        val uri = SshFmt.toUri(config!!)
        val back = SshFmt.parse("ssh://$uri")
        assertNotNull(back)
        val b = back!!
        assertEquals(config.server, b.server)
        assertEquals(config.serverPort, b.serverPort)
        assertEquals(config.username, b.username)
        assertEquals(config.password, b.password)
        assertEquals(config.method, b.method)
        assertEquals(config.remarks, b.remarks)
    }

    @Test
    fun toUri_parseRoundTrip_key() {
        val config = SshFmt.parse("ssh://bob@ssh.example.com:22?hostkey=SHA256%3Axyz#jump")
        assertNotNull(config)
        val c = config!!
        c.secretKey = samplePem
        c.method = AppConfig.SSH_AUTH_KEY
        val uri = SshFmt.toUri(c)
        val back = SshFmt.parse("ssh://$uri")
        assertNotNull(back)
        val b = back!!
        assertEquals(samplePem, b.secretKey)
        assertEquals(AppConfig.SSH_AUTH_KEY, b.method)
        assertEquals("SHA256:xyz", b.publicKey)
        assertEquals("bob", b.username)
    }

    @Test
    fun isComplete_requirements() {
        val pw = SshFmt.parse("ssh://alice:hunter2@ssh.example.com:22")
        assertNotNull(pw)
        assertTrue(SshFmt.isComplete(pw!!))

        val noPw = SshFmt.parse("ssh://alice@ssh.example.com:22")
        assertNotNull(noPw)
        assertFalse(SshFmt.isComplete(noPw!!))

        val encoded = SshFmt.encodePrivateKey(samplePem)
        val keyed = SshFmt.parse(
            "ssh://bob@ssh.example.com:22?key=${java.net.URLEncoder.encode(encoded, "UTF-8")}"
        )
        assertNotNull(keyed)
        assertTrue(SshFmt.isComplete(keyed!!))
    }

    @Test
    fun encodeDecodePrivateKey_roundTrip() {
        val encoded = SshFmt.encodePrivateKey(samplePem)
        assertEquals(samplePem, SshFmt.decodePrivateKey(encoded))
    }
}
