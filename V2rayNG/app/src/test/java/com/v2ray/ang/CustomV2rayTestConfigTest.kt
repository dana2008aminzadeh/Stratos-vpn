package com.v2ray.ang

import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.fmt.CustomFmt
import com.v2ray.ang.util.JsonUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Stratos test configuration shipped for QA: a VLESS + REALITY full config
 * (Germany node) with the Iran-direct routing split. Guards that the custom-config
 * import path keeps working for exactly this document — remarks, endpoint
 * (vnext shape) and routing rules must all survive parsing.
 */
class CustomV2rayTestConfigTest {

    private val testConfig = """
{
  "dns": {
    "servers": ["1.1.1.1", "1.0.0.1"],
    "queryStrategy": "UseIP"
  },
  "log": { "loglevel": "warning" },
  "routing": {
    "rules": [
      { "type": "field", "protocol": ["bittorrent"], "outboundTag": "block" },
      {
        "domain": [
          "regexp:.*\\.ir",
          "domain:digikala.com",
          "domain:snapp.taxi",
          "domain:technolife.com",
          "domain:eitaa.com",
          "domain:sibapp.com",
          "domain:sanjesh.org"
        ],
        "outboundTag": "direct"
      }
    ],
    "domainMatcher": "hybrid",
    "domainStrategy": "IPIfNonMatch"
  },
  "inbounds": [
    {
      "tag": "socks",
      "port": 10808,
      "listen": "127.0.0.1",
      "protocol": "socks",
      "settings": { "udp": true, "auth": "noauth" },
      "sniffing": { "enabled": true, "routeOnly": false, "destOverride": ["http", "tls", "quic"] }
    },
    {
      "tag": "http",
      "port": 10809,
      "listen": "127.0.0.1",
      "protocol": "http",
      "settings": { "allowTransparent": false },
      "sniffing": { "enabled": true, "routeOnly": false, "destOverride": ["http", "tls", "quic"] }
    }
  ],
  "outbounds": [
    {
      "tag": "proxy",
      "protocol": "vless",
      "settings": {
        "vnext": [
          {
            "address": "181.214.99.150",
            "port": 443,
            "users": [
              {
                "id": "fb558f95-e881-4009-8c58-f82c87d5bdba",
                "encryption": "none",
                "flow": "xtls-rprx-vision"
              }
            ]
          }
        ]
      },
      "streamSettings": {
        "network": "tcp",
        "tcpSettings": {},
        "security": "reality",
        "realitySettings": {
          "serverName": "play.google.com",
          "publicKey": "-3FYNptvHNKGKULt4jOkSqSfBbpl9ef_ijzo_8I7OXk",
          "shortId": "8f2798f9",
          "fingerprint": "chrome"
        }
      }
    },
    { "tag": "direct", "protocol": "freedom" },
    { "tag": "block", "protocol": "blackhole" }
  ],
  "remarks": "🇩🇪 ⛓️ REAL [TLS]"
}
    """.trimIndent()

    @Test
    fun `custom import fills remarks and endpoint`() {
        val profile = CustomFmt.parse(testConfig)
        assertEquals(EConfigType.CUSTOM, profile.configType)
        assertEquals("🇩🇪 ⛓️ REAL [TLS]", profile.remarks)
        // Endpoint is stored in the raw vnext array — must be recovered for display.
        assertEquals("181.214.99.150", profile.server)
        assertEquals("443", profile.serverPort)
    }

    @Test
    fun `full config keeps inbounds, outbounds and routing intact`() {
        val full = JsonUtil.fromJson(testConfig, V2rayConfig::class.java)
        assertNotNull(full)
        assertEquals(2, full!!.inbounds.size)
        assertEquals(3, full.outbounds.size)
        assertEquals(2, full.routing?.rules?.size)
        assertEquals("IPIfNonMatch", full.routing?.domainStrategy)

        val proxy = full.getProxyOutbound()
        assertNotNull(proxy)
        assertEquals("vless", proxy!!.protocol)
        assertEquals("reality", proxy.streamSettings?.security)
        assertEquals("tcp", proxy.streamSettings?.network)
        assertEquals("play.google.com", proxy.streamSettings?.realitySettings?.serverName)
        assertEquals("8f2798f9", proxy.streamSettings?.realitySettings?.shortId)
        assertTrue(full.routing!!.rules.any { it.outboundTag == "block" })
        assertTrue(full.routing!!.rules.any { it.outboundTag == "direct" })
    }
}
