package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.util.Utils
import java.net.URI

/**
 * Share-link format for Lightway profiles.
 *
 * Accepted form:
 *   lightway://user:secret@host:27690?dn=<server-dn>&mode=udp&cipher=aes256&ca=<percent-encoded PEM>#remarks
 *
 * Defaults: port 27690, mode "udp", cipher "aes256".
 *
 * Field mapping: [ProfileItem.username]/[ProfileItem.password] = credentials,
 * [ProfileItem.sni] = expected server domain, [ProfileItem.network] = tcp|udp,
 * [ProfileItem.method] = cipher, [ProfileItem.path] = CA certificate (PEM).
 */
object LightwayFmt : FmtBase() {

    const val DEFAULT_MODE = "udp"
    const val DEFAULT_CIPHER = "aes256"
    const val DEFAULT_PORT = "27690"

    /**
     * Parses a Lightway URI string into a ProfileItem object.
     *
     * @param str the URI string to parse
     * @return the parsed ProfileItem object, or null if parsing fails
     */
    fun parse(str: String): ProfileItem? {
        val config = ProfileItem.create(EConfigType.LIGHTWAY)

        val uri = URI(Utils.fixIllegalUrl(str))
        if (uri.idnHost.isEmpty()) return null

        config.remarks = Utils.decodeURIComponent(uri.fragment.orEmpty()).let { it.ifEmpty { "none" } }
        config.server = uri.idnHost
        config.serverPort = if (uri.port > 0) uri.port.toString() else DEFAULT_PORT

        val queryParam = if (uri.rawQuery.isNullOrEmpty()) emptyMap() else getQueryParam(uri)
        config.sni = queryParam["dn"]?.takeIf { it.isNotBlank() }
        val mode = queryParam["mode"]?.lowercase()
        config.network = if (mode == "tcp" || mode == "udp") mode else DEFAULT_MODE
        config.method = queryParam["cipher"]?.takeIf { it.isNotBlank() } ?: DEFAULT_CIPHER
        config.path = queryParam["ca"]?.takeIf { it.isNotBlank() }
        config.publicKey = queryParam["token"]?.takeIf { it.isNotBlank() }

        if (uri.userInfo?.isEmpty() == false) {
            val result = if (uri.userInfo.contains(":")) {
                uri.userInfo.split(":", limit = 2)
            } else {
                Utils.decode(uri.userInfo).split(":", limit = 2)
            }
            if (result.count() == 2) {
                config.username = result.first()
                config.password = result.last()
            }
        }
        if (config.username.isNullOrEmpty() && config.publicKey.isNullOrEmpty()) return null

        return config
    }

    /**
     * Converts a ProfileItem object to a Lightway URI string.
     *
     * @param config the ProfileItem object to convert
     * @return the converted URI string
     */
    fun toUri(config: ProfileItem): String {
        val dicQuery = HashMap<String, String>()
        config.sni?.takeIf { it.isNotBlank() }?.let { dicQuery["dn"] = it }
        dicQuery["mode"] = config.network?.lowercase()?.takeIf { it == "tcp" || it == "udp" } ?: DEFAULT_MODE
        dicQuery["cipher"] = config.method?.takeIf { it.isNotBlank() } ?: DEFAULT_CIPHER
        config.path?.takeIf { it.isNotBlank() }?.let { dicQuery["ca"] = it }
        config.publicKey?.takeIf { it.isNotBlank() }?.let { dicQuery["token"] = it }

        val pw =
            if (config.password.isNotNullEmpty())
                "${config.username.orEmpty()}:${config.password}"
            else
                "${config.username.orEmpty()}:"

        return toUri(config, Utils.encode(pw, true), dicQuery)
    }
}
