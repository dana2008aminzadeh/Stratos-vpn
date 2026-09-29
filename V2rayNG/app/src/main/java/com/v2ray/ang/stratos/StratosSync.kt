package com.v2ray.ang.stratos

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the app in sync with the operator panel while the app is alive:
 * - account snapshot every 2 minutes (data, expiry, status),
 * - device heartbeat (session moved elsewhere => local sign-out),
 * - server fleet + operator config refresh every 10 minutes,
 * - enforcement: expired / out-of-data accounts are disconnected immediately.
 */
object StratosSync {

    private const val USER_SYNC_INTERVAL_MS = 2 * 60 * 1000L
    private const val FLEET_SYNC_EVERY = 5 // every 5th user sync => ~10 min

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var syncCounter = 0

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncAt = MutableStateFlow(0L)
    val lastSyncAt: StateFlow<Long> = _lastSyncAt.asStateFlow()

    @Synchronized
    fun start(context: Context) {
        if (job?.isActive == true) return
        val appContext = context.applicationContext
        job = scope.launch {
            while (isActive) {
                if (StratosSession.isLoggedIn) {
                    runCatching { syncNow(appContext) }
                        .onFailure { LogUtil.e(AppConfig.TAG, "Stratos sync failed", it) }
                }
                delay(USER_SYNC_INTERVAL_MS)
            }
        }
        LogUtil.i(AppConfig.TAG, "Stratos sync started")
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * Runs one sync round. Returns true when the account snapshot refreshed.
     */
    suspend fun syncNow(context: Context, refreshServers: Boolean = false): Boolean {
        if (!StratosSession.isLoggedIn) return false
        _isSyncing.value = true
        try {
            val api = StratosApiProvider.get()
            val token = StratosSession.token() ?: return false

            // 1) device binding heartbeat
            val bound = runCatching { api.heartbeat(token, StratosSession.deviceId(context)) }
                .getOrDefault(true)
            if (!bound) {
                kick(context)
                return false
            }

            // 2) account snapshot
            val user = runCatching { api.fetchUser(token) }.getOrNull()
            if (user == null) {
                return false // transient network error; keep current state
            }
            // Never drop locally measured, not-yet-reported traffic.
            StratosSession.saveUser(user, resetLocalUsage = false)

            // 3) expired / out of data => disconnect fast, keep ping & update allowed
            if (!user.canConnect) {
                StratosSession.reevaluateBlock()
                if (StratosSession.blockedReason() != StratosSession.BLOCKED_NONE) {
                    LogUtil.w(AppConfig.TAG, "Stratos account inactive — stopping service")
                    LauncherManager.stopService(context)
                    MessageHelper.sendMsg2UI(
                        context, AppConfig.MSG_STRATOS_BLOCKED, StratosSession.blockedReason(),
                    )
                }
            }

            // 4) operator config (+ forced settings)
            val admin = runCatching { api.fetchAdminConfig(token) }.getOrNull()
            if (admin != null) {
                StratosSession.saveAdmin(admin)
                StratosSettingsController.applyForcedSettings()
            }

            // 5) server fleet (periodic or on demand)
            syncCounter++
            if (refreshServers || syncCounter >= FLEET_SYNC_EVERY || StratosSession.serversState.value.isEmpty()) {
                syncCounter = 0
                val servers = runCatching { api.fetchServers(token) }.getOrNull()
                if (servers != null && servers.isNotEmpty()) {
                    StratosSession.saveServers(servers)
                    StratosServersStore.sync(servers)
                }
            }

            _lastSyncAt.value = System.currentTimeMillis()
            return true
        } finally {
            _isSyncing.value = false
        }
    }

    /** The account moved to another device: disconnect + forget the session. */
    private fun kick(context: Context) {
        LogUtil.w(AppConfig.TAG, "Stratos session moved to another device — signing out")
        StratosSession.forceBlock(StratosSession.BLOCKED_KICKED)
        LauncherManager.stopService(context)
        MessageHelper.sendMsg2UI(context, AppConfig.MSG_STRATOS_BLOCKED, StratosSession.BLOCKED_KICKED)
        val token = StratosSession.token()
        StratosSession.logout()
        if (!token.isNullOrBlank()) {
            scope.launch { runCatching { StratosApiProvider.get().logout(token) } }
        }
    }
}
