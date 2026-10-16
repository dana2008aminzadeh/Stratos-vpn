package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.util.Utils
import java.net.URI

/**
 * dnstt (DNS tunnel) profile format.
 *
 * URI layout:
 * `dnstt://<server-pubkey-hex>@<tunnel-domain>?resolver=<spec>&mtu=<n>#<name>`
 *
 * - `server-pubkey-hex`: the dnstt server public key as 64 hex characters.
 * - `tunnel-domain`: root of the DNS zone delegated to the tunnel.
 * - `resolver`: upstream DNS transport used to carry the tunnel:
 *   `8.8.8.8` (UDP, default port 53), `tls:8.8.8.8[:853]` (DoT),
 *   `https://dns.google/dns-query` (DoH).
 * - `mtu`: tunnel MTU, 1280 by default.
 *
 * The resolver port is kept in [ProfileItem.serverPort] for display; the resolver
 * transport endpoint is kept verbatim in [ProfileItem.host].
 */
object DnsttFmt : FmtBase() {

    /** Parses a `dnstt://` URI into a ProfileItem, or null when the URI is not a valid dnstt link. */
    fun parse(str: String): ProfileItem? {
        val config = ProfileItem.create(EConfigType.DNSTT)

        val uri = URI(Utils.fixIllegalUrl(str))
        if (uri.idnHost.isEmpty()) return null

        val publicKey = uri.userInfo.orEmpty().trim().lowercase()
        if (!isValidPubKey(publicKey)) return null

        config.remarks = Utils.decodeURIComponent(uri.fragment.orEmpty()).let { it.ifEmpty { "none" } }
        config.server = uri.idnHost
        config.publicKey = publicKey

        val queryParam = if (uri.rawQuery.isNullOrEmpty()) emptyMap() else getQueryParam(uri)
        val resolver = queryParam["resolver"].nullIfBlank() ?: AppConfig.DNSTT_DEFAULT_RESOLVER
        config.host = normalizeResolver(resolver)
        config.serverPort = resolverPort(config.host.orEmpty()).toString()
        config.mtu = Utils.parseInt(queryParam["mtu"], AppConfig.DNSTT_DEFAULT_MTU)

        return config
    }

    /** Converts a ProfileItem to the body of a dnstt URI (scheme is prefixed by the caller). */
    fun toUri(config: ProfileItem): String {
        val dicQuery = HashMap<String, String>()
        dicQuery["resolver"] = config.host.nullIfBlank() ?: AppConfig.DNSTT_DEFAULT_RESOLVER
        config.mtu?.let { dicQuery["mtu"] = it.toString() }

        return toUri(config, config.publicKey.orEmpty(), dicQuery)
    }

    /** True when [key] is a well-formed 32-byte hex public key. */
    fun isValidPubKey(key: String?): Boolean =
        !key.isNullOrEmpty() && key.length == 64 && key.all { it in '0'..'9' || it in 'a'..'f' }

    /** Normalizes the accepted resolver spellings into `udp:`, `tls:` or a DoH URL. */
    fun normalizeResolver(resolver: String): String {
        val trimmed = resolver.trim().trimEnd('/')
        return when {
            trimmed.isEmpty() -> AppConfig.DNSTT_DEFAULT_RESOLVER
            trimmed.startsWith("udp:", ignoreCase = true) -> "udp:" + trimmed.substring(4)
            trimmed.startsWith("tls:", ignoreCase = true) -> "tls:" + trimmed.substring(4)
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            else -> "udp:$trimmed"
        }
    }

    /** True when the resolver is a DoH URL. */
    fun isDohResolver(resolver: String?): Boolean =
        resolver != null && (resolver.startsWith("https://", true) || resolver.startsWith("http://", true))

    /** True when the resolver is DNS-over-TLS. */
    fun isTlsResolver(resolver: String?): Boolean =
        resolver != null && resolver.startsWith("tls:", ignoreCase = true)

    /** Host part of a stored resolver spec, without scheme or port. */
    fun resolverHost(resolver: String?): String {
        val spec = resolver.orEmpty().removePrefix("udp:").removePrefix("tls:")
            .removePrefix("UDP:").removePrefix("TLS:")
        return when {
            isDohResolver(resolver) -> spec
            spec.count { it == ':' } == 1 && spec.substringAfter(':').toIntOrNull() != null ->
                spec.substringBefore(':')

            else -> spec
        }
    }

    /** Port part of a stored resolver spec, or the transport default. */
    fun resolverPort(resolver: String): Int {
        if (isDohResolver(resolver)) {
            return if (resolver.startsWith("http://", true)) 80 else 443
        }
        val spec = resolver.removePrefix("udp:").removePrefix("tls:")
            .removePrefix("UDP:").removePrefix("TLS:")
        val default = if (isTlsResolver(resolver)) 853 else 53
        val after = spec.substringAfter(':', "")
        return after.toIntOrNull() ?: default
    }
}
