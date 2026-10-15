package com.v2ray.ang.fmt

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.util.Utils
import java.net.URI

/**
 * Share-link format for SSH profiles.
 *
 * Accepted forms (password auth):
 *   ssh://user:secret@host:22#remarks
 *
 * Accepted forms (private-key auth):
 *   ssh://user@host:22?auth=key&privkey=<percent-encoded PEM>&passphrase=<pw>#remarks
 *
 * Optional query keys:
 *   auth        "password" (default) or "key"
 *   privkey     percent-encoded OpenSSH private key (stored in [ProfileItem.path])
 *   passphrase  key passphrase (stored in [ProfileItem.password] when auth=key)
 *   pin         expected host-key fingerprint, e.g. SHA256:base64 (stored in [ProfileItem.publicKey])
 */
object SshFmt : FmtBase() {

    const val AUTH_PASSWORD = "password"
    const val AUTH_KEY = "key"

    /**
     * Parses an SSH URI string into a ProfileItem object.
     *
     * @param str the URI string to parse
     * @return the parsed ProfileItem object, or null if parsing fails
     */
    fun parse(str: String): ProfileItem? {
        val config = ProfileItem.create(EConfigType.SSH)

        val uri = URI(Utils.fixIllegalUrl(str))
        if (uri.idnHost.isEmpty()) return null

        config.remarks = Utils.decodeURIComponent(uri.fragment.orEmpty()).let { it.ifEmpty { "none" } }
        config.server = uri.idnHost
        config.serverPort = if (uri.port > 0) uri.port.toString() else "22"

        val queryParam = if (uri.rawQuery.isNullOrEmpty()) emptyMap() else getQueryParam(uri)
        val auth = queryParam["auth"]?.lowercase() ?: if (queryParam["privkey"].isNotNullEmpty()) AUTH_KEY else AUTH_PASSWORD
        config.method = if (auth == AUTH_KEY) AUTH_KEY else AUTH_PASSWORD
        config.path = queryParam["privkey"]?.nullIfBlank()
        config.publicKey = queryParam["pin"]?.nullIfBlank()
        if (config.method == AUTH_KEY) {
            config.password = queryParam["passphrase"] ?: ""
        }

        if (uri.userInfo?.isEmpty() == false) {
            val result = if (uri.userInfo.contains(":")) {
                uri.userInfo.split(":", limit = 2)
            } else {
                Utils.decode(uri.userInfo).split(":", limit = 2)
            }
            if (result.count() == 2) {
                config.username = result.first()
                if (config.method != AUTH_KEY || config.password.isNullOrEmpty()) {
                    config.password = result.last()
                }
            }
        }
        if (config.username.isNullOrEmpty()) return null

        return config
    }

    /**
     * Converts a ProfileItem object to an SSH URI string.
     *
     * @param config the ProfileItem object to convert
     * @return the converted URI string
     */
    fun toUri(config: ProfileItem): String {
        val dicQuery = HashMap<String, String>()
        val auth = if (config.method == AUTH_KEY) AUTH_KEY else AUTH_PASSWORD
        dicQuery["auth"] = auth
        if (auth == AUTH_KEY) {
            config.path?.nullIfBlank()?.let { dicQuery["privkey"] = it }
            config.password?.nullIfBlank()?.let { dicQuery["passphrase"] = it }
        }
        config.publicKey?.nullIfBlank()?.let { dicQuery["pin"] = it }

        val pw =
            if (config.password.isNotNullEmpty())
                "${config.username.orEmpty()}:${config.password}"
            else
                "${config.username.orEmpty()}:"

        return toUri(config, Utils.encode(pw, true), dicQuery)
    }
}
