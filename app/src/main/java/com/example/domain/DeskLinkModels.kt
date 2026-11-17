package com.example.domain

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    AUTH_REQUIRED,
    ONLINE,
    ERROR,
}

enum class PowerAction(
    val apiPath: String,
    val label: String,
    val requiresConfirmation: Boolean,
) {
    LOCK("lock", "Lock Screen", false),
    SLEEP("sleep", "Sleep PC", true),
    RESTART("restart", "Restart PC", true),
    SHUTDOWN("shutdown", "Shut Down PC", true),
    ABORT("abort", "Abort Pending Power Action", false),
}

enum class MediaAction(val apiValue: String) {
    UP("up"),
    DOWN("down"),
    MUTE("mute"),
    SET("set"),
    PLAY_PAUSE("play_pause"),
    NEXT("next"),
    PREV("prev"),
}

data class PcProfile(
    val id: String,
    val label: String,
    val host: String,
    val port: Int,
    val pin: String,
    val useHttps: Boolean = false,
) {
    val baseUrl: String
        get() {
            val scheme = if (useHttps) "https" else "http"
            val cleanHost = host.trim()
                .removePrefix("http://")
                .removePrefix("https://")
                .trimEnd('/')
            return "$scheme://$cleanHost:$port"
        }
}

data class PcStatus(
    val online: Boolean,
    val hostname: String,
    val os: String,
    val architecture: String,
    val uptimeSeconds: Long,
    val localIps: List<String>,
    val volume: Int,
    val muted: Boolean,
    val pendingPowerAction: String?,
    val unrestrictedShell: Boolean,
    val latencyMs: Long,
)

data class QuickAction(
    val id: String,
    val label: String,
    val command: String,
    val isServerPreset: Boolean = false,
)

data class CommandLogEntry(
    val id: String,
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
    val timestampLabel: String,
    val isSuccess: Boolean,
)

data class DeskLinkUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val statusMessage: String = "Configure your PC's local IP and PIN to connect.",
    val activeProfileId: String = "default_lan",
    val draftLabel: String = "Home Windows PC",
    val draftHost: String = "192.168.1.100",
    val draftPort: String = "8765",
    val draftPin: String = "",
    val draftUseHttps: Boolean = false,
    val autoPollEnabled: Boolean = true,
    val savedProfiles: List<PcProfile> = emptyList(),
    val pcStatus: PcStatus? = null,
    val serverActions: List<QuickAction> = emptyList(),
    val customActions: List<QuickAction> = emptyList(),
    val commandInput: String = "",
    val isExecutingCommand: Boolean = false,
    val commandLogs: List<CommandLogEntry> = emptyList(),
    val pendingPowerConfirmation: PowerAction? = null,
    val actionFeedback: String? = null,
    val isActionInProgress: Boolean = false,
    val phoneWifiConnected: Boolean = false,
    val phoneLocalIp: String? = null,
    val bundledServerScript: String = "",
)
