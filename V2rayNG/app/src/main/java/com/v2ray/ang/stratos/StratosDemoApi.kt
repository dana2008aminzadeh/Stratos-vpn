package com.v2ray.ang.stratos

import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.JsonUtil
import kotlinx.coroutines.delay
import java.security.MessageDigest
import java.util.UUID

/**
 * Offline demo backend for Stratos VPN.
 *
 * Lets the whole app run end-to-end before the management panel exists:
 * - any `fyx????`-style username + 5–10 character password signs in (first sign-in
 *   creates the demo account, later sign-ins must reuse the same password);
 * - plan: 120 GiB / 30 days starting from first sign-in;
 * - device binding enforces the 1-device limit and models the conflict error;
 * - locally measured usage is persisted and counts against the plan.
 *
 * Replace with [StratosHttpApi] once the real panel ships — see docs/STRATOS_API.md.
 */
object StratosDemoApi : StratosApi {

    private const val PLAN_DATA_BYTES = 120L * 1024 * 1024 * 1024 // 120 GiB
    private const val PLAN_DURATION_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
    private const val KEY_PREFIX = "stratos_demo_account_"

    private data class DemoAccount(
        val username: String,
        val passwordHash: String,
        val firstLoginAt: Long,
        val usedBytes: Long,
        val boundDeviceId: String?,
    )

    private fun key(username: String) = KEY_PREFIX + username

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun load(username: String): DemoAccount? =
        MmkvManager.decodeSettingsString(key(username))?.let { JsonUtil.fromJsonSafe(it, DemoAccount::class.java) }

    private fun store(account: DemoAccount) {
        MmkvManager.encodeSettings(key(account.username), JsonUtil.toJson(account))
    }

    private fun toUser(account: DemoAccount): StratosUser {
        val expireAt = account.firstLoginAt + PLAN_DURATION_MS
        val expired = System.currentTimeMillis() >= expireAt
        val outOfData = account.usedBytes >= PLAN_DATA_BYTES
        return StratosUser(
            username = account.username,
            status = if (expired || outOfData) StratosUserStatus.EXPIRED else StratosUserStatus.ACTIVE,
            dataLimitBytes = PLAN_DATA_BYTES,
            usedBytes = account.usedBytes,
            expireAtMillis = expireAt,
            deviceLimit = 1,
        )
    }

    private fun tokenFor(username: String, account: DemoAccount): String =
        "demo-" + sha256("$username:${account.firstLoginAt}:${StratosSession.deviceIdSalt()}").take(24)

    override suspend fun login(username: String, password: String, deviceId: String, deviceName: String): StratosLoginResult {
        delay(700) // simulate network
        val normalized = StratosValidators.normalizeUsername(username)
        if (!StratosValidators.isValidUsername(normalized) || !StratosValidators.isValidPassword(password)) {
            return StratosLoginResult.InvalidCredentials
        }
        val now = System.currentTimeMillis()
        val existing = load(normalized)
        val account = existing ?: DemoAccount(
            username = normalized,
            passwordHash = sha256(password),
            firstLoginAt = now,
            usedBytes = 0,
            boundDeviceId = null,
        )
        if (existing != null && existing.passwordHash != sha256(password)) {
            return StratosLoginResult.InvalidCredentials
        }
        if (account.boundDeviceId != null && account.boundDeviceId != deviceId) {
            return StratosLoginResult.DeviceConflict
        }
        val bound = account.copy(boundDeviceId = deviceId)
        store(bound)
        return StratosLoginResult.Success(
            token = tokenFor(normalized, bound),
            user = toUser(bound),
            admin = fetchAdminConfig(null),
        )
    }

    override suspend fun logout(token: String) {
        delay(150)
        val account = accountForToken(token) ?: return
        store(account.copy(boundDeviceId = null))
    }

    override suspend fun fetchUser(token: String): StratosUser? {
        delay(200)
        return accountForToken(token)?.let(::toUser)
    }

    override suspend fun fetchServers(token: String): List<StratosServer> {
        delay(250)
        if (accountForToken(token) == null) return emptyList()
        return demoServers()
    }

    override suspend fun fetchAdminConfig(token: String?): StratosAdminConfig {
        return StratosAdminConfig(
            renewUrl = "https://stratos.example.com/renew",
            websiteUrl = "https://stratos.example.com",
            telegramUrl = "https://t.me/stratos_vpn",
            notice = "",
            forcedSettings = emptyMap(),
            bestSettings = mapOf(
                "pref_speed_enabled" to "true",
                "pref_mode" to "VPN",
                "pref_prefer_ipv6" to "false",
                "pref_proxy_sharing_enabled" to "false",
            ),
        )
    }

    override suspend fun reportUsage(token: String, bytesUp: Long, bytesDown: Long): StratosUser? {
        val account = accountForToken(token) ?: return null
        val updated = account.copy(usedBytes = account.usedBytes + bytesUp + bytesDown)
        store(updated)
        return toUser(updated)
    }

    override suspend fun heartbeat(token: String, deviceId: String): Boolean {
        val account = accountForToken(token) ?: return false
        return account.boundDeviceId == null || account.boundDeviceId == deviceId
    }

    override suspend fun changePassword(token: String, currentPassword: String, newPassword: String): Boolean {
        delay(300)
        val account = accountForToken(token) ?: return false
        if (account.passwordHash != sha256(currentPassword)) return false
        if (!StratosValidators.isValidPassword(newPassword)) return false
        store(account.copy(passwordHash = sha256(newPassword)))
        return true
    }

    private fun accountForToken(token: String): DemoAccount? {
        if (!token.startsWith("demo-")) return null
        // Demo tokens embed no username; scan the few demo accounts instead.
        val candidates = MmkvManager.decodeSettingsString(KEYS_DEMO_ACCOUNTS).orEmpty()
            .split(',').filter { it.isNotBlank() }
        for (username in candidates) {
            val account = load(username) ?: continue
            if (tokenFor(username, account) == token) return account
        }
        // discover on demand: caller registered the username at login time
        val last = MmkvManager.decodeSettingsString(KEY_LAST_USERNAME).orEmpty()
        if (last.isNotBlank()) {
            register(last)
            val account = load(last) ?: return null
            if (tokenFor(last, account) == token) return account
        }
        return null
    }

    /** Registers a demo account username for token lookup (called by [StratosSession]). */
    fun register(username: String) {
        val current = MmkvManager.decodeSettingsString(KEYS_DEMO_ACCOUNTS).orEmpty()
        if (current.split(',').contains(username)) return
        MmkvManager.encodeSettings(KEYS_DEMO_ACCOUNTS, if (current.isBlank()) username else "$current,$username")
    }

    private const val KEYS_DEMO_ACCOUNTS = "stratos_demo_account_index"
    private const val KEY_LAST_USERNAME = "stratos_session_username"

    /** Demo fleet: clearly-marked demo endpoints across 6 countries with admin tags. */
    fun demoServers(): List<StratosServer> = listOf(
        StratosServer("de-1", "Frankfurt 1", "de", "Germany", demoLink("de1", "🇩🇪 Frankfurt 1"), listOf("Gaming", "Low ping")),
        StratosServer("de-2", "Frankfurt 2", "de", "Germany", demoLink("de2", "🇩🇪 Frankfurt 2"), listOf("Economy")),
        StratosServer("nl-1", "Amsterdam 1", "nl", "Netherlands", demoLink("nl1", "🇳🇱 Amsterdam 1"), listOf("Streaming")),
        StratosServer("nl-2", "Amsterdam 2", "nl", "Netherlands", demoLink("nl2", "🇳🇱 Amsterdam 2"), listOf("Half price")),
        StratosServer("tr-1", "Istanbul 1", "tr", "Türkiye", demoLink("tr1", "🇹🇷 Istanbul 1"), listOf("Economy")),
        StratosServer("fr-1", "Paris 1", "fr", "France", demoLink("fr1", "🇫🇷 Paris 1"), listOf("Gaming")),
        StratosServer("gb-1", "London 1", "gb", "United Kingdom", demoLink("gb1", "🇬🇧 London 1"), listOf("Streaming")),
        StratosServer("us-1", "New York 1", "us", "United States", demoLink("us1", "🇺🇸 New York 1"), listOf("Streaming", "Half price")),
    )

    /**
     * A syntactically valid VLESS share link pointing at a clearly-marked demo host.
     * It parses cleanly through the standard import pipeline; the host is not routable,
     * which is fine until the real panel delivers production servers.
     */
    private fun demoLink(hostCode: String, remark: String): String {
        val id = UUID.nameUUIDFromBytes("stratos-demo-$hostCode".toByteArray()).toString()
        val host = "$hostCode.servers.stratos-demo.internal"
        return "vless://$id@$host:443?encryption=none&security=tls&sni=$host&type=ws&path=%2Fstratos#${
            java.net.URLEncoder.encode(remark, "UTF-8")
        }"
    }
}
