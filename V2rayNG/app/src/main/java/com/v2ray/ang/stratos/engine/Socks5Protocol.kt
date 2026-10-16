package com.v2ray.ang.stratos.engine

/**
 * Minimal SOCKS5 (RFC 1928) request/reply framing used by the built-in tunnel
 * engines. Pure byte-level logic with no I/O so it can be unit tested on the
 * JVM.
 */
object Socks5Protocol {
    const val VERSION: Int = 0x05

    const val AUTH_NONE: Int = 0x00
    const val AUTH_UNACCEPTABLE: Int = 0xFF

    const val CMD_CONNECT: Int = 0x01
    const val CMD_BIND: Int = 0x02
    const val CMD_UDP_ASSOCIATE: Int = 0x03

    const val ATYP_IPV4: Int = 0x01
    const val ATYP_DOMAIN: Int = 0x03
    const val ATYP_IPV6: Int = 0x04

    const val REP_SUCCESS: Int = 0x00
    const val REP_GENERAL_FAILURE: Int = 0x01
    const val REP_COMMAND_NOT_SUPPORTED: Int = 0x07
    const val REP_ADDRESS_TYPE_NOT_SUPPORTED: Int = 0x08

    /** Parsed SOCKS5 request: command plus destination. */
    data class Request(val command: Int, val host: String, val port: Int)

    /**
     * Parses a SOCKS5 request body (without the greeting).
     *
     * Layout: VER RSV CMD ATYP ADDR PORT
     * ADDR is 4 bytes (IPv4), 16 bytes (IPv6) or LEN+bytes (domain).
     *
     * @return The parsed request, or null when the buffer is not a valid request.
     */
    fun parseRequest(data: ByteArray, length: Int = data.size): Request? {
        if (length < 7) return null
        if (data[0].toInt() and 0xFF != VERSION) return null
        val command = data[1].toInt() and 0xFF
        val atyp = data[3].toInt() and 0xFF
        return when (atyp) {
            ATYP_IPV4 -> {
                if (length < 10) return null
                val host = (4..7).joinToString(".") { (data[it].toInt() and 0xFF).toString() }
                Request(command, host, readPort(data, 8))
            }

            ATYP_DOMAIN -> {
                val len = data[4].toInt() and 0xFF
                if (len == 0 || length < 5 + len + 2) return null
                val host = String(data, 5, len, Charsets.UTF_8)
                Request(command, host, readPort(data, 5 + len))
            }

            ATYP_IPV6 -> {
                if (length < 22) return null
                val groups = ArrayList<String>(8)
                var i = 4
                while (i < 20) {
                    groups.add(
                        (((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF))
                            .toString(16),
                    )
                    i += 2
                }
                Request(command, groups.joinToString(":"), readPort(data, 20))
            }

            else -> null
        }
    }

    /**
     * Builds the standard reply with an unspecified bound address.
     *
     * @param replyCode One of the REP_* constants.
     */
    fun buildReply(replyCode: Int): ByteArray = byteArrayOf(
        VERSION.toByte(),
        replyCode.toByte(),
        0x00,
        ATYP_IPV4.toByte(),
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00,
    )

    /** The reply selecting "no authentication" during the greeting. */
    fun buildGreetingReply(): ByteArray = byteArrayOf(VERSION.toByte(), AUTH_NONE.toByte())

    /** True when the greeting offers the no-authentication method. */
    fun greetingOffersNoAuth(data: ByteArray, length: Int = data.size): Boolean {
        if (length < 3) return false
        if (data[0].toInt() and 0xFF != VERSION) return false
        val nMethods = data[1].toInt() and 0xFF
        if (length < 2 + nMethods) return false
        for (i in 0 until nMethods) {
            if (data[2 + i].toInt() and 0xFF == AUTH_NONE) return true
        }
        return false
    }

    private fun readPort(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
}
