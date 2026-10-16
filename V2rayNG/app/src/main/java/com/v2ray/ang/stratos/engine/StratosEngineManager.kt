package com.v2ray.ang.stratos.engine

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.LogUtil
import java.net.Socket

/**
 * Owns the built-in data-plane engines (SSH and friends) that back profile
 * types Xray-core cannot dial directly. The common service lifecycle starts
 * and stops the engine around the core loop.
 */
object StratosEngineManager {

    sealed interface StartResult {
        data object Running : StartResult

        /** Engine failed to start; [reason] is a plain diagnostic message. */
        data class Failed(val reason: String) : StartResult

        /** The protocol's engine is not bundled in this build; UI should localize. */
        data class EngineMissing(val protocolLabel: String) : StartResult
    }

    private val lock = Any()
    private var engine: StratosSshEngine? = null
    private var runningProfile: ProfileItem? = null

    /**
     * True when this profile type is carried by a local engine loopback
     * outbound (and therefore needs an engine running before the core starts).
     */
    fun usesLocalEngine(configType: EConfigType?): Boolean = when (configType) {
        EConfigType.SSH,
        EConfigType.DNSTT,
        EConfigType.LIGHTWAY,
        -> true

        else -> false
    }

    /** Loopback address the core outbound points at. */
    const val ENGINE_HOST: String = "127.0.0.1"

    /** Loopback port the engine's SOCKS5 front-end listens on. */
    const val ENGINE_PORT: Int = AppConfig.ENGINE_LOOP_PORT

    /**
     * Starts (or reuses) the engine required by [profile].
     *
     * @param socketProtector Applied to upstream sockets so they bypass the
     * VPN interface in run modes that own one; may be null in proxy-only mode.
     */
    fun ensureEngine(profile: ProfileItem, socketProtector: ((Socket) -> Boolean)?): StartResult {
        synchronized(lock) {
            val existing = engine
            if (existing != null && existing.isRunning && runningProfile == profile) {
                return StartResult.Running
            }
            if (existing != null) {
                existing.stop()
                engine = null
                runningProfile = null
            }
            return when (profile.configType) {
                EConfigType.SSH -> {
                    val sshEngine = StratosSshEngine(
                        profile = profile,
                        localPort = ENGINE_PORT,
                        socketProtector = socketProtector,
                    )
                    val error = sshEngine.start()
                    if (error == null) {
                        engine = sshEngine
                        runningProfile = profile
                        LogUtil.i(AppConfig.TAG, "EngineManager: SSH engine running on port $ENGINE_PORT")
                        StartResult.Running
                    } else {
                        StartResult.Failed(error)
                    }
                }

                EConfigType.DNSTT -> StartResult.EngineMissing("DNSTT")
                EConfigType.LIGHTWAY -> StartResult.EngineMissing("Lightway")
                else -> StartResult.Failed("No local engine for ${profile.configType}")
            }
        }
    }

    /** Stops the running engine, if any. Idempotent. */
    fun stopEngine() {
        synchronized(lock) {
            engine?.stop()
            engine = null
            runningProfile = null
        }
    }

    fun isEngineRunning(): Boolean = synchronized(lock) { engine?.isRunning == true }
}
