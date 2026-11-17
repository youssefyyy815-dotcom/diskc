package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.ControlScreen
import com.example.ui.screens.SetupScreen
import com.example.ui.screens.TerminalScreen
import com.example.ui.theme.ConsoleBackground
import com.example.ui.theme.ConsoleBorder
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.ConsoleTextSecondary
import com.example.ui.theme.DeskLinkTheme
import com.example.ui.theme.PhosphorCyan
import com.example.ui.theme.PhosphorCyanContainer
import com.example.viewmodel.DeskLinkViewModel

private enum class DeskLinkTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
) {
    CONTROL(
        labelRes = R.string.nav_control,
        selectedIcon = Icons.Filled.DesktopWindows,
        unselectedIcon = Icons.Outlined.DesktopWindows,
        testTag = "nav_tab_control",
    ),
    TERMINAL(
        labelRes = R.string.nav_terminal,
        selectedIcon = Icons.Filled.Terminal,
        unselectedIcon = Icons.Outlined.Terminal,
        testTag = "nav_tab_terminal",
    ),
    SETUP(
        labelRes = R.string.nav_setup,
        selectedIcon = Icons.Filled.SettingsEthernet,
        unselectedIcon = Icons.Outlined.SettingsEthernet,
        testTag = "nav_tab_setup",
    ),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val darkScrim = ConsoleBackground.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(darkScrim),
            navigationBarStyle = SystemBarStyle.dark(darkScrim),
        )
        setContent {
            DeskLinkTheme {
                DeskLinkApp()
            }
        }
    }
}

@Composable
private fun DeskLinkApp(
    viewModel: DeskLinkViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var currentTabName by rememberSaveable { mutableStateOf(DeskLinkTab.CONTROL.name) }
    val currentTab = DeskLinkTab.valueOf(currentTabName)

    BackHandler(enabled = currentTab != DeskLinkTab.CONTROL) {
        currentTabName = DeskLinkTab.CONTROL.name
    }

    val exportScriptLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-python"),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(uiState.bundledServerScript.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                Toast.makeText(context, "Saved pc_remote_server.py", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(context, "Export failed: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val openWebDashboard: (String) -> Unit = { url ->
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, "Could not open browser for $url", Toast.LENGTH_SHORT).show()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsoleBackground),
    ) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ConsoleBackground,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = ConsoleSurface,
                        contentColor = PhosphorCyan,
                        windowInsets = WindowInsets.navigationBars,
                    ) {
                        DeskLinkTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTabName = tab.name },
                                modifier = Modifier.testTag(tab.testTag),
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes),
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PhosphorCyan,
                                    selectedTextColor = PhosphorCyan,
                                    indicatorColor = PhosphorCyanContainer,
                                    unselectedIconColor = ConsoleTextSecondary,
                                    unselectedTextColor = ConsoleTextSecondary,
                                ),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = ConsoleSurface,
                        contentColor = PhosphorCyan,
                    ) {
                        DeskLinkTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTabName = tab.name },
                                modifier = Modifier.testTag(tab.testTag),
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes),
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = PhosphorCyan,
                                    selectedTextColor = PhosphorCyan,
                                    indicatorColor = PhosphorCyanContainer,
                                    unselectedIconColor = ConsoleTextSecondary,
                                    unselectedTextColor = ConsoleTextSecondary,
                                ),
                            )
                        }
                    }
                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        color = ConsoleBorder,
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (currentTab) {
                        DeskLinkTab.CONTROL -> ControlScreen(
                            uiState = uiState,
                            onRequestPowerAction = viewModel::requestPowerAction,
                            onConfirmPowerAction = viewModel::executePowerAction,
                            onDismissPowerConfirmation = viewModel::dismissPowerConfirmation,
                            onSetVolume = viewModel::setVolume,
                            onMediaAction = { viewModel.sendMediaAction(it) },
                            onExecuteQuickAction = { viewModel.runCommand(it.command) },
                            onAddCustomAction = viewModel::addCustomQuickAction,
                            onDeleteCustomAction = viewModel::deleteCustomQuickAction,
                            onRefreshConnection = viewModel::connectAndVerify,
                            onNavigateToSetup = { currentTabName = DeskLinkTab.SETUP.name },
                            onDismissFeedback = viewModel::dismissFeedback,
                        )

                        DeskLinkTab.TERMINAL -> TerminalScreen(
                            uiState = uiState,
                            onCommandInputChanged = viewModel::updateCommandInput,
                            onRunCommand = viewModel::runCommand,
                            onClearLog = viewModel::clearCommandLog,
                        )

                        DeskLinkTab.SETUP -> SetupScreen(
                            uiState = uiState,
                            onLabelChanged = viewModel::updateDraftLabel,
                            onHostChanged = viewModel::updateDraftHost,
                            onPortChanged = viewModel::updateDraftPort,
                            onPinChanged = viewModel::updateDraftPin,
                            onUseHttpsChanged = viewModel::updateDraftUseHttps,
                            onAutoPollChanged = viewModel::setAutoPollEnabled,
                            onConnectClick = viewModel::connectAndVerify,
                            onPausePollingClick = viewModel::pausePolling,
                            onSelectProfile = viewModel::selectProfile,
                            onSaveNewProfile = viewModel::saveAsNewProfile,
                            onDeleteProfile = viewModel::deleteProfile,
                            onExportPythonScript = {
                                exportScriptLauncher.launch("pc_remote_server.py")
                            },
                            onOpenWebDashboard = openWebDashboard,
                        )
                    }
                }
            }
        }
    }
}
