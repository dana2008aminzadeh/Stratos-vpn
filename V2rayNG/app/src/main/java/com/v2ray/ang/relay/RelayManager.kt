package com.v2ray.ang.relay

import android.content.Context
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType

/**
 * Single entry point for the sidecar relays of protocols Xray does not implement
 * (SSH, DNSTT). The core service manager calls [ensureStarted] right before it
 * starts the core loop and [stop] right after the loop ends, so relay lifetime is
 * bound to the connection lifetime.
 */
object RelayManager {

    /**
     * Ensures the relay required by [config] is running (and no stale relay from a
     * previously connected profile lingers). Returns false only when this profile
     * REQUIRES a relay that could not be started — callers should fail the core
     * start in that case instead of connecting into a dead endpoint.
     */
    fun ensureStarted(context: Context, config: ProfileItem): Boolean = when (config.configType) {
        EConfigType.SSH -> SshRelay.start(context, config)

        EConfigType.DNSTT -> DnsttRelay.start(context, config)

        else -> {
            stop()
            true
        }
    }

    /** Stops every relay this object may own. Safe to call multiple times. */
    fun stop() {
        SshRelay.stop()
        DnsttRelay.stop()
    }
}
