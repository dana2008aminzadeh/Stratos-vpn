package com.v2ray.ang.stratos

import com.v2ray.ang.util.JsonUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * HTTP implementation of [StratosApi] for the real management panel.
 *
 * Endpoints (JSON over HTTPS, Bearer token auth) — see docs/STRATOS_API.md:
 *   POST {base}/api/v1/auth/login        {username,password,device_id,device_name}
 *   POST {base}/api/v1/auth/logout
 *   POST {base}/api/v1/auth/heartbeat    {device_id}
 *   POST {base}/api/v1/auth/password     {current_password,new_password}
 *   GET  {base}/api/v1/user/me
 *   GET  {base}/api/v1/servers
 *   GET  {base}/api/v1/config
 *   POST {base}/api/v1/usage             {bytes_up,bytes_down}
 */
class StratosHttpApi(private val baseUrl: String) : StratosApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val base = baseUrl.trimEnd('/')

    private data class ApiUser(
        val username: String? = null,
        val status: String? = null,
        val data_limit_bytes: Long? = null,
        val used_bytes: Long? = null,
        val expire_at: Long? = null,          // epoch seconds or millis; normalize below
        val device_limit: Int? = null,
    )

    private data class ApiServer(
        val id: String? = null,
        val name: String? = null,
        val country_code: String? = null,
        val country_name: String? = null,
        val config: String? = null,
        val tags: List<String>? = null,
    )

    private data class ApiAdmin(
        val renew_url: String? = null,
        val website_url: String? = null,
        val telegram_url: String? = null,
        val notice: String? = null,
        val forced_settings: Map<String, String>? = null,
        val best_settings: Map<String, String>? = null,
        val min_supported_version_code: Int? = null,
    )

    private data class LoginResponse(
        val token: String? = null,
        val user: ApiUser? = null,
        val admin: ApiAdmin? = null,
        val error: String? = null,
    )

    private class StratosException(val code: Int, message: String?) : Exception(message)

    private fun normalizeEpoch(value: Long?): Long = when {
        value == null -> 0L
        value in 1 until 10_000_000_000L -> value * 1000 // seconds -> millis
        else -> value
    }

    private fun ApiUser.toDomain(fallbackUsername: String = "") = StratosUser(
        username = username ?: fallbackUsername,
        status = StratosUserStatus.fromWire(status),
        dataLimitBytes = data_limit_bytes ?: 0L,
        usedBytes = used_bytes ?: 0L,
        expireAtMillis = normalizeEpoch(expire_at),
        deviceLimit = device_limit ?: 1,
    )

    private fun ApiAdmin.toDomain() = StratosAdminConfig(
        renewUrl = renew_url.orEmpty(),
        websiteUrl = website_url.orEmpty(),
        telegramUrl = telegram_url.orEmpty(),
        notice = notice.orEmpty(),
        forcedSettings = forced_settings.orEmpty(),
        bestSettings = best_settings.orEmpty(),
        minSupportedVersionCode = min_supported_version_code ?: 0,
    )

    private suspend inline fun <reified T> call(
        method: String,
        path: String,
        token: String?,
        body: Any?,
    ): T = withContext(Dispatchers.IO) {
        val requestBody = (body?.let { JsonUtil.toJson(it) } ?: "{}").toRequestBody(jsonMedia)
        val builder = Request.Builder().url(base + path)
        if (method == "GET") builder.get() else builder.method(method, requestBody)
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        builder.header("Accept", "application/json")
        builder.header("User-Agent", "StratosVPN-Android")
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (response.code == 401 || response.code == 403) {
                    throw StratosException(response.code, text.ifBlank { "unauthorized" })
                }
                throw StratosException(response.code, "HTTP ${response.code}")
            }
            JsonUtil.fromJsonSafe(text, T::class.java) ?: throw StratosException(-1, "bad json")
        }
    }

    override suspend fun login(username: String, password: String, deviceId: String, deviceName: String): StratosLoginResult {
        return try {
            val response = call<LoginResponse>(
                "POST", "/api/v1/auth/login", null,
                mapOf(
                    "username" to username,
                    "password" to password,
                    "device_id" to deviceId,
                    "device_name" to deviceName,
                ),
            )
            when {
                response.error == "device_conflict" -> StratosLoginResult.DeviceConflict
                response.error != null -> StratosLoginResult.InvalidCredentials
                response.token != null && response.user != null -> StratosLoginResult.Success(
                    token = response.token,
                    user = response.user.toDomain(username),
                    admin = response.admin?.toDomain() ?: StratosAdminConfig(),
                )

                else -> StratosLoginResult.Failure("bad response")
            }
        } catch (e: StratosException) {
            if (e.code == 401 || e.code == 403) StratosLoginResult.InvalidCredentials
            else StratosLoginResult.Failure(e.message)
        } catch (e: Exception) {
            StratosLoginResult.Failure(e.message)
        }
    }

    override suspend fun logout(token: String) {
        runCatching { call<Map<String, Any>>("POST", "/api/v1/auth/logout", token, null) }
    }

    override suspend fun fetchUser(token: String): StratosUser? = try {
        call<ApiUser>("GET", "/api/v1/user/me", token, null).toDomain()
    } catch (e: Exception) {
        null
    }

    override suspend fun fetchServers(token: String): List<StratosServer> = try {
        call<Array<ApiServer>>("GET", "/api/v1/servers", token, null).mapNotNull { s ->
            val config = s.config ?: return@mapNotNull null
            StratosServer(
                id = s.id ?: config.hashCode().toString(),
                name = s.name ?: s.country_code?.uppercase() ?: "Server",
                countryCode = s.country_code?.lowercase() ?: "zz",
                countryName = s.country_name ?: s.country_code?.uppercase() ?: "Other",
                config = config,
                tags = s.tags.orEmpty(),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    override suspend fun fetchAdminConfig(token: String?): StratosAdminConfig = try {
        call<ApiAdmin>("GET", "/api/v1/config", token, null).toDomain()
    } catch (e: Exception) {
        StratosAdminConfig()
    }

    override suspend fun reportUsage(token: String, bytesUp: Long, bytesDown: Long): StratosUser? = try {
        call<ApiUser>(
            "POST", "/api/v1/usage", token,
            mapOf("bytes_up" to bytesUp, "bytes_down" to bytesDown),
        ).toDomain()
    } catch (e: Exception) {
        null
    }

    override suspend fun heartbeat(token: String, deviceId: String): Boolean = try {
        call<Map<String, Any>>("POST", "/api/v1/auth/heartbeat", token, mapOf("device_id" to deviceId))
        true
    } catch (e: StratosException) {
        e.code != 401 && e.code != 403
    } catch (e: Exception) {
        true // network hiccup: do not kick the user
    }

    override suspend fun changePassword(token: String, currentPassword: String, newPassword: String): Boolean = try {
        call<Map<String, Any>>(
            "POST", "/api/v1/auth/password", token,
            mapOf("current_password" to currentPassword, "new_password" to newPassword),
        )
        true
    } catch (e: Exception) {
        false
    }
}
