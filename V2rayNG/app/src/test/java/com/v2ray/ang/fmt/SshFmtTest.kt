package com.v2ray.ang.fmt

import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SshFmtTest {

    @Test
    fun `parse password auth uri`() {
        val config = SshFmt.parse("ssh://alice:s3cret@ssh.example.com:2222#My%20SSH")!!
        assertEquals(EConfigType.SSH, config.configType)
        assertEquals("ssh.example.com", config.server)
        assertEquals("2222", config.serverPort)
        assertEquals("alice", config.username)
        assertEquals("s3cret", config.password)
        assertEquals(SshFmt.AUTH_PASSWORD, config.method)
        assertEquals("My SSH", config.remarks)
    }

    @Test
    fun `parse defaults port 22 and remarks none`() {
        val config = SshFmt.parse("ssh://bob:pw@host.example")!!
        assertEquals("22", config.serverPort)
        assertEquals("none", config.remarks)
    }

    @Test
    fun `parse key auth with pin and passphrase`() {
        val uri = "ssh://carol@key.example?auth=key&privkey=-----BEGIN+OPENSSH+PRIVATE+KEY-----&passphrase=top&pin=SHA256%3Aabc%2Bdef"
        val config = SshFmt.parse(uri)!!
        assertEquals(SshFmt.AUTH_KEY, config.method)
        // decodeURIComponent keeps '+' literal (RFC 3986 strictness)
        assertEquals("-----+OPENSSH+PRIVATE+KEY-----", config.path)
        assertEquals("top", config.password)
        assertEquals("SHA256:abc+def", config.publicKey)
    }

    @Test
    fun `key auth inferred from privkey without auth param`() {
        val config = SshFmt.parse("ssh://dave@k.example?privkey=KEYDATA")!!
        assertEquals(SshFmt.AUTH_KEY, config.method)
        assertEquals("KEYDATA", config.path)
    }

    @Test
    fun `rejects missing host or username`() {
        assertNull(SshFmt.parse("ssh://user@"))
        assertNull(SshFmt.parse("ssh://:pw@example.com"))
        assertNull(SshFmt.parse("ssh://example.com"))
    }

    @Test
    fun `round trip preserves the profile`() {
        val original = SshFmt.parse("ssh://alice:s3cret@ssh.example.com:2222#My%20SSH")!!
        val uri = EConfigType.SSH.protocolScheme + SshFmt.toUri(original)
        val back = SshFmt.parse(uri)!!
        assertEquals(original.server, back.server)
        assertEquals(original.serverPort, back.serverPort)
        assertEquals(original.username, back.username)
        assertEquals(original.password, back.password)
        assertEquals(original.remarks, back.remarks)
        assertEquals(original.method, back.method)
    }

    @Test
    fun `round trip key auth`() {
        val original = SshFmt.parse("ssh://carol@k.example:22?auth=key&privkey=AAAAB3Nza&passphrase=pp&pin=SHA256%3Axyz#k")!!
        val back = SshFmt.parse(EConfigType.SSH.protocolScheme + SshFmt.toUri(original))!!
        assertEquals(original.path, back.path)
        assertEquals(original.password, back.password)
        assertEquals(original.publicKey, back.publicKey)
        assertEquals(SshFmt.AUTH_KEY, back.method)
    }
}
