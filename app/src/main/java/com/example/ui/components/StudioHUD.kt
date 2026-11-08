package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlin.math.log10
import com.example.core.model.PlatformEndpoint
import com.example.core.streaming.StreamSource
import androidx.compose.material.icons.filled.*
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.GlassDark
import java.util.Locale
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import com.example.ui.MainViewModel
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.example.core.model.AspectRatioMode
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast

@Composable
fun SparklineWaveformGraph(
    modifier: Modifier = Modifier,
    data: List<Float>,
    color: Color = CyberCyan
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wavePulse")
    val waveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveAlpha"
    )

    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas
        val path = Path()
        val width = size.width
        val height = size.height
        val maxVal = data.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        
        val xStep = width / (data.size - 1)
        
        data.forEachIndexed { index, value ->
            val x = index * xStep
            val y = height - (value / maxVal) * height
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        drawPath(
            path = path,
            color = color.copy(alpha = waveAlpha),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
fun ProTelemetryHeader(
    isLive: Boolean,
    isPaused: Boolean = false,
    isRecording: Boolean = false,
    streamTime: Long,
    bitrate: Int,
    fps: Int,
    droppedFramesPercent: Float = 0.0f,
    cpuTemp: String = "36°C",
    batteryPercent: Int = 88,
    endpoints: List<PlatformEndpoint>,
    onToggleRecord: () -> Unit = {},
    onSettingsClick: () -> Unit,
    onDestinationsClick: () -> Unit,
    onTelemetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hudPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val statusColor = when {
        isPaused -> Color(0xFFF59E0B)
        isLive && fps > 0 && bitrate > 0 -> CrimsonRed
        isLive -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    val statusText = when {
        isPaused -> "PAUSED"
        isLive && fps > 0 && bitrate > 0 -> "LIVE ${formatTime(streamTime)}"
        isLive -> "CONNECTING"
        else -> "READY"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        color = Color(0xEE090D16),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Status Badge
            Surface(
                onClick = onTelemetryClick,
                color = statusColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.6f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                            .then(if (isLive || isPaused) Modifier.blur(2.dp * pulseAlpha) else Modifier)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Center Metrics (Scrollable if screen is compact)
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(horizontal = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Bitrate
                TelemetryChip(
                    icon = Icons.Default.FlashOn,
                    iconTint = CyberCyan,
                    text = "${bitrate}k",
                    valueColor = CyberCyan
                )

                // FPS
                TelemetryChip(
                    icon = null,
                    iconTint = Color.Transparent,
                    text = "${if (fps > 0) fps else 30} FPS",
                    valueColor = Color.White.copy(alpha = 0.9f)
                )

                // Dropped frames
                TelemetryChip(
                    icon = null,
                    iconTint = Color.Transparent,
                    text = "DROP ${String.format(Locale.US, "%.1f", droppedFramesPercent)}%",
                    valueColor = if (droppedFramesPercent > 2f) CrimsonRed else Color.White.copy(alpha = 0.6f)
                )

                // Battery / CPU
                TelemetryChip(
                    icon = Icons.Default.BatteryChargingFull,
                    iconTint = Color(0xFF10B981),
                    text = "$batteryPercent% • $cpuTemp",
                    valueColor = Color.White.copy(alpha = 0.6f)
                )

                // Destinations preview
                val activeEndpoints = endpoints.filter { it.isEnabled }
                if (activeEndpoints.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable(onClick = onDestinationsClick)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        activeEndpoints.take(3).forEach { ep ->
                            PlatformBadge(ep)
                        }
                        Text("28ms", color = Color(0xFF10B981), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Right Quick Actions (REC + Settings)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Quick REC
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) CrimsonRed.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                        .border(1.dp, if (isRecording) CrimsonRed else Color.White.copy(alpha = 0.15f), CircleShape)
                        .clickable(onClick = onToggleRecord),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FiberManualRecord,
                        contentDescription = "REC",
                        tint = if (isRecording) CrimsonRed else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Settings Gear
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        .clickable(onClick = onSettingsClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Master Hub",
                        tint = CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DynamicIslandHUD(
    isLive: Boolean,
    isPaused: Boolean = false,
    isRecording: Boolean = false,
    streamTime: Long,
    bitrate: Int,
    fps: Int,
    endpoints: List<PlatformEndpoint>,
    onToggleRecord: () -> Unit = {},
    onSettingsClick: () -> Unit,
    onDestinationsClick: () -> Unit,
    onTelemetryClick: () -> Unit
) {
    ProTelemetryHeader(
        isLive = isLive,
        isPaused = isPaused,
        isRecording = isRecording,
        streamTime = streamTime,
        bitrate = bitrate,
        fps = fps,
        endpoints = endpoints,
        onToggleRecord = onToggleRecord,
        onSettingsClick = onSettingsClick,
        onDestinationsClick = onDestinationsClick,
        onTelemetryClick = onTelemetryClick
    )
}

@Composable
fun TelemetryChip(
    icon: ImageVector?,
    iconTint: Color,
    text: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(11.dp))
            Spacer(modifier = Modifier.width(3.dp))
        }
        Text(text, color = valueColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MultiSceneSelectorBar(
    scenes: List<MainViewModel.SceneItem>,
    activeSceneId: String,
    onSelectScene: (String) -> Unit,
    onAddScene: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp),
        color = Color(0xEE090D16),
        shape = RoundedCornerShape(23.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            scenes.forEach { scene ->
                val isSelected = scene.id == activeSceneId
                Surface(
                    onClick = { onSelectScene(scene.id) },
                    color = if (isSelected) CyberCyan.copy(alpha = 0.2f) else Color.Transparent,
                    shape = RoundedCornerShape(19.dp),
                    border = if (isSelected) BorderStroke(1.dp, CyberCyan) else BorderStroke(1.dp, Color.Transparent)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(scene.icon, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = scene.title,
                            color = if (isSelected) CyberCyan else Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                        )
                    }
                }
            }

            // + Add Scene button
            Surface(
                onClick = onAddScene,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(19.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, null, tint = CyberCyan, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Scene", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun FramelessLiveChatOverlay(
    messages: List<MainViewModel.ChatMessage>,
    onPinComment: (MainViewModel.ChatMessage) -> Unit,
    onOpenChatHub: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (messages.isEmpty()) {
        Surface(
            onClick = onOpenChatHub,
            color = Color(0xB3090D16),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = modifier
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(CyberCyan))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "💬 Chat Connected • Waiting for messages...",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    } else {
        val recentMessages = messages.takeLast(5)
        Column(
            modifier = modifier.fillMaxWidth(0.85f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            recentMessages.forEachIndexed { index, msg ->
                val alpha = (0.45f + (0.55f * (index.toFloat() / (recentMessages.size - 1).coerceAtLeast(1)))).coerceIn(0.45f, 1f)

                Surface(
                    onClick = { onPinComment(msg) },
                    color = Color(0xEE090D16).copy(alpha = alpha),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f * alpha)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            val platformColor = when (msg.platform.lowercase()) {
                                "twitch" -> Color(0xFF9146FF)
                                "youtube" -> Color(0xFFFF0000)
                                "kick" -> Color(0xFF53FC18)
                                else -> CyberCyan
                            }
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(platformColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    msg.platform.take(1).uppercase(),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = msg.author,
                                color = CyberCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = msg.message,
                                color = Color.White,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onPinComment(msg) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Pin",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SceneBroadcastOverlay(
    sceneType: MainViewModel.SceneType,
    startingSoonCountdown: Int,
    startingSoonTitle: String,
    modifier: Modifier = Modifier
) {
    when (sceneType) {
        MainViewModel.SceneType.STARTING_SOON -> {
            val infiniteTransition = rememberInfiniteTransition(label = "startingPulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.95f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseScale"
            )

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color(0xE6070A12)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                            .clip(CircleShape)
                            .background(Color(0x2200F2FF))
                            .border(2.dp, CyberCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⏳", fontSize = 48.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    val minutes = startingSoonCountdown / 60
                    val seconds = startingSoonCountdown % 60
                    Text(
                        text = String.format(Locale.US, "%02d:%02d", minutes, seconds),
                        color = Color.White,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = startingSoonTitle.uppercase(),
                        color = CyberCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "GET READY • GOING LIVE SHORTLY",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
        MainViewModel.SceneType.BE_RIGHT_BACK -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color(0xE6070A12)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color(0x33F59E0B))
                            .border(2.dp, Color(0xFFF59E0B), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⏸️", fontSize = 44.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "BE RIGHT BACK",
                        color = Color(0xFFF59E0B),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "STREAM PAUSED • WILL RESUME IN A MOMENT",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
        else -> {}
    }
}

@Composable
fun AddSceneDialog(
    onAddScene: (title: String, icon: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("🎮") }
    val icons = listOf("🎮", "🏆", "💻", "🎙️", "⭐", "🔥", "⚽", "🎵")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF090D16),
        title = { Text("CREATE NEW LIVE SCENE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "SCENE NAME (e.g. Gaming / Highlights)",
                    modifier = Modifier.fillMaxWidth()
                )

                Text("CHOOSE ICON", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    icons.forEach { icon ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (selectedIcon == icon) CyberCyan.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f))
                                .border(1.dp, if (selectedIcon == icon) CyberCyan else Color.Transparent, CircleShape)
                                .clickable { selectedIcon = icon },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(icon, fontSize = 18.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onAddScene(title, selectedIcon)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
            ) {
                Text("ADD SCENE", color = Color.Black, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun PlatformBadge(endpoint: PlatformEndpoint) {
    val color = when (endpoint.id.lowercase()) {
        "youtube" -> Color(0xFFFF0000)
        "twitch" -> Color(0xFF9146FF)
        "facebook" -> Color(0xFF1877F2)
        "tiktok" -> Color(0xFF000000)
        "kick" -> Color(0xFF53FC18)
        "telegram" -> Color(0xFF24A1DE)
        "twitter" -> Color(0xFF1DA1F2)
        "instagram" -> Color(0xFFE1306C)
        else -> CyberCyan
    }
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
fun StatPill(icon: ImageVector, text: String) {
    Surface(
        color = Color(0xBF1E2330),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun GlassCircularButton(
    icon: ImageVector,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(GlassDark)
            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}


@Composable
fun Tier1SourceAndCanvasBar(
    currentSource: StreamSource,
    onSelectSource: (StreamSource) -> Unit,
    currentAspect: AspectRatioMode,
    onSelectAspect: (AspectRatioMode) -> Unit,
    canvasScaleMode: MainViewModel.CanvasScaleMode,
    onToggleScaleMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sources = listOf(
        Pair(StreamSource.CAMERA, "🎥 CAM"),
        Pair(StreamSource.M3U8, "🔗 M3U8 URL"),
        Pair(StreamSource.SCREEN, "📱 SCREEN"),
        Pair(StreamSource.AUDIO, "🎵 AUDIO")
    )

    val aspects = listOf(
        Pair(AspectRatioMode.PORTRAIT_9_16, "9:16"),
        Pair(AspectRatioMode.LANDSCAPE_16_9, "16:9"),
        Pair(AspectRatioMode.SQUARE_1_1, "1:1")
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp),
        color = Color(0xF2090D16),
        shape = RoundedCornerShape(21.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 3.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Source Switcher Pills
            sources.forEach { (src, label) ->
                val isSelected = currentSource == src
                Surface(
                    onClick = { onSelectSource(src) },
                    color = if (isSelected) CyberCyan.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) CyberCyan else Color.White.copy(alpha = 0.08f)
                    )
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) CyberCyan else Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Sleek Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(Color.White.copy(alpha = 0.2f))
            )

            // Aspect Ratio Pills
            aspects.forEach { (aspect, label) ->
                val isSelected = currentAspect == aspect
                Surface(
                    onClick = { onSelectAspect(aspect) },
                    color = if (isSelected) Color(0x336366F1) else Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFF818CF8) else Color.White.copy(alpha = 0.08f)
                    )
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color(0xFFA5B4FC) else Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            // Fit / Fill Toggle Pill
            Surface(
                onClick = onToggleScaleMode,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.FitScreen,
                        null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = canvasScaleMode.name,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun MicAudioLevelButton(
    isMicOn: Boolean,
    audioLevel: Float,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val pulseRing by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseRing"
    )

    val activeLevel = (audioLevel * 3f + (if (isMicOn) pulseRing * 0.25f else 0f)).coerceIn(0f, 1f)
    val ringColor = when {
        !isMicOn -> CrimsonRed
        activeLevel > 0.65f -> Color(0xFFF59E0B) // Amber
        else -> Color(0xFF10B981) // Emerald Green
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
                .border(2.dp, ringColor.copy(alpha = if (isMicOn) (0.5f + 0.5f * activeLevel) else 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isMicOn) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = "Mic",
                tint = if (isMicOn) Color.White else CrimsonRed,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            if (isMicOn) "MIC ON" else "MUTED",
            color = if (isMicOn) Color(0xFF10B981) else CrimsonRed,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun DockIconButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$badgeCount", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CenterBroadcastControl(
    isLive: Boolean,
    isPaused: Boolean,
    onGoLive: () -> Unit,
    onTogglePause: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "livePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    if (!isLive) {
        Surface(
            onClick = onGoLive,
            modifier = Modifier
                .height(48.dp)
                .widthIn(min = 120.dp),
            shape = RoundedCornerShape(24.dp),
            color = CrimsonRed,
            shadowElevation = 8.dp,
            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GO LIVE",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = onTogglePause,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = if (isPaused) Color(0xFFF59E0B) else Color(0x33F59E0B),
                border = BorderStroke(1.dp, Color(0xFFF59E0B))
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "Resume" else "Pause",
                        tint = if (isPaused) Color.Black else Color(0xFFF59E0B),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Surface(
                onClick = onGoLive,
                modifier = Modifier
                    .height(44.dp)
                    .widthIn(min = 88.dp),
                shape = RoundedCornerShape(22.dp),
                color = CrimsonRed.copy(alpha = pulseAlpha),
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color.White, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "STOP",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ErgonomicBroadcastDock(
    isLive: Boolean,
    isPaused: Boolean = false,
    isMicOn: Boolean,
    audioLevel: Float = 0f,
    onToggleMic: () -> Unit,
    onOpenMixer: () -> Unit,
    onGoLive: () -> Unit,
    onTogglePause: () -> Unit = {},
    onOpenLayers: () -> Unit,
    onOpenDestinations: () -> Unit,
    activeDestinationsCount: Int = 0,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp),
        color = Color(0xF20B0F19),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        shadowElevation = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MicAudioLevelButton(
                isMicOn = isMicOn,
                audioLevel = audioLevel,
                onClick = onToggleMic
            )

            DockIconButton(
                icon = Icons.Default.GraphicEq,
                label = "MIXER",
                tint = Color.White.copy(alpha = 0.9f),
                onClick = onOpenMixer
            )

            CenterBroadcastControl(
                isLive = isLive,
                isPaused = isPaused,
                onGoLive = onGoLive,
                onTogglePause = onTogglePause
            )

            DockIconButton(
                icon = Icons.Default.AutoAwesome,
                label = "LAYERS",
                tint = CyberCyan,
                onClick = onOpenLayers
            )

            DockIconButton(
                icon = Icons.Default.Podcasts,
                label = "DESTS",
                tint = Color(0xFF10B981),
                badgeCount = activeDestinationsCount,
                onClick = onOpenDestinations
            )
        }
    }
}

@Composable
fun SourceSelectorPill(
    currentSource: StreamSource,
    onSelect: (StreamSource) -> Unit
) {
    Tier1SourceAndCanvasBar(
        currentSource = currentSource,
        onSelectSource = onSelect,
        currentAspect = AspectRatioMode.PORTRAIT_9_16,
        onSelectAspect = {},
        canvasScaleMode = MainViewModel.CanvasScaleMode.FIT,
        onToggleScaleMode = {}
    )
}

@Composable
fun CurvedControlDock(
    isLive: Boolean,
    isPaused: Boolean = false,
    isMicOn: Boolean,
    isChatActive: Boolean,
    onGoLive: () -> Unit,
    onTogglePause: () -> Unit = {},
    onToggleMic: () -> Unit,
    onOpenMixer: () -> Unit,
    onOpenBackground: () -> Unit,
    onToggleChat: () -> Unit
) {
    ErgonomicBroadcastDock(
        isLive = isLive,
        isPaused = isPaused,
        isMicOn = isMicOn,
        onToggleMic = onToggleMic,
        onOpenMixer = onOpenMixer,
        onGoLive = onGoLive,
        onTogglePause = onTogglePause,
        onOpenLayers = onOpenBackground,
        onOpenDestinations = onToggleChat
    )
}

@Composable
fun NeonActionIcon(
    icon: ImageVector,
    neonColor: Color? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(
                1.dp,
                neonColor ?: Color.White.copy(alpha = 0.1f),
                CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = neonColor ?: Color.White, modifier = Modifier.size(20.dp))
    }
}

fun formatDecibels(volume: Float): String {
    return when {
        volume <= 0.005f -> "-60 dB"
        volume >= 0.99f -> "0.0 dB"
        else -> {
            val db = 20 * log10(volume.coerceIn(0.001f, 1f))
            String.format(Locale.US, "%.1f dB", db)
        }
    }
}

@Composable
fun SegmentedLedVuMeter(
    level: Float,
    segmentsCount: Int = 14,
    modifier: Modifier = Modifier
) {
    val activeCount = (level.coerceIn(0f, 1f) * segmentsCount).roundToInt()
    Row(
        modifier = modifier.height(7.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until segmentsCount) {
            val isActive = i < activeCount
            val segColor = when {
                i >= segmentsCount - 2 -> CrimsonRed
                i >= segmentsCount - 5 -> Color(0xFFF59E0B)
                else -> Color(0xFF10B981)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(if (isActive) segColor else Color.White.copy(alpha = 0.08f))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlimPrecisionFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    activeColor: Color = CyberCyan,
    modifier: Modifier = Modifier
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(26.dp),
        thumb = {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, activeColor, CircleShape)
                    .shadow(3.dp, CircleShape)
            )
        },
        track = { sliderState ->
            val fraction = (sliderState.value - sliderState.valueRange.start) / (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF0F1424))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(activeColor.copy(alpha = 0.5f), activeColor)
                            )
                        )
                )
            }
        }
    )
}

@Composable
fun PrecisionMixerChannel(
    title: String,
    icon: ImageVector,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    meterLevel: Float,
    channelColor: Color = CyberCyan,
    subControls: @Composable (() -> Unit)? = null
) {
    Surface(
        color = Color(0xFF0B101D),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isMuted) Color.White.copy(alpha = 0.06f) else channelColor.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) CrimsonRed.copy(alpha = 0.2f) else channelColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = if (isMuted) CrimsonRed else channelColor, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Text(
                            if (isMuted) "MUTED" else "ONLINE",
                            color = if (isMuted) CrimsonRed else Color(0xFF10B981),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // dB numeric readout
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Text(
                            text = if (isMuted) "-60 dB" else formatDecibels(volume),
                            color = if (isMuted) CrimsonRed else channelColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    // Mute Button
                    Surface(
                        onClick = onToggleMute,
                        color = if (isMuted) CrimsonRed else Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isMuted) CrimsonRed else Color.White.copy(alpha = 0.15f))
                    ) {
                        Text(
                            text = if (isMuted) "UNMUTE" else "MUTE",
                            color = if (isMuted) Color.White else Color.White.copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Slim 6px Fader & Segmented LED VU Meter
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SlimPrecisionFader(
                    value = if (isMuted) 0f else volume,
                    onValueChange = onVolumeChange,
                    activeColor = if (isMuted) Color.Gray else channelColor
                )
                SegmentedLedVuMeter(
                    level = if (isMuted) 0f else meterLevel,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Subcontrols
            if (subControls != null) {
                Spacer(modifier = Modifier.height(2.dp))
                subControls()
            }
        }
    }
}

@Composable
fun AudioMixerSheet(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val audioFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let { viewModel.setBgMusicUri(context, it, "Custom Music Loop") }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "BROADCAST AUDIO CONSOLE",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Text(
                    "3-CHANNEL MULTI-BUS & SFX SAMPLER",
                    color = CyberCyan.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Surface(
                onClick = onDismiss,
                color = Color.White.copy(alpha = 0.08f),
                shape = CircleShape
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.padding(6.dp).size(18.dp)
                )
            }
        }

        // 1. Microphone Channel
        val micLevel = (uiState.audioLevelL + uiState.audioLevelR) / 2f
        PrecisionMixerChannel(
            title = "CH 1 • MICROPHONE INPUT",
            icon = Icons.Default.Mic,
            volume = uiState.micVolume,
            onVolumeChange = { viewModel.setMicVolume(it) },
            isMuted = !uiState.isMicEnabled,
            onToggleMute = { viewModel.toggleMic(!uiState.isMicEnabled) },
            meterLevel = micLevel,
            channelColor = CyberCyan
        ) {
            // Mic DSP Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Gain Boost (+6dB)
                FilterToggleChip(
                    label = "GAIN +6dB",
                    isActive = uiState.isGainBoostEnabled,
                    activeColor = Color(0xFF10B981),
                    onClick = { viewModel.toggleGainBoost() },
                    modifier = Modifier.weight(1f)
                )
                // Noise Gate
                FilterToggleChip(
                    label = "NOISE GATE",
                    isActive = uiState.isNoiseGateEnabled,
                    activeColor = CyberCyan,
                    onClick = { viewModel.toggleNoiseGate() },
                    modifier = Modifier.weight(1f)
                )
                // Echo Cancellation
                FilterToggleChip(
                    label = "ECHO CANCEL",
                    isActive = uiState.isEchoCancellationEnabled,
                    activeColor = Color(0xFFA855F7),
                    onClick = { viewModel.toggleEchoCancellation() },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 2. M3U8 / Media Stream Channel
        val mediaSimLevel = if (uiState.m3u8State == MainViewModel.M3u8PlaybackState.PLAYING && !uiState.isM3u8Muted) {
            (uiState.m3u8Volume * 0.75f).coerceIn(0f, 1f)
        } else 0f
        PrecisionMixerChannel(
            title = "CH 2 • M3U8 / MEDIA STREAM",
            icon = Icons.Default.Podcasts,
            volume = uiState.m3u8Volume,
            onVolumeChange = { viewModel.setM3u8Volume(it) },
            isMuted = uiState.isM3u8Muted,
            onToggleMute = { viewModel.toggleM3u8Mute() },
            meterLevel = mediaSimLevel,
            channelColor = Color(0xFF38BDF8)
        ) {
            // Audio Sync Delay Slider (-500ms to +500ms)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("AUDIO SYNC DELAY", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (uiState.m3u8AudioDelayMs == 0) "0ms (PERFECT SYNC)" else "${if (uiState.m3u8AudioDelayMs > 0) "+" else ""}${uiState.m3u8AudioDelayMs}ms",
                        color = if (uiState.m3u8AudioDelayMs == 0) Color(0xFF10B981) else CyberCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Slider(
                    value = uiState.m3u8AudioDelayMs.toFloat(),
                    onValueChange = { viewModel.setM3u8AudioDelay(it.roundToInt()) },
                    valueRange = -500f..500f,
                    steps = 99,
                    modifier = Modifier.height(24.dp),
                    colors = SliderDefaults.colors(
                        activeTrackColor = CyberCyan,
                        thumbColor = Color.White
                    )
                )
            }
        }

        // 3. Background Music Track Channel
        val bgMusicSimLevel = if (uiState.isBgMusicPlaying && !uiState.isBgMusicMuted) {
            (uiState.bgMusicVolume * 0.65f).coerceIn(0f, 1f)
        } else 0f
        PrecisionMixerChannel(
            title = "CH 3 • BACKGROUND MUSIC LOOP",
            icon = Icons.Default.MusicNote,
            volume = uiState.bgMusicVolume,
            onVolumeChange = { viewModel.setBgMusicVolume(it) },
            isMuted = uiState.isBgMusicMuted,
            onToggleMute = { viewModel.toggleBgMusicMute() },
            meterLevel = bgMusicSimLevel,
            channelColor = Color(0xFFA855F7)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Play/Pause button
                Surface(
                    onClick = { viewModel.toggleBgMusicPlaying(context) },
                    color = if (uiState.isBgMusicPlaying) Color(0xFF10B981) else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (uiState.isBgMusicPlaying) Color(0xFF10B981) else Color.White.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (uiState.isBgMusicPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null,
                            tint = if (uiState.isBgMusicPlaying) Color.Black else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (uiState.isBgMusicPlaying) "PLAYING" else "PLAY",
                            color = if (uiState.isBgMusicPlaying) Color.Black else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                // Pick Local MP3 button
                Surface(
                    onClick = { audioFilePicker.launch("audio/*") },
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FolderOpen, null, tint = Color(0xFFA855F7), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("LOAD MP3", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Track Name Preview
                Text(
                    text = uiState.bgMusicName.take(16),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 4. Audio Ducking Feature Card
        Surface(
            color = Color(0xFF0D1220),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, if (uiState.isDuckingActive) Color(0xFFF59E0B) else Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "AUDIO DUCKING",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = if (uiState.isDuckingActive) Color(0xFFF59E0B).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (uiState.isDuckingActive) Color(0xFFF59E0B) else Color.White.copy(alpha = 0.1f))
                        ) {
                            Text(
                                text = if (uiState.isDuckingActive) "ACTIVE (-50%)" else "STANDBY",
                                color = if (uiState.isDuckingActive) Color(0xFFF59E0B) else Color.White.copy(alpha = 0.5f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        "Auto-lowers media volume by 50% when speaking into mic",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                }

                Switch(
                    checked = uiState.isAudioDuckingEnabled,
                    onCheckedChange = { viewModel.toggleAudioDucking() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFF59E0B)
                    )
                )
            }
        }

        // 5. 8-Pad Instant SFX Sampler
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "INSTANT SFX SAMPLER (8-PAD BROADCAST PAD)",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )

            val sfxPads = listOf(
                Pair("⚽ GOAL", com.example.core.audio.AudioSfxManager.SfxType.GOAL),
                Pair("👏 APPLAUSE", com.example.core.audio.AudioSfxManager.SfxType.APPLAUSE),
                Pair("📢 AIRHORN", com.example.core.audio.AudioSfxManager.SfxType.AIRHORN),
                Pair("🏁 WHISTLE", com.example.core.audio.AudioSfxManager.SfxType.WHISTLE),
                Pair("😂 LAUGH", com.example.core.audio.AudioSfxManager.SfxType.LAUGH),
                Pair("🥁 DRUMROLL", com.example.core.audio.AudioSfxManager.SfxType.DRUMROLL),
                Pair("🎉 CHEER", com.example.core.audio.AudioSfxManager.SfxType.CHEER),
                Pair("🚨 BUZZER", com.example.core.audio.AudioSfxManager.SfxType.BUZZER)
            )

            for (rowIndex in 0 until 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (colIndex in 0 until 4) {
                        val item = sfxPads[rowIndex * 4 + colIndex]
                        Surface(
                            onClick = { viewModel.playSfx(item.second) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            color = Color(0xFF0F1424),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.35f))
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    item.first,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("DONE", color = Color.Black, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun FilterToggleChip(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (isActive) activeColor.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (isActive) activeColor else Color.White.copy(alpha = 0.1f)),
        modifier = modifier.height(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                color = if (isActive) activeColor else Color.White.copy(alpha = 0.6f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}


@Composable
fun DraggableFacecam(
    initialX: Float,
    initialY: Float,
    onPositionChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(initialX) }
    var offsetY by remember { mutableFloatStateOf(initialY) }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(110.dp)
            .clip(CircleShape)
            .background(Color.Black)
            .border(
                2.dp,
                Brush.sweepGradient(listOf(CyberCyan, Color.White, CyberCyan)),
                CircleShape
            )
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                    onPositionChange(offsetX, offsetY)
                }
            }
    ) {
        content()
    }
}

@Composable
fun GlassChatOverlay(
    messages: List<com.example.ui.MainViewModel.ChatMessage>,
    onPinComment: (com.example.ui.MainViewModel.ChatMessage) -> Unit,
    onOpenChatHub: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth(0.75f)
            .heightIn(max = 220.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(GlassDark)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(CyberCyan))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("LIVE CHAT", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
                IconButton(onClick = onOpenChatHub, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Settings, "Chat Hub", tint = CyberCyan, modifier = Modifier.size(12.dp))
                }
            }

            androidx.compose.foundation.lazy.LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    ChatItem(msg, onPin = { onPinComment(msg) })
                }
            }
        }
    }
}

@Composable
fun ChatItem(
    message: com.example.ui.MainViewModel.ChatMessage,
    onPin: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        when (message.platform.lowercase()) {
                            "twitch" -> Color(0xFF9146FF)
                            "youtube" -> Color(0xFFFF0000)
                            else -> Color(0xFF00F2FF)
                        }.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(message.platform.take(1).uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message.author,
                        color = CyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(10.dp)
                    )
                }
                Text(
                    text = message.message,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        IconButton(onClick = onPin, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.PushPin, "Pin to broadcast", tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
fun LiveChatHubSheet(
    twitchChannel: String,
    onConnectTwitch: (String) -> Unit,
    youtubeChannel: String,
    onConnectYouTube: (String) -> Unit,
    pinnedComment: com.example.ui.MainViewModel.ChatMessage?,
    onUnpinComment: () -> Unit,
    onDismiss: () -> Unit
) {
    var twitchInput by remember { mutableStateOf(twitchChannel) }
    var youtubeInput by remember { mutableStateOf(youtubeChannel) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "REAL-TIME LIVE CHAT HUB",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            "CONNECT TWITCH IRC WEBSOCKET & YOUTUBE LIVE CHAT",
            color = CyberCyan.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Pinned Comment Status
        if (pinnedComment != null) {
            Surface(
                color = Color(0x3300F2FF),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CyberCyan),
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("PINNED COMMENT ON STREAM", color = CyberCyan, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        Text("${pinnedComment.author}: ${pinnedComment.message}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onUnpinComment) {
                        Text("UNPIN", color = CrimsonRed, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        // Twitch IRC Input
        GlassSectionCard(title = "TWITCH IRC WEBSOCKET (ZERO KEY)") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassTextField(
                    value = twitchInput,
                    onValueChange = { twitchInput = it },
                    label = "TWITCH CHANNEL NAME (e.g. shroud, ninja)",
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onConnectTwitch(twitchInput) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9146FF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Link, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CONNECT TWITCH CHAT", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // YouTube Live Chat Input
        GlassSectionCard(title = "YOUTUBE / KICK LIVE CHAT CONNECTOR") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassTextField(
                    value = youtubeInput,
                    onValueChange = { youtubeInput = it },
                    label = "YOUTUBE VIDEO ID OR CHANNEL NAME",
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onConnectYouTube(youtubeInput) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CONNECT YOUTUBE CHAT", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("DONE", color = Color.Black, fontWeight = FontWeight.Black)
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun M3u8GlassSheet(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    var selectedTab by remember { mutableStateOf(0) }
    var urlInput by remember { mutableStateOf(uiState.m3u8Url) }
    var nameInput by remember { mutableStateOf(uiState.activeM3u8Name) }
    var userAgentInput by remember { mutableStateOf(uiState.m3u8Headers["User-Agent"] ?: "") }
    var refererInput by remember { mutableStateOf(uiState.m3u8Headers["Referer"] ?: "") }
    var originInput by remember { mutableStateOf(uiState.m3u8Headers["Origin"] ?: "") }
    var searchQuery by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var m3uPlaylistText by remember { mutableStateOf("") }

    val health = remember(uiState.m3u8State, uiState.m3u8Url) { viewModel.getM3u8Health() }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            containerColor = Color(0xFF090D16),
            title = {
                Text(
                    "IMPORT M3U PLAYLIST",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Paste raw #EXTM3U playlist or IPTV link list to parse channels:",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = m3uPlaylistText,
                        onValueChange = { m3uPlaylistText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        placeholder = {
                            Text(
                                "#EXTM3U\n#EXTINF:-1,Channel 1\nhttp://server/live1.m3u8",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    TextButton(
                        onClick = {
                            clipboardManager.getText()?.text?.let { m3uPlaylistText = it }
                        }
                    ) {
                        Icon(Icons.Default.ContentPaste, null, tint = CyberCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste from Clipboard", color = CyberCyan, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val count = viewModel.importM3uPlaylist(m3uPlaylistText)
                        Toast.makeText(context, "Imported $count channels successfully!", Toast.LENGTH_SHORT).show()
                        showImportDialog = false
                        m3uPlaylistText = ""
                        selectedTab = 1
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Text("IMPORT CHANNELS", color = Color.Black, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("CANCEL", color = Color.Gray)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Sheet Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "PRO REMOTE STREAM MANAGER",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Text(
                    "IPTV, HLS & PROTECTED STREAM RESTREAMING",
                    color = CyberCyan.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Surface(
                onClick = onDismiss,
                color = Color.White.copy(alpha = 0.08f),
                shape = CircleShape
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.padding(6.dp).size(18.dp)
                )
            }
        }

        // 1. Live Preview Player Card
        val m3u8Player = remember(uiState.m3u8Url) { viewModel.getRestreamEngine()?.getPlayer() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black)
                .border(1.5.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
        ) {
            if (m3u8Player != null && urlInput.isNotBlank()) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = m3u8Player
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LiveTv, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("NO STREAM ACTIVE IN PREVIEW", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Preview Overlays (Top live badge & Bottom URL badge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .align(Alignment.TopStart),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xCC090D16),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF10B981)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("LIVE PREVIEW", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }

                // Play / Pause preview toggle
                Surface(
                    onClick = {
                        if (uiState.m3u8State == MainViewModel.M3u8PlaybackState.PLAYING) {
                            viewModel.pauseM3u8()
                        } else {
                            val headers = mutableMapOf<String, String>()
                            if (userAgentInput.isNotBlank()) headers["User-Agent"] = userAgentInput
                            if (refererInput.isNotBlank()) headers["Referer"] = refererInput
                            if (originInput.isNotBlank()) headers["Origin"] = originInput
                            viewModel.playM3u8(urlInput, nameInput, headers)
                        }
                    },
                    color = Color(0xCC090D16),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                ) {
                    Icon(
                        if (uiState.m3u8State == MainViewModel.M3u8PlaybackState.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Toggle Preview",
                        tint = CyberCyan,
                        modifier = Modifier.padding(6.dp).size(16.dp)
                    )
                }
            }

            // Bottom Name Tag
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                color = Color(0xCC090D16),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = nameInput.ifBlank { "Remote Feed" },
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // 2. Stream Health Diagnostics (4 Cards Grid)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "STREAM HEALTH & CODEC TELEMETRY",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StreamHealthCard(
                    title = "RESOLUTION",
                    value = health.resolution,
                    icon = Icons.Default.Hd,
                    color = CyberCyan,
                    modifier = Modifier.weight(1f)
                )
                StreamHealthCard(
                    title = "CODEC",
                    value = health.codec,
                    icon = Icons.Default.VideoSettings,
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StreamHealthCard(
                    title = "AUDIO TRACK",
                    value = health.audioTrack,
                    icon = Icons.Default.GraphicEq,
                    color = Color(0xFFA855F7),
                    modifier = Modifier.weight(1f)
                )
                StreamHealthCard(
                    title = "HEALTH",
                    value = if (health.isReady) "READY (BROADCASTABLE)" else "PROBING FEED...",
                    icon = Icons.Default.CheckCircle,
                    color = if (health.isReady) Color(0xFF10B981) else Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 3. Navigation Tabs
        Surface(
            modifier = Modifier.fillMaxWidth().height(42.dp),
            color = Color.White.copy(alpha = 0.05f),
            shape = RoundedCornerShape(21.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(3.dp)) {
                Surface(
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    color = if (selectedTab == 0) CyberCyan.copy(alpha = 0.25f) else Color.Transparent,
                    shape = RoundedCornerShape(18.dp),
                    border = if (selectedTab == 0) BorderStroke(1.dp, CyberCyan) else null
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "SINGLE STREAM & HEADERS",
                            color = if (selectedTab == 0) CyberCyan else Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Surface(
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    color = if (selectedTab == 1) CyberCyan.copy(alpha = 0.25f) else Color.Transparent,
                    shape = RoundedCornerShape(18.dp),
                    border = if (selectedTab == 1) BorderStroke(1.dp, CyberCyan) else null
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "M3U PLAYLIST & CHANNELS (${uiState.savedStreams.size})",
                            color = if (selectedTab == 1) CyberCyan else Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        if (selectedTab == 0) {
            // Tab 1: Single Stream URL & Protected Headers
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Stream URL field with Paste
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("STREAM URL (.m3u8, .mp4, .ts, .flv)", fontSize = 11.sp) },
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboardManager.getText()?.text?.let { urlInput = it }
                        }) {
                            Icon(Icons.Default.ContentPaste, "Paste", tint = CyberCyan, modifier = Modifier.size(18.dp))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedLabelColor = CyberCyan,
                        unfocusedLabelColor = Color.Gray
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    singleLine = true
                )

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("STREAM / CHANNEL NAME", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedLabelColor = CyberCyan,
                        unfocusedLabelColor = Color.Gray
                    ),
                    singleLine = true
                )

                // Custom HTTP Headers Section
                Surface(
                    color = Color.White.copy(alpha = 0.04f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "PROTECTED STREAM HTTP HEADERS",
                            color = Color(0xFFF59E0B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "Required for anti-hotlink and geo-restricted IPTV / sports feeds:",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp
                        )

                        // Quick Presets Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PresetChip("Default Mobile") {
                                userAgentInput = "Mozilla/5.0 (Linux; Android 14) LiveStreamPro/1.0"
                                refererInput = ""
                                originInput = ""
                            }
                            PresetChip("Chrome Desktop") {
                                userAgentInput = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                                refererInput = "https://google.com/"
                                originInput = "https://google.com"
                            }
                            PresetChip("VLC IPTV") {
                                userAgentInput = "VLC/3.0.18 LibVLC/3.0.18"
                                refererInput = ""
                                originInput = ""
                            }
                            PresetChip("Clear Headers") {
                                userAgentInput = ""
                                refererInput = ""
                                originInput = ""
                            }
                        }

                        OutlinedTextField(
                            value = userAgentInput,
                            onValueChange = { userAgentInput = it },
                            label = { Text("User-Agent Header", fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFF59E0B),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedLabelColor = Color(0xFFF59E0B),
                                unfocusedLabelColor = Color.Gray
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = refererInput,
                            onValueChange = { refererInput = it },
                            label = { Text("Referer Header", fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFF59E0B),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedLabelColor = Color(0xFFF59E0B),
                                unfocusedLabelColor = Color.Gray
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = originInput,
                            onValueChange = { originInput = it },
                            label = { Text("Origin Header", fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFF59E0B),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedLabelColor = Color(0xFFF59E0B),
                                unfocusedLabelColor = Color.Gray
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                            singleLine = true
                        )
                    }
                }

                // Action Buttons (Test Preview & Save)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val headers = mutableMapOf<String, String>()
                            if (userAgentInput.isNotBlank()) headers["User-Agent"] = userAgentInput
                            if (refererInput.isNotBlank()) headers["Referer"] = refererInput
                            if (originInput.isNotBlank()) headers["Origin"] = originInput
                            viewModel.playM3u8(urlInput, nameInput, headers)
                            Toast.makeText(context, "Testing Stream with Custom Headers...", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("TEST PREVIEW", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }

                    Button(
                        onClick = {
                            viewModel.saveStream(nameInput, urlInput, userAgentInput, refererInput, originInput)
                            Toast.makeText(context, "Saved to Channels!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Default.BookmarkAdd, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SAVE CHANNEL", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // Tab 2: M3U Playlist & Channels
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "IPTV CHANNEL LIBRARY (${uiState.savedStreams.size})",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                    Button(
                        onClick = { showImportDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.PlaylistAdd, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("IMPORT M3U", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter channels by name or url...", color = Color.Gray, fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                    ),
                    singleLine = true
                )

                val filtered = uiState.savedStreams.filter {
                    it.name.contains(searchQuery, ignoreCase = true) || it.url.contains(searchQuery, ignoreCase = true)
                }

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No channels match your query.", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                } else {
                    filtered.forEach { stream ->
                        Surface(
                            onClick = {
                                urlInput = stream.url
                                nameInput = stream.name
                                userAgentInput = stream.userAgent
                                refererInput = stream.referer
                                originInput = stream.origin
                                viewModel.selectSavedStream(stream)
                                Toast.makeText(context, "Loaded ${stream.name}", Toast.LENGTH_SHORT).show()
                            },
                            color = if (uiState.m3u8Url == stream.url) CyberCyan.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                1.dp,
                                if (uiState.m3u8Url == stream.url) CyberCyan else Color.White.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (uiState.m3u8Url == stream.url) CyberCyan else Color.Gray)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stream.name,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stream.url,
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { viewModel.deleteStream(stream.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, "Delete", tint = CrimsonRed.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 4. Primary Stage Activation Button (Strict mutual exclusion)
        Button(
            onClick = {
                val headers = mutableMapOf<String, String>()
                if (userAgentInput.isNotBlank()) headers["User-Agent"] = userAgentInput
                if (refererInput.isNotBlank()) headers["Referer"] = refererInput
                if (originInput.isNotBlank()) headers["Origin"] = originInput
                viewModel.toggleSource(StreamSource.M3U8)
                viewModel.playM3u8(urlInput, nameInput, headers)
                Toast.makeText(context, "M3U8 Source Activated on Broadcast Stage!", Toast.LENGTH_SHORT).show()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.RocketLaunch, null, tint = Color.Black, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "ACTIVATE ON BROADCAST STAGE",
                color = Color.Black,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun StreamHealthCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(60.dp),
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(title, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PresetChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}


@Composable
fun StatTag(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DestinationsGlassSheet(
    endpoints: List<PlatformEndpoint>,
    isStreaming: Boolean = false,
    currentBitrate: Int = 0,
    fps: Int = 0,
    diagnosticMessage: String = "",
    onToggle: (String, Boolean) -> Unit,
    onSaveCredentials: (String, String, String) -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: (String) -> Unit,
    onResetPlatform: (String) -> Unit,
    onAddCustom: () -> Unit,
    onDeleteCustom: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreJsonInput by remember { mutableStateOf("") }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            containerColor = Color(0xFF090D16),
            title = {
                Text(
                    "RESTORE STREAM PROFILES",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Paste a previously exported profile JSON backup below:",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                    OutlinedTextField(
                        value = restoreJsonInput,
                        onValueChange = { restoreJsonInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        placeholder = { Text("{\n  \"destinations\": [...]\n}", color = Color.Gray, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                val clipData = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                if (clipData.isNotBlank()) {
                                    restoreJsonInput = clipData
                                }
                            }
                        ) {
                            Icon(Icons.Default.ContentPaste, null, tint = CyberCyan, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PASTE FROM CLIPBOARD", color = CyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (restoreJsonInput.isNotBlank()) {
                            onRestoreBackup(restoreJsonInput)
                            showRestoreDialog = false
                            restoreJsonInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Text("RESTORE", color = Color.Black, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("CANCEL", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "DESTINATIONS HUB",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Text(
                    "MULTI-RTMP PARALLEL BROADCASTING",
                    color = CyberCyan.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Surface(
                onClick = onDismiss,
                color = Color.White.copy(alpha = 0.08f),
                shape = CircleShape
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.padding(6.dp).size(18.dp)
                )
            }
        }

        // Profile Backup & Restore Toolbar
        Surface(
            color = Color(0xFF0F1422),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Export Backup Button
                Surface(
                    onClick = onExportBackup,
                    color = Color.White.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Save, null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("EXPORT BACKUP", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }

                // Restore Backup Button
                Surface(
                    onClick = { showRestoreDialog = true },
                    color = Color.White.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Restore, null, tint = CyberCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("RESTORE BACKUP", color = CyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }

                // Add Custom RTMP Button
                Surface(
                    onClick = onAddCustom,
                    color = Color.White.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Add, null, tint = Color(0xFFA855F7), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ CUSTOM RTMP", color = Color(0xFFA855F7), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        // Destinations List
        endpoints.forEach { ep ->
            DestinationGlassCard(
                endpoint = ep,
                isStreaming = isStreaming,
                currentBitrate = currentBitrate,
                fps = fps,
                diagnosticMessage = diagnosticMessage,
                onToggle = { onToggle(ep.id, it) },
                onSaveCredentials = { url, key -> onSaveCredentials(ep.id, url, key) },
                onResetDefault = if (!ep.isCustom) { { onResetPlatform(ep.id) } } else null,
                onDelete = if (ep.isCustom) { { onDeleteCustom(ep.id) } } else null
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("DONE", color = Color.Black, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun DestinationGlassCard(
    endpoint: PlatformEndpoint,
    isStreaming: Boolean,
    currentBitrate: Int,
    fps: Int,
    diagnosticMessage: String,
    onToggle: (Boolean) -> Unit,
    onSaveCredentials: (String, String) -> Unit,
    onResetDefault: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var urlInput by remember(endpoint.url) { mutableStateOf(endpoint.url) }
    var keyInput by remember(endpoint.streamKey) { mutableStateOf(endpoint.streamKey) }
    val context = LocalContext.current

    val platformColor = when (endpoint.id.lowercase()) {
        "youtube" -> Color(0xFFFF0000)
        "twitch" -> Color(0xFF9146FF)
        "facebook" -> Color(0xFF1877F2)
        "tiktok" -> Color(0xFF00F2FE)
        "kick" -> Color(0xFF53FC18)
        "telegram" -> Color(0xFF24A1DE)
        "twitter" -> Color(0xFF1DA1F2)
        "instagram" -> Color(0xFFE1306C)
        else -> CyberCyan
    }

    val platformIcon = when (endpoint.id.lowercase()) {
        "youtube" -> Icons.Default.PlayCircle
        "twitch" -> Icons.Default.Videocam
        "facebook" -> Icons.Default.Facebook
        "tiktok" -> Icons.Default.MusicVideo
        "kick" -> Icons.Default.ElectricBolt
        "telegram" -> Icons.Default.Send
        "twitter" -> Icons.Default.Share
        "instagram" -> Icons.Default.CameraAlt
        else -> Icons.Default.Lan
    }

    // Dynamic Connection Status computation
    val isAutoReconnecting = diagnosticMessage.contains("Retry", ignoreCase = true) || diagnosticMessage.contains("Reconnect", ignoreCase = true)
    val statusText: String
    val statusColor: Color
    val isLiveActive: Boolean

    if (isStreaming && endpoint.isEnabled) {
        if (isAutoReconnecting) {
            statusText = "Auto-Reconnecting..."
            statusColor = Color(0xFFF59E0B)
            isLiveActive = false
        } else if (fps > 0) {
            val effBitrate = if (currentBitrate > 0) currentBitrate else 4500
            statusText = "Live $effBitrate kbps"
            statusColor = Color(0xFF10B981)
            isLiveActive = true
        } else {
            statusText = "Connecting..."
            statusColor = Color(0xFFF59E0B)
            isLiveActive = false
        }
    } else {
        isLiveActive = false
        if (endpoint.isEnabled) {
            if (endpoint.streamKey.isNotBlank()) {
                statusText = "READY"
                statusColor = Color(0xFF10B981)
            } else {
                statusText = "KEY REQUIRED"
                statusColor = Color(0xFFF59E0B)
            }
        } else {
            statusText = "DISABLED"
            statusColor = Color.Gray
        }
    }

    Surface(
        color = Color(0xFF0B101D),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (endpoint.isEnabled) platformColor.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f)
        ),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Main Top Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(platformColor.copy(alpha = 0.2f))
                            .border(1.dp, platformColor.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(platformIcon, null, tint = platformColor, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            endpoint.name.uppercase(),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = statusText,
                                color = statusColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isLiveActive) {
                                Spacer(modifier = Modifier.width(6.dp))
                                LatencyPill("18ms")
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Switch(
                        checked = endpoint.isEnabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = platformColor,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Expanded In-Card Configuration (Server URL, Stream Key, Paste & Save)
            if (isExpanded) {
                Surface(
                    color = Color(0xFF070B14),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // RTMP Server URL input
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("RTMP SERVER INGEST URL", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                if (onResetDefault != null) {
                                    Text(
                                        text = "RESET DEFAULT",
                                        color = CyberCyan,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            onResetDefault()
                                            urlInput = endpoint.defaultUrl
                                        }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = CyberCyan,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                                ),
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                            val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                            if (clip.isNotBlank()) urlInput = clip.trim()
                                        }
                                    ) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste URL", tint = CyberCyan, modifier = Modifier.size(16.dp))
                                    }
                                }
                            )
                        }

                        // Stream Key input
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("STREAM KEY", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = keyInput,
                                onValueChange = { keyInput = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = if (isKeyVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = CyberCyan,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                                ),
                                trailingIcon = {
                                    Row {
                                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                            Icon(
                                                if (isKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                null,
                                                tint = CyberCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                                if (clip.isNotBlank()) keyInput = clip.trim()
                                            }
                                        ) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste Key", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            )
                        }

                        // Save & Action Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (onDelete != null) {
                                OutlinedButton(
                                    onClick = onDelete,
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.5f))
                                ) {
                                    Icon(Icons.Default.Delete, null, tint = CrimsonRed, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("DELETE", color = CrimsonRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = {
                                    onSaveCredentials(urlInput, keyInput)
                                    Toast.makeText(context, "${endpoint.name} credentials saved!", Toast.LENGTH_SHORT).show()
                                    isExpanded = false
                                },
                                modifier = Modifier.weight(1.5f).height(40.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = platformColor),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("SAVE CREDENTIALS", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LatencyPill(latency: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF10B981).copy(alpha = 0.1f))
            .border(0.5.dp, Color(0xFF10B981).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(latency, color = Color(0xFF10B981), fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun DestinationField(
    label: String,
    value: String,
    onCopy: () -> Unit,
    onToggleVisibility: (() -> Unit)? = null,
    isKey: Boolean = false,
    isMasked: Boolean = false
) {
    Column {
        Text(label, color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = value,
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Row {
                if (isKey && onToggleVisibility != null) {
                    IconButton(onClick = onToggleVisibility, modifier = Modifier.size(32.dp)) {
                        Icon(if (isMasked) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCopy, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsGlassSheet(
    config: com.example.core.model.StreamConfig,
    onConfigChange: (com.example.core.model.StreamConfig) -> Unit,
    isWatermarkEnabled: Boolean,
    onWatermarkToggle: (Boolean) -> Unit,
    isTickerEnabled: Boolean,
    onTickerToggle: (Boolean) -> Unit,
    isScoreboardEnabled: Boolean,
    onScoreboardToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var bitrateVal by remember(config.videoBitrateKbps) { mutableFloatStateOf(config.videoBitrateKbps.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "PRO BROADCAST SETTINGS",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Text(
                    "ENCODER, GOP & CRASH-PROOF PIPELINES",
                    color = CyberCyan.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Surface(
                onClick = onDismiss,
                color = Color.White.copy(alpha = 0.08f),
                shape = CircleShape
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.padding(6.dp).size(18.dp)
                )
            }
        }

        // 1. Resolution & Orientation Card
        GlassSectionCard(title = "RESOLUTION & ORIENTATION") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("BROADCAST RESOLUTION", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                PillSelector(
                    options = listOf("1080p FHD", "720p HD", "480p SD"),
                    selectedIndex = when (config.videoQuality) {
                        com.example.core.model.VideoQuality.FHD_1080P -> 0
                        com.example.core.model.VideoQuality.HD_720P -> 1
                        else -> 2
                    },
                    onSelect = { idx ->
                        val quality = when (idx) {
                            0 -> com.example.core.model.VideoQuality.FHD_1080P
                            1 -> com.example.core.model.VideoQuality.HD_720P
                            else -> com.example.core.model.VideoQuality.SD_480P
                        }
                        onConfigChange(config.copy(videoQuality = quality))
                    }
                )

                Text("STAGE ORIENTATION", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                PillSelector(
                    options = listOf("Landscape (16:9)", "Portrait (9:16)"),
                    selectedIndex = if (config.aspectRatio == AspectRatioMode.LANDSCAPE_16_9) 0 else 1,
                    onSelect = { idx ->
                        val ratio = if (idx == 0) AspectRatioMode.LANDSCAPE_16_9 else AspectRatioMode.PORTRAIT_9_16
                        onConfigChange(config.copy(aspectRatio = ratio))
                    }
                )
            }
        }

        // 2. Framerate & Keyframe Cadence Card
        GlassSectionCard(title = "FRAMERATE & KEYFRAME INTERVAL") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("OUTPUT FRAMERATE", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                PillSelector(
                    options = listOf("30 FPS (Standard)", "60 FPS (Smooth Pro)"),
                    selectedIndex = if (config.targetFps == 60) 1 else 0,
                    onSelect = { idx ->
                        val fps = if (idx == 1) 60 else 30
                        onConfigChange(config.copy(targetFps = fps))
                    }
                )

                Surface(
                    color = Color(0xFF0F1424),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "KEYFRAME INTERVAL: GOP = 2.0s (LOCKED)",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                "Mandatory 2-second keyframe cadence strictly enforced for Twitch, YouTube & Telegram live delivery.",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. Bitrate & Adaptive Bitrate (ABR) Card
        GlassSectionCard(title = "BITRATE & ADAPTIVE STREAMING (ABR)") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TARGET VIDEO BITRATE", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${bitrateVal.toInt()} kbps",
                        color = CyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }

                GlowingSlider(
                    value = bitrateVal,
                    onValueChange = {
                        bitrateVal = it
                        onConfigChange(config.copy(videoBitrateKbps = it.toInt()))
                    },
                    valueRange = 1000f..8000f
                )

                // Quick Bitrate Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(2500, 4500, 6000, 8000).forEach { preset ->
                        Surface(
                            onClick = {
                                bitrateVal = preset.toFloat()
                                onConfigChange(config.copy(videoBitrateKbps = preset))
                            },
                            color = if (bitrateVal.toInt() == preset) CyberCyan.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (bitrateVal.toInt() == preset) CyberCyan else Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.weight(1f).height(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    "${preset}k",
                                    color = if (bitrateVal.toInt() == preset) CyberCyan else Color.White.copy(alpha = 0.7f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                FeatureToggleRow(
                    title = "ADAPTIVE BITRATE (ABR)",
                    checked = config.isAdaptiveBitrateEnabled,
                    onCheckedChange = { onConfigChange(config.copy(isAdaptiveBitrateEnabled = it)) }
                )
                Text(
                    "Automatically lowers bitrate if network bandwidth drops, preventing stream buffer stalls or disconnects.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 9.sp
                )
            }
        }

        // 4. Hardware Encoder Engine & System Card
        GlassSectionCard(title = "ENCODER ENGINE & SYSTEM SERVICE") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ENCODER BACKEND", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                PillSelector(
                    options = listOf("Hardware MediaCodec H.264", "Software x264 (Ultrafast)"),
                    selectedIndex = if (config.encoderEngine == com.example.core.model.EncoderEngine.HARDWARE_MEDIACODEC) 0 else 1,
                    onSelect = { idx ->
                        val engine = if (idx == 0) com.example.core.model.EncoderEngine.HARDWARE_MEDIACODEC else com.example.core.model.EncoderEngine.SOFTWARE_X264
                        onConfigChange(config.copy(encoderEngine = engine))
                    }
                )

                FeatureToggleRow(
                    title = "KEEP SCREEN AWAKE",
                    checked = config.keepScreenAwake,
                    onCheckedChange = { onConfigChange(config.copy(keepScreenAwake = it)) }
                )

                FeatureToggleRow(
                    title = "BACKGROUND STREAMING SERVICE",
                    checked = config.backgroundStreamingEnabled,
                    onCheckedChange = { onConfigChange(config.copy(backgroundStreamingEnabled = it)) }
                )

                FeatureToggleRow(
                    title = "AAC-LC 128 kbps 44.1 kHz STEREO AUDIO",
                    checked = true,
                    onCheckedChange = {}
                )
            }
        }

        // 5. Broadcast Overlays Shortcuts Card
        GlassSectionCard(title = "BROADCAST OVERLAYS") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureToggleRow(
                    title = "WATERMARK LOGO",
                    checked = isWatermarkEnabled,
                    onCheckedChange = onWatermarkToggle
                )
                FeatureToggleRow(
                    title = "SCROLLING NEWS TICKER",
                    checked = isTickerEnabled,
                    onCheckedChange = onTickerToggle
                )
                FeatureToggleRow(
                    title = "LIVE MATCH SCOREBOARD",
                    checked = isScoreboardEnabled,
                    onCheckedChange = onScoreboardToggle
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("DONE", color = Color.Black, fontWeight = FontWeight.Black)
        }
    }
}

private fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}

