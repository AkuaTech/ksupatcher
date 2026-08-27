package org.akuatech.ksupatcher.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.AlertCircle
import me.rerere.hugeicons.stroke.CheckmarkCircle01
import me.rerere.hugeicons.stroke.InformationCircle
import me.rerere.hugeicons.stroke.RefreshCw
import me.rerere.hugeicons.stroke.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.akuatech.ksupatcher.ui.components.*
import org.akuatech.ksupatcher.util.defaultLogFileName
import org.akuatech.ksupatcher.util.writeLogToUri
import org.akuatech.ksupatcher.viewmodel.KsuVariant
import org.akuatech.ksupatcher.viewmodel.OtaPhase
import org.akuatech.ksupatcher.viewmodel.OtaState
import org.akuatech.ksupatcher.viewmodel.RootStatus

@Composable
fun OtaScreen(
    otaState: OtaState,
    rootStatus: RootStatus,
    isCheckingRoot: Boolean,
    variant: KsuVariant,
    moduleName: String?,
    allowShell: Boolean,
    enableAdbd: Boolean,
    onVariantSelected: (KsuVariant) -> Unit,
    onPickModule: (Uri) -> Unit,
    onRunOta: () -> Unit,
    onResetOta: () -> Unit,
    onReboot: () -> Unit,
    onToggleAllowShell: (Boolean) -> Unit,
    onToggleEnableAdbd: (Boolean) -> Unit
) {
    val modulePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(onPickModule) }
    )
    val context = LocalContext.current
    var pendingLogExport by remember { mutableStateOf("") }
    val logExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri ->
            if (uri != null) {
                writeLogToUri(context, uri, pendingLogExport)
                    .onSuccess {
                        Toast.makeText(context, "Logs saved", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { error ->
                        Toast.makeText(context, "Failed to save logs: ${error.message}", Toast.LENGTH_LONG).show()
                    }
            }
        }
    )

    val scrollState = rememberScrollState()
    val isRunning = otaState.phase !in listOf(
        OtaPhase.IDLE, OtaPhase.DONE, OtaPhase.ERROR,
        OtaPhase.NO_ROOT, OtaPhase.NO_OTA_PENDING
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Root OTA",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = HugeIcons.InformationCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Root Persistence Guide",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "1. Apply OTA system update (do not reboot)\n" +
                        "2. Tap Flash (OTA) below to patch the inactive slot\n" +
                        "3. Reboot to preserve root on the new system",
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified, // fallback
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppStepHeader(number = "01", title = "Variant")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppActionTile(
                    title = "KernelSU",
                    drawableRes = org.akuatech.ksupatcher.R.drawable.ic_ksu_logo,
                    selected = variant == KsuVariant.KSU,
                    onClick = { onVariantSelected(KsuVariant.KSU) },
                    modifier = Modifier.weight(1f)
                )
                AppActionTile(
                    title = "KernelSU-Next",
                    drawableRes = org.akuatech.ksupatcher.R.drawable.ic_ksun_logo,
                    selected = variant == KsuVariant.KSUN,
                    onClick = { onVariantSelected(KsuVariant.KSUN) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        StepConnector()
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppStepHeader(number = "02", title = "Action")
            FileSelector(
                label = "Kernel Module",
                fileName = moduleName,
                placeholder = "Optional: custom kernelsu.ko",
                onSelect = { modulePicker.launch(arrayOf("application/octet-stream")) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        StepConnector()
        Spacer(modifier = Modifier.height(8.dp))

        AdvancedOptionsSection(
            allowShell = allowShell,
            enableAdbd = enableAdbd,
            onToggleAllowShell = onToggleAllowShell,
            onToggleEnableAdbd = onToggleEnableAdbd
        )

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedVisibility(
            visible = otaState.phase != OtaPhase.IDLE,
            enter = expandVertically(spring(stiffness = Spring.StiffnessLow)),
            exit = shrinkVertically()
        ) {
            Column {
                PhaseStatusCard(otaState)
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        if (!isRunning) {
            if (otaState.phase == OtaPhase.IDLE) {
                if (!isCheckingRoot && rootStatus != RootStatus.GRANTED) {
                    RootRequiredBanner()
                    Spacer(modifier = Modifier.height(12.dp))
                }
                val (otaPress, otaScale) = rememberPressScale()
                Button(
                    onClick = onRunOta,
                    enabled = rootStatus == RootStatus.GRANTED,
                    interactionSource = otaPress,
                    modifier = Modifier.fillMaxWidth().height(56.dp).then(otaScale),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        "Flash OTA",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AnimatedVisibility(
                        visible = otaState.rebootRequired,
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

                    OutlinedButton(
                        onClick = onResetOta,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Clear / Reset", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        AnimatedVisibility(
            visible = otaState.log.isNotBlank(),
            enter = expandVertically(spring(stiffness = Spring.StiffnessLow)),
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
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF1A1D23),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "$",
                                        color = Color(0xFF62A0EA),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Terminal Output",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = {
                                pendingLogExport = otaState.log.trimStart('\n')
                                logExportLauncher.launch(defaultLogFileName("ota"))
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = HugeIcons.Save,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save logs", fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TerminalView(
                        log = otaState.log.trimStart('\n'),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(120.dp))
    }
}

@Composable
private fun PhaseStatusCard(otaState: OtaState) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val (iconColor, icon, label) = when (otaState.phase) {
        OtaPhase.DONE ->
            Triple(if (isDark) SuccessGreen else Color(0xFF2E7D32), HugeIcons.CheckmarkCircle01, "Complete")
        OtaPhase.ERROR ->
            Triple(MaterialTheme.colorScheme.error, HugeIcons.AlertCircle, "Error")
        OtaPhase.NO_ROOT ->
            Triple(MaterialTheme.colorScheme.error, HugeIcons.AlertCircle, "No Root Access")
        OtaPhase.NO_OTA_PENDING ->
            Triple(MaterialTheme.colorScheme.secondary, HugeIcons.Alert01, "No OTA Pending")
        else ->
            Triple(MaterialTheme.colorScheme.primary, HugeIcons.RefreshCw, phaseLabel(otaState.phase))
    }

    val containerColor = if (otaState.phase == OtaPhase.DONE) {
        if (isDark) SuccessGreen.copy(alpha = 0.15f) else Color(0xFFE8F5E9)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    val titleColor = if (otaState.phase == OtaPhase.DONE) {
        if (isDark) Color(0xFFA5D6A7) else Color(0xFF002208)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val subtitleColor = if (otaState.phase == OtaPhase.DONE) {
        titleColor.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    AppStatusCard(
        title = label,
        subtitle = if (otaState.currentSlot != null) "Current: ${otaState.currentSlot} → Target: ${otaState.nextSlot ?: "—"}" else "OTA Update Phase",
        icon = icon,
        iconColor = iconColor,
        titleColor = titleColor,
        subtitleColor = subtitleColor,
        containerColor = containerColor
    )
}

private fun phaseLabel(phase: OtaPhase): String = when (phase) {
    OtaPhase.IDLE           -> "Idle"
    OtaPhase.CHECKING_ROOT  -> "Checking root access…"
    OtaPhase.NO_ROOT        -> "Root access denied"
    OtaPhase.CHECKING_OTA_PROP -> "Checking OTA property…"
    OtaPhase.NO_OTA_PENDING -> "No OTA pending"
    OtaPhase.READING_SLOT   -> "Reading A/B slot…"
    OtaPhase.PATCHING       -> "Patching & flashing…"
    OtaPhase.DONE           -> "Done"
    OtaPhase.ERROR          -> "Error"
}
