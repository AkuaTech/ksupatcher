package org.akuatech.ksupatcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.net.Uri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.LaunchedEffect
import org.akuatech.ksupatcher.ui.components.DisclaimerDialog
import org.akuatech.ksupatcher.ui.components.InstallPermissionRationaleDialog
import org.akuatech.ksupatcher.ui.screens.FlashScreen
import org.akuatech.ksupatcher.ui.screens.OtaScreen
import org.akuatech.ksupatcher.ui.screens.PatchScreen
import org.akuatech.ksupatcher.ui.screens.SettingsScreen
import org.akuatech.ksupatcher.viewmodel.MainViewModel

@Immutable
private data class NavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun KsuPatcherNavGraph(
    viewModel: MainViewModel,
    pendingUri: Uri? = null,
    pendingRoute: String? = null
) {
    val navController = rememberNavController()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navItems = remember {
        listOf(
            NavItem("install", "Install", Icons.Filled.Build, Icons.Outlined.Build),
            NavItem("ota", "OTA", Icons.Filled.SystemUpdate, Icons.Outlined.SystemUpdate),
            NavItem("flash", "Flash", Icons.Filled.FlashOn, Icons.Outlined.FlashOn),
            NavItem("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val selectedIndex by remember(currentDestination) {
        derivedStateOf {
            navItems
                .indexOfFirst { item -> currentDestination?.hierarchy?.any { it.route == item.route } == true }
                .coerceAtLeast(0)
        }
    }

    LaunchedEffect(pendingUri, pendingRoute) {
        if (pendingUri == null && pendingRoute == null) return@LaunchedEffect
        if (pendingUri != null) {
            if (pendingRoute == "flash") viewModel.importFlashZip(pendingUri)
            else viewModel.importBootImage(pendingUri)
        }
        pendingRoute?.let { navController.navigate(it) { launchSingleTop = true } }
    }

    var flashExpanded by remember { mutableStateOf(false) }

    if (state.showDisclaimer) {
        DisclaimerDialog(onDismiss = viewModel::dismissDisclaimer)
    }

    if (state.showInstallPermissionRationale) {
        InstallPermissionRationaleDialog(
            onOpenSettings = viewModel::openInstallPermissionSettings,
            onDismiss = viewModel::dismissInstallPermissionRationale
        )
    }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            NavHost(
                navController = navController,
                startDestination = pendingRoute ?: "install",
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(tween(300, easing = FastOutSlowInEasing)) },
                exitTransition = { fadeOut(tween(300, easing = FastOutSlowInEasing)) },
                popEnterTransition = { fadeIn(tween(300, easing = FastOutSlowInEasing)) },
                popExitTransition = { fadeOut(tween(300, easing = FastOutSlowInEasing)) }
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
                        onToggleAllowShell = { viewModel.toggleAllowShell(it) },
                        onToggleEnableAdbd = { viewModel.toggleEnableAdbd(it) },
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
                composable("flash") {
                    FlashScreen(
                        state = state,
                        expanded = flashExpanded,
                        onExpandedChange = { flashExpanded = it },
                        onPickZip = { viewModel.importFlashZip(it) },
                        onRunFlash = { viewModel.runFlashKernel() },
                        onBackupBoot = { viewModel.backupBoot() },
                        onReset = { viewModel.resetFlash() },
                        onReboot = { viewModel.rebootNow() }
                    )
                }
                composable("ota") {
                    OtaScreen(
                        otaState = state.otaState,
                        rootStatus = state.rootStatus,
                        isCheckingRoot = state.isCheckingRoot,
                        variant = state.patchState.variant,
                        moduleName = state.patchState.moduleName,
                        allowShell = state.patchState.allowShell,
                        enableAdbd = state.patchState.enableAdbd,
                        onVariantSelected = { viewModel.selectVariant(it) },
                        onPickModule = { viewModel.importModule(it) },
                        onRunOta = { viewModel.runOtaPatch() },
                        onResetOta = { viewModel.resetOta() },
                        onReboot = { viewModel.rebootNow() },
                        onToggleAllowShell = { viewModel.toggleAllowShell(it) },
                        onToggleEnableAdbd = { viewModel.toggleEnableAdbd(it) }
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

            AnimatedVisibility(
                visible = !flashExpanded,
                enter = fadeIn(tween(300, easing = FastOutSlowInEasing)) + expandVertically(),
                exit = fadeOut(tween(200, easing = FastOutSlowInEasing)) + shrinkVertically(),
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .align(Alignment.BottomCenter)
            ) {
                FloatingNavBar(
                    items = navItems,
                    selectedIndex = selectedIndex,
                    onSelect = { index ->
                        navController.navigate(navItems[index].route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FloatingNavBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        tonalElevation = 2.dp,
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(36.dp))
            .wrapContentWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                key(item.route) {
                    BubbleItem(
                        item = item,
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BubbleItem(
    item: NavItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val haptic = LocalHapticFeedback.current
    val iconAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "icon"
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            )
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .animateContentSize(animationSpec = tween(250, easing = FastOutSlowInEasing)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.size(26.dp)) {
            Icon(
                imageVector = item.unselectedIcon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.graphicsLayer { alpha = 1f - iconAlpha }
            )
            Icon(
                imageVector = item.selectedIcon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.graphicsLayer { alpha = iconAlpha }
            )
        }
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(100, easing = FastOutSlowInEasing)) + expandHorizontally(animationSpec = tween(100, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(100, easing = FastOutSlowInEasing)) + shrinkHorizontally(animationSpec = tween(100, easing = FastOutSlowInEasing))
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = tint,
                maxLines = 1
            )
        }
    }
}
