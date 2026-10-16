package com.v2ray.ang.relay

/**
 * Pure parsing helpers for the subset of SOCKS5 the local relays speak.
 *
 * Kept free of Android and socket dependencies so every branch is JVM-testable.
 * Wire layout (RFC 1928):
 *
 *  greeting:  VER(1) NMETHODS(1) METHODS(n)
 *  request:   VER(1) CMD(1) RSV(1) ATYP(1) ADDR(1..256) PORT(2)
 */
object Socks5Protocol {

    const val VERSION: Byte = 0x05
    const val METHOD_NO_AUTH: Byte = 0x00
    const val METHOD_UNACCEPTABLE: Byte = 0xFF.toByte()
    const val CMD_CONNECT: Byte = 0x01
    const val ATYP_IPV4: Byte = 0x01
    const val ATYP_DOMAIN: Byte = 0x03
    const val ATYP_IPV6: Byte = 0x04

    const val REPLY_SUCCESS: Byte = 0x00
    const val REPLY_GENERAL_FAILURE: Byte = 0x01
    const val REPLY_NOT_ALLOWED: Byte = 0x02
    const val REPLY_NETWORK_UNREACHABLE: Byte = 0x03
    const val REPLY_HOST_UNREACHABLE: Byte = 0x04
    const val REPLY_CONNECTION_REFUSED: Byte = 0x05

    /** CONNECT target parsed from a request. */
    data class ConnectTarget(val host: String, val port: Int)

    /**
     * Chooses the authentication method from a client greeting.
     * Returns [METHOD_NO_AUTH] when the client offers no-auth, otherwise
     * [METHOD_UNACCEPTABLE] so the caller closes the connection.
     */
    fun chooseMethod(greeting: ByteArray): Byte {
        if (greeting.size < 2 || greeting[0] != VERSION) return METHOD_UNACCEPTABLE
        val nMethods = greeting[1].toInt() and 0xFF
        if (greeting.size < 2 + nMethods) return METHOD_UNACCEPTABLE
        val methods = greeting.copyOfRange(2, 2 + nMethods)
        return if (METHOD_NO_AUTH in methods) METHOD_NO_AUTH else METHOD_UNACCEPTABLE
    }

    /**
     * Parses a CONNECT request. Returns null when the message is malformed, uses a
     * command other than CONNECT, or carries an unsupported address type.
     */
    fun parseConnectRequest(request: ByteArray): ConnectTarget? {
        if (request.size < 5) return null
        if (request[0] != VERSION) return null
        if (request[1] != CMD_CONNECT) return null
        // request[2] must be 0x00
        if (request[2] != 0x00.toByte()) return null

        val atyp = request[3]
        val host: String
        var offset: Int
        when (atyp) {
            ATYP_IPV4 -> {
                if (request.size < 10) return null
                host = (4..7).joinToString(".") { (request[it].toInt() and 0xFF).toString() }
                offset = 8
            }

            ATYP_IPV6 -> {
                if (request.size < 22) return null
                val groups = (0 until 8).joinToString(":") { i ->
                    val hi = request[4 + i * 2].toInt() and 0xFF
                    val lo = request[5 + i * 2].toInt() and 0xFF
                    ((hi shl 8) or lo).toString(16)
                }
                host = groups
                offset = 20
            }

            ATYP_DOMAIN -> {
                val len = request[4].toInt() and 0xFF
                if (len == 0) return null
                if (request.size < 7 + len) return null
                host = String(request, 5, len, Charsets.ISO_8859_1)
                offset = 5 + len
            }

            else -> return null
        }

        if (request.size < offset + 2) return null
        val port = ((request[offset].toInt() and 0xFF) shl 8) or (request[offset + 1].toInt() and 0xFF)
        if (port == 0) return null
        return ConnectTarget(host, port)
    }

    /** Builds a SOCKS5 reply message. [bindAddress]/[bindPort] may be unknown (0.0.0.0:0). */
    fun buildReply(replyCode: Byte, bindAddress: String = "0.0.0.0", bindPort: Int = 0): ByteArray {
        val ipv4 = bindAddress.split(".").mapNotNull { it.toIntOrNull() }
        val addr = if (ipv4.size == 4) ipv4.map { it.toByte() }.toByteArray() else ByteArray(4)
        return byteArrayOf(
            VERSION,
            replyCode,
            0x00,
            ATYP_IPV4,
        ) + addr + byteArrayOf(
            ((bindPort shr 8) and 0xFF).toByte(),
            (bindPort and 0xFF).toByte(),
        )
    }

    /** Exact byte count of one message for the given stage, used to read from the stream. */
    fun greetingLength(firstTwoBytes: ByteArray): Int {
        if (firstTwoBytes.size < 2 || firstTwoBytes[0] != VERSION) return -1
        return 2 + (firstTwoBytes[1].toInt() and 0xFF)
    }

    /** Total length of a request given its first header bytes, or -1 when invalid/unknown. */
    fun requestLength(header: ByteArray): Int {
        if (header.size < 4) return -1
        if (header[0] != VERSION || header[1] != CMD_CONNECT || header[2] != 0x00.toByte()) return -1
        return when (header[3]) {
            ATYP_IPV4 -> 10
            ATYP_IPV6 -> 22
            ATYP_DOMAIN -> {
                if (header.size < 5) -1 else 7 + (header[4].toInt() and 0xFF)
            }

            else -> -1
        }
    }
}
