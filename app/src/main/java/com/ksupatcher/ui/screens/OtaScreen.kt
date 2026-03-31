package com.ksupatcher.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ksupatcher.ui.components.AppStatusCard
import com.ksupatcher.viewmodel.OtaPhase
import com.ksupatcher.viewmodel.OtaState

@Composable
fun OtaScreen(
    otaState: OtaState,
    onRunOta: () -> Unit,
    onRunLkm: () -> Unit,
    onReset: () -> Unit,
    onReboot: () -> Unit
) {
    val scrollState = rememberScrollState()
    val isRunning = otaState.phase !in listOf(
        OtaPhase.IDLE, OtaPhase.DONE, OtaPhase.ERROR,
        OtaPhase.NO_ROOT, OtaPhase.NO_OTA_PENDING
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "OTA Root Patch",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

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
                        imageVector = Icons.Filled.Info,
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

        AnimatedVisibility(
            visible = otaState.phase != OtaPhase.IDLE,
            enter = expandVertically(spring(stiffness = Spring.StiffnessLow)),
            exit = shrinkVertically()
        ) {
            PhaseStatusCard(otaState)
        }

        if (!isRunning) {
            if (otaState.phase == OtaPhase.IDLE) {
                Button(
                    onClick = onRunOta,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        "Flash (OTA Slot)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Clear / Reset", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        AnimatedVisibility(visible = otaState.rebootRequired) {
            Button(
                onClick = onReboot,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    "Reboot Now",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onError
                )
            }
        }

        AnimatedVisibility(
            visible = otaState.log.isNotBlank(),
            enter = expandVertically(spring(stiffness = Spring.StiffnessLow)),
            exit = shrinkVertically()
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF14151A),
                    contentColor = Color(0xFFE7EAF3)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Log",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF9098A9),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SelectionContainer {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF090A0C))
                                .padding(12.dp)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = otaState.log.trimStart('\n'),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFE7EAF3)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhaseStatusCard(otaState: OtaState) {
    val (iconColor, icon, label) = when (otaState.phase) {
        OtaPhase.DONE ->
            Triple(MaterialTheme.colorScheme.tertiary, Icons.Filled.CheckCircle, "Complete")
        OtaPhase.ERROR ->
            Triple(MaterialTheme.colorScheme.error, Icons.Filled.Error, "Error")
        OtaPhase.NO_ROOT ->
            Triple(MaterialTheme.colorScheme.error, Icons.Filled.Error, "No Root Access")
        OtaPhase.NO_OTA_PENDING ->
            Triple(MaterialTheme.colorScheme.secondary, Icons.Filled.Warning, "No OTA Pending")
        else ->
            Triple(MaterialTheme.colorScheme.primary, Icons.Filled.Sync, phaseLabel(otaState.phase))
    }

    AppStatusCard(
        title = label,
        subtitle = if (otaState.currentSlot != null) "Current: ${otaState.currentSlot} → Target: ${otaState.nextSlot ?: "—"}" else "OTA Update Phase",
        icon = icon,
        iconColor = iconColor
    )
}

private fun phaseLabel(phase: OtaPhase): String = when (phase) {
    OtaPhase.IDLE           -> "Idle"
    OtaPhase.CHECKING_ROOT  -> "Checking root access…"
    OtaPhase.NO_ROOT        -> "Root access denied"
    OtaPhase.CHECKING_OTA_PROP -> "Checking OTA property…"
    OtaPhase.NO_OTA_PENDING -> "No OTA pending"
    OtaPhase.READING_SLOT   -> "Reading A/B slot…"
    OtaPhase.DUMPING_BOOT   -> "Dumping boot image…"
    OtaPhase.PATCHING       -> "Patching boot image…"
    OtaPhase.FLASHING       -> "Flashing patched image…"
    OtaPhase.DONE           -> "Done"
    OtaPhase.ERROR          -> "Error"
}
