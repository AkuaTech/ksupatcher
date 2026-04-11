package org.akuatech.ksupatcher.ui

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
import org.akuatech.ksupatcher.ui.screens.OtaScreen
import org.akuatech.ksupatcher.ui.screens.PatchScreen
import org.akuatech.ksupatcher.ui.screens.SettingsScreen
import org.akuatech.ksupatcher.viewmodel.MainViewModel

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
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navItems = listOf(
        NavItem("install", "Install") { Icon(Icons.Filled.Build, contentDescription = null) },
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
            startDestination = "install",
            modifier = Modifier.padding(padding)
        ) {
            composable("install") {
                PatchScreen(
                    state = state,
                    onVariantSelected = { viewModel.selectVariant(it) },
                    onMethodSelected = { viewModel.selectMethod(it) },
                    onPickBoot = { viewModel.importBootImage(it) },
                    onPickModule = { viewModel.importModule(it) },
                    onRunPatch = { viewModel.runPatch() },
                    onRunLkm = { viewModel.runLkmUpdate() },
                    onResetInstall = { viewModel.resetInstall() },
                    onReboot = { viewModel.rebootNow() },
                    onNavigateToSettings = {
                        navController.navigate("settings") {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                        viewModel.refreshVersion()
                    }
                )
            }
            composable("ota") {
                OtaScreen(
                    otaState = state.otaState,
                    rootStatus = state.rootStatus,
                    isCheckingRoot = state.isCheckingRoot,
                    variant = state.patchState.variant,
                    moduleName = state.patchState.moduleName,
                    onVariantSelected = { viewModel.selectVariant(it) },
                    onPickModule = { viewModel.importModule(it) },
                    onRunOta = { viewModel.runOtaPatch() },
                    onResetOta = { viewModel.resetOta() },
                    onReboot = { viewModel.rebootNow() }
                )
            }
            composable("settings") {
                SettingsScreen(
                    state = state,
                    onRefreshVersion = { viewModel.refreshVersion() },
                    onRefreshRoot = { viewModel.refreshRootStatus() },
                    onInstallAppUpdate = { viewModel.installAppUpdate() },
                    onUpdateKmi = { viewModel.updateKmi(it) },
                    onUpdateTheme = { viewModel.setThemeMode(it) }
                )
            }
        }
    }
}
