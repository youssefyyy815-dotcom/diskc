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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.CommandLogEntry
import com.example.domain.ConnectionState
import com.example.domain.DeskLinkUiState
import com.example.ui.components.ConsolePanel
import com.example.ui.components.ConsoleSectionHeader
import com.example.ui.components.DeskLinkScreenHeader
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.AlertCrimsonContainer
import com.example.ui.theme.ConsoleBackground
import com.example.ui.theme.ConsoleBorder
import com.example.ui.theme.ConsoleSurfaceElevated
import com.example.ui.theme.ConsoleSurfaceInput
import com.example.ui.theme.ConsoleTextMuted
import com.example.ui.theme.ConsoleTextPrimary
import com.example.ui.theme.ConsoleTextSecondary
import com.example.ui.theme.DiagnosticEmerald
import com.example.ui.theme.DiagnosticEmeraldContainer
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PhosphorCyan

private val PRESET_SAFE_COMMANDS = listOf(
    "whoami",
    "ipconfig",
    "systeminfo",
    "tasklist",
    "netstat -ano",
    "ping -n 2 127.0.0.1",
    "powercfg /a",
)

@Composable
fun TerminalScreen(
    uiState: DeskLinkUiState,
    onCommandInputChanged: (String) -> Unit,
    onRunCommand: (String?) -> Unit,
    onClearLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOnline = uiState.connectionState == ConnectionState.ONLINE
    val clipboard = LocalClipboardManager.current

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
                    title = stringResource(R.string.nav_terminal),
                    subtitle = stringResource(R.string.terminal_screen_subtitle),
                    connectionState = uiState.connectionState,
                    latencyMs = uiState.pcStatus?.latencyMs,
                )
            }

            item {
                ConsolePanel {
                    ConsoleSectionHeader(title = stringResource(R.string.terminal_section_presets))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PRESET_SAFE_COMMANDS.forEach { cmd ->
                            AssistChip(
                                onClick = {
                                    onCommandInputChanged(cmd)
                                    if (isOnline && !uiState.isExecutingCommand) {
                                        onRunCommand(cmd)
                                    }
                                },
                                label = {
                                    Text(
                                        text = cmd,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = ConsoleSurfaceElevated,
                                    labelColor = PhosphorCyan,
                                ),
                                border = BorderStroke(1.dp, ConsoleBorder),
                                modifier = Modifier.testTag("preset_chip_${cmd.replace(" ", "_")}"),
                            )
                        }
                    }
                }
            }

            item {
                ConsolePanel {
                    ConsoleSectionHeader(title = stringResource(R.string.terminal_section_console))
                    OutlinedTextField(
                        value = uiState.commandInput,
                        onValueChange = onCommandInputChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("terminal_command_input"),
                        placeholder = {
                            Text(
                                text = "ipconfig /all",
                                fontFamily = JetBrainsMonoFontFamily,
                                color = ConsoleTextMuted,
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            color = ConsoleTextPrimary,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (isOnline && !uiState.isExecutingCommand) {
                                    onRunCommand(null)
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ConsoleSurfaceInput,
                            unfocusedContainerColor = ConsoleSurfaceInput,
                            focusedBorderColor = PhosphorCyan,
                            unfocusedBorderColor = ConsoleBorder,
                        ),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isOnline) {
                                if (uiState.pcStatus?.unrestrictedShell == true) {
                                    "Unrestricted Shell active on PC"
                                } else {
                                    "Safe Shell Guard active (allowlisted)"
                                }
                            } else {
                                "Connect to your Windows PC first"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = ConsoleTextMuted,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onRunCommand(null) },
                            enabled = isOnline && uiState.commandInput.isNotBlank() && !uiState.isExecutingCommand,
                            modifier = Modifier.testTag("terminal_execute_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PhosphorCyan,
                                contentColor = ConsoleBackground,
                            ),
                        ) {
                            if (uiState.isExecutingCommand) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = ConsoleBackground,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Execute command",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.terminal_run_button),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            item {
                ConsoleSectionHeader(
                    title = stringResource(R.string.terminal_section_history),
                    trailing = {
                        if (uiState.commandLogs.isNotEmpty()) {
                            TextButton(
                                onClick = onClearLog,
                                modifier = Modifier.testTag("terminal_clear_log_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteSweep,
                                    contentDescription = "Clear command log",
                                    tint = ConsoleTextSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.terminal_clear_log),
                                    color = ConsoleTextSecondary,
                                )
                            }
                        }
                    },
                )
            }

            if (uiState.commandLogs.isEmpty()) {
                item {
                    ConsolePanel {
                        Text(
                            text = "No commands executed in this session yet. Tap a preset chip or type a safe command above to run it on your PC.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ConsoleTextMuted,
                        )
                    }
                }
            } else {
                items(uiState.commandLogs, key = { it.id }) { entry ->
                    CommandLogCard(
                        entry = entry,
                        onCopy = {
                            val combined = buildString {
                                appendLine("> ${entry.command}")
                                if (entry.stdout.isNotBlank()) appendLine(entry.stdout)
                                if (entry.stderr.isNotBlank()) appendLine("[STDERR] ${entry.stderr}")
                            }
                            clipboard.setText(AnnotatedString(combined))
                        },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun CommandLogCard(
    entry: CommandLogEntry,
    onCopy: () -> Unit,
) {
    val badgeColor = if (entry.isSuccess) DiagnosticEmerald else AlertCrimson
    val badgeBg = if (entry.isSuccess) DiagnosticEmeraldContainer else AlertCrimsonContainer

    ConsolePanel(
        borderColor = badgeColor.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "> ${entry.command}",
                    style = MaterialTheme.typography.titleSmall,
                    color = PhosphorCyan,
                )
                Text(
                    text = "${entry.timestampLabel} • ${entry.durationMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = ConsoleTextMuted,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .background(badgeBg, MaterialTheme.shapes.small)
                        .border(1.dp, badgeColor.copy(alpha = 0.45f), MaterialTheme.shapes.small)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "EXIT ${entry.exitCode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy command output",
                        tint = ConsoleTextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        val outputText = buildString {
            if (entry.stdout.isNotBlank()) append(entry.stdout.trimEnd())
            if (entry.stderr.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("[STDERR]\n")
                append(entry.stderr.trimEnd())
            }
            if (isEmpty()) append("(no output)")
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleBackground, MaterialTheme.shapes.small)
                .border(1.dp, ConsoleBorder, MaterialTheme.shapes.small)
                .padding(10.dp),
        ) {
            Text(
                text = outputText,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = JetBrainsMonoFontFamily,
                color = ConsoleTextPrimary,
            )
        }
    }
}
