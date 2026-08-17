package org.akuatech.ksupatcher.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.akuatech.ksupatcher.ui.components.RootRequiredBanner
import org.akuatech.ksupatcher.ui.components.TerminalView
import org.akuatech.ksupatcher.util.defaultLogFileName
import org.akuatech.ksupatcher.util.writeLogToUri
import org.akuatech.ksupatcher.viewmodel.RootStatus
import org.akuatech.ksupatcher.viewmodel.UiState

@Composable
fun FlashScreen(
    state: UiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPickZip: (Uri) -> Unit,
    onRunFlash: () -> Unit,
    onBackupBoot: () -> Unit,
    onReset: () -> Unit,
    onReboot: () -> Unit
) {
    val flash = state.flashState
    val scrollState = rememberScrollState()
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
    val zipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(onPickZip) }
    )

    LaunchedEffect(flash.isFlashing) {
        if (flash.isFlashing) onExpandedChange(true)
    }

    BackHandler(enabled = expanded) {
        onExpandedChange(false)
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState)
        ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Flash Kernel",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Flash an AK3 zip or back up your current boot.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        FileSelector(
            label = "AK3 ZIP",
            fileName = flash.zipName,
            placeholder = "Select zip",
            onSelect = {
                zipPicker.launch(
                    arrayOf(
                        "application/zip",
                        "application/x-zip-compressed"
                    )
                )
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (state.rootStatus != RootStatus.GRANTED && !flash.isFlashing) {
            RootRequiredBanner()
            Spacer(modifier = Modifier.height(16.dp))
        }

        AnimatedVisibility(
            visible = flash.isFlashing,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        text = flash.status ?: "Processing...",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        if (!flash.isFlashing) {
            Button(
                onClick = onRunFlash,
                enabled = !flash.zipPath.isNullOrBlank() && state.rootStatus == RootStatus.GRANTED,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Filled.FlashOn, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Flash", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onBackupBoot,
                enabled = state.rootStatus == RootStatus.GRANTED,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Filled.SaveAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Backup Current Boot")
            }

            if (flash.rebootRequired) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onReboot,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reboot", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        if (!flash.isFlashing && !flash.status.isNullOrBlank()) {
            val isFailed = flash.status.contains("failed", ignoreCase = true) || flash.status.contains("denied", ignoreCase = true) || flash.status.contains("error", ignoreCase = true)
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isFailed) MaterialTheme.colorScheme.error.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = flash.status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isFailed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = !flash.lastOutput.isNullOrBlank(),
            enter = expandVertically(spring(stiffness = Spring.StiffnessLow)),
            exit = shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .animateContentSize()
                    ) {
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
                            Row {
                                TextButton(
                                    onClick = {
                                        pendingLogExport = (flash.rawLog ?: flash.lastOutput).orEmpty()
                                        logExportLauncher.launch(defaultLogFileName("flash"))
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save logs", fontSize = 13.sp)
                                }
                                IconButton(
                                    onClick = { onExpandedChange(true) },
                                    enabled = !flash.lastOutput.isNullOrBlank()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Fullscreen,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TerminalView(
                            log = flash.lastOutput ?: "",
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }

        if (!flash.isFlashing && (flash.status != null || flash.lastOutput != null)) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Clear / Reset")
            }
            Spacer(modifier = Modifier.height(88.dp))
        }

        Spacer(modifier = Modifier.height(120.dp))
        }
    }

    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(tween(250, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(250, easing = FastOutSlowInEasing))
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                    IconButton(onClick = { onExpandedChange(false) }) {
                        Icon(Icons.Filled.FullscreenExit, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TerminalView(
                    log = flash.lastOutput ?: "",
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    fillHeight = true
                )
            }
        }
    }
}
