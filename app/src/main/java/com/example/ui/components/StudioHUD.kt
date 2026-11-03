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
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.shadow

@Composable
fun SparklineWaveformGraph(
    modifier: Modifier = Modifier,
    data: List<Float>,
    color: Color = CyberCyan
) {
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
            color = color,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
fun DynamicIslandHUD(
    isLive: Boolean,
    streamTime: Long,
    bitrate: Int,
    fps: Int,
    endpoints: List<PlatformEndpoint>,
    onSettingsClick: () -> Unit,
    onDestinationsClick: () -> Unit,
    onTelemetryClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val bitrateHistory = remember { mutableStateListOf<Float>() }
    LaunchedEffect(bitrate) {
        bitrateHistory.add(bitrate.toFloat())
        if (bitrateHistory.size > 30) bitrateHistory.removeAt(0)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Main HUD Pill
        Box(
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(GlassDark)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp))
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                // Row 1: Status & Platforms
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isLive) CrimsonRed else Color.Gray)
                                .then(if (isLive) Modifier.blur(2.dp * pulseAlpha) else Modifier)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isLive) "LIVE ${formatTime(streamTime)}" else "READY",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable { onDestinationsClick() }
                    ) {
                        endpoints.filter { it.isEnabled }.take(3).forEach { ep ->
                            PlatformBadge(ep)
                        }
                        if (endpoints.none { it.isEnabled }) {
                             StatPill(icon = Icons.Default.Add, text = "DESTS")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Row 2: Bitrate, Sparkline, FPS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.FlashOn,
                        null,
                        tint = CyberCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "$bitrate kbps",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(60.dp)
                    )
                    
                    SparklineWaveformGraph(
                        modifier = Modifier
                            .weight(1f)
                            .height(16.dp)
                            .padding(horizontal = 8.dp),
                        data = if (bitrateHistory.isEmpty()) listOf(0f, 0f) else bitrateHistory,
                        color = if (isLive) CrimsonRed else CyberCyan
                    )
                    
                    Text(
                        text = "$fps FPS",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(40.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right Action Buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassCircularButton(
                icon = Icons.Default.SignalCellularAlt,
                onClick = onTelemetryClick
            )
            GlassCircularButton(
                icon = Icons.Default.Settings,
                onClick = onSettingsClick
            )
        }
    }
}

@Composable
fun PlatformBadge(endpoint: PlatformEndpoint) {
    val color = when (endpoint.id.lowercase()) {
        "youtube" -> Color(0xFFFF0000)
        "twitch" -> Color(0xFF9146FF)
        "facebook" -> Color(0xFF1877F2)
        "tiktok" -> Color(0xFF000000)
        else -> Color.Gray
    }
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Just a colored dot for now to represent platform
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
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}


@Composable
fun SourceSelectorPill(
    currentSource: StreamSource,
    onSelect: (StreamSource) -> Unit
) {
    val modes = listOf(StreamSource.CAMERA, StreamSource.M3U8, StreamSource.SCREEN, StreamSource.AUDIO)
    val labels = listOf("CAM", "REMOTE URL", "SCREEN", "AUDIO")
    val icons = listOf(Icons.Default.CameraAlt, Icons.Default.Link, Icons.Default.PhoneAndroid, Icons.Default.Mic)
    
    val selectedIndex = modes.indexOf(currentSource).coerceAtLeast(0)
    
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(GlassDark)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp))
            .padding(4.dp)
    ) {
        val itemWidth = maxWidth / modes.size
        val indicatorOffset by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "indicator"
        )
        
        // Animated indicator background
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(itemWidth)
                .fillMaxHeight()
                .padding(2.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.15f))
                .border(1.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
        )
        
        Row(modifier = Modifier.fillMaxSize()) {
            modes.forEachIndexed { index, mode ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(mode) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val isSelected = currentSource == mode
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            icons[index],
                            null,
                            tint = if (isSelected) CyberCyan else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            labels[index],
                            color = if (isSelected) CyberCyan else Color.White.copy(alpha = 0.4f),
                            fontSize = 8.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CurvedControlDock(
    isLive: Boolean,
    isMicOn: Boolean,
    isChatActive: Boolean,
    onGoLive: () -> Unit,
    onToggleMic: () -> Unit,
    onOpenMixer: () -> Unit,
    onOpenBackground: () -> Unit,
    onToggleChat: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Symmetric 5-Column Row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(GlassDark)
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(32.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left 2 Buttons
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                    NeonActionIcon(if (isMicOn) Icons.Default.Mic else Icons.Default.MicOff, if (isMicOn) null else CrimsonRed, onToggleMic)
                    NeonActionIcon(Icons.Default.GraphicEq, null, onOpenMixer)
                }

                // Center Spacer for elevated button
                Spacer(modifier = Modifier.width(80.dp))

                // Right 2 Buttons
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                    NeonActionIcon(Icons.Default.Layers, null, onOpenBackground)
                    NeonActionIcon(
                        Icons.Default.Chat,
                        if (isChatActive) CyberCyan else null,
                        onToggleChat
                    )
                }
            }
        }

        // Center Hero Button (Elevated)
        val infiniteTransition = rememberInfiniteTransition(label = "liveGlow")
        val glowScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowScale"
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-4).dp) // Adjust to match "-mt-6" style elevation
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (isLive) CrimsonRed else Color(0xFF10B981),
                            if (isLive) CrimsonRed.copy(alpha = 0.4f) else Color(0xFF10B981).copy(alpha = 0.4f)
                        )
                    )
                )
                .then(
                    if (isLive) Modifier.border(
                        (3.dp * glowScale),
                        CrimsonRed.copy(alpha = 0.6f),
                        CircleShape
                    ) else Modifier.border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                )
                .shadow(12.dp, CircleShape, ambientColor = if (isLive) CrimsonRed else Color(0xFF10B981))
                .clickable(onClick = onGoLive),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (isLive) Icons.Default.Stop else Icons.Default.PlayArrow,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    if (isLive) "STOP" else "LIVE",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
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

@Composable
fun AudioMixerSheet(
    micVolume: Float,
    onMicVolumeChange: (Float) -> Unit,
    m3u8Volume: Float,
    onM3u8VolumeChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            "AUDIO MIXER",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        MixerSlider("MICROPHONE", micVolume, onMicVolumeChange)
        Spacer(modifier = Modifier.height(24.dp))
        MixerSlider("MEDIA / M3U8", m3u8Volume, onM3u8VolumeChange)
        
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("CLOSE MIXER", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MixerSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("${(value * 100).toInt()}%", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                activeTrackColor = CyberCyan,
                thumbColor = Color.White
            )
        )
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
            .fillMaxWidth(0.65f)
            .heightIn(max = 220.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(GlassDark)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                ChatItem(msg)
            }
        }
    }
}

@Composable
fun ChatItem(message: com.example.ui.MainViewModel.ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // User Avatar Placeholder
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
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
                // Verified Badge
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
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun M3u8GlassSheet(
    url: String,
    onUrlChange: (String) -> Unit,
    savedStreams: List<com.example.ui.MainViewModel.SavedStreamItem>,
    onSelectStream: (com.example.ui.MainViewModel.SavedStreamItem) -> Unit,
    onSaveStream: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textValue by remember { mutableStateOf(url) }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "REMOTE SOURCE HUB",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            "MANAGED HLS / RTMP / MP4 PIPELINES",
            color = CyberCyan.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // URL Input with Glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.4f))
                .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { 
                        textValue = it
                        onUrlChange(it)
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("https://source.m3u8", color = Color.Gray, fontSize = 13.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = CyberCyan
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    singleLine = true
                )
                
                IconButton(onClick = { 
                    clipboardManager.getText()?.text?.let { 
                        textValue = it
                        onUrlChange(it)
                    }
                }) {
                    Icon(Icons.Default.ContentPaste, "Paste", tint = CyberCyan, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onSaveStream(textValue) },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GlassDark),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Save, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("SAVE CHIP", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            
            Button(
                onClick = { onSelectStream(com.example.ui.MainViewModel.SavedStreamItem(name = "Live Test", url = textValue)) },
                modifier = Modifier.weight(1.2f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Icon(Icons.Default.Speed, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("TEST & ACTIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            "QUICK ACCESS LIBRARY",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Saved Streams as Horizontal Glass Cards
        savedStreams.forEach { stream ->
            Surface(
                onClick = { 
                    textValue = stream.url
                    onSelectStream(stream)
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                color = if (textValue == stream.url) CyberCyan.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp, 
                    if (textValue == stream.url) CyberCyan else Color.White.copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(6.dp).clip(CircleShape).background(if (textValue == stream.url) CyberCyan else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stream.name,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatTag("1080p HLS")
                            StatTag(if (stream.url.startsWith("rtmp")) "RTMP" else "HLS")
                        }
                    }
                    
                    Icon(
                        Icons.Default.ChevronRight,
                        null,
                        tint = if (textValue == stream.url) CyberCyan else Color.White.copy(alpha = 0.3f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("BACK TO STUDIO", color = Color.Black, fontWeight = FontWeight.Black)
        }
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
    onToggle: (String, Boolean) -> Unit,
    onEdit: (PlatformEndpoint) -> Unit,
    onAddCustom: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "STREAM DESTINATIONS",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Enable and configure where you want to broadcast live.",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        endpoints.forEach { ep ->
            DestinationGlassCard(
                endpoint = ep,
                onToggle = { onToggle(ep.id, it) },
                onEdit = { onEdit(ep) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onAddCustom,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, null, tint = CyberCyan, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("ADD CUSTOM RTMP", color = CyberCyan, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("CLOSE", color = Color.Black, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun DestinationGlassCard(
    endpoint: PlatformEndpoint,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isKeyVisible by remember { mutableStateOf(false) }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    val platformColor = when (endpoint.id.lowercase()) {
        "youtube" -> Color(0xFFFF0000)
        "twitch" -> Color(0xFF9146FF)
        "facebook" -> Color(0xFF1877F2)
        "tiktok" -> Color(0xFF000000)
        "kick" -> Color(0xFF53FC18)
        else -> Color(0xFF38BDF8)
    }

    val glowAlpha by animateFloatAsState(
        targetValue = if (endpoint.isEnabled) 0.15f else 0.05f,
        animationSpec = tween(500),
        label = "glow"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(platformColor.copy(alpha = glowAlpha))
            .border(
                1.dp, 
                if (endpoint.isEnabled) platformColor.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f), 
                RoundedCornerShape(24.dp)
            )
            .animateContentSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(platformColor.copy(alpha = 0.2f))
                        .border(1.dp, platformColor.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = when (endpoint.id.lowercase()) {
                        "youtube" -> Icons.Default.PlayCircle
                        "twitch" -> Icons.Default.Videocam
                        "facebook" -> Icons.Default.Facebook
                        "kick" -> Icons.Default.ElectricBolt
                        else -> Icons.Default.Lan
                    }
                    Icon(icon, null, tint = platformColor, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        endpoint.name.uppercase(),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                         Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (endpoint.isEnabled) Color(0xFF10B981) else Color.Gray))
                         Spacer(modifier = Modifier.width(6.dp))
                         Text(
                            if (endpoint.isEnabled) "ACTIVE" else "DISABLED",
                            color = if (endpoint.isEnabled) Color(0xFF10B981) else Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (endpoint.isEnabled) {
                            Spacer(modifier = Modifier.width(8.dp))
                            LatencyPill("24ms")
                        }
                    }
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = Color.White.copy(alpha = 0.3f)
                )
            }
        }

        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Server URL Field
                    DestinationField(
                        label = "RTMP INGEST URL",
                        value = endpoint.url,
                        onCopy = { clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(endpoint.url)) }
                    )
                    
                    // Stream Key Field
                    DestinationField(
                        label = "STREAM KEY",
                        value = if (isKeyVisible) endpoint.streamKey else "••••••••••••••••",
                        onCopy = { clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(endpoint.streamKey)) },
                        onToggleVisibility = { isKeyVisible = !isKeyVisible },
                        isKey = true,
                        isMasked = !isKeyVisible
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Button(
                        onClick = onEdit,
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("EDIT CREDENTIALS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
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
    onVersionClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var currentBitrate by remember { mutableFloatStateOf(config.videoQuality.defaultBitrate / 1000f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "PRO STUDIO SETTINGS",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            "ULTRAFAST HARDWARE ENCODER & UI OVERLAYS",
            color = CyberCyan.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Video Quality
        GlassSectionCard(title = "ENCODER PIPELINE") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("OUTPUT RESOLUTION", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                PillSelector(
                    options = listOf("720p HD", "1080p FHD", "9:16 Port"),
                    selectedIndex = when (config.videoQuality) {
                        com.example.core.model.VideoQuality.HD_720P -> 0
                        com.example.core.model.VideoQuality.FHD_1080P -> 1
                        else -> 2
                    },
                    onSelect = { index ->
                        val quality = when (index) {
                            0 -> com.example.core.model.VideoQuality.HD_720P
                            1 -> com.example.core.model.VideoQuality.FHD_1080P
                            else -> com.example.core.model.VideoQuality.HD_720P // Simplified for UI
                        }
                        onConfigChange(config.copy(videoQuality = quality))
                    }
                )

                Text("TARGET FRAMERATE", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                PillSelector(
                    options = listOf("30 FPS", "60 FPS"),
                    selectedIndex = if (config.videoQuality.fps == 60) 1 else 0,
                    onSelect = { /* In real app, update fps config */ }
                )

                // Bitrate Slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("VIDEO BITRATE", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("${currentBitrate.toInt()} kbps", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                    GlowingSlider(
                        value = currentBitrate,
                        onValueChange = { currentBitrate = it },
                        valueRange = 1500f..6000f
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                         Text("1500", color = Color.Gray, fontSize = 9.sp)
                         Text("6000", color = Color.Gray, fontSize = 9.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hardware Options
        GlassSectionCard(title = "CORE HARDWARE") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureToggleRow(
                    title = "MEDIACODEC HARDWARE ACCEL",
                    checked = true,
                    onCheckedChange = {}
                )
                FeatureToggleRow(
                    title = "ULTRAFAST x264 ENCODING",
                    checked = false,
                    onCheckedChange = {}
                )
                FeatureToggleRow(
                    title = "AAC-LC HIGH FIDELITY AUDIO",
                    checked = true,
                    onCheckedChange = {}
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Pro Features
        GlassSectionCard(title = "BROADCAST OVERLAYS") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureToggleRow(
                    title = "WATERMARK LOGO",
                    checked = isWatermarkEnabled,
                    onCheckedChange = onWatermarkToggle
                )
                FeatureToggleRow(
                    title = "SCROLLING TICKER",
                    checked = isTickerEnabled,
                    onCheckedChange = onTickerToggle
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(16.dp)
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

