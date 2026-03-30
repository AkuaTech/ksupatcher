package com.ksupatcher.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
        NavItem("patch", "Patch") { Icon(Icons.Filled.Build, contentDescription = null) },
        NavItem("settings", "Settings") { Icon(Icons.Filled.Settings, contentDescription = null) }
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
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
                        label = { Text(item.label) }
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
                    onPickBoot = viewModel::importBootImage,
                    onPickModule = viewModel::importModule,
                    onRunPatch = viewModel::runPatch
                )
            }
            composable("settings") {
                val state by viewModel.state.collectAsStateWithLifecycle()
                SettingsScreen(
                    state = state,
                    onRefreshVersion = viewModel::refreshVersion,
                    onVersionUrlChange = viewModel::updateVersionUrl,
                    onSaveVersionUrl = viewModel::saveVersionUrl,
                    onCheckLatestRelease = viewModel::checkLatestReleaseUpdate,
                    onRunOta = viewModel::runOtaPatch,
                    onRunLkm = viewModel::runLkmUpdate,
                    onResetOta = viewModel::resetOta,
                    onRebootNow = viewModel::rebootNow
                )
            }
        }
    }
}
