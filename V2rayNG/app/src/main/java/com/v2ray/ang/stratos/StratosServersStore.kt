package com.v2ray.ang.stratos

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.fmt.Hysteria2Fmt
import com.v2ray.ang.fmt.ShadowsocksFmt
import com.v2ray.ang.fmt.SocksFmt
import com.v2ray.ang.fmt.TrojanFmt
import com.v2ray.ang.fmt.VlessFmt
import com.v2ray.ang.fmt.VmessFmt
import com.v2ray.ang.fmt.WireguardFmt
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import java.security.MessageDigest

/**
 * Bridges the Stratos panel server list into the v2rayNG profile store.
 *
 * Servers arrive from the panel as share links (never shown to the user) and are
 * imported under a dedicated, hidden subscription with **deterministic GUIDs**, so
 * re-syncs never produce duplicates and ping results survive refreshes.
 */
object StratosServersStore {

    const val SUB_ID = "stratos_fleet"
    private const val SUB_REMARKS = "Stratos Fleet"

    /** Deterministic profile GUID for a panel server id (stable across re-syncs). */
    fun guidFor(serverId: String): String {
        val digest = MessageDigest.getInstance("MD5").digest("stratos:$serverId".toByteArray())
        return "st" + digest.joinToString("") { "%02x".format(it) }.take(22)
    }

    /** Makes sure the hidden Stratos subscription entry exists (URL stays empty). */
    fun ensureSubscription() {
        if (MmkvManager.decodeSubscription(SUB_ID) == null) {
            MmkvManager.encodeSubscription(
                SUB_ID,
                SubscriptionItem(
                    remarks = SUB_REMARKS,
                    url = "",
                    enabled = true,
                    autoUpdate = false,
                ),
            )
        }
    }

    /**
     * Reconciles the local profile store with the given panel servers.
     * New/changed entries are (re)imported, vanished entries removed.
     */
    @Synchronized
    fun sync(servers: List<StratosServer>) {
        ensureSubscription()

        val wantedGuids = mutableListOf<String>()

        for (server in servers) {
            val parsed = parse(server.config) ?: continue
            val guid = guidFor(server.id)
            parsed.subscriptionId = SUB_ID
            parsed.remarks = displayRemark(server)
            MmkvManager.encodeServerConfig(guid, parsed)
            wantedGuids.add(guid)
        }

        // Remove imported servers that disappeared from the panel
        val previous = MmkvManager.decodeServerList(SUB_ID)
        for (guid in previous) {
            if (guid !in wantedGuids) {
                MmkvManager.removeServer(guid)
            }
        }

        // Persist list order (panel order)
        if (wantedGuids.isNotEmpty()) {
            MmkvManager.encodeServerList(wantedGuids.toMutableList(), SUB_ID)
        }

        // Fix the active selection if it vanished, seed it if empty
        val selected = MmkvManager.getSelectServer()
        val selectionValid = selected != null && MmkvManager.decodeServerConfig(selected) != null
        if (!selectionValid) {
            wantedGuids.firstOrNull()?.let { MmkvManager.setSelectServer(it) }
        }

        LogUtil.i(AppConfig.TAG, "Stratos fleet synced: ${wantedGuids.size} servers")
    }

    /** All Stratos profile GUIDs currently present, in panel order. */
    fun currentGuids(): List<String> = MmkvManager.decodeServerList(SUB_ID).filter {
        MmkvManager.decodeServerConfig(it) != null
    }

    /** The guid representing a panel server, if imported. */
    fun resolveGuid(server: StratosServer): String = guidFor(server.id)

    private fun displayRemark(server: StratosServer): String =
        "${flagEmoji(server.countryCode)} ${server.name}"

    private fun parse(config: String): ProfileItem? {
        val trimmed = config.trim()
        return try {
            when {
                trimmed.startsWith("vless://") -> VlessFmt.parse(trimmed)
                trimmed.startsWith("vmess://") -> VmessFmt.parse(trimmed)
                trimmed.startsWith("ss://") -> ShadowsocksFmt.parse(trimmed)
                trimmed.startsWith("trojan://") -> TrojanFmt.parse(trimmed)
                trimmed.startsWith("socks://") || trimmed.startsWith("socks4://") || trimmed.startsWith("socks5://") -> SocksFmt.parse(trimmed)
                trimmed.startsWith("wireguard://") -> WireguardFmt.parse(trimmed)
                trimmed.startsWith("hysteria2://") || trimmed.startsWith("hy2://") -> Hysteria2Fmt.parse(trimmed)
                else -> null
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Stratos: failed to parse server config", e)
            null
        }
    }

    fun flagEmoji(countryCode: String): String {
        if (countryCode.length != 2) return "🌐"
        val base = 0x1F1E6
        val first = base + (countryCode[0].uppercaseChar() - 'A')
        val second = base + (countryCode[1].uppercaseChar() - 'A')
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }
}
