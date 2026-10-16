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
 * SSH tunnel profile format.
 *
 * URI layout:
 * `ssh://<user>[:<password>]@<host>:<port>?auth=<password|key>&key=<base64-pem>&hostkey=<fingerprint>&insecure=<0|1>#<name>`
 *
 * - password auth: `ssh://user:pass@host:22#name` (`auth=password` is implied).
 * - key auth: private key (PEM, base64-encoded in `key`), `auth=key`.
 * - `hostkey`: expected server host-key fingerprint, `SHA256:...` or `MD5:...`;
 *   when empty the first seen key is trusted (TOFU) unless `insecure=1` skips checks.
 */
object SshFmt : FmtBase() {

    /** Parses an `ssh://` URI into a ProfileItem, or null when the URI is not a valid SSH link. */
    fun parse(str: String): ProfileItem? {
        val config = ProfileItem.create(EConfigType.SSH)

        val uri = URI(Utils.fixIllegalUrl(str))
        if (uri.idnHost.isEmpty()) return null

        config.remarks = Utils.decodeURIComponent(uri.fragment.orEmpty()).let { it.ifEmpty { "none" } }
        config.server = uri.idnHost
        config.serverPort = if (uri.port > 0) uri.port.toString() else AppConfig.SSH_DEFAULT_PORT

        // toUri encodes "user:password" as one component; decode first, then split
        // on the first ':' so passwords that themselves contain ':' survive.
        val userInfo = Utils.decodeURIComponent(uri.rawUserInfo.orEmpty())
        if (userInfo.isNotEmpty()) {
            val idx = userInfo.indexOf(':')
            config.username = if (idx >= 0) userInfo.substring(0, idx) else userInfo
            if (idx >= 0) {
                config.password = userInfo.substring(idx + 1)
            }
        }

        val queryParam = if (uri.rawQuery.isNullOrEmpty()) emptyMap() else getQueryParam(uri)

        config.secretKey = queryParam["key"]?.nullIfBlank()?.let { decodePrivateKey(it) }
        config.publicKey = queryParam["hostkey"]?.nullIfBlank()
        config.insecure = queryParam["insecure"] == "1"
        config.method = when {
            config.secretKey.isNotNullEmpty() -> AppConfig.SSH_AUTH_KEY
            queryParam["auth"] == AppConfig.SSH_AUTH_KEY -> AppConfig.SSH_AUTH_KEY
            else -> AppConfig.SSH_AUTH_PASSWORD
        }

        return config
    }

    /** Converts a ProfileItem to the body of an ssh URI (scheme is prefixed by the caller). */
    fun toUri(config: ProfileItem): String {
        val dicQuery = HashMap<String, String>()
        dicQuery["auth"] = if (config.method == AppConfig.SSH_AUTH_KEY) {
            AppConfig.SSH_AUTH_KEY
        } else {
            AppConfig.SSH_AUTH_PASSWORD
        }
        config.secretKey?.nullIfBlank()?.let { dicQuery["key"] = encodePrivateKey(it) }
        config.publicKey?.nullIfBlank()?.let { dicQuery["hostkey"] = it }
        dicQuery["insecure"] = if (config.insecure == true) "1" else "0"

        val userInfo = if (config.password.isNotNullEmpty()) {
            "${config.username.orEmpty()}:${config.password.orEmpty()}"
        } else {
            config.username.orEmpty()
        }
        return toUri(config, userInfo, dicQuery)
    }

    /** Encodes a PEM private key for transport inside a URI query parameter. */
    fun encodePrivateKey(pem: String): String =
        java.util.Base64.getEncoder().withoutPadding()
            .encodeToString(pem.replace("\r\n", "\n").trim().toByteArray(Charsets.UTF_8))

    /** Decodes a PEM private key stored in a URI query parameter. */
    fun decodePrivateKey(encoded: String): String {
        val trimmed = encoded.trim()
        val decoded = try {
            java.util.Base64.getDecoder().decode(trimmed).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            try {
                java.util.Base64.getUrlDecoder().decode(trimmed).toString(Charsets.UTF_8)
            } catch (e2: Exception) {
                ""
            }
        }
        return decoded.ifBlank { trimmed }
    }

    /** True when the profile holds the credentials the engine needs to dial. */
    fun isComplete(config: ProfileItem): Boolean =
        config.server.isNotNullEmpty() &&
                config.serverPort.isNotNullEmpty() &&
                config.username.isNotNullEmpty() &&
                when (config.method) {
                    AppConfig.SSH_AUTH_KEY -> config.secretKey.isNotNullEmpty()
                    else -> config.password.isNotNullEmpty()
                }
}
