package org.akuatech.ksupatcher.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import okhttp3.OkHttpClient
import okhttp3.Request

private val client = OkHttpClient()

val SuccessGreen = Color(0xFF4CAF50)
val SuccessGreenDark = Color(0xFF2E7D32)

@Composable
fun NetworkImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(url) {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bytes = response.body?.bytes()
                        if (bytes != null) {
                            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            bitmap = decoded.asImageBitmap()
                        }
                    }
                }
            } catch (e: Exception) {
                // Fail silently
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            )
        }
    }
}

@Composable
fun AppStatusCard(
    title: String,
    subtitle: String,
    icon: ImageVector = Icons.Default.Info,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    onAction: (() -> Unit)? = null,
    actionLabel: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(iconColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtitleColor
                )
            }
            if (onAction != null && actionLabel != null) {
                TextButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
fun AppActionTile(
    title: String,
    subtitle: String? = null,
    imageUrl: String? = null,
    drawableRes: Int? = null,
    icon: ImageVector? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = if (selected)
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        else
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = modifier.height(84.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        RoundedCornerShape(9.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (drawableRes != null) {
                    Image(
                        painter = painterResource(id = drawableRes),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (imageUrl != null) {
                    NetworkImage(
                        url = imageUrl,
                        modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                    )
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AppSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun AppStepHeader(
    number: String,
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun StepConnector(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(start = 13.dp)
            .width(2.dp)
            .height(24.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    )
}

@Composable
fun RootStatusCard(
    status: org.akuatech.ksupatcher.viewmodel.RootStatus,
    isChecking: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isGranted = status == org.akuatech.ksupatcher.viewmodel.RootStatus.GRANTED
    
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    
    val containerColor = if (isGranted) {
        if (isDark) SuccessGreen.copy(alpha = 0.15f) else Color(0xFFE8F5E9)
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    
    val contentColor = if (isGranted) {
        if (isDark) Color(0xFFA5D6A7) else Color(0xFF002208)
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    
    val accentColor = if (isGranted) {
        if (isDark) SuccessGreen else Color(0xFF2E7D32)
    } else {
        MaterialTheme.colorScheme.error
    }
    
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isGranted) "Root Status: Granted" else "Root Status: Not Granted",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = contentColor
                )
                Text(
                    text = if (isGranted) "ᕙ(  •̀ ᗜ •́  )ᕗ" else "Please grant root access",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }

            if (onRefresh != null) {
                FilledTonalButton(
                    onClick = { if (!isChecking) onRefresh() },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = contentColor.copy(alpha = 0.1f),
                        contentColor = contentColor
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = contentColor,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Check",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun RootRequiredBanner(modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Root access is required.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun TerminalView(
    log: String,
    modifier: Modifier = Modifier
) {
    if (log.isBlank()) return

    val annotatedLog = buildAnnotatedString {
        val lines = log.split('\n')
        lines.forEachIndexed { index, line ->
            if (line.trim().startsWith("$")) {
                withStyle(style = SpanStyle(color = Color(0xFF62A0EA), fontWeight = FontWeight.Bold)) {
                    append(line)
                }
            } else {
                append(line)
            }
            if (index < lines.size - 1) {
                append('\n')
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF090A0C))
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
    ) {
        SelectionContainer {
            Text(
                text = annotatedLog,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFE7EAF3)
            )
        }
    }
}
