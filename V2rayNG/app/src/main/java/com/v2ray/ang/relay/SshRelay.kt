package com.v2ray.ang.relay

import com.jcraft.jsch.ChannelDirectTcpip
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.v2ray.ang.AppConfig
import android.content.Context
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.fmt.SshFmt
import com.v2ray.ang.util.LogUtil
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Local SOCKS5 relay backed by an SSH session.
 *
 * Xray has no SSH outbound, so the app keeps one SSH session to the profile's
 * server open and serves a loopback SOCKS5 endpoint; the core chains to it with a
 * socks outbound (see CoreOutboundBuilder). Every SOCKS CONNECT becomes a JSch
 * `direct-tcpip` channel over the same session.
 *
 * Lifecycle: [start] is called before the core loop begins (non-blocking — the
 * session connects in the background) and [stop] after it ends. The accept loop
 * gates early connections until the session is ready, so the core does not have
 * to race the SSH handshake.
 */
object SshRelay {

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val SESSION_READY_TIMEOUT_MS = 12_000
    private const val SERVER_ALIVE_INTERVAL_MS = 30_000
    private const val SESSION_ALIVE_COUNT_MAX = 3
    private const val COPY_BUFFER_SIZE = 16 * 1024
    private const val MAX_CONCURRENT_CONNECTIONS = 256

    private val lock = Any()
    private val running = AtomicBoolean(false)
    private val openSockets = ConcurrentHashMap.newKeySet<Socket>()

    @Volatile
    private var session: Session? = null

    @Volatile
    private var serverSocket: ServerSocket? = null

    private var acceptThread: Thread? = null
    private var sessionThread: Thread? = null
    private var executor = newExecutor()

    /** Identity of the profile the relay is currently serving (host|port|user). */
    @Volatile
    private var activeKey: String? = null

    @Volatile
    private var appContext: Context? = null

    private fun newExecutor() = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "stratos-ssh-relay").apply { isDaemon = true }
    }

    /** True when the SOCKS endpoint is configured for the given profile. */
    fun isActiveFor(config: ProfileItem): Boolean = activeKey == endpointKey(config)

    private fun endpointKey(config: ProfileItem): String =
        "${config.server}|${config.serverPort}|${config.username}|${config.method}"

    /**
     * Starts (or restarts) the relay for this profile. Returns immediately; the SSH
     * session connects on a background thread while the SOCKS listener is already
     * accepting and gating connections.
     */
    fun start(context: Context, config: ProfileItem): Boolean {
        appContext = context.applicationContext
        val host = config.server?.takeIf { it.isNotBlank() } ?: run {
            LogUtil.e(AppConfig.TAG, "SSH relay start refused: profile has no host")
            return false
        }
        val port = config.serverPort?.toIntOrNull()?.takeIf { it in 1..65535 } ?: 22
        val username = config.username?.takeIf { it.isNotBlank() } ?: run {
            LogUtil.e(AppConfig.TAG, "SSH relay start refused: profile has no username")
            return false
        }
        val useKeyAuth = config.method == SshFmt.AUTH_KEY
        val privateKey = config.path?.takeIf { it.isNotBlank() }
        if (useKeyAuth && privateKey == null) {
            LogUtil.e(AppConfig.TAG, "SSH relay start refused: key auth without a private key")
            return false
        }
        val password = config.password.orEmpty()
        val hostKeyPin = config.publicKey?.takeIf { it.isNotBlank() }
        val key = endpointKey(config)

        synchronized(lock) {
            if (running.get() && activeKey == key && session?.isConnected == true) {
                return true // already serving this exact endpoint
            }
            stopLocked()
            activeKey = key
            running.set(true)

            try {
                val socket = ServerSocket(AppConfig.PORT_SSH_RELAY, 50, InetAddress.getByName(AppConfig.LOOPBACK))
                serverSocket = socket
            } catch (e: Exception) {
                running.set(false)
                activeKey = null
                LogUtil.e(AppConfig.TAG, "SSH relay failed to open loopback listener", e)
                return false
            }

            sessionThread = Thread({
                connectSession(host, port, username, useKeyAuth, privateKey, password, hostKeyPin)
            }, "stratos-ssh-session").apply {
                isDaemon = true
                start()
            }
            acceptThread = Thread({ acceptLoop() }, "stratos-ssh-accept").apply {
                isDaemon = true
                start()
            }
            LogUtil.i(AppConfig.TAG, "SSH relay starting for $host:$port")
            return true
        }
    }

    /** Stops the relay: closes the listener, all client sockets and the SSH session. */
    fun stop() {
        synchronized(lock) { stopLocked() }
    }

    private fun stopLocked() {
        running.set(false)
        activeKey = null
        appContext = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        openSockets.forEach { s ->
            try {
                s.close()
            } catch (_: Exception) {
            }
        }
        openSockets.clear()
        session?.disconnect()
        session = null
        executor.shutdownNow()
        try {
            executor.awaitTermination(1, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        executor = newExecutor()
        acceptThread = null
        sessionThread = null
        LogUtil.i(AppConfig.TAG, "SSH relay stopped")
    }

    private fun connectSession(
        host: String,
        port: Int,
        username: String,
        useKeyAuth: Boolean,
        privateKey: String?,
        password: String,
        hostKeyPin: String?,
    ) {
        try {
            if (!running.get()) return
            val jsch = JSch()
            if (useKeyAuth && privateKey != null) {
                jsch.addIdentity(
                    "stratos-relay",
                    privateKey.toByteArray(Charsets.UTF_8),
                    null,
                    password.ifEmpty { null }?.toByteArray(Charsets.UTF_8),
                )
            }
            val ssh = jsch.getSession(username, host, port)
            ssh.setConfig("StrictHostKeyChecking", "no")
            ssh.setConfig("UserKnownHostsFile", "/dev/null")
            ssh.setConfig("PreferredAuthentications", if (useKeyAuth) "publickey,password" else "password,keyboard-interactive")
            ssh.setServerAliveInterval(SERVER_ALIVE_INTERVAL_MS)
            ssh.setServerAliveCountMax(SESSION_ALIVE_COUNT_MAX)
            ssh.setTcpKeepAlive(true)
            if (!useKeyAuth) {
                ssh.setPassword(password)
            }
            ssh.userInfo = object : com.jcraft.jsch.UserInfo {
                override fun getPassword(): String = password
                override fun getPassphrase(): String? = password.ifEmpty { null }
                override fun promptYesNo(message: String?): Boolean = true
                override fun promptPassword(message: String?): Boolean = true
                override fun promptPassphrase(message: String?): Boolean = true
                override fun showMessage(message: String?) {}
            }
            ssh.connect(CONNECT_TIMEOUT_MS)

            if (hostKeyPin != null && !hostKeyPinMatches(ssh, hostKeyPin)) {
                ssh.disconnect()
                LogUtil.e(AppConfig.TAG, "SSH host key pin mismatch for $host — rejecting session")
                onSessionFailed()
                return
            }
            if (!running.get()) {
                ssh.disconnect()
                return
            }
            session = ssh
            LogUtil.i(AppConfig.TAG, "SSH relay session established for $host:$port")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "SSH relay session failed for $host:$port", e)
            onSessionFailed()
        }
    }

    /**
     * The session handshake failed while the relay is supposed to be active: tear the
     * relay down and order a disconnect so the UI reports a failed connection instead
     * of a silently dead tunnel. Failures during intentional shutdown are ignored.
     */
    private fun onSessionFailed() {
        val ctx: Context?
        synchronized(lock) {
            if (!running.get()) return
            ctx = appContext
            stopLocked()
        }
        if (ctx == null) return
        MessageHelper.sendMsg2Service(ctx, com.v2ray.ang.AppConfig.MSG_STATE_STOP, "")
        MessageHelper.sendMsg2UI(
            ctx,
            com.v2ray.ang.AppConfig.MSG_STATE_START_FAILURE,
            ctx.getString(com.v2ray.ang.R.string.stratos_relay_failed),
        )
    }

    /**
     * Compares the configured pin against the server's host key. Accepts the OpenSSH
     * `SHA256:<base64>` format; a mismatch fails the relay start on purpose instead
     * of silently trusting a different server.
     */
    private fun hostKeyPinMatches(ssh: Session, expected: String): Boolean {
        return try {
            val hostKey = ssh.hostKey ?: return false
            val blob = Base64.getDecoder().decode(hostKey.key)
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(blob)
            val actual = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
            val normalized = expected.trim()
            normalized.equals(actual, ignoreCase = true) ||
                normalized.equals("SHA256:" + Base64.getEncoder().encodeToString(digest), ignoreCase = true)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "SSH host key pin check failed", e)
            false
        }
    }

    private fun acceptLoop() {
        val listener = serverSocket ?: return
        while (running.get()) {
            val client = try {
                listener.accept()
            } catch (_: Exception) {
                break
            }
            if (openSockets.size >= MAX_CONCURRENT_CONNECTIONS) {
                try {
                    client.close()
                } catch (_: Exception) {
                }
                continue
            }
            openSockets.add(client)
            executor.execute {
                try {
                    handleClient(client)
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "SSH relay connection handler failed", e)
                } finally {
                    openSockets.remove(client)
                    try {
                        client.close()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    /** Waits (bounded) for the background SSH handshake — used by early core connections. */
    private fun awaitSession(): Session? {
        val deadline = System.currentTimeMillis() + SESSION_READY_TIMEOUT_MS
        while (running.get() && System.currentTimeMillis() < deadline) {
            session?.takeIf { it.isConnected }?.let { return it }
            try {
                Thread.sleep(150)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
        }
        return session?.takeIf { it.isConnected }
    }

    private fun handleClient(client: Socket) {
        try {
            client.tcpNoDelay = true
            val input = DataInputStream(client.getInputStream())
            val output = client.getOutputStream()

            val head2 = readFullyOrNull(input, 2) ?: return
            val greetingLen = Socks5Protocol.greetingLength(head2)
            if (greetingLen < 2) {
                return
            }
            val greeting = if (greetingLen == 2) head2 else head2.copyOf(greetingLen).also { input.readFully(it, 2, greetingLen - 2) }
            val method = Socks5Protocol.chooseMethod(greeting)
            output.write(byteArrayOf(Socks5Protocol.VERSION, method))
            output.flush()
            if (method != Socks5Protocol.METHOD_NO_AUTH) return

            // Request header: 4 bytes, plus the length byte for domain targets.
            val header = readFullyOrNull(input, 4) ?: return
            var total = Socks5Protocol.requestLength(header)
            var full = header
            if (total < 0 && header[3] == Socks5Protocol.ATYP_DOMAIN) {
                val lenByte = input.read()
                if (lenByte < 0) return
                full = header + lenByte.toByte()
                total = Socks5Protocol.requestLength(full)
            }
            if (total < 0 || total < full.size) return
            if (full.size < total) {
                full = full + readFullyOrNull(input, total - full.size) ?: return
            }

            val target = Socks5Protocol.parseConnectRequest(full) ?: run {
                output.write(Socks5Protocol.buildReply(Socks5Protocol.REPLY_GENERAL_FAILURE))
                output.flush()
                return
            }

            val sshSession = awaitSession() ?: run {
                output.write(Socks5Protocol.buildReply(Socks5Protocol.REPLY_NETWORK_UNREACHABLE))
                output.flush()
                return
            }

            val channel = sshSession.openChannel("direct-tcpip") as ChannelDirectTcpip
            channel.setHost(target.host)
            channel.setPort(target.port)
            channel.setOriginatorIPAddress(client.inetAddress?.hostAddress ?: AppConfig.LOOPBACK)
            channel.setOriginatorPort(client.port)
            try {
                channel.connect(CONNECT_TIMEOUT_MS)
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "SSH relay channel to ${target.port} failed: ${e.javaClass.simpleName}")
                output.write(
                    Socks5Protocol.buildReply(
                        if (isRefused(e)) Socks5Protocol.REPLY_CONNECTION_REFUSED else Socks5Protocol.REPLY_HOST_UNREACHABLE,
                    ),
                )
                output.flush()
                return
            }

            output.write(Socks5Protocol.buildReply(Socks5Protocol.REPLY_SUCCESS))
            output.flush()

            pump(channel, client, input, output)
        } catch (e: EOFException) {
            // client hung up during the handshake — nothing to log
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "SSH relay client error", e)
        }
    }

    private fun isRefused(e: Exception): Boolean =
        e.message?.contains("Connection refused", ignoreCase = true) == true ||
            e.cause?.message?.contains("Connection refused", ignoreCase = true) == true

    /** Copies bytes in both directions until either side closes. */
    private fun pump(channel: ChannelDirectTcpip, client: Socket, input: InputStream, output: OutputStream) {
        val channelIn = channel.getInputStream()
        val channelOut = channel.getOutputStream()
        val up = Thread({
            try {
                channelOut.writeLoop(input, COPY_BUFFER_SIZE)
            } catch (_: Exception) {
            } finally {
                try {
                    channel.disconnect()
                } catch (_: Exception) {
                }
                try {
                    client.shutdownOutput()
                } catch (_: Exception) {
                }
            }
        }, "stratos-ssh-up")
        val down = Thread({
            try {
                output.writeLoop(channelIn, COPY_BUFFER_SIZE)
            } catch (_: Exception) {
            } finally {
                try {
                    client.close()
                } catch (_: Exception) {
                }
            }
        }, "stratos-ssh-down")
        up.isDaemon = true
        down.isDaemon = true
        up.start()
        down.start()
        // Wait for both directions so the accept pool does not spawn replacements early.
        up.join()
        down.join()
    }

    private fun OutputStream.writeLoop(source: InputStream, bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        while (true) {
            val read = source.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            write(buffer, 0, read)
            flush()
        }
    }

    private fun readFullyOrNull(input: InputStream, count: Int): ByteArray? {
        val buffer = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = input.read(buffer, read, count - read)
            if (n < 0) return null
            read += n
        }
        return buffer
    }
}
