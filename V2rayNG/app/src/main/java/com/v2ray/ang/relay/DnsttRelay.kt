package com.v2ray.ang.relay

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.fmt.DnsttFmt
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.util.LogUtil
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local relay around the official `dnstt-client` binary.
 *
 * Xray has no DNS-tunnel outbound, so the app ships dnstt-client (built in CI for
 * every supported ABI and packaged as `libdnsttclient.so` to satisfy Android's
 * W^X exec policy) and runs it against the profile's tunnel domain. The client
 * listens on loopback and forwards each accepted TCP connection through the DNS
 * tunnel to the dnstt server's forward target; the core chains to that endpoint
 * with a socks outbound (see CoreOutboundBuilder).
 *
 * Lifecycle mirrors [SshRelay]: [start] is invoked before the core loop begins and
 * [stop] after it ends or the profile changes.
 */
object DnsttRelay {

    private const val START_GRACE_MS = 1_500L

    private val lock = Any()
    private val running = AtomicBoolean(false)

    @Volatile
    private var process: Process? = null

    @Volatile
    private var activeKey: String? = null

    @Volatile
    private var appContext: Context? = null

    private var watcherThread: Thread? = null

    private fun endpointKey(config: ProfileItem): String =
        "${config.server}|${config.method}|${config.host}|${config.path}|${config.serverPort}|${config.publicKey}"

    /** True when the relay process is configured for the given profile. */
    fun isActiveFor(config: ProfileItem): Boolean = running.get() && activeKey == endpointKey(config)

    /**
     * Starts (or restarts) the dnstt-client process for this profile.
     * Returns false when the profile is invalid or the binary is not part of this build.
     */
    fun start(context: Context, config: ProfileItem): Boolean {
        val domain = config.server?.takeIf { it.isNotBlank() } ?: run {
            LogUtil.e(AppConfig.TAG, "DNSTT relay start refused: profile has no tunnel domain")
            return false
        }
        val pubkey = config.publicKey?.trim()?.takeIf { it.isNotEmpty() } ?: run {
            LogUtil.e(AppConfig.TAG, "DNSTT relay start refused: profile has no public key")
            return false
        }
        if (!pubkey.matches(Regex("[0-9a-fA-F]+")) || pubkey.length !in 32..128) {
            LogUtil.e(AppConfig.TAG, "DNSTT relay start refused: public key is not hexadecimal")
            return false
        }
        val key = endpointKey(config)

        synchronized(lock) {
            if (running.get() && activeKey == key && process?.exitValueOrNull() == null) {
                return true
            }
            stopLocked()

            val binary = File(context.applicationInfo.nativeLibraryDir, AppConfig.DNSTT_CLIENT_BINARY)
            if (!binary.isFile || !binary.canExecute()) {
                LogUtil.e(
                    AppConfig.TAG,
                    "DNSTT relay unavailable: ${AppConfig.DNSTT_CLIENT_BINARY} is not bundled in this build",
                )
                return false
            }

            val args = buildArgs(config, domain, pubkey)
            val builder = ProcessBuilder(listOf(binary.absolutePath) + args)
                .redirectErrorStream(true)
            val proc = try {
                builder.start()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "DNSTT relay failed to spawn client", e)
                return false
            }

            process = proc
            activeKey = key
            appContext = context.applicationContext
            running.set(true)

            // Drain child output so the pipe never blocks the process, and watch for
            // early death so the user is not left with a silently dead tunnel.
            watcherThread = Thread({
                try {
                    BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            line?.takeIf { it.isNotBlank() }?.let {
                                LogUtil.d(AppConfig.TAG, "dnstt: $it")
                            }
                        }
                    }
                } catch (_: Exception) {
                }
                onProcessDied(proc)
            }, "stratos-dnstt-io").apply {
                isDaemon = true
                start()
            }

            LogUtil.i(AppConfig.TAG, "DNSTT relay starting for $domain")
            return true
        }
    }

    /** Stops the dnstt-client process if the relay owns one. */
    fun stop() {
        synchronized(lock) { stopLocked() }
    }

    private fun stopLocked() {
        val proc = process
        process = null
        activeKey = null
        val wasRunning = running.getAndSet(false)
        if (wasRunning) appContext = null
        if (proc != null) {
            proc.destroy()
            try {
                if (!proc.waitFor(500, TimeUnit.MILLISECONDS)) {
                    proc.destroyForcibly()
                }
            } catch (_: Exception) {
            }
        }
        watcherThread = null
        if (wasRunning) {
            LogUtil.i(AppConfig.TAG, "DNSTT relay stopped")
        }
    }

    /**
     * Called from the io watcher: if the process dies while the relay is supposed to
     * be active, tear down and stop the core loop so the UI reports a failed
     * connection instead of a silent black hole. Deaths during intentional shutdown
     * (running=false) are ignored.
     */
    private fun onProcessDied(proc: Process) {
        synchronized(lock) {
            if (process !== proc || !running.get()) return
        }
        val code = proc.exitValueOrNull() ?: return
        LogUtil.e(AppConfig.TAG, "DNSTT client exited unexpectedly (code $code)")
        val ctx = appContext
        stop()
        failActiveConnection(ctx)
    }

    /** Orders a disconnect and surfaces a localized failure toast when the relay dies mid-session. */
    private fun failActiveConnection(ctx: Context?) {
        if (ctx == null) return
        MessageHelper.sendMsg2Service(ctx, com.v2ray.ang.AppConfig.MSG_STATE_STOP, "")
        MessageHelper.sendMsg2UI(
            ctx,
            com.v2ray.ang.AppConfig.MSG_STATE_START_FAILURE,
            ctx.getString(com.v2ray.ang.R.string.stratos_relay_failed),
        )
    }

    /**
     * Command line for the official client:
     *   dnstt-client (-udp host:port | -doh URL | -dot host:port) -pubkey HEX DOMAIN LOCALADDR:LOCALPORT
     */
    fun buildArgs(config: ProfileItem, domain: String, pubkey: String): List<String> {
        val transport = config.method?.lowercase() ?: DnsttFmt.TRANSPORT_UDP
        val dnsFlag = when (transport) {
            DnsttFmt.TRANSPORT_DOH -> {
                val url = config.path?.takeIf { it.isNotBlank() } ?: DnsttFmt.DEFAULT_RESOLVER_HOST
                listOf("-doh", url)
            }

            DnsttFmt.TRANSPORT_DOT -> {
                val target = config.path?.takeIf { it.isNotBlank() } ?: "${DnsttFmt.DEFAULT_RESOLVER_HOST}:853"
                listOf("-dot", target)
            }

            else -> {
                val resolver = config.host?.takeIf { it.isNotBlank() } ?: DnsttFmt.DEFAULT_RESOLVER_HOST
                val port = config.serverPort?.toIntOrNull()?.takeIf { it in 1..65535 } ?: 53
                listOf("-udp", "$resolver:$port")
            }
        }
        return dnsFlag + listOf("-pubkey", pubkey, domain, "${AppConfig.LOOPBACK}:${AppConfig.PORT_DNSTT_RELAY}")
    }

    private fun Process.exitValueOrNull(): Int? = try {
        exitValue()
    } catch (_: IllegalThreadStateException) {
        null
    }
}
