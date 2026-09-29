package com.v2ray.ang.stratos

/**
 * Chooses the active [StratosApi] backend.
 *
 * - Blank base URL  -> offline demo backend ([StratosDemoApi])
 * - Otherwise       -> [StratosHttpApi] against the configured panel
 *
 * The base URL lives in MMKV (`stratos_api_base`) and can later be edited from
 * the administrator build config or a hidden settings entry.
 */
object StratosApiProvider {

    @Volatile
    private var cached: Pair<String, StratosApi>? = null

    fun get(): StratosApi {
        val base = StratosSession.apiBaseUrl()
        cached?.let { if (it.first == base) return it.second }
        val api: StratosApi = if (base.isBlank()) StratosDemoApi else StratosHttpApi(base)
        cached = base to api
        return api
    }

    fun reset() {
        cached = null
    }

    fun isDemoMode(): Boolean = StratosSession.apiBaseUrl().isBlank()
}
