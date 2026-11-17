package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ClientResult
import com.example.data.DeskLinkClient
import com.example.data.DeskLinkPreferencesRepository
import com.example.domain.ConnectionState
import com.example.domain.DeskLinkUiState
import com.example.domain.MediaAction
import com.example.domain.PcProfile
import com.example.domain.PowerAction
import com.example.domain.QuickAction
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DeskLinkViewModel(application: Application) : AndroidViewModel(application) {

    private val client = DeskLinkClient(application)
    private val prefsRepo = DeskLinkPreferencesRepository(application)

    private val _uiState = MutableStateFlow(DeskLinkUiState())
    val uiState: StateFlow<DeskLinkUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        val script = prefsRepo.loadBundledServerScript()
        val (wifiOk, phoneIp) = client.getPhoneNetworkInfo()
        _uiState.update {
            it.copy(
                bundledServerScript = script,
                phoneWifiConnected = wifiOk,
                phoneLocalIp = phoneIp,
            )
        }

        viewModelScope.launch {
            val stored = prefsRepo.storedDataFlow.first()
            val active = stored.profiles.firstOrNull { it.id == stored.activeProfileId }
                ?: stored.profiles.firstOrNull()
                ?: DeskLinkPreferencesRepository.DEFAULT_PROFILE

            _uiState.update {
                it.copy(
                    activeProfileId = active.id,
                    draftLabel = active.label,
                    draftHost = active.host,
                    draftPort = active.port.toString(),
                    draftPin = active.pin,
                    draftUseHttps = active.useHttps,
                    savedProfiles = stored.profiles,
                    customActions = stored.customActions,
                    autoPollEnabled = stored.autoPollEnabled,
                )
            }

            if (stored.autoPollEnabled && active.pin.isNotBlank()) {
                startPolling()
            } else if (active.pin.isBlank()) {
                _uiState.update {
                    it.copy(
                        connectionState = ConnectionState.AUTH_REQUIRED,
                        statusMessage = "Enter your PC server's IP and PIN in PC Setup to connect.",
                    )
                }
            }
        }
    }

    fun refreshPhoneNetwork() {
        val (wifiOk, phoneIp) = client.getPhoneNetworkInfo()
        _uiState.update {
            it.copy(
                phoneWifiConnected = wifiOk,
                phoneLocalIp = phoneIp,
            )
        }
    }

    fun updateDraftLabel(value: String) {
        _uiState.update { it.copy(draftLabel = value) }
    }

    fun updateDraftHost(value: String) {
        _uiState.update { it.copy(draftHost = value) }
    }

    fun updateDraftPort(value: String) {
        val digitsOnly = value.filter { it.isDigit() }.take(5)
        _uiState.update { it.copy(draftPort = digitsOnly) }
    }

    fun updateDraftPin(value: String) {
        _uiState.update { it.copy(draftPin = value) }
    }

    fun updateDraftUseHttps(value: Boolean) {
        _uiState.update { it.copy(draftUseHttps = value) }
    }

    fun setAutoPollEnabled(enabled: Boolean) {
        _uiState.update { it.copy(autoPollEnabled = enabled) }
        viewModelScope.launch {
            prefsRepo.setAutoPoll(enabled)
        }
        if (enabled) {
            startPolling()
        } else {
            stopPolling()
        }
    }

    fun connectAndVerify() {
        refreshPhoneNetwork()
        val profile = buildDraftProfile()
        client.clearSessionToken()

        viewModelScope.launch {
            persistDraftIntoActiveProfile(profile)
            _uiState.update {
                it.copy(
                    connectionState = ConnectionState.CONNECTING,
                    statusMessage = "Connecting to ${profile.baseUrl}…",
                    actionFeedback = null,
                )
            }
            pollOnce(profile, fetchPresets = true)
            if (_uiState.value.autoPollEnabled) {
                startPolling()
            }
        }
    }

    fun pausePolling() {
        stopPolling()
        _uiState.update {
            it.copy(
                connectionState = ConnectionState.DISCONNECTED,
                statusMessage = "Polling paused. Tap Connect & Verify to reconnect.",
            )
        }
    }

    fun selectProfile(profile: PcProfile) {
        client.clearSessionToken()
        _uiState.update {
            it.copy(
                activeProfileId = profile.id,
                draftLabel = profile.label,
                draftHost = profile.host,
                draftPort = profile.port.toString(),
                draftPin = profile.pin,
                draftUseHttps = profile.useHttps,
                pcStatus = null,
                serverActions = emptyList(),
                connectionState = if (profile.pin.isBlank()) ConnectionState.AUTH_REQUIRED else ConnectionState.CONNECTING,
                statusMessage = if (profile.pin.isBlank()) {
                    "Enter the server PIN for ${profile.label}."
                } else {
                    "Switching to ${profile.label}…"
                },
            )
        }
        viewModelScope.launch {
            prefsRepo.saveProfiles(profile.id, _uiState.value.savedProfiles)
            if (profile.pin.isNotBlank()) {
                pollOnce(profile, fetchPresets = true)
                if (_uiState.value.autoPollEnabled) startPolling()
            } else {
                stopPolling()
            }
        }
    }

    fun saveAsNewProfile() {
        val current = buildDraftProfile(idOverride = UUID.randomUUID().toString())
        val updated = _uiState.value.savedProfiles + current
        _uiState.update {
            it.copy(
                activeProfileId = current.id,
                savedProfiles = updated,
                actionFeedback = "Saved profile '${current.label}'.",
            )
        }
        viewModelScope.launch {
            prefsRepo.saveProfiles(current.id, updated)
        }
    }

    fun deleteProfile(profileId: String) {
        val currentList = _uiState.value.savedProfiles
        if (currentList.size <= 1) {
            _uiState.update { it.copy(actionFeedback = "At least one PC profile must remain.") }
            return
        }
        val remaining = currentList.filterNot { it.id == profileId }
        val nextActive = if (_uiState.value.activeProfileId == profileId) {
            remaining.first()
        } else {
            remaining.firstOrNull { it.id == _uiState.value.activeProfileId } ?: remaining.first()
        }
        _uiState.update {
            it.copy(
                savedProfiles = remaining,
                activeProfileId = nextActive.id,
                draftLabel = nextActive.label,
                draftHost = nextActive.host,
                draftPort = nextActive.port.toString(),
                draftPin = nextActive.pin,
                draftUseHttps = nextActive.useHttps,
            )
        }
        viewModelScope.launch {
            prefsRepo.saveProfiles(nextActive.id, remaining)
        }
    }

    fun requestPowerAction(action: PowerAction) {
        if (action.requiresConfirmation) {
            _uiState.update { it.copy(pendingPowerConfirmation = action) }
        } else {
            executePowerAction(action)
        }
    }

    fun dismissPowerConfirmation() {
        _uiState.update { it.copy(pendingPowerConfirmation = null) }
    }

    fun executePowerAction(action: PowerAction) {
        val profile = buildDraftProfile()
        _uiState.update {
            it.copy(
                pendingPowerConfirmation = null,
                isActionInProgress = true,
            )
        }
        viewModelScope.launch {
            when (val res = client.sendPower(profile, action)) {
                is ClientResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isActionInProgress = false,
                            actionFeedback = res.data,
                        )
                    }
                    pollOnce(profile, fetchPresets = false)
                }
                is ClientResult.AuthError -> handleAuthError(res.message)
                is ClientResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isActionInProgress = false,
                            actionFeedback = res.message,
                        )
                    }
                }
            }
        }
    }

    fun setVolume(level: Int) {
        sendMediaAction(MediaAction.SET, level)
    }

    fun sendMediaAction(action: MediaAction, level: Int? = null) {
        val profile = buildDraftProfile()
        viewModelScope.launch {
            when (val res = client.sendMedia(profile, action, level)) {
                is ClientResult.Success -> {
                    val (vol, muted) = res.data
                    _uiState.update { state ->
                        val updatedStatus = state.pcStatus?.copy(volume = vol, muted = muted)
                        state.copy(pcStatus = updatedStatus)
                    }
                }
                is ClientResult.AuthError -> handleAuthError(res.message)
                is ClientResult.Failure -> {
                    _uiState.update { it.copy(actionFeedback = res.message) }
                }
            }
        }
    }

    fun updateCommandInput(value: String) {
        _uiState.update { it.copy(commandInput = value) }
    }

    fun runCommand(commandOverride: String? = null) {
        val cmd = (commandOverride ?: _uiState.value.commandInput).trim()
        if (cmd.isEmpty()) return
        val profile = buildDraftProfile()

        _uiState.update { it.copy(isExecutingCommand = true) }
        viewModelScope.launch {
            when (val res = client.executeCommand(profile, cmd)) {
                is ClientResult.Success -> {
                    val entry = res.data
                    _uiState.update { state ->
                        val preview = if (entry.isSuccess) {
                            "Executed '${entry.command}' (${entry.durationMs}ms)"
                        } else {
                            "Command failed (exit ${entry.exitCode}): ${entry.stderr.ifBlank { "See Terminal log" }}"
                        }
                        state.copy(
                            isExecutingCommand = false,
                            commandLogs = (listOf(entry) + state.commandLogs).take(30),
                            actionFeedback = preview,
                        )
                    }
                }
                is ClientResult.AuthError -> handleAuthError(res.message)
                is ClientResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isExecutingCommand = false,
                            actionFeedback = res.message,
                        )
                    }
                }
            }
        }
    }

    fun clearCommandLog() {
        _uiState.update { it.copy(commandLogs = emptyList()) }
    }

    fun addCustomQuickAction(label: String, command: String) {
        val cleanLabel = label.trim()
        val cleanCmd = command.trim()
        if (cleanLabel.isEmpty() || cleanCmd.isEmpty()) return
        val updated = _uiState.value.customActions + QuickAction(
            id = UUID.randomUUID().toString(),
            label = cleanLabel,
            command = cleanCmd,
            isServerPreset = false,
        )
        _uiState.update {
            it.copy(
                customActions = updated,
                actionFeedback = "Added quick action '$cleanLabel'.",
            )
        }
        viewModelScope.launch {
            prefsRepo.saveCustomActions(updated)
        }
    }

    fun deleteCustomQuickAction(id: String) {
        val updated = _uiState.value.customActions.filterNot { it.id == id }
        _uiState.update { it.copy(customActions = updated) }
        viewModelScope.launch {
            prefsRepo.saveCustomActions(updated)
        }
    }

    fun dismissFeedback() {
        _uiState.update { it.copy(actionFeedback = null) }
    }

    fun buildCurrentTargetUrl(): String = buildDraftProfile().baseUrl

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            var fetchedPresets = false
            while (isActive) {
                val profile = buildDraftProfile()
                if (profile.host.isNotBlank() && profile.pin.isNotBlank()) {
                    val ok = pollOnce(profile, fetchPresets = !fetchedPresets)
                    if (ok) fetchedPresets = true
                }
                delay(3000L)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private suspend fun pollOnce(profile: PcProfile, fetchPresets: Boolean): Boolean {
        refreshPhoneNetwork()
        return when (val res = client.fetchStatus(profile)) {
            is ClientResult.Success -> {
                val status = res.data
                _uiState.update {
                    it.copy(
                        connectionState = ConnectionState.ONLINE,
                        pcStatus = status,
                        statusMessage = "Connected to ${status.hostname} (${status.os}) via ${profile.baseUrl}",
                    )
                }
                if (fetchPresets) {
                    val presets = client.fetchServerActions(profile)
                    if (presets is ClientResult.Success) {
                        _uiState.update { it.copy(serverActions = presets.data) }
                    }
                }
                true
            }
            is ClientResult.AuthError -> {
                handleAuthError(res.message)
                false
            }
            is ClientResult.Failure -> {
                _uiState.update {
                    it.copy(
                        connectionState = ConnectionState.ERROR,
                        pcStatus = null,
                        statusMessage = res.message,
                    )
                }
                false
            }
        }
    }

    private fun handleAuthError(message: String) {
        _uiState.update {
            it.copy(
                connectionState = ConnectionState.AUTH_REQUIRED,
                isActionInProgress = false,
                isExecutingCommand = false,
                pcStatus = null,
                statusMessage = message,
                actionFeedback = message,
            )
        }
    }

    private suspend fun persistDraftIntoActiveProfile(profile: PcProfile) {
        val currentList = _uiState.value.savedProfiles
        val replaced = if (currentList.any { it.id == profile.id }) {
            currentList.map { if (it.id == profile.id) profile else it }
        } else {
            currentList + profile
        }
        _uiState.update { it.copy(savedProfiles = replaced) }
        prefsRepo.saveProfiles(profile.id, replaced)
    }

    private fun buildDraftProfile(idOverride: String? = null): PcProfile {
        val s = _uiState.value
        val parsedPort = s.draftPort.toIntOrNull()?.coerceIn(1, 65535) ?: 8765
        return PcProfile(
            id = idOverride ?: s.activeProfileId,
            label = s.draftLabel.ifBlank { "Windows PC" },
            host = s.draftHost.trim(),
            port = parsedPort,
            pin = s.draftPin.trim(),
            useHttps = s.draftUseHttps,
        )
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
