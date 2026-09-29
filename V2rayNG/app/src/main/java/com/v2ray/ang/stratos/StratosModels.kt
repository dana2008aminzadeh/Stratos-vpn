package com.v2ray.ang.stratos

import java.io.Serializable

/**
 * Stratos VPN — domain models for the account-facing layer.
 *
 * The app never sees a raw subscription link: all servers are delivered by the
 * panel through [StratosApi] and imported internally.
 */

enum class StratosUserStatus(val wire: String) {
    ACTIVE("active"),
    EXPIRED("expired"),
    DISABLED("disabled"),
    LIMITED("limited");

    companion object {
        fun fromWire(value: String?): StratosUserStatus =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: ACTIVE
    }
}

data class StratosUser(
    val username: String,
    val status: StratosUserStatus,
    val dataLimitBytes: Long,          // 0 or negative => unlimited
    val usedBytes: Long,
    val expireAtMillis: Long,          // 0 => never expires
    val deviceLimit: Int = 1,
) : Serializable {

    val isUnlimitedData: Boolean get() = dataLimitBytes <= 0L

    val remainingBytes: Long
        get() = if (isUnlimitedData) Long.MAX_VALUE else (dataLimitBytes - usedBytes).coerceAtLeast(0L)

    val remainingMillis: Long
        get() = if (expireAtMillis <= 0L) Long.MAX_VALUE else (expireAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)

    val hasTimeLeft: Boolean get() = expireAtMillis <= 0L || System.currentTimeMillis() < expireAtMillis
    val hasDataLeft: Boolean get() = isUnlimitedData || usedBytes < dataLimitBytes

    /** True when the user is allowed to open a VPN connection right now. */
    val canConnect: Boolean
        get() = status == StratosUserStatus.ACTIVE && hasTimeLeft && hasDataLeft

    /** Fraction of the data plan still available, in [0, 1] (1 when unlimited). */
    val remainingFraction: Float
        get() = when {
            isUnlimitedData -> 1f
            dataLimitBytes <= 0L -> 1f
            else -> (remainingBytes.toDouble() / dataLimitBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        }
}

data class StratosServer(
    val id: String,
    val name: String,
    val countryCode: String,           // ISO-3166 alpha-2, lower case
    val countryName: String,
    val config: String,                // share link or JSON consumed internally, never displayed
    val tags: List<String> = emptyList(),
) : Serializable

/** Remote configuration controlled by the operator (the panel owner). */
data class StratosAdminConfig(
    val renewUrl: String = "",
    val websiteUrl: String = "",
    val telegramUrl: String = "",
    val notice: String = "",
    /** Settings the operator forces on every login (key -> value, AppConfig PREF_* keys). */
    val forcedSettings: Map<String, String> = emptyMap(),
    /** Settings applied when the user taps "apply optimal settings". */
    val bestSettings: Map<String, String> = emptyMap(),
    val minSupportedVersionCode: Int = 0,
) : Serializable

sealed interface StratosLoginResult {
    data class Success(val token: String, val user: StratosUser, val admin: StratosAdminConfig) : StratosLoginResult
    /** The account is bound to a different device (device limit reached). */
    data object DeviceConflict : StratosLoginResult
    data object InvalidCredentials : StratosLoginResult
    data class Failure(val message: String?) : StratosLoginResult
}

/** Live traffic snapshot broadcast from the daemon side to the UI. */
data class StratosTrafficUpdate(
    val upSpeedBytesPerSec: Long,
    val downSpeedBytesPerSec: Long,
    val sessionUpBytes: Long,
    val sessionDownBytes: Long,
    val accountUsedBytes: Long,
) : Serializable {
    val sessionTotalBytes: Long get() = sessionUpBytes + sessionDownBytes
}
