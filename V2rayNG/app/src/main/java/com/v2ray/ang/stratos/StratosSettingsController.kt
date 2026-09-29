package com.v2ray.ang.stratos

import androidx.annotation.StringRes
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil

/**
 * Stratos-owned subset of app settings: DNS presets and operator-controlled values.
 *
 * The operator (panel) can force settings on login ([forcedSettings]) and publish a
 * recommended bundle applied when the user taps "apply optimal settings"
 * ([bestSettings]). Users may still change any setting afterwards; a fresh sync can
 * re-apply the forced ones.
 */
object StratosSettingsController {

    data class DnsPreset(
        val id: String,
        @param:StringRes val labelRes: Int,
        @param:StringRes val descRes: Int,
        val remoteDns: String,
    )

    val dnsPresets = listOf(
        DnsPreset("default", R.string.stratos_dns_default, R.string.stratos_dns_default_desc, AppConfig.DNS_PROXY),
        DnsPreset("ads", R.string.stratos_dns_ads, R.string.stratos_dns_ads_desc, "https://dns.adguard-dns.com/dns-query"),
        DnsPreset("family", R.string.stratos_dns_family, R.string.stratos_dns_family_desc, "https://family.adguard-dns.com/dns-query"),
        DnsPreset("gaming", R.string.stratos_dns_gaming, R.string.stratos_dns_gaming_desc, "https://dns.google/dns-query"),
    )

    private const val KEY_DNS_PRESET = "stratos_dns_preset"

    fun currentDnsPresetId(): String {
        val stored = MmkvManager.decodeSettingsString(KEY_DNS_PRESET)
        if (!stored.isNullOrBlank()) return stored
        val remote = MmkvManager.decodeSettingsString(AppConfig.PREF_REMOTE_DNS)
        val match = dnsPresets.firstOrNull { it.remoteDns.equals(remote, ignoreCase = true) }
        return match?.id ?: "default"
    }

    fun applyDnsPreset(id: String): Boolean {
        val preset = dnsPresets.firstOrNull { it.id == id } ?: return false
        MmkvManager.encodeSettings(AppConfig.PREF_REMOTE_DNS, preset.remoteDns)
        MmkvManager.encodeSettings(KEY_DNS_PRESET, preset.id)
        LogUtil.i(AppConfig.TAG, "Stratos DNS preset applied: ${preset.id}")
        return true
    }

    /**
     * Applies an operator settings map (PREF_* key -> stringified value).
     * Values "true"/"false", integers and longs are stored typed; anything else
     * is stored as string. Settings the operator does not own are untouched.
     * Returns the number of settings changed.
     */
    fun applySettingsMap(settings: Map<String, String>): Int {
        var changed = 0
        for ((key, rawValue) in settings) {
            if (!ALLOWED_KEYS.contains(key)) continue
            val value = rawValue.trim()
            val previous = MmkvManager.decodeSettingsString(key)
            when {
                value.equals("true", true) || value.equals("false", true) ->
                    MmkvManager.encodeSettings(key, value.equals("true", true))

                value.toIntOrNull() != null ->
                    MmkvManager.encodeSettings(key, value.toInt())

                value.toLongOrNull() != null ->
                    MmkvManager.encodeSettings(key, value.toLong())

                else -> MmkvManager.encodeSettings(key, value)
            }
            if (previous != value) changed++
        }
        if (changed > 0) {
            LogUtil.i(AppConfig.TAG, "Stratos operator settings applied: $changed changed")
        }
        return changed
    }

    /** Applies the operator's recommended bundle (the "best settings" button). */
    fun applyBestSettings(): Int =
        applySettingsMap(StratosSession.adminState.value.bestSettings.ifEmpty { DEFAULT_BEST_SETTINGS })

    /** Applies the settings the operator enforces on every sync. */
    fun applyForcedSettings() {
        val forced = StratosSession.adminState.value.forcedSettings
        if (forced.isNotEmpty()) applySettingsMap(forced)
    }

    /** Sensible provider defaults, also used when the panel publishes no bundle. */
    val DEFAULT_BEST_SETTINGS = mapOf(
        AppConfig.PREF_SPEED_ENABLED to "true",
        AppConfig.PREF_MODE to "VPN",
        AppConfig.PREF_PROXY_SHARING to "false",
        AppConfig.PREF_PREFER_IPV6 to "false",
        AppConfig.PREF_IPV6_ENABLED to "false",
    )

    private val ALLOWED_KEYS = setOf(
        AppConfig.PREF_MODE,
        AppConfig.PREF_SPEED_ENABLED,
        AppConfig.PREF_PROXY_SHARING,
        AppConfig.PREF_LOCAL_DNS_ENABLED,
        AppConfig.PREF_FAKE_DNS_ENABLED,
        AppConfig.PREF_PREFER_IPV6,
        AppConfig.PREF_IPV6_ENABLED,
        AppConfig.PREF_REMOTE_DNS,
        AppConfig.PREF_DOMESTIC_DNS,
        AppConfig.PREF_VPN_DNS,
        AppConfig.PREF_PER_APP_PROXY,
        AppConfig.PREF_BYPASS_APPS,
        AppConfig.PREF_MUX_CONCURRENCY,
        AppConfig.PREF_MUX_XUDP_CONCURRENCY,
        AppConfig.PREF_MUX_ENABLED,
        AppConfig.PREF_FRAGMENT_PACKETS,
        AppConfig.PREF_FRAGMENT_LENGTH,
        AppConfig.PREF_FRAGMENT_INTERVAL,
        AppConfig.PREF_SNIFFING_ENABLED,
        AppConfig.PREF_ROUTE_ONLY_ENABLED,
        AppConfig.PREF_UI_MODE_NIGHT,
        AppConfig.PREF_DYNAMIC_COLOR,
    )
}
