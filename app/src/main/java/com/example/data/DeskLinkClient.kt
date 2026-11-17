package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.domain.CommandLogEntry
import com.example.domain.MediaAction
import com.example.domain.PcProfile
import com.example.domain.PcStatus
import com.example.domain.PowerAction
import com.example.domain.QuickAction
import java.io.IOException
import java.net.Inet4Address
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

sealed interface ClientResult<out T> {
    data class Success<T>(val data: T, val latencyMs: Long = 0L) : ClientResult<T>
    data class AuthError(val message: String) : ClientResult<Nothing>
    data class Failure(val message: String) : ClientResult<Nothing>
}

class DeskLinkClient(
    private val context: Context,
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(16, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile
    private var cachedTokenProfileKey: String? = null

    @Volatile
    private var cachedSessionToken: String? = null

    fun clearSessionToken() {
        cachedTokenProfileKey = null
        cachedSessionToken = null
    }

    fun getPhoneNetworkInfo(): Pair<Boolean, String?> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false to null
        val active = cm.activeNetwork ?: return false to null
        val caps = cm.getNetworkCapabilities(active) ?: return false to null
        val hasWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val hasVpnOrEth = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

        val linkProps = cm.getLinkProperties(active)
        val ipv4 = linkProps?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress

        return (hasWifi || hasVpnOrEth) to ipv4
    }

    suspend fun fetchStatus(profile: PcProfile): ClientResult<PcStatus> = withContext(Dispatchers.IO) {
        val validationError = validateProfile(profile)
        if (validationError != null) {
            return@withContext ClientResult.Failure(validationError)
        }

        val tokenResult = ensureTokenOrPinHeader(profile)
        if (tokenResult is ClientResult.AuthError) {
            return@withContext tokenResult
        }
        if (tokenResult is ClientResult.Failure) {
            return@withContext tokenResult
        }

        val start = System.currentTimeMillis()
        val response = executeGet(profile, "/api/status", (tokenResult as ClientResult.Success).data)
        val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)

        when (response) {
            is ClientResult.AuthError -> {
                clearSessionToken()
                val reauth = authenticate(profile)
                if (reauth is ClientResult.Success) {
                    val retryStart = System.currentTimeMillis()
                    val retry = executeGet(profile, "/api/status", reauth.data)
                    val retryElapsed = (System.currentTimeMillis() - retryStart).coerceAtLeast(1L)
                    parseStatusResult(retry, retryElapsed)
                } else {
                    reauth as ClientResult<PcStatus>
                }
            }
            is ClientResult.Failure -> response
            is ClientResult.Success -> parseStatusResult(response, elapsed)
        }
    }

    suspend fun fetchServerActions(profile: PcProfile): ClientResult<List<QuickAction>> =
        withContext(Dispatchers.IO) {
            val auth = ensureTokenOrPinHeader(profile)
            if (auth !is ClientResult.Success) return@withContext auth as ClientResult<List<QuickAction>>

            when (val res = executeGet(profile, "/api/actions", auth.data)) {
                is ClientResult.Success -> {
                    val arr = res.data.optJSONArray("actions") ?: JSONArray()
                    val list = buildList {
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            val label = obj.optString("label").trim()
                            val cmd = obj.optString("command").trim()
                            if (label.isNotEmpty() && cmd.isNotEmpty()) {
                                add(
                                    QuickAction(
                                        id = obj.optString("id").ifBlank { "srv_$i" },
                                        label = label,
                                        command = cmd,
                                        isServerPreset = true,
                                    )
                                )
                            }
                        }
                    }
                    ClientResult.Success(list)
                }
                is ClientResult.AuthError -> res
                is ClientResult.Failure -> res
            }
        }

    suspend fun sendPower(
        profile: PcProfile,
        action: PowerAction,
        delaySeconds: Int = 5,
    ): ClientResult<String> = withContext(Dispatchers.IO) {
        val auth = ensureTokenOrPinHeader(profile)
        if (auth !is ClientResult.Success) return@withContext auth as ClientResult<String>

        val payload = JSONObject().apply { put("delay", delaySeconds) }
        when (val res = executePost(profile, "/api/power/${action.apiPath}", payload, auth.data)) {
            is ClientResult.Success -> {
                val msg = res.data.optString("message").ifBlank { "${action.label} dispatched." }
                ClientResult.Success(msg, res.latencyMs)
            }
            is ClientResult.AuthError -> res
            is ClientResult.Failure -> res
        }
    }

    suspend fun sendMedia(
        profile: PcProfile,
        action: MediaAction,
        level: Int? = null,
    ): ClientResult<Pair<Int, Boolean>> = withContext(Dispatchers.IO) {
        val auth = ensureTokenOrPinHeader(profile)
        if (auth !is ClientResult.Success) return@withContext auth as ClientResult<Pair<Int, Boolean>>

        val payload = JSONObject().apply {
            put("action", action.apiValue)
            if (level != null) put("level", level.coerceIn(0, 100))
        }
        when (val res = executePost(profile, "/api/volume", payload, auth.data)) {
            is ClientResult.Success -> {
                val vol = res.data.optInt("volume", level ?: 50)
                val muted = res.data.optBoolean("muted", false)
                ClientResult.Success(vol to muted, res.latencyMs)
            }
            is ClientResult.AuthError -> res
            is ClientResult.Failure -> res
        }
    }

    suspend fun executeCommand(
        profile: PcProfile,
        command: String,
    ): ClientResult<CommandLogEntry> = withContext(Dispatchers.IO) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) {
            return@withContext ClientResult.Failure("Enter a command to execute.")
        }

        val auth = ensureTokenOrPinHeader(profile)
        if (auth !is ClientResult.Success) return@withContext auth as ClientResult<CommandLogEntry>

        val payload = JSONObject().apply { put("command", trimmed) }
        val res = executePost(profile, "/api/command", payload, auth.data, acceptBadRequestJson = true)
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

        when (res) {
            is ClientResult.Success -> {
                val json = res.data
                ClientResult.Success(
                    CommandLogEntry(
                        id = UUID.randomUUID().toString(),
                        command = trimmed,
                        exitCode = json.optInt("exit_code", -1),
                        stdout = json.optString("stdout", ""),
                        stderr = json.optString("stderr", json.optString("error", "")),
                        durationMs = json.optLong("duration_ms", res.latencyMs),
                        timestampLabel = timestamp,
                        isSuccess = json.optBoolean("ok", false),
                    )
                )
            }
            is ClientResult.AuthError -> res
            is ClientResult.Failure -> res
        }
    }

    private fun validateProfile(profile: PcProfile): String? {
        val host = profile.host.trim()
        if (host.isEmpty()) return "Enter your PC's local IP address or hostname."
        if (profile.port !in 1..65535) return "Port must be between 1 and 65535."
        return null
    }

    private fun ensureTokenOrPinHeader(profile: PcProfile): ClientResult<String> {
        val profileKey = "${profile.baseUrl}|${profile.pin}"
        val existing = cachedSessionToken
        if (cachedTokenProfileKey == profileKey && !existing.isNullOrBlank()) {
            return ClientResult.Success(existing)
        }
        if (profile.pin.isBlank()) {
            return ClientResult.AuthError("Enter the server PIN shown in your PC terminal.")
        }
        return authenticate(profile)
    }

    private fun authenticate(profile: PcProfile): ClientResult<String> {
        val profileKey = "${profile.baseUrl}|${profile.pin}"
        val body = JSONObject().apply { put("pin", profile.pin.trim()) }
        val request = Request.Builder()
            .url("${profile.baseUrl}/api/auth")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrNull()
                if (response.isSuccessful && json?.optBoolean("ok") == true) {
                    val token = json.optString("token")
                    cachedTokenProfileKey = profileKey
                    cachedSessionToken = token
                    ClientResult.Success(token)
                } else if (response.code == 401 || response.code == 429) {
                    ClientResult.AuthError(
                        json?.optString("error")?.ifBlank { "Invalid PIN" } ?: "Invalid PIN"
                    )
                } else {
                    ClientResult.Failure(
                        json?.optString("error")?.ifBlank { "HTTP ${response.code}" }
                            ?: "Server returned HTTP ${response.code}"
                    )
                }
            }
        } catch (e: IOException) {
            ClientResult.Failure(friendlyNetworkError(e, profile))
        }
    }

    private fun executeGet(
        profile: PcProfile,
        path: String,
        token: String,
    ): ClientResult<JSONObject> {
        val request = Request.Builder()
            .url("${profile.baseUrl}$path")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        return executeJsonCall(request, profile, acceptBadRequestJson = false)
    }

    private fun executePost(
        profile: PcProfile,
        path: String,
        payload: JSONObject,
        token: String,
        acceptBadRequestJson: Boolean = false,
    ): ClientResult<JSONObject> {
        val request = Request.Builder()
            .url("${profile.baseUrl}$path")
            .header("Authorization", "Bearer $token")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        return executeJsonCall(request, profile, acceptBadRequestJson)
    }

    private fun executeJsonCall(
        request: Request,
        profile: PcProfile,
        acceptBadRequestJson: Boolean,
    ): ClientResult<JSONObject> {
        val start = System.currentTimeMillis()
        return try {
            httpClient.newCall(request).execute().use { response ->
                val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                val raw = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()

                when {
                    response.isSuccessful -> ClientResult.Success(json, elapsed)
                    acceptBadRequestJson && response.code == 400 && json.has("exit_code") ->
                        ClientResult.Success(json, elapsed)
                    response.code == 401 || response.code == 429 ->
                        ClientResult.AuthError(
                            json.optString("error").ifBlank { "Authentication rejected" }
                        )
                    else ->
                        ClientResult.Failure(
                            json.optString("error").ifBlank { "HTTP ${response.code}" }
                        )
                }
            }
        } catch (e: IOException) {
            ClientResult.Failure(friendlyNetworkError(e, profile))
        } catch (e: IllegalArgumentException) {
            ClientResult.Failure("Invalid host or URL format: ${e.message}")
        }
    }

    private fun parseStatusResult(
        result: ClientResult<JSONObject>,
        latencyMs: Long,
    ): ClientResult<PcStatus> {
        return when (result) {
            is ClientResult.Success -> {
                val j = result.data
                val ipsJson = j.optJSONArray("local_ips") ?: JSONArray()
                val ips = buildList {
                    for (i in 0 until ipsJson.length()) {
                        val ip = ipsJson.optString(i)
                        if (ip.isNotBlank()) add(ip)
                    }
                }
                val pending = if (j.isNull("pending_power_action")) {
                    null
                } else {
                    j.optString("pending_power_action").ifBlank { null }
                }
                ClientResult.Success(
                    PcStatus(
                        online = j.optBoolean("online", true),
                        hostname = j.optString("hostname", "Windows PC"),
                        os = j.optString("os", "Windows"),
                        architecture = j.optString("architecture", ""),
                        uptimeSeconds = j.optLong("uptime_seconds", 0L),
                        localIps = ips,
                        volume = j.optInt("volume", 50).coerceIn(0, 100),
                        muted = j.optBoolean("muted", false),
                        pendingPowerAction = pending,
                        unrestrictedShell = j.optBoolean("unrestricted_shell", false),
                        latencyMs = latencyMs,
                    ),
                    latencyMs = latencyMs,
                )
            }
            is ClientResult.AuthError -> result
            is ClientResult.Failure -> result
        }
    }

    private fun friendlyNetworkError(e: IOException, profile: PcProfile): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("ECONNREFUSED", ignoreCase = true) ->
                "Connection refused by ${profile.host}:${profile.port}. Ensure pc_remote_server.py is running and Windows Firewall allows port ${profile.port}."
            msg.contains("timeout", ignoreCase = true) ->
                "Timed out reaching ${profile.host}:${profile.port}. Verify both devices are on the same Wi-Fi LAN or VPN."
            msg.contains("Unable to resolve host", ignoreCase = true) ->
                "Cannot resolve '${profile.host}'. Check the IP address or hostname."
            else -> "Unreachable (${profile.host}:${profile.port}): ${msg.ifBlank { "Network error" }}"
        }
    }
}
