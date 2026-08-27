package org.akuatech.ksupatcher.ui.screens

import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.saveable.rememberSaveable
import org.akuatech.ksupatcher.ui.components.RootStatusCard
import org.akuatech.ksupatcher.ui.components.AllFilesAccessButton
import org.akuatech.ksupatcher.ui.components.allFilesAccessGranted
import org.akuatech.ksupatcher.viewmodel.UiState
import org.akuatech.ksupatcher.util.DateUtils
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.SourceCode
import me.rerere.hugeicons.stroke.Tick01
import androidx.compose.ui.res.painterResource
import org.akuatech.ksupatcher.R
import org.akuatech.ksupatcher.BuildConfig
import kotlinx.coroutines.launch
import java.time.Year
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
fun SettingsScreen(
    state: UiState,
    onRefreshVersion: () -> Unit,
    onRefreshRoot: () -> Unit,
    onInstallAppUpdate: () -> Unit,
    onUpdateKmi: (String) -> Unit,
    onUpdateTheme: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    var versionInfoExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onBackground
        )

        RootStatusCard(
            status = state.rootStatus,
            isChecking = state.isCheckingRoot,
            onRefresh = onRefreshRoot
        )

        KmiSelectionCard(
            selectedKmi = state.patchState.kmi,
            onUpdateKmi = onUpdateKmi
        )

        AppearanceCard(
            themeMode = state.themeMode,
            onUpdateTheme = onUpdateTheme
        )

        AdvancedCard()

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val info = state.appUpdateInfo
                val hasUpdate = info?.isUpdateAvailable == true

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { versionInfoExpanded = !versionInfoExpanded }
                            .padding(end = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Version Info",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val rotation by animateFloatAsState(if (versionInfoExpanded) 180f else 0f, label = "rotate")

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (hasUpdate) {
                                Text(
                                    text = "Update available ",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "(View Detail)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            } else if (state.lastVersionCheck != null) {
                                Text(
                                    text = "Last checked: ${DateUtils.formatToPlainEnglish(state.lastVersionCheck)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Icon(
                                imageVector = HugeIcons.ArrowDown01,
                                contentDescription = if (versionInfoExpanded) "Collapse" else "Expand",
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(rotation),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }

                    if (hasUpdate) {
                        FilledTonalButton(
                            onClick = onInstallAppUpdate,
                            enabled = !state.isUpdatingApp && state.versionError == null,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            if (state.isUpdatingApp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Updating...")
                            } else {
                                Text("Update", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else if (state.isCheckingVersion) {
                        FilledTonalButton(
                            onClick = onRefreshVersion,
                            enabled = false,
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking...")
                        }
                    }
                }

                AnimatedVisibility(visible = versionInfoExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.versionError?.let { err ->
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        if (info != null) {
                            val updateStatus = if (info.isUpdateAvailable) "Update available" else "Up to date"
                            val updateStatusColor = if (info.isUpdateAvailable) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            InfoRow(
                                label = "Current Build", 
                                value = info.currentBuildHash,
                                valueColor = MaterialTheme.colorScheme.onSurface,
                                copyable = true
                            )
                            InfoRow(
                                label = "Latest Release", 
                                value = info.latestReleaseHash,
                                valueColor = MaterialTheme.colorScheme.onSurface,
                                copyable = true
                            )
                            InfoRow(
                                label = "Status",
                                value = updateStatus,
                                valueColor = updateStatusColor
                            )
                            info.publishedAt?.let {
                                InfoRow(
                                    label = "Published", 
                                    value = DateUtils.formatToPlainEnglish(it),
                                    valueColor = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            info.releaseUrl?.let { url ->
                                val uriHandler = LocalUriHandler.current
                                InfoRow(
                                    label = "Release URL",
                                    value = "Open",
                                    valueColor = MaterialTheme.colorScheme.primary,
                                    onClick = { uriHandler.openUri(url) }
                                )
                            }

                            if (!info.notes.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Release Notes",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = info.notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (state.versionError == null) {
                            Text(
                                text = "No version information available.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (state.isUpdatingApp) {
                            LinearProgressIndicator(
                                progress = { state.appUpdateProgress / 100f },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            )
                        }

                        state.appUpdateStatus?.let { status ->
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        state.appUpdateError?.let { error ->
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        AboutCard(buildHash = state.appUpdateInfo?.currentBuildHash ?: BuildConfig.VERSION_NAME)
        
        Spacer(modifier = Modifier.height(120.dp))
    }
}

@Composable
fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null, copyable: Boolean = false) {
    InfoRow(label = label, value = value, valueColor = MaterialTheme.colorScheme.onSurface, onClick = onClick, copyable = copyable)
}

@Composable
fun InfoRow(
    label: String, 
    value: String, 
    valueColor: Color, 
    onClick: (() -> Unit)? = null,
    copyable: Boolean = false
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    fun copyValue() {
        coroutineScope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value)))
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            val modifier = if (copyable) {
                Modifier.clickable { copyValue() }.padding(horizontal = 4.dp, vertical = 2.dp)
            } else if (onClick != null) {
                Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 2.dp)
            } else {
                Modifier
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (onClick != null) TextDecoration.Underline else null,
                modifier = modifier
            )
            if (copyable) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = { copyValue() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = HugeIcons.Copy01,
                        contentDescription = "Copy",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KmiSelectionCard(
    selectedKmi: String,
    onUpdateKmi: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val kmis = org.akuatech.ksupatcher.data.UpdateConfig.supportedKmis

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Kernel Module Interface (KMI)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Select your device's KMI version for compatible patching.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedKmi,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = HugeIcons.ArrowDown01,
                            contentDescription = "Select KMI",
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(if (expanded) 180f else 0f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.exposedDropdownSize()
                ) {
                    kmis.forEach { kmi ->
                        DropdownMenuItem(
                            text = { Text(kmi) },
                            onClick = {
                                onUpdateKmi(kmi)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceCard(
    themeMode: String,
    onUpdateTheme: (String) -> Unit
) {
    val options = listOf(
        "auto"  to "Auto",
        "light" to "Light",
        "dark"  to "Dark"
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Choose how the app looks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = themeMode == value,
                        onClick = { onUpdateTheme(value) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = options.size
                        ),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            activeContentColor = MaterialTheme.colorScheme.primary,
                            activeBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            inactiveBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (themeMode == value) FontWeight.SemiBold else FontWeight.Normal
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdvancedCard() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(allFilesAccessGranted(context)) }
    LifecycleResumeEffect(Unit) {
        granted = allFilesAccessGranted(context)
        onPauseOrDispose { }
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Advanced",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "All files access",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Only needed for ADB boot patching. In-app picker skips this.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                if (granted) {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = HugeIcons.Tick01,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Granted", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    AllFilesAccessButton()
                }
            }
        }
    }
}

@Composable
fun AboutCard(buildHash: String) {
    val uriHandler = LocalUriHandler.current

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_logo),
                        contentDescription = "Ksupatcher Logo",
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "KSUPatcher",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Text(
                    text = "Build $buildHash",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Text(
                text = "Developed by AkuaTech",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            FilledTonalButton(
                onClick = { uriHandler.openUri("https://github.com/akuatech/ksupatcher") },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(top = 8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = HugeIcons.SourceCode,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("GitHub Repository", style = MaterialTheme.typography.labelLarge)
            }

            Text(
                text = "© ${Year.now().value} AkuaTech • All rights reserved",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
