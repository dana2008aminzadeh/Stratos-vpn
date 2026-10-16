package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.util.Utils
import java.net.URI

/**
 * Lightway (ExpressVPN protocol) profile format.
 *
 * URI layout:
 * `lightway://<password>@<host>:<port>?proto=<udp|tcp>&cipher=<aes-256-gcm|chacha20-poly1305>&sni=<name>&insecure=<0|1>&obfs=<pw>&mport=<hops>&mtu=<n>#<name>`
 *
 * - `password`: Lightway session password (PSK material).
 * - `proto`: `udp` (default) or `tcp` control channel.
 * - `cipher`: data-channel cipher, `aes-256-gcm` by default.
 * - `mport`: optional port hopping spec (same syntax as hysteria2 `mport`).
 * - `obfs`: optional obfuscation passphrase.
 */
object LightwayFmt : FmtBase() {

    /** Parses a `lightway://` or `lw://` URI body (scheme already stripped by caller prefix check). */
    fun parse(str: String): ProfileItem? {
        val config = ProfileItem.create(EConfigType.LIGHTWAY)

        val uri = URI(Utils.fixIllegalUrl(str))
        if (uri.idnHost.isEmpty()) return null
        if (uri.port <= 0) return null

        config.remarks = Utils.decodeURIComponent(uri.fragment.orEmpty()).let { it.ifEmpty { "none" } }
        config.server = uri.idnHost
        config.serverPort = uri.port.toString()
        config.password = uri.userInfo

        val queryParam = if (uri.rawQuery.isNullOrEmpty()) emptyMap() else getQueryParam(uri)

        config.network = when (queryParam["proto"]?.lowercase()) {
            AppConfig.LIGHTWAY_PROTO_TCP -> AppConfig.LIGHTWAY_PROTO_TCP
            else -> AppConfig.LIGHTWAY_PROTO_UDP
        }
        config.method = when (queryParam["cipher"]?.lowercase()) {
            AppConfig.LIGHTWAY_CIPHER_CHACHA -> AppConfig.LIGHTWAY_CIPHER_CHACHA
            else -> AppConfig.LIGHTWAY_CIPHER_AES
        }
        config.sni = queryParam["sni"]?.nullIfBlank()
        config.alpn = queryParam["alpn"]?.nullIfBlank()
        config.insecure = queryParam["insecure"] == "1"
        config.obfsPassword = queryParam["obfs"]?.nullIfBlank()
        config.portHopping = queryParam["mport"]?.nullIfBlank()
        config.portHoppingInterval = queryParam["phi"]?.nullIfBlank()
        config.mtu = Utils.parseInt(queryParam["mtu"], AppConfig.LIGHTWAY_DEFAULT_MTU)

        return config
    }

    /** Converts a ProfileItem to the body of a lightway URI (scheme is prefixed by the caller). */
    fun toUri(config: ProfileItem): String {
        val dicQuery = HashMap<String, String>()
        dicQuery["proto"] = config.network?.lowercase()?.takeIf {
            it == AppConfig.LIGHTWAY_PROTO_TCP || it == AppConfig.LIGHTWAY_PROTO_UDP
        } ?: AppConfig.LIGHTWAY_PROTO_UDP
        dicQuery["cipher"] = config.method?.lowercase()?.takeIf {
            it == AppConfig.LIGHTWAY_CIPHER_CHACHA || it == AppConfig.LIGHTWAY_CIPHER_AES
        } ?: AppConfig.LIGHTWAY_CIPHER_AES
        config.sni?.nullIfBlank()?.let { dicQuery["sni"] = it }
        config.alpn?.nullIfBlank()?.let { dicQuery["alpn"] = it }
        dicQuery["insecure"] = if (config.insecure == true) "1" else "0"
        config.obfsPassword?.nullIfBlank()?.let { dicQuery["obfs"] = it }
        config.portHopping?.nullIfBlank()?.let { dicQuery["mport"] = it }
        config.portHoppingInterval?.nullIfBlank()?.let { dicQuery["phi"] = it }
        config.mtu?.let { dicQuery["mtu"] = it.toString() }

        return toUri(config, config.password, dicQuery)
    }

    /** True when the profile has everything the engine needs to dial. */
    fun isComplete(config: ProfileItem): Boolean =
        config.server.isNotNullEmpty() && config.serverPort.isNotNullEmpty() && config.password.isNotNullEmpty()
}
