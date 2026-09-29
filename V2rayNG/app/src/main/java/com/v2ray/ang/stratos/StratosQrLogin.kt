package com.v2ray.ang.stratos

/**
 * Parses login QR payloads for Stratos VPN.
 *
 * Accepted forms:
 * - JSON:  {"u":"fyx12345","p":"secret"}  (also accepts "username"/"password" keys)
 * - URI:   stratos://login?u=fyx12345&p=secret
 *
 * Pure string logic (JVM-testable, no Android dependencies).
 */
object StratosQrLogin {

    data class Credentials(val username: String, val password: String)

    private val JSON_U = Regex("\"(?:u|username)\"\\s*:\\s*\"([^\"]+)\"")
    private val JSON_P = Regex("\"(?:p|password)\"\\s*:\\s*\"([^\"]+)\"")

    fun parse(content: String?): Credentials? {
        val text = content?.trim().orEmpty()
        if (text.isEmpty()) return null

        if (text.startsWith("{")) {
            val u = JSON_U.find(text)?.groupValues?.get(1)
            val p = JSON_P.find(text)?.groupValues?.get(1)
            if (u != null && p != null) return Credentials(u, p)
        }

        if (text.startsWith("stratos://", ignoreCase = true)) {
            val query = text.substringAfter('?', "")
            if (query.isBlank()) return null
            val params = query.split('&').mapNotNull {
                val idx = it.indexOf('=')
                if (idx <= 0) null else it.substring(0, idx) to it.substring(idx + 1)
            }.toMap()
            val u = params["u"] ?: params["username"]
            val p = params["p"] ?: params["password"]
            if (!u.isNullOrBlank() && !p.isNullOrBlank()) return Credentials(u, p)
        }
        return null
    }
}
