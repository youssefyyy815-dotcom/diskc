package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.DeskLinkUiState
import com.example.domain.PcProfile
import com.example.ui.components.ConsolePanel
import com.example.ui.components.ConsoleSectionHeader
import com.example.ui.components.DeskLinkScreenHeader
import com.example.ui.components.TelemetryRow
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.CautionAmberContainer
import com.example.ui.theme.ConsoleBackground
import com.example.ui.theme.ConsoleBorder
import com.example.ui.theme.ConsoleSurfaceElevated
import com.example.ui.theme.ConsoleSurfaceInput
import com.example.ui.theme.ConsoleTextMuted
import com.example.ui.theme.ConsoleTextPrimary
import com.example.ui.theme.ConsoleTextSecondary
import com.example.ui.theme.DiagnosticEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PhosphorCyan
import com.example.ui.theme.PhosphorCyanContainer

@Composable
fun SetupScreen(
    uiState: DeskLinkUiState,
    onLabelChanged: (String) -> Unit,
    onHostChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onPinChanged: (String) -> Unit,
    onUseHttpsChanged: (Boolean) -> Unit,
    onAutoPollChanged: (Boolean) -> Unit,
    onConnectClick: () -> Unit,
    onPausePollingClick: () -> Unit,
    onSelectProfile: (PcProfile) -> Unit,
    onSaveNewProfile: () -> Unit,
    onDeleteProfile: (String) -> Unit,
    onExportPythonScript: () -> Unit,
    onOpenWebDashboard: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    var showPin by remember { mutableStateOf(false) }
    var showScriptDialog by remember { mutableStateOf(false) }
    var copiedBanner by remember { mutableStateOf<String?>(null) }

    val scheme = if (uiState.draftUseHttps) "https" else "http"
    val previewUrl = "$scheme://${uiState.draftHost.trim().ifBlank { "192.168.1.100" }}:${uiState.draftPort.ifBlank { "8765" }}"

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
                    title = stringResource(R.string.nav_setup),
                    subtitle = stringResource(R.string.setup_screen_subtitle),
                    connectionState = uiState.connectionState,
                    latencyMs = uiState.pcStatus?.latencyMs,
                )
            }

            if (copiedBanner != null) {
                item {
                    ConsolePanel(
                        borderColor = DiagnosticEmerald.copy(alpha = 0.5f),
                        containerColor = ConsoleSurfaceElevated,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = copiedBanner.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = DiagnosticEmerald,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { copiedBanner = null }) {
                                Text("OK", color = PhosphorCyan)
                            }
                        }
                    }
                }
            }

            item {
                ConsolePanel {
                    ConsoleSectionHeader(title = stringResource(R.string.setup_section_target))

                    TelemetryRow(
                        label = "Device Network",
                        value = if (uiState.phoneWifiConnected) {
                            "LAN / Wi-Fi (${uiState.phoneLocalIp ?: "Active"})"
                        } else {
                            "No Wi-Fi / LAN link detected"
                        },
                        valueColor = if (uiState.phoneWifiConnected) DiagnosticEmerald else CautionAmber,
                    )

                    OutlinedTextField(
                        value = uiState.draftLabel,
                        onValueChange = onLabelChanged,
                        label = { Text("PC Name / Alias") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_label_input"),
                        colors = fieldColors(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = uiState.draftHost,
                            onValueChange = onHostChanged,
                            label = { Text("PC IP or Hostname") },
                            placeholder = { Text("192.168.1.42") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.8f)
                                .testTag("setup_host_input"),
                            colors = fieldColors(),
                        )
                        OutlinedTextField(
                            value = uiState.draftPort,
                            onValueChange = onPortChanged,
                            label = { Text("Port") },
                            placeholder = { Text("8765") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("setup_port_input"),
                            colors = fieldColors(),
                        )
                    }

                    OutlinedTextField(
                        value = uiState.draftPin,
                        onValueChange = onPinChanged,
                        label = { Text("Server PIN / Auth Secret") },
                        placeholder = { Text("6-digit PIN from PC terminal") },
                        singleLine = true,
                        visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { showPin = !showPin },
                                modifier = Modifier.testTag("toggle_pin_visibility_button"),
                            ) {
                                Icon(
                                    imageVector = if (showPin) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (showPin) "Hide PIN" else "Show PIN",
                                    tint = ConsoleTextSecondary,
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_pin_input"),
                        colors = fieldColors(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Use HTTPS (Reverse Proxy)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ConsoleTextPrimary,
                            )
                            Text(
                                text = "Keep OFF for direct LAN HTTP; turn ON for Caddy/Nginx TLS remote proxy.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ConsoleTextMuted,
                            )
                        }
                        Switch(
                            checked = uiState.draftUseHttps,
                            onCheckedChange = onUseHttpsChanged,
                            modifier = Modifier.testTag("setup_https_switch"),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Live 3s Status Heartbeat",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ConsoleTextPrimary,
                            )
                            Text(
                                text = "Continuously monitors online status, latency, and PC volume.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ConsoleTextMuted,
                            )
                        }
                        Switch(
                            checked = uiState.autoPollEnabled,
                            onCheckedChange = onAutoPollChanged,
                            modifier = Modifier.testTag("setup_autopoll_switch"),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onConnectClick,
                            modifier = Modifier
                                .weight(1.4f)
                                .testTag("setup_connect_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PhosphorCyan,
                                contentColor = ConsoleBackground,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Connect and verify PC",
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.setup_connect_button),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        OutlinedButton(
                            onClick = onPausePollingClick,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("setup_pause_button"),
                            border = BorderStroke(1.dp, ConsoleBorder),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Pause,
                                contentDescription = "Pause polling",
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pause")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { onOpenWebDashboard(previewUrl) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_web_dashboard_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open built-in browser dashboard",
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.setup_open_web))
                        }
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(previewUrl))
                                copiedBanner = "Copied Web Dashboard URL: $previewUrl"
                            },
                            modifier = Modifier.testTag("copy_web_url_button"),
                            border = BorderStroke(1.dp, ConsoleBorder),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy dashboard URL",
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy URL")
                        }
                    }
                }
            }

            item {
                ConsolePanel {
                    ConsoleSectionHeader(
                        title = stringResource(R.string.setup_section_profiles),
                        trailing = {
                            TextButton(
                                onClick = onSaveNewProfile,
                                modifier = Modifier.testTag("save_new_profile_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Save current connection as new profile",
                                    tint = PhosphorCyan,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.setup_save_profile), color = PhosphorCyan)
                            }
                        },
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.savedProfiles.forEach { profile ->
                            val isActive = profile.id == uiState.activeProfileId
                            Surface(
                                onClick = { onSelectProfile(profile) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_item_${profile.id}"),
                                shape = MaterialTheme.shapes.small,
                                color = if (isActive) PhosphorCyanContainer.copy(alpha = 0.45f) else ConsoleSurfaceElevated,
                                border = BorderStroke(
                                    1.dp,
                                    if (isActive) PhosphorCyan.copy(alpha = 0.6f) else ConsoleBorder,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = profile.label,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = ConsoleTextPrimary,
                                        )
                                        Text(
                                            text = profile.baseUrl,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isActive) PhosphorCyan else ConsoleTextSecondary,
                                        )
                                    }
                                    if (uiState.savedProfiles.size > 1) {
                                        IconButton(
                                            onClick = { onDeleteProfile(profile.id) },
                                            modifier = Modifier.testTag("delete_profile_${profile.id}"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteOutline,
                                                contentDescription = "Delete ${profile.label}",
                                                tint = ConsoleTextMuted,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                ConsolePanel {
                    ConsoleSectionHeader(title = stringResource(R.string.setup_section_server))
                    Text(
                        text = "Run pc_remote_server.py on your Windows PC (Python 3.8+, zero external pip packages required). It serves both this mobile app API and a responsive Web/PWA dashboard at http://<PC_IP>:8765.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ConsoleTextSecondary,
                    )
                    CodeSnippetBox("python pc_remote_server.py --pin 482910 --port 8765")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { showScriptDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("inspect_python_script_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Code,
                                contentDescription = "Inspect Python script",
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.setup_view_script))
                        }
                        FilledTonalButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(uiState.bundledServerScript))
                                copiedBanner = "Copied complete pc_remote_server.py to clipboard."
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("copy_python_script_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy Python script",
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.setup_copy_script))
                        }
                        Button(
                            onClick = onExportPythonScript,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_python_script_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PhosphorCyan,
                                contentColor = ConsoleBackground,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = "Export Python script file",
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.setup_export_script), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                ConsolePanel {
                    ConsoleSectionHeader(title = stringResource(R.string.setup_section_docs))

                    GuideSection(
                        title = "1. 100% Offline Local Wi-Fi (LAN) Binding",
                        body = "The Python server binds to 0.0.0.0:8765 so any phone or browser on the same router can connect with zero internet access. Find your Windows IPv4 via ipconfig and allow TCP port 8765 on Private networks:",
                        code = "New-NetFirewallRule -DisplayName \"DeskLink\" -Direction Inbound -Protocol TCP -LocalPort 8765 -Profile Private -Action Allow",
                    )

                    GuideSection(
                        title = "2. Optional Remote Access (Internet / Outside LAN)",
                        body = "Recommended: Install Tailscale or WireGuard on your Windows PC and phone, then connect using the PC's encrypted VPN IP (e.g. 100.x.y.z:8765). If using router Port Forwarding instead, reserve a static DHCP IP for your PC, forward TCP 8765, and place an HTTPS reverse proxy (Caddy/Nginx) in front of the server.",
                        code = null,
                    )
                }
            }

            item {
                ConsolePanel(
                    borderColor = CautionAmber.copy(alpha = 0.45f),
                    containerColor = CautionAmberContainer.copy(alpha = 0.3f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lan,
                            contentDescription = "User Responsibility Notice",
                            tint = CautionAmber,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.setup_disclaimer_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = CautionAmber,
                        )
                    }
                    Text(
                        text = stringResource(R.string.setup_disclaimer_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = ConsoleTextPrimary,
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }

    if (showScriptDialog) {
        AlertDialog(
            onDismissRequest = { showScriptDialog = false },
            containerColor = ConsoleSurfaceElevated,
            title = { Text("pc_remote_server.py", color = ConsoleTextPrimary) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .background(ConsoleBackground, MaterialTheme.shapes.small)
                        .border(1.dp, ConsoleBorder, MaterialTheme.shapes.small)
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = uiState.bundledServerScript,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = ConsoleTextPrimary,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(uiState.bundledServerScript))
                        copiedBanner = "Copied pc_remote_server.py to clipboard."
                        showScriptDialog = false
                    },
                ) {
                    Text("Copy All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showScriptDialog = false }) {
                    Text("Close", color = ConsoleTextSecondary)
                }
            },
        )
    }
}

@Composable
private fun GuideSection(
    title: String,
    body: String,
    code: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = ConsoleTextPrimary,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = ConsoleTextSecondary,
        )
        if (code != null) {
            CodeSnippetBox(code)
        }
    }
}

@Composable
private fun CodeSnippetBox(code: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleBackground, MaterialTheme.shapes.small)
            .border(1.dp, ConsoleBorder, MaterialTheme.shapes.small)
            .padding(10.dp)
            .horizontalScroll(rememberScrollState()),
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = JetBrainsMonoFontFamily,
            color = PhosphorCyan,
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = ConsoleSurfaceInput,
    unfocusedContainerColor = ConsoleSurfaceInput,
    focusedBorderColor = PhosphorCyan,
    unfocusedBorderColor = ConsoleBorder,
)
