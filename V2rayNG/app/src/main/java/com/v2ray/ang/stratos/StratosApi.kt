package com.v2ray.ang.stratos

/**
 * Transport contract between the Stratos app and the operator panel.
 *
 * Default implementation: [StratosDemoApi] (fully offline, demo account).
 * Production implementation: [StratosHttpApi] once the management panel ships.
 */
interface StratosApi {

    /**
     * Authenticates a user and binds the device.
     * Implementations must enforce the device limit and return
     * [StratosLoginResult.DeviceConflict] when another device holds the slot.
     */
    suspend fun login(username: String, password: String, deviceId: String, deviceName: String): StratosLoginResult

    /** Releases the device binding. Best effort; never throws. */
    suspend fun logout(token: String)

    /** Fresh account snapshot: data, expiry, status. */
    suspend fun fetchUser(token: String): StratosUser?

    /** Server list for the account. Never contains a raw subscription URL. */
    suspend fun fetchServers(token: String): List<StratosServer>

    /** Operator-controlled links and settings. */
    suspend fun fetchAdminConfig(token: String?): StratosAdminConfig

    /**
     * Reports locally-measured consumption (uplink + downlink since last report).
     * Returns the fresh user snapshot (with server-authoritative usage when available).
     */
    suspend fun reportUsage(token: String, bytesUp: Long, bytesDown: Long): StratosUser?

    /** False when the token is no longer bound to this device (kicked). */
    suspend fun heartbeat(token: String, deviceId: String): Boolean

    /** Change the account password. Current password is required. */
    suspend fun changePassword(token: String, currentPassword: String, newPassword: String): Boolean
}
