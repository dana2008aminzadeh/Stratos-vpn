package com.v2ray.ang.stratos.engine

import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64

/**
 * Built-in SSH tunnel engine: a local SOCKS5 front-end whose CONNECT requests
 * are carried over SSH `direct-tcpip` channels.
 *
 * TCP only (SSH has no datagram transport): UDP applications will not pass
 * through this engine; DNS keeps working when the profile's remote DNS uses
 * TCP/DoH, which the config generator arranges.
 *
 * The engine owns its coroutine scope and everything it opens; [stop] is
 * idempotent and releases all of it.
 */
class StratosSshEngine(
    private val profile: ProfileItem,
    private val localPort: Int,
    private val socketProtector: ((Socket) -> Boolean)? = null,
) {
    private val lock = Any()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)

    private var serverSocket: ServerSocket? = null
    private var session: Session? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    /**
     * Connects the SSH session and starts the SOCKS5 front-end.
     *
     * @return null on success, or a short English reason describing the failure.
     */
    fun start(): String? {
        synchronized(lock) {
            if (isRunning) return null
            val host = profile.server.orEmpty()
            if (host.isBlank()) return "SSH server address is empty"
            val port = profile.serverPort?.toIntOrNull() ?: 0
            if (port !in 1..65535) return "SSH server port is invalid"

            try {
                val jsch = JSch()
                val method = profile.method.orEmpty()
                if (method == SshFmtAuth.KEY) {
                    val pem = profile.secretKey.orEmpty()
                    if (pem.isBlank()) return "SSH private key is empty"
                    val passphrase = profile.password?.takeIf { it.isNotEmpty() }?.toByteArray()
                    jsch.addIdentity("stratos", pem.toByteArray(), null, passphrase)
                }

                val newSession = jsch.getSession(
                    profile.username.orEmpty().ifBlank { "root" },
                    host,
                    port,
                )
                if (method != SshFmtAuth.KEY) {
                    newSession.setPassword(profile.password.orEmpty())
                }
                newSession.setSocketFactory(ProtectingSocketFactory())

                val fingerprint = profile.publicKey.orEmpty().trim()
                val insecure = profile.insecure == "1"
                if (insecure || fingerprint.isBlank()) {
                    newSession.setConfig("StrictHostKeyChecking", "no")
                } else {
                    newSession.setHostKeyRepository(FingerprintRepository(fingerprint))
                    newSession.setConfig("StrictHostKeyChecking", "yes")
                }
                newSession.setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive")
                newSession.setServerAliveInterval(30_000)
                newSession.setServerAliveCountMax(4)
                newSession.connect(30_000)

                val server = ServerSocket()
                server.reuseAddress = true
                server.bind(InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), localPort), 16)

                session = newSession
                serverSocket = server
                isRunning = true

                scope.launch { acceptLoop(server, newSession) }
                LogUtil.i(TAG, "SSH engine started on 127.0.0.1:$localPort")
                return null
            } catch (e: JSchException) {
                LogUtil.e(TAG, "SSH engine failed to start", e)
                stopInternal()
                return e.message?.takeIf { it.isNotBlank() } ?: "SSH handshake failed"
            } catch (e: Exception) {
                LogUtil.e(TAG, "SSH engine failed to start", e)
                stopInternal()
                return e.message?.takeIf { it.isNotBlank() } ?: "SSH engine failed to start"
            }
        }
    }

    /** Stops the engine and releases every socket, channel and coroutine. */
    fun stop() {
        synchronized(lock) {
            stopInternal()
        }
    }

    private fun stopInternal() {
        isRunning = false
        scope.cancel()
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        try {
            session?.disconnect()
        } catch (_: Exception) {
        }
        session = null
    }

    private fun acceptLoop(server: ServerSocket, session: Session) {
        while (isRunning && !server.isClosed) {
            val client = try {
                server.accept()
            } catch (_: Exception) {
                break
            }
            scope.launch {
                try {
                    handleClient(client, session)
                } catch (e: Exception) {
                    LogUtil.d(TAG, "SOCKS client error: ${e.message}")
                } finally {
                    try {
                        client.close()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private suspend fun handleClient(client: Socket, session: Session) {
        client.tcpNoDelay = true
        val input = client.getInputStream()
        val output = client.getOutputStream()

        // greeting: VER NMETHODS METHODS...
        val greeting = ByteArray(32)
        val gLen = readMessage(input, greeting) ?: return
        if (!Socks5Protocol.greetingOffersNoAuth(greeting, gLen)) {
            output.write(Socks5Protocol.buildReply(Socks5Protocol.AUTH_UNACCEPTABLE))
            output.flush()
            return
        }
        output.write(Socks5Protocol.buildGreetingReply())
        output.flush()

        // request: VER RSV CMD ATYP ADDR PORT
        val requestBuffer = ByteArray(520)
        val rLen = readMessage(input, requestBuffer) ?: return
        val request = Socks5Protocol.parseRequest(requestBuffer, rLen)
        if (request == null) {
            output.write(Socks5Protocol.buildReply(Socks5Protocol.REP_ADDRESS_TYPE_NOT_SUPPORTED))
            output.flush()
            return
        }
        if (request.command != Socks5Protocol.CMD_CONNECT) {
            output.write(Socks5Protocol.buildReply(Socks5Protocol.REP_COMMAND_NOT_SUPPORTED))
            output.flush()
            return
        }

        val channel = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
        channel.setHost(request.host)
        channel.setPort(request.port)
        channel.setOrgIPAddress(client.inetAddress?.hostAddress.orEmpty())
        channel.setOrgPort(client.port)
        try {
            channel.connect(30_000)
        } catch (e: Exception) {
            LogUtil.d(TAG, "direct-tcpip connect failed for ${request.host}:${request.port}: ${e.message}")
            output.write(Socks5Protocol.buildReply(Socks5Protocol.REP_GENERAL_FAILURE))
            output.flush()
            return
        }

        output.write(Socks5Protocol.buildReply(Socks5Protocol.REP_SUCCESS))
        output.flush()

        val remoteIn = channel.getInputStream()
        val remoteOut = channel.getOutputStream()
        val up = scope.launch { pump(remoteIn, output, closeOnEnd = true) }
        val down = scope.launch { pump(input, remoteOut, closeOnEnd = true) }
        // hold the handler until either pump finishes (side closed) or we stop
        up.join()
        down.join()
        try {
            channel.disconnect()
        } catch (_: Exception) {
        }
    }

    private fun pump(from: InputStream, to: OutputStream, closeOnEnd: Boolean) {
        val buffer = ByteArray(32 * 1024)
        try {
            while (isRunning) {
                val n = from.read(buffer)
                if (n < 0) break
                if (n == 0) continue
                to.write(buffer, 0, n)
                to.flush()
            }
        } catch (_: Exception) {
        } finally {
            if (closeOnEnd) {
                try {
                    to.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Reads one length-prefixed SOCKS message (up to 255+2 bytes for domains).
     * Returns the byte count, or null on EOF/overflow.
     */
    private fun readMessage(input: InputStream, buffer: ByteArray): Int? {
        var total = 0
        while (total < buffer.size) {
            val n = input.read(buffer, total, buffer.size - total)
            if (n < 0) return null
            total += n
            // heuristics: greeting done when we've seen all advertised methods,
            // request done when we've seen the full address + port.
            if (total >= 2) {
                val second = buffer[1].toInt() and 0xFF
                if (buffer.size <= 32) {
                    // greeting buffer
                    if (total >= 2 + second) return total
                } else {
                    // request buffer
                    val atyp = if (total >= 4) buffer[3].toInt() and 0xFF else -1
                    val need = when (atyp) {
                        Socks5Protocol.ATYP_IPV4 -> 10
                        Socks5Protocol.ATYP_DOMAIN -> if (total >= 5) 7 + (buffer[4].toInt() and 0xFF) else Int.MAX_VALUE
                        Socks5Protocol.ATYP_IPV6 -> 22
                        else -> return total
                    }
                    if (total >= need) return total
                }
            }
        }
        return total
    }

    /** Protects the engine's upstream socket from routing back into the VPN. */
    private inner class ProtectingSocketFactory : SocketFactory {
        override fun createSocket(host: String, port: Int): Socket {
            val socket = Socket()
            socketProtector?.invoke(socket)
            socket.connect(InetSocketAddress(host, port), 30_000)
            socket.tcpNoDelay = true
            return socket
        }

        override fun createSocket(s: Socket, host: String, port: Int, timeout: Int): Socket {
            socketProtector?.invoke(s)
            s.connect(InetSocketAddress(host, port), timeout)
            return s
        }

        override fun getInputStream(s: Socket): InputStream = s.getInputStream()
        override fun getOutputStream(s: Socket): OutputStream = s.getOutputStream()
    }

    /** Accepts the server host key only when its SHA-256 fingerprint matches. */
    private inner class FingerprintRepository(
        private val expectedFingerprint: String,
    ) : HostKeyRepository {
        override fun check(host: String, key: ByteArray): Int {
            val actual = sha256Fingerprint(key)
            val expected = expectedFingerprint
                .removePrefix("SHA256:")
                .removePrefix("sha256:")
                .trim()
            return if (actual == expected) {
                HostKeyRepository.OK
            } else {
                LogUtil.e(TAG, "SSH host key mismatch for $host: expected SHA256:$expected, got SHA256:$actual")
                HostKeyRepository.CHANGED
            }
        }

        override fun add(hostkey: HostKey, userInfo: com.jcraft.jsch.UserInfo?) {}
        override fun remove(host: String, keyType: String) {}
        override fun remove(host: String, keyType: String, key: ByteArray) {}
        override fun getKnownHostsRepositoryID(): String = "stratos-fingerprint"
        override fun getHostKey(): Array<HostKey> = emptyArray()
        override fun getHostKey(host: String, keyType: String): Array<HostKey> = emptyArray()
    }

    companion object {
        private const val TAG = "StratosSshEngine"

        /** OpenSSH-style base64url SHA-256 fingerprint of a raw host key blob. */
        fun sha256Fingerprint(key: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(key)
            return Base64.getEncoder().withoutPadding().encodeToString(digest)
        }
    }
}

/** Auth method values stored in [ProfileItem.method] by SshFmt. */
object SshFmtAuth {
    const val PASSWORD = "password"
    const val KEY = "key"
}
