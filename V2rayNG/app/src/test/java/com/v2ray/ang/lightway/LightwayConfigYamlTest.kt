package com.v2ray.ang.lightway

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.fmt.LightwayFmt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LightwayConfigYamlTest {

    private fun profile(
        server: String = "lw.example.com",
        port: String = "27690",
        mode: String = "udp",
        cipher: String = "aes256",
        user: String = "alice",
        password: String = "s3cret",
        dn: String = "lw.example.com",
        ca: String? = null,
        token: String? = null,
    ) = ProfileItem.create(EConfigType.LIGHTWAY).apply {
        this.server = server
        this.serverPort = port
        this.network = mode
        this.method = cipher
        this.username = user
        this.password = password
        this.sni = dn
        this.path = ca
        this.publicKey = token
    }

    @Test
    fun `renders a single server entry with all fields`() {
        val yaml = LightwayConfigYaml.build(profile())
        assertTrue(yaml.contains("servers:"))
        assertTrue(yaml.contains("server: 'lw.example.com:27690'"))
        assertTrue(yaml.contains("mode: 'udp'"))
        assertTrue(yaml.contains("cipher: 'aes256'"))
        assertTrue(yaml.contains("server_dn: 'lw.example.com'"))
        assertTrue(yaml.contains("user: 'alice'"))
        assertTrue(yaml.contains("password: 's3cret'"))
        assertFalse(yaml.contains("token:"))
        assertFalse(yaml.contains("ca_cert:"))
    }

    @Test
    fun `omits absent optional fields`() {
        val yaml = LightwayConfigYaml.build(
            profile(user = "", password = "", dn = "", cipher = ""),
        )
        assertFalse(yaml.contains("user:"))
        assertFalse(yaml.contains("password:"))
        assertFalse(yaml.contains("server_dn:"))
        assertTrue(yaml.contains("cipher: 'aes256'")) // cipher falls back to the default
    }

    @Test
    fun `single-quoted scalars escape embedded quotes`() {
        val yaml = LightwayConfigYaml.build(profile(password = "a'b"))
        assertTrue(yaml.contains("password: 'a''b'"))
    }

    @Test
    fun `multiline ca renders as a block literal`() {
        val ca = "-----BEGIN CERTIFICATE-----\nMIIBfake\n-----END CERTIFICATE-----"
        val yaml = LightwayConfigYaml.build(profile(ca = ca))
        assertTrue(yaml.contains("ca_cert: |\n"))
        assertTrue(yaml.contains("      -----BEGIN CERTIFICATE-----\n"))
        assertTrue(yaml.contains("      MIIBfake\n"))
        assertTrue(yaml.contains("      -----END CERTIFICATE-----\n"))
    }

    @Test
    fun `token profile renders token`() {
        val yaml = LightwayConfigYaml.build(profile(user = "", password = "", token = "tkn.123"))
        assertTrue(yaml.contains("token: 'tkn.123'"))
        assertFalse(yaml.contains("user:"))
    }

    @Test
    fun `tcp mode preserved and invalid mode normalized`() {
        assertTrue(LightwayConfigYaml.build(profile(mode = "tcp")).contains("mode: 'tcp'"))
        assertTrue(LightwayConfigYaml.build(profile(mode = "bogus")).contains("mode: 'udp'"))
    }

    @Test
    fun `host with colon in server field does not duplicate the port`() {
        // server may already be an IPv6 literal; the builder only appends the port
        val yaml = LightwayConfigYaml.build(profile(server = "2001:db8::1", port = "443"))
        assertTrue(yaml.contains("server: '2001:db8::1:443'") || yaml.contains("server: '[2001:db8::1]:443'"))
    }
}
