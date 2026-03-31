package com.ksupatcher.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ksupatcher.ui.screens.OtaScreen
import com.ksupatcher.ui.screens.PatchScreen
import com.ksupatcher.ui.screens.SettingsScreen
import com.ksupatcher.viewmodel.MainViewModel

private data class NavItem(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit
)

@Composable
fun KsuPatcherNavGraph(
    viewModel: MainViewModel
) {
    val navController = rememberNavController()
    val navItems = listOf(
        NavItem("patch", "Install") { Icon(Icons.Filled.Build, contentDescription = null) },
        NavItem("ota", "OTA") { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
        NavItem("settings", "Settings") { Icon(Icons.Filled.Settings, contentDescription = null) }
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                navItems.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = item.icon,
                        label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "patch",
            modifier = Modifier.padding(padding)
        ) {
            composable("patch") {
                val state by viewModel.state.collectAsStateWithLifecycle()
                PatchScreen(
                    state = state,
                    onVariantSelected = viewModel::selectVariant,
                    onMethodSelected = viewModel::selectMethod,
                    onPickBoot = viewModel::importBootImage,
                    onPickModule = viewModel::importModule,
                    onRunPatch = viewModel::runPatch,
                    onRunLkm = viewModel::runLkmUpdate
                )
            }
            composable("ota") {
                val state by viewModel.state.collectAsStateWithLifecycle()
                OtaScreen(
                    otaState = state.otaState,
                    onRunOta = viewModel::runOtaPatch,
                    onRunLkm = viewModel::runLkmUpdate,
                    onReset = viewModel::resetOta,
                    onReboot = viewModel::rebootNow
                )
            }
            composable("settings") {
                val state by viewModel.state.collectAsStateWithLifecycle()
                SettingsScreen(
                    state = state,
                    onRefreshVersion = viewModel::refreshVersion,
                    onVersionUrlChange = viewModel::updateVersionUrl,
                    onSaveVersionUrl = viewModel::saveVersionUrl,
                    onCheckLatestRelease = viewModel::checkLatestReleaseUpdate
                )
            }
        }
    }
}
