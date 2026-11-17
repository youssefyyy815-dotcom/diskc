package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.ConnectionState
import com.example.domain.DeskLinkUiState
import com.example.domain.MediaAction
import com.example.domain.PowerAction
import com.example.domain.QuickAction
import com.example.ui.components.ConsolePanel
import com.example.ui.components.ConsoleSectionHeader
import com.example.ui.components.DeskLinkScreenHeader
import com.example.ui.components.PowerActionTile
import com.example.ui.components.TelemetryRow
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.AlertCrimsonContainer
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.CautionAmberContainer
import com.example.ui.theme.ConsoleBackground
import com.example.ui.theme.ConsoleBorder
import com.example.ui.theme.ConsoleBorderBright
import com.example.ui.theme.ConsoleSurfaceElevated
import com.example.ui.theme.ConsoleTextMuted
import com.example.ui.theme.ConsoleTextPrimary
import com.example.ui.theme.ConsoleTextSecondary
import com.example.ui.theme.DiagnosticEmerald
import com.example.ui.theme.DiagnosticEmeraldContainer
import com.example.ui.theme.PhosphorCyan
import com.example.ui.theme.PhosphorCyanContainer

@Composable
fun ControlScreen(
    uiState: DeskLinkUiState,
    onRequestPowerAction: (PowerAction) -> Unit,
    onConfirmPowerAction: (PowerAction) -> Unit,
    onDismissPowerConfirmation: () -> Unit,
    onSetVolume: (Int) -> Unit,
    onMediaAction: (MediaAction) -> Unit,
    onExecuteQuickAction: (QuickAction) -> Unit,
    onAddCustomAction: (String, String) -> Unit,
    onDeleteCustomAction: (String) -> Unit,
    onRefreshConnection: () -> Unit,
    onNavigateToSetup: () -> Unit,
    onDismissFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddActionDialog by remember { mutableStateOf(false) }

    val pcStatus = uiState.pcStatus
    val isOnline = uiState.connectionState == ConnectionState.ONLINE && pcStatus != null

    var sliderVolume by remember { mutableFloatStateOf(50f) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    LaunchedEffect(pcStatus?.volume) {
        if (!isDraggingSlider && pcStatus != null) {
            sliderVolume = pcStatus.volume.toFloat()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DeskLinkScreenHeader(
                    title = stringResource(R.string.console_header_title),
                    subtitle = stringResource(R.string.control_screen_subtitle),
                    connectionState = uiState.connectionState,
                    latencyMs = pcStatus?.latencyMs,
                )
            }

            item {
                AnimatedVisibility(visible = uiState.actionFeedback != null) {
                    val feedback = uiState.actionFeedback.orEmpty()
                    ConsolePanel(
                        borderColor = PhosphorCyan.copy(alpha = 0.5f),
                        containerColor = PhosphorCyanContainer.copy(alpha = 0.4f),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = feedback,
                                style = MaterialTheme.typography.bodySmall,
                                color = ConsoleTextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = onDismissFeedback,
                                modifier = Modifier.testTag("dismiss_feedback_button"),
                            ) {
                                Text("OK", color = PhosphorCyan)
                            }
                        }
                    }
                }
            }

            item {
                ConnectionSummaryCard(
                    uiState = uiState,
                    onRefreshConnection = onRefreshConnection,
                    onNavigateToSetup = onNavigateToSetup,
                )
            }

            item {
                ConsolePanel {
                    ConsoleSectionHeader(title = stringResource(R.string.control_section_power))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PowerActionTile(
                            label = stringResource(R.string.action_lock),
                            subtitle = "Lock Windows session",
                            icon = Icons.Filled.Lock,
                            accentColor = PhosphorCyan,
                            containerColor = PhosphorCyanContainer.copy(alpha = 0.45f),
                            enabled = isOnline && !uiState.isActionInProgress,
                            testTag = "power_lock_button",
                            onClick = { onRequestPowerAction(PowerAction.LOCK) },
                            modifier = Modifier.weight(1f),
                        )
                        PowerActionTile(
                            label = stringResource(R.string.action_sleep),
                            subtitle = "Suspend to RAM",
                            icon = Icons.Filled.Bedtime,
                            accentColor = CautionAmber,
                            containerColor = CautionAmberContainer.copy(alpha = 0.45f),
                            enabled = isOnline && !uiState.isActionInProgress,
                            testTag = "power_sleep_button",
                            onClick = { onRequestPowerAction(PowerAction.SLEEP) },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PowerActionTile(
                            label = stringResource(R.string.action_restart),
                            subtitle = "Reboot Windows (5s)",
                            icon = Icons.Filled.RestartAlt,
                            accentColor = CautionAmber,
                            containerColor = CautionAmberContainer.copy(alpha = 0.45f),
                            enabled = isOnline && !uiState.isActionInProgress,
                            testTag = "power_restart_button",
                            onClick = { onRequestPowerAction(PowerAction.RESTART) },
                            modifier = Modifier.weight(1f),
                        )
                        PowerActionTile(
                            label = stringResource(R.string.action_shutdown),
                            subtitle = "Power off PC (5s)",
                            icon = Icons.Filled.PowerSettingsNew,
                            accentColor = AlertCrimson,
                            containerColor = AlertCrimsonContainer.copy(alpha = 0.55f),
                            enabled = isOnline && !uiState.isActionInProgress,
                            testTag = "power_shutdown_button",
                            onClick = { onRequestPowerAction(PowerAction.SHUTDOWN) },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    AnimatedVisibility(visible = isOnline && pcStatus?.pendingPowerAction != null) {
                        Button(
                            onClick = { onRequestPowerAction(PowerAction.ABORT) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("power_abort_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DiagnosticEmerald,
                                contentColor = ConsoleBackground,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Cancel,
                                contentDescription = "Abort pending shutdown or restart",
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.action_abort_power),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            item {
                ConsolePanel {
                    val volText = when {
                        pcStatus == null -> "--%"
                        pcStatus.muted -> "${sliderVolume.toInt()}% (MUTED)"
                        else -> "${sliderVolume.toInt()}%"
                    }
                    ConsoleSectionHeader(
                        title = stringResource(R.string.control_section_media),
                        trailing = {
                            Text(
                                text = volText,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (pcStatus?.muted == true) CautionAmber else PhosphorCyan,
                            )
                        },
                    )

                    Slider(
                        value = sliderVolume,
                        onValueChange = {
                            isDraggingSlider = true
                            sliderVolume = it
                        },
                        onValueChangeFinished = {
                            isDraggingSlider = false
                            onSetVolume(sliderVolume.toInt())
                        },
                        valueRange = 0f..100f,
                        enabled = isOnline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("volume_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = PhosphorCyan,
                            activeTrackColor = PhosphorCyan,
                            inactiveTrackColor = ConsoleSurfaceElevated,
                        ),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { onMediaAction(MediaAction.DOWN) },
                            enabled = isOnline,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("volume_down_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                                contentDescription = "Volume Down",
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("-5")
                        }
                        FilledTonalButton(
                            onClick = { onMediaAction(MediaAction.MUTE) },
                            enabled = isOnline,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("volume_mute_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = "Mute or Unmute",
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (pcStatus?.muted == true) "Unmute" else "Mute")
                        }
                        FilledTonalButton(
                            onClick = { onMediaAction(MediaAction.UP) },
                            enabled = isOnline,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("volume_up_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Volume Up",
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+5")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onMediaAction(MediaAction.PREV) },
                            enabled = isOnline,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("media_prev_button"),
                            border = BorderStroke(1.dp, ConsoleBorderBright),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = "Previous Track",
                            )
                        }
                        Button(
                            onClick = { onMediaAction(MediaAction.PLAY_PAUSE) },
                            enabled = isOnline,
                            modifier = Modifier
                                .weight(1.4f)
                                .testTag("media_play_pause_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PhosphorCyanContainer,
                                contentColor = PhosphorCyan,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play or Pause Media",
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play / Pause")
                        }
                        OutlinedButton(
                            onClick = { onMediaAction(MediaAction.NEXT) },
                            enabled = isOnline,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("media_next_button"),
                            border = BorderStroke(1.dp, ConsoleBorderBright),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "Next Track",
                            )
                        }
                    }
                }
            }

            item {
                ConsolePanel {
                    ConsoleSectionHeader(
                        title = stringResource(R.string.control_section_actions),
                        trailing = {
                            TextButton(
                                onClick = { showAddActionDialog = true },
                                modifier = Modifier.testTag("add_quick_action_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Add custom quick action",
                                    tint = PhosphorCyan,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.action_add_custom), color = PhosphorCyan)
                            }
                        },
                    )

                    val allActions = remember(uiState.customActions, uiState.serverActions) {
                        val customCmds = uiState.customActions.map { it.command.lowercase() }.toSet()
                        uiState.customActions + uiState.serverActions.filterNot {
                            it.command.lowercase() in customCmds
                        }
                    }

                    if (allActions.isEmpty()) {
                        Text(
                            text = "No quick actions configured yet. Tap Add Action to create a one-tap command trigger.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ConsoleTextMuted,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            allActions.forEach { action ->
                                QuickActionRow(
                                    action = action,
                                    enabled = isOnline && !uiState.isExecutingCommand,
                                    onTrigger = { onExecuteQuickAction(action) },
                                    onDelete = if (!action.isServerPreset) {
                                        { onDeleteCustomAction(action.id) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }

    val pendingPower = uiState.pendingPowerConfirmation
    if (pendingPower != null) {
        AlertDialog(
            onDismissRequest = onDismissPowerConfirmation,
            containerColor = ConsoleSurfaceElevated,
            title = {
                Text(
                    text = "Confirm ${pendingPower.label}",
                    color = ConsoleTextPrimary,
                )
            },
            text = {
                Text(
                    text = when (pendingPower) {
                        PowerAction.SHUTDOWN -> "This will shut down ${pcStatus?.hostname ?: "your PC"} in 5 seconds. You can tap Abort immediately afterward if needed."
                        PowerAction.RESTART -> "This will restart ${pcStatus?.hostname ?: "your PC"} in 5 seconds."
                        PowerAction.SLEEP -> "This will suspend ${pcStatus?.hostname ?: "your PC"} to Sleep mode and drop the active Wi-Fi connection."
                        else -> "Execute ${pendingPower.label} on the connected Windows PC?"
                    },
                    color = ConsoleTextSecondary,
                )
            },
            confirmButton = {
                Button(
                    onClick = { onConfirmPowerAction(pendingPower) },
                    modifier = Modifier.testTag("confirm_power_dialog_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pendingPower == PowerAction.SHUTDOWN) AlertCrimson else CautionAmber,
                        contentColor = ConsoleBackground,
                    ),
                ) {
                    Text(pendingPower.label, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissPowerConfirmation,
                    modifier = Modifier.testTag("cancel_power_dialog_button"),
                ) {
                    Text("Cancel", color = ConsoleTextSecondary)
                }
            },
        )
    }

    if (showAddActionDialog) {
        AddQuickActionDialog(
            onDismiss = { showAddActionDialog = false },
            onSave = { label, cmd ->
                onAddCustomAction(label, cmd)
                showAddActionDialog = false
            },
        )
    }
}

@Composable
private fun ConnectionSummaryCard(
    uiState: DeskLinkUiState,
    onRefreshConnection: () -> Unit,
    onNavigateToSetup: () -> Unit,
) {
    val pc = uiState.pcStatus
    val isOnline = uiState.connectionState == ConnectionState.ONLINE && pc != null
    val border = when (uiState.connectionState) {
        ConnectionState.ONLINE -> DiagnosticEmerald.copy(alpha = 0.45f)
        ConnectionState.ERROR -> AlertCrimson.copy(alpha = 0.45f)
        ConnectionState.AUTH_REQUIRED -> CautionAmber.copy(alpha = 0.45f)
        else -> ConsoleBorder
    }

    ConsolePanel(borderColor = border) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isOnline) DiagnosticEmeraldContainer else ConsoleSurfaceElevated,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.DesktopWindows,
                            contentDescription = "Target Windows PC",
                            tint = if (isOnline) DiagnosticEmerald else ConsoleTextSecondary,
                        )
                    }
                }
                Column {
                    Text(
                        text = pc?.hostname ?: uiState.draftLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = ConsoleTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${uiState.draftHost}:${uiState.draftPort}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PhosphorCyan,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onRefreshConnection,
                    modifier = Modifier.testTag("refresh_connection_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Reconnect and refresh PC status",
                        tint = PhosphorCyan,
                    )
                }
                IconButton(
                    onClick = onNavigateToSetup,
                    modifier = Modifier.testTag("open_setup_shortcut_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Open PC connection setup",
                        tint = ConsoleTextSecondary,
                    )
                }
            }
        }

        if (isOnline && pc != null) {
            TelemetryRow(label = "Operating System", value = "${pc.os} (${pc.architecture})")
            TelemetryRow(
                label = "System Uptime",
                value = formatUptime(pc.uptimeSeconds),
                valueColor = DiagnosticEmerald,
            )
            TelemetryRow(
                label = "Shell Mode",
                value = if (pc.unrestrictedShell) "Unrestricted" else "Safe Allowlist",
                valueColor = if (pc.unrestrictedShell) CautionAmber else PhosphorCyan,
            )
        } else {
            Text(
                text = uiState.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (uiState.connectionState == ConnectionState.ERROR) AlertCrimson else ConsoleTextSecondary,
            )
            OutlinedButton(
                onClick = onNavigateToSetup,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("configure_pc_cta"),
                border = BorderStroke(1.dp, PhosphorCyan.copy(alpha = 0.5f)),
            ) {
                Text("Configure IP, PIN & Python Server", color = PhosphorCyan)
            }
        }
    }
}

@Composable
private fun QuickActionRow(
    action: QuickAction,
    enabled: Boolean,
    onTrigger: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Surface(
        onClick = onTrigger,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quick_action_${action.id}"),
        shape = MaterialTheme.shapes.small,
        color = ConsoleSurfaceElevated,
        border = BorderStroke(1.dp, ConsoleBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Filled.Terminal,
                    contentDescription = action.label,
                    tint = if (enabled) PhosphorCyan else ConsoleTextMuted,
                    modifier = Modifier.size(18.dp),
                )
                Column {
                    Text(
                        text = action.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (enabled) ConsoleTextPrimary else ConsoleTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = action.command,
                        style = MaterialTheme.typography.bodySmall,
                        color = ConsoleTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_quick_action_${action.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete ${action.label}",
                        tint = ConsoleTextMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddQuickActionDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleSurfaceElevated,
        title = { Text("New Quick Action", color = ConsoleTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Button Label") },
                    placeholder = { Text("e.g. Check Disk Space") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_action_label_input"),
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("Safe Shell Command") },
                    placeholder = { Text("e.g. ipconfig /flushdns") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_action_command_input"),
                )
                Text(
                    text = "Runs through the PC server's Safe Shell Guard (e.g. ipconfig, whoami, tasklist, systeminfo, netstat, ping).",
                    style = MaterialTheme.typography.bodySmall,
                    color = ConsoleTextMuted,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(label, command) },
                enabled = label.isNotBlank() && command.isNotBlank(),
                modifier = Modifier.testTag("save_new_action_button"),
            ) {
                Text("Save Action")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ConsoleTextSecondary)
            }
        },
    )
}

private fun formatUptime(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    return if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m ${seconds % 60}s"
}
