package com.v2ray.ang.fmt

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.Utils
import java.net.URI

/**
 * Share-link format for DNSTT (DNS-tunnel) profiles.
 *
 * Accepted form:
 *   dnstt://tunnel.example.com?pubkey=<64 hex digits>&transport=udp&resolver=1.1.1.1:53#remarks
 *
 * Transport variants:
 *   - `udp`  (default): plain DNS to `resolver` (`host:port`, defaults to 1.1.1.1:53)
 *   - `doh`            : DNS over HTTPS; `dns` is the full DoH URL (…/dns-query)
 *   - `dot`            : DNS over TLS;   `dns` is `host:853`
 *
 * Field mapping: [ProfileItem.server] = tunnel domain, [ProfileItem.serverPort] = UDP
 * resolver port, [ProfileItem.host] = UDP resolver host, [ProfileItem.method] =
 * transport (udp|doh|dot), [ProfileItem.path] = DoH URL / DoT address,
 * [ProfileItem.publicKey] = dnstt server public key (64 hex digits from
 * `dnstt-server -gen-key`).
 */
object DnsttFmt : FmtBase() {

    const val TRANSPORT_UDP = "udp"
    const val TRANSPORT_DOH = "doh"
    const val TRANSPORT_DOT = "dot"
    const val DEFAULT_RESOLVER_HOST = "1.1.1.1"
    private const val DEFAULT_RESOLVER_PORT = "53"

    /**
     * Parses a dnstt URI string into a ProfileItem object.
     *
     * @param str the URI string to parse
     * @return the parsed ProfileItem object, or null if parsing fails
     */
    fun parse(str: String): ProfileItem? {
        val config = ProfileItem.create(EConfigType.DNSTT)

        val uri = URI(Utils.fixIllegalUrl(str))
        if (uri.idnHost.isEmpty()) return null
        if (uri.rawQuery.isNullOrEmpty()) return null

        val queryParam = getQueryParam(uri)
        val pubkey = queryParam["pubkey"]?.trim().orEmpty()
        if (pubkey.isEmpty()) return null

        val transport = queryParam["transport"]?.lowercase()
        val dnsTarget = queryParam["dns"].orEmpty()
        val transportFinal = when {
            transport == TRANSPORT_DOH || transport == TRANSPORT_DOT -> transport
            transport == TRANSPORT_UDP -> TRANSPORT_UDP
            dnsTarget.startsWith("http://") || dnsTarget.startsWith("https://") -> TRANSPORT_DOH
            dnsTarget.isNotBlank() -> TRANSPORT_DOT
            else -> TRANSPORT_UDP
        }

        config.remarks = Utils.decodeURIComponent(uri.fragment.orEmpty()).let { it.ifEmpty { "none" } }
        config.server = uri.idnHost
        config.method = transportFinal
        config.publicKey = pubkey
        config.path = dnsTarget.nullIfBlank()
        config.host = when (transportFinal) {
            TRANSPORT_UDP -> queryParam["resolver"]?.substringBeforeLast(':')?.nullIfBlank() ?: DEFAULT_RESOLVER_HOST
            else -> null
        }
        config.serverPort = when (transportFinal) {
            TRANSPORT_UDP ->
                queryParam["resolver"]?.substringAfterLast(':', "")?.toIntOrNull()?.toString()
                    ?: (if (uri.port > 0) uri.port.toString() else DEFAULT_RESOLVER_PORT)
            else -> if (uri.port > 0) uri.port.toString() else DEFAULT_RESOLVER_PORT
        }

        return config
    }

    /**
     * Converts a ProfileItem object to a dnstt URI string.
     *
     * @param config the ProfileItem object to convert
     * @return the converted URI string
     */
    fun toUri(config: ProfileItem): String {
        val transport = when (config.method?.lowercase()) {
            TRANSPORT_DOH, TRANSPORT_DOT -> config.method!!.lowercase()
            else -> TRANSPORT_UDP
        }
        val dicQuery = HashMap<String, String>()
        dicQuery["pubkey"] = config.publicKey.orEmpty()
        dicQuery["transport"] = transport
        when (transport) {
            TRANSPORT_UDP -> {
                val resolverHost = config.host?.nullIfBlank() ?: DEFAULT_RESOLVER_HOST
                val resolverPort = config.serverPort?.toIntOrNull() ?: DEFAULT_RESOLVER_PORT.toInt()
                dicQuery["resolver"] = "$resolverHost:$resolverPort"
            }

            else -> config.path?.nullIfBlank()?.let { dicQuery["dns"] = it }
        }

        val query = dicQuery.entries.joinToString("&") { (k, v) -> "$k=${Utils.encodeURIComponent(v)}" }
        val host = Utils.getIpv6Address(HttpUtil.toIdnDomain(config.server.orEmpty()))
        // NOTE: the scheme is intentionally NOT included — AngConfigManager.shareConfig
        // prepends EConfigType.protocolScheme, same as every other format.
        return "$host?$query#${Utils.encodeURIComponent(config.remarks)}"
    }
}
