package com.v2ray.ang.stratos

import android.content.Context
import android.provider.Settings
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Stratos VPN session store: credentials, account snapshot, device binding,
 * blocked state and the operator links. Backed by MMKV, exposed as flows.
 */
object StratosSession {

    private const val KEY_TOKEN = "stratos_session_token"
    private const val KEY_USERNAME = "stratos_session_username"
    private const val KEY_USER = "stratos_session_user"
    private const val KEY_ADMIN = "stratos_session_admin"
    private const val KEY_SERVERS = "stratos_session_servers"
    private const val KEY_GUID_MAP = "stratos_session_guid_map"
    private const val KEY_DEVICE_ID = "stratos_device_id"
    private const val KEY_BLOCKED = "stratos_blocked_reason"
    private const val KEY_LOCAL_USED = "stratos_local_used_bytes"
    private const val KEY_API_BASE = "stratos_api_base"
    private const val KEY_FIRST_RUN_DONE = "stratos_first_run_done"

    /** Why connecting is currently blocked. Empty means not blocked by account state. */
    const val BLOCKED_NONE = ""
    const val BLOCKED_EXPIRED = "expired"       // time over
    const val BLOCKED_NO_DATA = "no_data"       // volume exhausted
    const val BLOCKED_DISABLED = "disabled"     // account deactivated by operator
    const val BLOCKED_KICKED = "kicked"         // session moved to another device

    private val _userState = MutableStateFlow<StratosUser?>(null)
    val userState: StateFlow<StratosUser?> = _userState.asStateFlow()

    private val _adminState = MutableStateFlow(StratosAdminConfig())
    val adminState: StateFlow<StratosAdminConfig> = _adminState.asStateFlow()

    private val _serversState = MutableStateFlow<List<StratosServer>>(emptyList())
    val serversState: StateFlow<List<StratosServer>> = _serversState.asStateFlow()

    private val _blockedState = MutableStateFlow(BLOCKED_NONE)
    val blockedState: StateFlow<String> = _blockedState.asStateFlow()

    private val _localUsedState = MutableStateFlow(0L)
    val localUsedState: StateFlow<Long> = _localUsedState.asStateFlow()

    val isLoggedIn: Boolean get() = !token().isNullOrBlank()

    fun token(): String? = MmkvManager.decodeSettingsString(KEY_TOKEN)?.takeIf { it.isNotBlank() }

    fun username(): String = MmkvManager.decodeSettingsString(KEY_USERNAME).orEmpty()

    /** Stable anonymous device identifier used for the 1-device binding. */
    fun deviceId(context: Context): String {
        MmkvManager.decodeSettingsString(KEY_DEVICE_ID)?.takeIf { it.isNotBlank() }?.let { return it }
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()
        val id = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            "and-" + androidId
        } else {
            "rnd-" + UUID.randomUUID().toString()
        }
        MmkvManager.encodeSettings(KEY_DEVICE_ID, id)
        return id
    }

    /** Device-independent salt for demo token derivation. */
    fun deviceIdSalt(): String =
        MmkvManager.decodeSettingsString(KEY_DEVICE_ID) ?: "stratos"

    /** Panel base URL; blank means offline demo mode. */
    fun apiBaseUrl(): String = MmkvManager.decodeSettingsString(KEY_API_BASE).orEmpty()

    fun setApiBaseUrl(url: String) {
        MmkvManager.encodeSettings(KEY_API_BASE, url)
        StratosApiProvider.reset()
    }

    @Synchronized
    fun restoreFromStorage() {
        _userState.value = MmkvManager.decodeSettingsString(KEY_USER)
            ?.let { JsonUtil.fromJsonSafe(it, StratosUser::class.java) }
        _adminState.value = MmkvManager.decodeSettingsString(KEY_ADMIN)
            ?.let { JsonUtil.fromJsonSafe(it, StratosAdminConfig::class.java) }
            ?: StratosAdminConfig()
        _serversState.value = MmkvManager.decodeSettingsString(KEY_SERVERS)
            ?.let { JsonUtil.fromJsonSafe(it, Array<StratosServer>::class.java) }?.toList()
            .orEmpty()
        _blockedState.value = MmkvManager.decodeSettingsString(KEY_BLOCKED).orEmpty()
        _localUsedState.value = MmkvManager.decodeSettingsLong(KEY_LOCAL_USED, 0L)
    }

    @Synchronized
    fun onLoginSuccess(token: String, username: String, user: StratosUser, admin: StratosAdminConfig) {
        MmkvManager.encodeSettings(KEY_TOKEN, token)
        MmkvManager.encodeSettings(KEY_USERNAME, username)
        StratosDemoApi.register(username)
        _blockedState.value = evaluateBlock(user)
        persistBlocked()
        saveUser(user, resetLocalUsage = true)
        saveAdmin(admin)
        LogUtil.i(AppConfig.TAG, "Stratos session started for $username")
    }

    @Synchronized
    fun saveUser(user: StratosUser, resetLocalUsage: Boolean = false) {
        _userState.value = user
        MmkvManager.encodeSettings(KEY_USER, JsonUtil.toJson(user))
        if (resetLocalUsage) setLocalUsedBytes(0L)
        reevaluateBlock()
    }

    @Synchronized
    fun saveAdmin(admin: StratosAdminConfig) {
        _adminState.value = admin
        MmkvManager.encodeSettings(KEY_ADMIN, JsonUtil.toJson(admin))
    }

    @Synchronized
    fun saveServers(servers: List<StratosServer>) {
        _serversState.value = servers
        MmkvManager.encodeSettings(KEY_SERVERS, JsonUtil.toJson(servers.toTypedArray()))
    }

    /** Stratos server id -> imported profile GUID in the v2rayNG profile store. */
    fun guidMap(): Map<String, String> =
        MmkvManager.decodeSettingsString(KEY_GUID_MAP)
            ?.let { JsonUtil.fromJsonSafe(it, Map::class.java) }
            ?.mapNotNull { (k, v) -> (k as? String)?.let { kk -> (v as? String)?.let { vv -> kk to vv } } }
            ?.toMap()
            .orEmpty()

    fun saveGuidMap(map: Map<String, String>) {
        MmkvManager.encodeSettings(KEY_GUID_MAP, JsonUtil.toJson(map))
    }

    /** Locally measured usage (bytes) added on top of the server-reported figure. */
    fun localUsedBytes(): Long = _localUsedState.value

    @Synchronized
    fun setLocalUsedBytes(value: Long) {
        val v = value.coerceAtLeast(0L)
        _localUsedState.value = v
        MmkvManager.encodeSettings(KEY_LOCAL_USED, v)
        reevaluateBlock()
    }

    @Synchronized
    fun addLocalUsedBytes(delta: Long) {
        if (delta > 0) setLocalUsedBytes(localUsedBytes() + delta)
    }

    /** Effective usage combining server snapshot and locally measured traffic. */
    fun effectiveUsedBytes(): Long {
        val user = _userState.value ?: return localUsedBytes()
        return user.usedBytes + localUsedBytes()
    }

    fun effectiveUser(): StratosUser? {
        val user = _userState.value ?: return null
        return user.copy(usedBytes = (user.usedBytes + localUsedBytes()).coerceAtMost(Long.MAX_VALUE))
    }

    fun blockedReason(): String = _blockedState.value

    fun isConnectAllowed(): Boolean = blockedReason() == BLOCKED_NONE && (effectiveUser()?.canConnect ?: false)

    private fun evaluateBlock(user: StratosUser): String = when {
        user.status == StratosUserStatus.DISABLED -> BLOCKED_DISABLED
        !user.hasTimeLeft -> BLOCKED_EXPIRED
        user.status == StratosUserStatus.EXPIRED -> BLOCKED_EXPIRED
        !user.hasDataLeft -> BLOCKED_NO_DATA
        else -> BLOCKED_NONE
    }

    @Synchronized
    fun reevaluateBlock() {
        val user = effectiveUser() ?: return
        val reason = evaluateBlock(user)
        if (reason != _blockedState.value && _blockedState.value != BLOCKED_KICKED) {
            _blockedState.value = reason
            persistBlocked()
        }
    }

    @Synchronized
    fun forceBlock(reason: String) {
        _blockedState.value = reason
        persistBlocked()
    }

    private fun persistBlocked() {
        MmkvManager.encodeSettings(KEY_BLOCKED, _blockedState.value)
    }

    /** Full sign-out: clears session, servers and (externally) stops the VPN. */
    @Synchronized
    fun logout() {
        val currentToken = token()
        MmkvManager.encodeSettings(KEY_TOKEN, "")
        MmkvManager.encodeSettings(KEY_USERNAME, "")
        MmkvManager.encodeSettings(KEY_USER, "")
        MmkvManager.encodeSettings(KEY_SERVERS, "")
        MmkvManager.encodeSettings(KEY_GUID_MAP, "")
        MmkvManager.encodeSettings(KEY_BLOCKED, "")
        setLocalUsedBytes(0L)
        _userState.value = null
        _serversState.value = emptyList()
        _blockedState.value = BLOCKED_NONE
        _localUsedState.value = 0L
        if (!currentToken.isNullOrBlank()) {
            // caller performs the API logout asynchronously
        }
    }

    fun markFirstRunDone(): Boolean {
        val done = MmkvManager.decodeSettingsBool(KEY_FIRST_RUN_DONE)
        if (!done) MmkvManager.encodeSettings(KEY_FIRST_RUN_DONE, true)
        return !done
    }
}
