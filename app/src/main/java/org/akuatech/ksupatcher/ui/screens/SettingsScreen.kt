package org.akuatech.ksupatcher.ui.screens

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
import org.akuatech.ksupatcher.ui.components.RootStatusCard
import org.akuatech.ksupatcher.viewmodel.UiState
import org.akuatech.ksupatcher.util.DateUtils
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
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

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Version Info",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    FilledTonalButton(
                        onClick = onRefreshVersion,
                        enabled = !state.isCheckingVersion,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (state.isCheckingVersion) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking...")
                        } else {
                            Text("Check Now", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                state.lastVersionCheck?.let { lastCheck ->
                    Text(
                        text = "Last checked: ${DateUtils.formatToPlainEnglish(lastCheck)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                state.versionError?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                val info = state.appUpdateInfo
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

                    if (info.isUpdateAvailable) {
                        FilledTonalButton(
                            onClick = onInstallAppUpdate,
                            enabled = !state.isUpdatingApp && state.versionError == null,
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            if (state.isUpdatingApp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Updating...")
                            } else {
                                Text("Install Update", fontWeight = FontWeight.SemiBold)
                            }
                        }
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
                        modifier = Modifier.fillMaxWidth()
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

@Composable
fun InfoRow(label: String, value: String) {
    InfoRow(label = label, value = value, valueColor = MaterialTheme.colorScheme.onSurface)
}

@Composable
fun InfoRow(
    label: String, 
    value: String, 
    valueColor: Color, 
    onClick: (() -> Unit)? = null,
    copyable: Boolean = false
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
                Modifier.clickable {
                    clipboardManager.setText(AnnotatedString(value))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }.padding(horizontal = 4.dp, vertical = 2.dp)
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
                    onClick = {
                        clipboardManager.setText(AnnotatedString(value))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                OutlinedTextField(
                    value = selectedKmi,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )

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
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
