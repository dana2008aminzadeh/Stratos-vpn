package com.v2ray.ang.stratos

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.compose.ThemeManager

/**
 * One-time Stratos defaults, applied on the very first launch:
 * - dark space theme as the default look; brand colors instead of dynamic color,
 * - the live-traffic notification enabled (Stratos keeps it mandatory),
 * - Persian as the default language (switchable in settings),
 * and on every launch: restore session state and start the panel sync loop.
 */
object StratosBootstrap {

    fun init(context: Context) {
        val isFirstRun = StratosSession.markFirstRunDone()
        if (isFirstRun) {
            applyFirstRunDefaults()
        }

        StratosSession.restoreFromStorage()
        StratosServersStore.ensureSubscription()

        // Re-import the cached fleet on cold start (profiles may have been cleaned).
        val cachedServers = StratosSession.serversState.value
        if (cachedServers.isNotEmpty()) {
            StratosServersStore.sync(cachedServers)
        }

        // The periodic panel sync runs in the main (UI) process only; the daemon
        // process handles its own usage accounting through StratosTrafficEngine.
        if (isMainProcess(context)) {
            StratosSync.start(context)
        }
    }

    private fun isMainProcess(context: Context): Boolean {
        val processName = runCatching {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentProcessName")
                .invoke(null) as? String
        }.getOrNull()
        return processName == null || processName == context.packageName
    }

    private fun applyFirstRunDefaults() {
        // Brand look & feel
        if (MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT).isNullOrBlank()) {
            MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, "2") // dark
            ThemeManager.refresh()
        }
        if (MmkvManager.decodeSettingsString(AppConfig.PREF_DYNAMIC_COLOR).isNullOrBlank()) {
            MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, false)
            ThemeManager.refresh()
        }
        // Mandatory live-traffic notification
        MmkvManager.encodeSettings(AppConfig.PREF_SPEED_ENABLED, true)
        // Persian default when the user never picked a language
        if (MmkvManager.decodeSettingsString(AppConfig.PREF_LANGUAGE).isNullOrBlank()) {
            runCatching { AppLocaleManager.setApplicationLanguage("fa") }
        }
    }
}
