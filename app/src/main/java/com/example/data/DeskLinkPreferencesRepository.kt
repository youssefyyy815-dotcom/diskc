package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.PcProfile
import com.example.domain.QuickAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.deskLinkDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "desklink_preferences"
)

data class StoredDeskLinkData(
    val activeProfileId: String,
    val profiles: List<PcProfile>,
    val customActions: List<QuickAction>,
    val autoPollEnabled: Boolean,
)

class DeskLinkPreferencesRepository(
    private val context: Context,
) {
    private object Keys {
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val PROFILES_JSON = stringPreferencesKey("profiles_json")
        val CUSTOM_ACTIONS_JSON = stringPreferencesKey("custom_actions_json")
        val AUTO_POLL_ENABLED = booleanPreferencesKey("auto_poll_enabled")
    }

    val storedDataFlow: Flow<StoredDeskLinkData> = context.deskLinkDataStore.data.map { prefs ->
        val profiles = decodeProfiles(prefs[Keys.PROFILES_JSON])
        val activeId = prefs[Keys.ACTIVE_PROFILE_ID]
            ?: profiles.firstOrNull()?.id
            ?: DEFAULT_PROFILE.id
        val actions = decodeActions(prefs[Keys.CUSTOM_ACTIONS_JSON])
        val autoPoll = prefs[Keys.AUTO_POLL_ENABLED] ?: true

        StoredDeskLinkData(
            activeProfileId = activeId,
            profiles = profiles,
            customActions = actions,
            autoPollEnabled = autoPoll,
        )
    }

    suspend fun saveProfiles(activeProfileId: String, profiles: List<PcProfile>) {
        val encoded = JSONArray().apply {
            profiles.forEach { p ->
                put(
                    JSONObject().apply {
                        put("id", p.id)
                        put("label", p.label)
                        put("host", p.host)
                        put("port", p.port)
                        put("pin", p.pin)
                        put("useHttps", p.useHttps)
                    }
                )
            }
        }.toString()

        context.deskLinkDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_PROFILE_ID] = activeProfileId
            prefs[Keys.PROFILES_JSON] = encoded
        }
    }

    suspend fun saveCustomActions(actions: List<QuickAction>) {
        val encoded = JSONArray().apply {
            actions.forEach { a ->
                put(
                    JSONObject().apply {
                        put("id", a.id)
                        put("label", a.label)
                        put("command", a.command)
                    }
                )
            }
        }.toString()

        context.deskLinkDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_ACTIONS_JSON] = encoded
        }
    }

    suspend fun setAutoPoll(enabled: Boolean) {
        context.deskLinkDataStore.edit { prefs ->
            prefs[Keys.AUTO_POLL_ENABLED] = enabled
        }
    }

    fun loadBundledServerScript(): String {
        return runCatching {
            context.assets.open("pc_server/pc_remote_server.py")
                .bufferedReader()
                .use { it.readText() }
        }.getOrElse { "# Failed to load bundled pc_remote_server.py" }
    }

    private fun decodeProfiles(raw: String?): List<PcProfile> {
        if (raw.isNullOrBlank()) return listOf(DEFAULT_PROFILE)
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        PcProfile(
                            id = o.optString("id").ifBlank { "profile_$i" },
                            label = o.optString("label").ifBlank { "Windows PC" },
                            host = o.optString("host").ifBlank { "192.168.1.100" },
                            port = o.optInt("port", 8765),
                            pin = o.optString("pin", ""),
                            useHttps = o.optBoolean("useHttps", false),
                        )
                    )
                }
            }.ifEmpty { listOf(DEFAULT_PROFILE) }
        }.getOrElse { listOf(DEFAULT_PROFILE) }
    }

    private fun decodeActions(raw: String?): List<QuickAction> {
        if (raw == null) return DEFAULT_CUSTOM_ACTIONS
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val label = o.optString("label").trim()
                    val cmd = o.optString("command").trim()
                    if (label.isNotEmpty() && cmd.isNotEmpty()) {
                        add(
                            QuickAction(
                                id = o.optString("id").ifBlank { "custom_$i" },
                                label = label,
                                command = cmd,
                                isServerPreset = false,
                            )
                        )
                    }
                }
            }
        }.getOrElse { DEFAULT_CUSTOM_ACTIONS }
    }

    companion object {
        val DEFAULT_PROFILE = PcProfile(
            id = "default_lan",
            label = "Home Windows PC",
            host = "192.168.1.100",
            port = 8765,
            pin = "",
            useHttps = false,
        )

        val DEFAULT_CUSTOM_ACTIONS = listOf(
            QuickAction("qa_whoami", "Active User", "whoami"),
            QuickAction("qa_ip", "IP & Gateway", "ipconfig"),
            QuickAction("qa_procs", "Running Tasks", "tasklist"),
            QuickAction("qa_ports", "Open Ports", "netstat -ano"),
        )
    }
}
