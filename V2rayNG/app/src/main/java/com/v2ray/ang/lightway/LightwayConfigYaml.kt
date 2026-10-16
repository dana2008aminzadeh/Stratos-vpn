package com.v2ray.ang.lightway

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.fmt.LightwayFmt

/**
 * Builds the YAML document consumed by the Lightway engine's `parallel_connect`
 * (`servers:` list of `ConnectionConfig`, see lightway-client `config.rs`).
 *
 * Pure string logic (JVM-testable, no Android dependencies). Every value is
 * single-quoted — except the CA certificate, which uses a block literal so PEM
 * line breaks survive — and unknown fields are omitted for serde defaults.
 */
object LightwayConfigYaml {

    /** Renders one server entry for the Lightway engine. */
    fun build(config: ProfileItem): String {
        val host = config.server.orEmpty().trim()
        val port = config.serverPort?.toIntOrNull() ?: LightwayFmt.DEFAULT_PORT.toInt()
        val mode = when (config.network?.lowercase()) {
            "tcp" -> "tcp"
            else -> LightwayFmt.DEFAULT_MODE
        }
        val cipher = config.method?.trim()?.takeIf { it.isNotEmpty() } ?: LightwayFmt.DEFAULT_CIPHER

        val out = StringBuilder()
        out.append("---\n")
        out.append("servers:\n")
        out.append("  - server: ${quote("$host:$port")}\n")
        out.append("    mode: ${quote(mode)}\n")
        out.append("    cipher: ${quote(cipher)}\n")
        config.sni?.trim()?.takeIf { it.isNotEmpty() }?.let {
            out.append("    server_dn: ${quote(it)}\n")
        }
        config.username?.trim()?.takeIf { it.isNotEmpty() }?.let {
            out.append("    user: ${quote(it)}\n")
        }
        config.password?.takeIf { it.isNotEmpty() }?.let {
            out.append("    password: ${quote(it)}\n")
        }
        config.publicKey?.trim()?.takeIf { it.isNotEmpty() }?.let {
            out.append("    token: ${quote(it)}\n")
        }
        config.path?.trim()?.takeIf { it.isNotEmpty() }?.let { ca ->
            if (ca.contains('\n')) {
                out.append("    ca_cert: |\n")
                ca.lineSequence().forEach { line -> out.append("      $line\n") }
            } else {
                out.append("    ca_cert: ${quote(ca)}\n")
            }
        }
        return out.toString()
    }

    /**
     * YAML single-quoted scalar: safe for any content except `'` itself,
     * which YAML escapes by doubling.
     */
    private fun quote(value: String): String = "'" + value.replace("'", "''") + "'"
}
