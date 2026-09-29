package com.v2ray.ang.stratos

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Local traffic accounting for Stratos VPN (runs in the daemon process).
 *
 * Some upstream nodes cannot measure usage server-side, so the app measures the
 * proxy traffic itself: [NotificationManager]-driven samples of cumulative Xray
 * counters are turned into per-interval deltas, accumulated locally against the
 * plan quota and periodically reported to the panel.
 *
 * When the quota (volume or time) is exhausted the engine immediately orders a
 * disconnect and blocks reconnection until the account is topped up again.
 */
object StratosTrafficEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var lastProxyUp: Long = -1
    private var lastProxyDown: Long = -1
    private var sessionUp: Long = 0
    private var sessionDown: Long = 0

    private var pendingReportUp: Long = 0
    private var pendingReportDown: Long = 0
    private var lastReportAt: Long = 0
    private var reporting: Boolean = false

    private var lastBroadcastAt: Long = 0

    private const val REPORT_MIN_BYTES = 4L * 1024 * 1024   // 4 MiB
    private const val REPORT_MIN_INTERVAL_MS = 30_000L
    private const val BROADCAST_MIN_INTERVAL_MS = 900L

    /** Reset per-connection counters. Call when the core (re)starts. */
    @Synchronized
    fun onCoreStarted(context: Context) {
        lastProxyUp = -1
        lastProxyDown = -1
        sessionUp = 0
        sessionDown = 0
        broadcast(context, 0, 0)
    }

    @Synchronized
    fun onCoreStopped(context: Context) {
        lastProxyUp = -1
        lastProxyDown = -1
        reportPending(context, force = true)
        broadcast(context, 0, 0)
    }

    /**
     * Feeds cumulative proxy counters (bytes since core start) into the engine.
     * Called from the daemon-side speed loop every few seconds.
     */
    @Synchronized
    fun onTrafficSample(context: Context, proxyUp: Long, proxyDown: Long, sinceLastQueryMs: Long) {
        if (!StratosSession.isLoggedIn) {
            lastProxyUp = proxyUp
            lastProxyDown = proxyDown
            return
        }

        val dUp = if (lastProxyUp >= 0) (proxyUp - lastProxyUp).coerceAtLeast(0) else 0L
        val dDown = if (lastProxyDown >= 0) (proxyDown - lastProxyDown).coerceAtLeast(0) else 0L
        lastProxyUp = proxyUp
        lastProxyDown = proxyDown

        val elapsed = sinceLastQueryMs.coerceAtLeast(1L)
        sessionUp += dUp
        sessionDown += dDown

        val delta = dUp + dDown
        if (delta > 0) {
            StratosSession.addLocalUsedBytes(delta)
            pendingReportUp += dUp
            pendingReportDown += dDown
        }

        val upSpeed = (dUp * 1000L / elapsed)
        val downSpeed = (dDown * 1000L / elapsed)
        broadcast(context, upSpeed, downSpeed)

        // Hard quota gate: expired time or exhausted volume => immediate disconnect.
        val user = StratosSession.effectiveUser()
        if (user != null && !user.canConnect) {
            val reason = when {
                !user.hasTimeLeft -> StratosSession.BLOCKED_EXPIRED
                !user.hasDataLeft -> StratosSession.BLOCKED_NO_DATA
                user.status == StratosUserStatus.DISABLED -> StratosSession.BLOCKED_DISABLED
                else -> StratosSession.BLOCKED_EXPIRED
            }
            LogUtil.w(AppConfig.TAG, "Stratos quota exhausted ($reason) — stopping service")
            StratosSession.forceBlock(reason)
            reportPending(context, force = true)
            MessageHelper.sendMsg2Service(context, AppConfig.MSG_STATE_STOP, "")
            MessageHelper.sendMsg2UI(context, AppConfig.MSG_STRATOS_BLOCKED, reason)
            return
        }

        reportPending(context, force = false)
    }

    /**
     * Reports locally measured consumption to the panel, throttled.
     * On success the reported amount is subtracted from the local counter,
     * because the panel snapshot now includes it.
     */
    private fun reportPending(context: Context, force: Boolean) {
        val total = pendingReportUp + pendingReportDown
        val now = System.currentTimeMillis()
        if (total <= 0) return
        if (!force && total < REPORT_MIN_BYTES && now - lastReportAt < REPORT_MIN_INTERVAL_MS) return
        if (reporting) return
        val token = StratosSession.token() ?: return

        val up = pendingReportUp
        val down = pendingReportDown
        pendingReportUp = 0
        pendingReportDown = 0
        lastReportAt = now
        reporting = true

        scope.launch {
            try {
                val user = StratosApiProvider.get().reportUsage(token, up, down)
                if (user != null) {
                    // Remove exactly the reported amount from the local tally.
                    StratosSession.setLocalUsedBytes(
                        (StratosSession.localUsedBytes() - (up + down)).coerceAtLeast(0L),
                    )
                    StratosSession.saveUser(user)
                } else {
                    // Panel unreachable: locally measured traffic stays counted.
                    pendingReportUp += up
                    pendingReportDown += down
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Stratos usage report failed", e)
                pendingReportUp += up
                pendingReportDown += down
            } finally {
                reporting = false
            }
        }
    }

    private fun broadcast(context: Context, upSpeed: Long, downSpeed: Long) {
        val now = System.currentTimeMillis()
        if (now - lastBroadcastAt < BROADCAST_MIN_INTERVAL_MS) return
        lastBroadcastAt = now
        MessageHelper.sendMsg2UI(
            context,
            AppConfig.MSG_STRATOS_TRAFFIC,
            StratosTrafficUpdate(
                upSpeedBytesPerSec = upSpeed,
                downSpeedBytesPerSec = downSpeed,
                sessionUpBytes = sessionUp,
                sessionDownBytes = sessionDown,
                accountUsedBytes = StratosSession.effectiveUsedBytes(),
            ),
        )
    }
}
