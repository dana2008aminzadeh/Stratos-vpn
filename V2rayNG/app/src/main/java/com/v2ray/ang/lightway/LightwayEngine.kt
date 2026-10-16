package com.v2ray.ang.lightway

import android.app.Service
import android.net.VpnService
import android.os.Process
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.util.LogUtil
import lightway.ClientResult
import lightway.EventHandlers
import lightway.ExpresslaneState
import lightway.RustVpnConnection
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Second tunnel engine of the app: Lightway.
 *
 * Xray has no Lightway outbound, so a Lightway profile replaces the core entirely —
 * the VPN service establishes the TUN exactly as usual and this engine feeds that
 * file descriptor straight into the official Lightway library
 * (`RustVpnConnection.parallel_connect`). Routing is the platform default route the
 * VPN builder already installs, so the whole device tunnels through Lightway while
 * the profile is selected.
 *
 * Safety properties:
 *  - The engine only ever receives a BORROWED tun fd; the VPN service keeps ownership
 *    and closes it in its normal stop path, which also unwinds the engine (its only
 *    data path) and makes `parallel_connect` return.
 *  - The engine runs in-process in the VPN's own uid, which the builder already
 *    excludes from the tunnel (`addDisallowedApplication(self)`), so Lightway's
 *    outside sockets egress physically without any extra protect() plumbing.
 *  - Every terminal path (engine error, natural return, [stop]) emits the same
 *    stop-success broadcast the core loop emits, so the UI never stays "connected"
 *    over a dead session.
 *
 * Usage accounting: the core's byte counters do not exist for this engine, so the
 * speed loop samples cumulative per-uid counters ([TrafficStats.getUidTxBytes] /
 * [TrafficStats.getUidRxBytes]) and feeds them to [com.v2ray.ang.stratos.StratosTrafficEngine]
 * unchanged (they are cumulative, exactly what the engine expects).
 */
object LightwayEngine {

    /** Lightway connection states reported by the engine (see lightway mobile API). */
    private const val STATE_DISCONNECTED = 1
    private const val STATE_DISCONNECTING = 4
    private const val STATE_ONLINE = 7

    private val started = AtomicBoolean(false)

    @Volatile
    private var running = false

    @Volatile
    private var online = false

    @Volatile
    private var stopRequested = false

    @Volatile
    private var worker: Thread? = null

    @Volatile
    private var connection: RustVpnConnection? = null

    @Volatile
    private var service: Service? = null

    fun isRunning(): Boolean = running

    /**
     * Starts the Lightway session for [profile] on the borrowed TUN fd.
     * Returns false only when the engine refuses synchronously (already running,
     * invalid profile); asynchronous failures surface through the usual
     * `MSG_STATE_START_FAILURE` / stop broadcasts.
     */
    fun start(vpnService: VpnService, tunFd: Int, profile: ProfileItem): Boolean {
        val host = profile.server?.takeIf { it.isNotBlank() }
        if (host == null) {
            LogUtil.e(AppConfig.TAG, "Lightway start refused: profile has no server")
            return false
        }
        if (!started.compareAndSet(false, true)) {
            LogUtil.w(AppConfig.TAG, "Lightway engine already running")
            return true
        }
        running = true
        online = false
        stopRequested = false
        service = vpnService

        val yaml = LightwayConfigYaml.build(profile)
        val appService = vpnService

        worker = Thread({
            var failureMessage: String? = null
            try {
                NotificationManager.showNotification(profile)
                NotificationManager.startSpeedNotification(uidTrafficSource)

                val conn = RustVpnConnection(null)
                connection = conn
                val result = conn.parallelConnect(lightwayEventHandler(appService), tunFd, yaml)
                LogUtil.i(AppConfig.TAG, "Lightway session ended: $result")
                if (result == ClientResult.NETWORK_CHANGE) {
                    // The transport cannot survive this network change by itself; surface
                    // a clean disconnect so the user (or the auto-connect path) reconnects.
                    LogUtil.w(AppConfig.TAG, "Lightway ended after a network change")
                }
            } catch (e: Exception) {
                failureMessage = e.message
                LogUtil.e(AppConfig.TAG, "Lightway engine failed", e)
            } finally {
                val wasOnline = online
                connection = null
                running = false
                online = false
                started.set(false)
                worker = null
                NotificationManager.stopSpeedNotification()
                val ctx = service
                service = null
                if (ctx != null) {
                    when {
                        wasOnline || stopRequested ->
                            MessageHelper.sendMsg2UI(ctx, AppConfig.MSG_STATE_STOP_SUCCESS, "")

                        else -> {
                            val message = failureMessage
                                ?: ctx.getString(R.string.stratos_lightway_failed)
                            MessageHelper.sendMsg2UI(ctx, AppConfig.MSG_STATE_START_FAILURE, message)
                        }
                    }
                }
            }
        }, "stratos-lightway").apply {
            isDaemon = true
            start()
        }
        LogUtil.i(AppConfig.TAG, "Lightway engine starting for $host")
        return true
    }

    /**
     * Best-effort stop: asks the engine to disconnect. The authoritative teardown is
     * the service closing its TUN fd — the engine's reads/writes then fail and
     * `parallel_connect` returns on its own (see the class doc).
     */
    fun stop() {
        stopRequested = true
        val conn = connection
        try {
            conn?.stopConnection()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Lightway stopConnection failed", e)
        }
    }

    /**
     * Cumulative (since boot) uid counters for the speed/usage loop.
     *
     * The `TrafficStats` uid getters are not visible on the API 37 compile classpath,
     * so they are looked up reflectively: shipped devices still provide them, and a
     * platform that finally drops them degrades the readout to zeros instead of the
     * whole engine failing to compile or run.
     */
    private val uidTrafficSource: () -> Pair<Long, Long> = {
        readUidTraffic()
    }

    private fun readUidTraffic(): Pair<Long, Long> {
        return try {
            val cls = Class.forName("android.os.TrafficStats")
            val uid = Process.myUid()
            val intType = Integer.TYPE
            val tx = cls.getMethod("getUidTxBytes", intType).invoke(null, uid) as Long
            val rx = cls.getMethod("getUidRxBytes", intType).invoke(null, uid) as Long
            (if (tx >= 0) tx else 0L) to (if (rx >= 0) rx else 0L)
        } catch (e: Exception) {
            if (!trafficStatsUnavailableLogged) {
                trafficStatsUnavailableLogged = true
                LogUtil.w(AppConfig.TAG, "uid traffic counters unavailable: ${e.javaClass.simpleName}")
            }
            0L to 0L
        }
    }

    private var trafficStatsUnavailableLogged = false

    private fun lightwayEventHandler(ctx: Service): EventHandlers = object : EventHandlers {
        override fun handleStatusChange(state: UByte) {
            val value = state.toInt()
            LogUtil.d(AppConfig.TAG, "Lightway status: $value")
            if (value == STATE_ONLINE && !online) {
                online = true
                MessageHelper.sendMsg2UI(ctx, AppConfig.MSG_STATE_START_SUCCESS, "")
            } else if (value == STATE_DISCONNECTED && running) {
                // Engine-initiated disconnect (server side, keepalive timeout, …).
                LogUtil.w(AppConfig.TAG, "Lightway reported disconnected")
            }
        }

        override fun handleExpresslaneStateChange(state: ExpresslaneState) {
            LogUtil.d(AppConfig.TAG, "Lightway expresslane: $state")
        }

        override fun receivedFirstPacket(timeInMs: ULong) {
            LogUtil.i(AppConfig.TAG, "Lightway first packet after ${timeInMs}ms")
        }

        override fun createdOutsideFd(fd: Int) {
            // The VPN builder excludes this app's uid from the tunnel, so the socket
            // egresses physically as-is; the callback is kept for parity with the
            // reference integration and for future explicit protect() wiring.
            LogUtil.d(AppConfig.TAG, "Lightway outside fd created: $fd")
        }

        override fun connectionHasFloated() {
            LogUtil.d(AppConfig.TAG, "Lightway connection floated")
        }

        override fun handleInsidePktCodecStatusChange(enabled: Boolean) {
            LogUtil.d(AppConfig.TAG, "Lightway inside codec: $enabled")
        }
    }
}
