package com.v2ray.ang.fmt

import com.google.gson.JsonParser
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.JsonUtil

object CustomFmt : FmtBase() {
    /** Protocol entries of raw configs that are never the user-selected proxy. */
    private val NON_PROXY_PROTOCOLS = setOf(
        "freedom", "blackhole", "dns", "dokodemo-door", "loopback", "block", "direct",
    )

    /**
     * Parses a JSON string into a ProfileItem object.
     *
     * @param str the JSON string to parse
     * @return the parsed ProfileItem object, or null if parsing fails
     */
    fun parse(str: String): ProfileItem {
        val config = ProfileItem.create(EConfigType.CUSTOM)

        val fullConfig = JsonUtil.fromJson(str, V2rayConfig::class.java)
        val outbound = fullConfig?.getProxyOutbound()

        config.remarks = fullConfig?.remarks ?: System.currentTimeMillis().toString()
        config.server = outbound?.getServerAddress()
        config.serverPort = outbound?.getServerPort()?.toString()

        // Raw configs keep the proxy address in vnext/servers arrays, which the flat
        // OutSettingsBean does not deserialize — recover them for display and ping.
        if (config.server == null || config.serverPort == null) {
            recoverRawAddress(str, config)
        }

        return config
    }

    /**
     * Reads `outbounds[].settings.vnext[0]` / `outbounds[].settings.servers[0]`
     * (the Xray wire shape for VMess/VLESS/SS/Trojan) so custom profiles show a
     * useful endpoint instead of an empty cell.
     */
    private fun recoverRawAddress(str: String, config: ProfileItem) {
        try {
            val root = JsonParser.parseString(str).asJsonObject
            val outbounds = root.getAsJsonArray("outbounds") ?: return
            for (element in outbounds) {
                val outbound = element.asJsonObject
                val protocol = outbound.get("protocol")?.asString?.lowercase() ?: continue
                if (protocol in NON_PROXY_PROTOCOLS) continue
                val settings = outbound.getAsJsonObject("settings") ?: continue

                val endpoint = settings.getAsJsonArray("vnext")?.firstOrNull()?.asJsonObject
                    ?: settings.getAsJsonArray("servers")?.firstOrNull()?.asJsonObject
                if (endpoint != null) {
                    if (config.server == null) {
                        config.server = endpoint.get("address")?.asString
                    }
                    if (config.serverPort == null) {
                        config.serverPort = endpoint.get("port")?.asInt?.toString()
                    }
                    return
                }
            }
        } catch (_: Exception) {
            // Not a JSON object / unexpected shape: the flattened values stay as-is.
        }
    }
}
