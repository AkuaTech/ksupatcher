package org.akuatech.ksupatcher.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import org.akuatech.ksupatcher.ui.components.*
import org.akuatech.ksupatcher.viewmodel.InstallMethod
import org.akuatech.ksupatcher.viewmodel.KsuVariant
import org.akuatech.ksupatcher.viewmodel.OtaPhase
import org.akuatech.ksupatcher.viewmodel.RootStatus
import org.akuatech.ksupatcher.viewmodel.UiState

@Composable
fun PatchScreen(
    state: UiState,
    onVariantSelected: (KsuVariant) -> Unit,
    onMethodSelected: (InstallMethod) -> Unit,
    onPickBoot: (Uri) -> Unit,
    onPickModule: (Uri) -> Unit,
    onRunPatch: () -> Unit,
    onRunLkm: () -> Unit,
    onResetInstall: () -> Unit,
    onReboot: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val bootPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(onPickBoot) }
    )
    val modulePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(onPickModule) }
    )

    val patch = state.patchState
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Install",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        state.appUpdateInfo?.let { info ->
            if (info.isUpdateAvailable) {
                Spacer(modifier = Modifier.height(16.dp))
                UpdateNotificationCard(
                    onClick = onNavigateToSettings
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Step 1: Variant
        AppStepHeader(number = "01", title = "Variant")
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppActionTile(
                title = "KernelSU",
                drawableRes = org.akuatech.ksupatcher.R.drawable.ic_ksu_logo,
                selected = patch.variant == KsuVariant.KSU,
                onClick = { onVariantSelected(KsuVariant.KSU) },
                modifier = Modifier.weight(1f)
            )
            AppActionTile(
                title = "KernelSU-Next",
                drawableRes = org.akuatech.ksupatcher.R.drawable.ic_ksun_logo,
                selected = patch.variant == KsuVariant.KSUN,
                onClick = { onVariantSelected(KsuVariant.KSUN) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        StepConnector()
        Spacer(modifier = Modifier.height(8.dp))
        // Step 2: Method
        AppStepHeader(number = "02", title = "Method")
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppActionTile(
                title = "Patch",
                subtitle = "Boot Image",
                icon = Icons.Filled.Edit,
                selected = patch.method == InstallMethod.PATCH,
                onClick = { onMethodSelected(InstallMethod.PATCH) },
                modifier = Modifier.weight(1f)
            )
            AppActionTile(
                title = "Install",
                subtitle = "LKM (Current)",
                icon = Icons.Filled.CloudDownload,
                selected = patch.method == InstallMethod.LKM,
                onClick = { onMethodSelected(InstallMethod.LKM) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        StepConnector()
        Spacer(modifier = Modifier.height(8.dp))
        // Step 3: Action
        AppStepHeader(number = "03", title = "Action")
        Spacer(modifier = Modifier.height(12.dp))

        if (patch.method == InstallMethod.PATCH) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FileSelector(
                    label = "Boot Image",
                    fileName = patch.bootImageName,
                    placeholder = "Select boot.img",
                    onSelect = { bootPicker.launch(arrayOf("application/octet-stream", "image/*")) }
                )

                FileSelector(
                    label = "Kernel Module",
                    fileName = patch.moduleName,
                    placeholder = "Option: custom kernelsu.ko",
                    onSelect = { modulePicker.launch(arrayOf("application/octet-stream")) }
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "This will update the KernelSU module on your current boot slot using root access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                FileSelector(
                    label = "Kernel Module",
                    fileName = patch.moduleName,
                    placeholder = "Option: custom kernelsu.ko",
                    onSelect = { modulePicker.launch(arrayOf("application/octet-stream")) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val isOtaActive = state.otaState.phase !in listOf(OtaPhase.IDLE, OtaPhase.DONE, OtaPhase.ERROR, OtaPhase.NO_ROOT, OtaPhase.NO_OTA_PENDING)
        val showStartButton = !patch.isPatching && !isOtaActive
        val showResetButton = !patch.isPatching && (patch.status != null || patch.lastOutput != null)

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AnimatedVisibility(
                visible = patch.isPatching,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = patch.status ?: "Processing...",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            if (showStartButton) {
                Button(
                    onClick = { if (patch.method == InstallMethod.PATCH) onRunPatch() else onRunLkm() },
                    enabled = patch.method == InstallMethod.PATCH || state.rootStatus == org.akuatech.ksupatcher.viewmodel.RootStatus.GRANTED,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = if (patch.method == InstallMethod.PATCH) "Start Patching" else "Update LKM",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            AnimatedVisibility(
                visible = patch.rebootRequired,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Button(
                    onClick = onReboot,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(
                        "Reboot Now",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            if (showResetButton) {
                OutlinedButton(
                    onClick = onResetInstall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear / Reset")
                }
            }

            if (patch.method == InstallMethod.LKM && state.rootStatus != org.akuatech.ksupatcher.viewmodel.RootStatus.GRANTED) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Root permission is required.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (!patch.isPatching && !patch.status.isNullOrBlank()) {
                val isFailed = patch.status.contains("failed", ignoreCase = true) || patch.status.contains("error", ignoreCase = true)
                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isFailed)
                            MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        else
                            if (isDark) SuccessGreen.copy(alpha = 0.15f) else Color(0xFFE8F5E9)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = patch.status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isFailed) Color.White else (if (isDark) Color(0xFFA5D6A7) else Color(0xFF002208)),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedVisibility(
            visible = !patch.lastOutput.isNullOrBlank(),
            enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)),
            exit = shrinkVertically()
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .animateContentSize()
                ) {
                    Text(
                        "Terminal Output",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF9098A9)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    TerminalView(log = patch.lastOutput ?: "")
                }
            }
        }
    }
}

@Composable
fun VariantButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )

    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        contentColor = contentColor,
        shadowElevation = if (selected) 4.dp else 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
fun FileSelector(
    label: String,
    fileName: String?,
    placeholder: String,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = null
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = fileName ?: placeholder,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (fileName != null) FontWeight.Medium else FontWeight.Normal
                ),
                color = if (fileName != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}

@Composable
fun UpdateNotificationCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
        ),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Update is available, click to upgrade!",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
