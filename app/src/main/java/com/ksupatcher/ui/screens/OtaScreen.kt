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
    val isRunning = otaState.phase !in listOf(
        OtaPhase.IDLE, OtaPhase.DONE, OtaPhase.ERROR,
        OtaPhase.NO_ROOT, OtaPhase.NO_OTA_PENDING
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "OTA Root Patch",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp).padding(top = 2.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "How to use OTA Patch",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "1. Apply an OTA update but DO NOT reboot yet.\n" +
                        "2. Tap \"Patch OTA Slot\" — this patches the next boot slot.\n" +
                        "3. Reboot when prompted. Root is preserved!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
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
            if (otaState.phase == OtaPhase.IDLE ||
                otaState.phase == OtaPhase.DONE ||
                otaState.phase == OtaPhase.ERROR ||
                otaState.phase == OtaPhase.NO_ROOT ||
                otaState.phase == OtaPhase.NO_OTA_PENDING
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onRunOta,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            "Patch OTA Slot",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    OutlinedButton(
                        onClick = onRunLkm,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            "Update LKM (current slot)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    if (otaState.phase != OtaPhase.IDLE) {
                        TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                            Text("Clear / Reset", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        phaseLabel(otaState.phase),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
    val (containerColor, icon, label) = when (otaState.phase) {
        OtaPhase.DONE ->
            Triple(MaterialTheme.colorScheme.tertiaryContainer, Icons.Filled.CheckCircle, "Complete")
        OtaPhase.ERROR ->
            Triple(MaterialTheme.colorScheme.errorContainer, Icons.Filled.Error, "Error")
        OtaPhase.NO_ROOT ->
            Triple(MaterialTheme.colorScheme.errorContainer, Icons.Filled.Error, "No Root Access")
        OtaPhase.NO_OTA_PENDING ->
            Triple(MaterialTheme.colorScheme.secondaryContainer, Icons.Filled.Warning, "No OTA Pending")
        else ->
            Triple(MaterialTheme.colorScheme.surfaceVariant, Icons.Filled.CheckCircle, phaseLabel(otaState.phase))
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                if (otaState.currentSlot != null) {
                    Text(
                        "Current slot: ${otaState.currentSlot}  →  target: ${otaState.nextSlot ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
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
