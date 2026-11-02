package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.streaming.StreamSource
import com.example.ui.MainViewModel
import com.example.ui.components.GlassTextField
import com.example.ui.components.SettingsBackground

@Composable
fun M3u8StreamsScreen(
    viewModel: MainViewModel,
    onNavigateToStudio: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Form states for new stream
    var newServerName by remember { mutableStateOf("") }
    var newServerUrl by remember { mutableStateOf("") }
    var newUserAgent by remember { mutableStateOf("") }
    var newReferer by remember { mutableStateOf("") }
    var showAdvancedHeaders by remember { mutableStateOf(false) }

    // Edit dialog state
    var editingStream by remember { mutableStateOf<MainViewModel.SavedStreamItem?>(null) }

    SettingsBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 20.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Screen Title
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "M3U8 STREAMS",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "HLS RESTREAM ENGINE",
                            color = Color(0xFF10B981),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Quick Switch to Studio Preview
                    Surface(
                        onClick = onNavigateToStudio,
                        color = Color(0x3338BDF8),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Dvr, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            Text("STUDIO", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 1. ACTIVE STREAM CONTROLLER CARD (TOP)
            item {
                ActiveStreamControllerCard(
                    uiState = uiState,
                    onPlayResume = {
                        viewModel.toggleSource(StreamSource.M3U8)
                        if (uiState.m3u8State == MainViewModel.M3u8PlaybackState.PAUSED) {
                            viewModel.resumeM3u8()
                        } else {
                            viewModel.playM3u8()
                        }
                        Toast.makeText(context, "Playing M3U8 Stream on Studio", Toast.LENGTH_SHORT).show()
                    },
                    onPause = {
                        viewModel.pauseM3u8()
                        Toast.makeText(context, "Playback Paused", Toast.LENGTH_SHORT).show()
                    },
                    onForceStop = {
                        viewModel.stopM3u8Stream()
                        Toast.makeText(context, "Background Audio & Stream Destroyed", Toast.LENGTH_SHORT).show()
                    },
                    onVolumeChange = { viewModel.setM3u8Volume(it) },
                    onToggleMute = { viewModel.toggleM3u8Mute() },
                    onCopyUrl = {
                        clipboardManager.setText(AnnotatedString(uiState.m3u8Url))
                        Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // 2. ADD NEW M3U8 SERVER FORM (MIDDLE)
            item {
                AddM3u8ServerFormCard(
                    serverName = newServerName,
                    onServerNameChange = { newServerName = it },
                    serverUrl = newServerUrl,
                    onServerUrlChange = { newServerUrl = it },
                    userAgent = newUserAgent,
                    onUserAgentChange = { newUserAgent = it },
                    referer = newReferer,
                    onRefererChange = { newReferer = it },
                    showAdvanced = showAdvancedHeaders,
                    onToggleAdvanced = { showAdvancedHeaders = !showAdvancedHeaders },
                    onPasteUrl = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrBlank()) {
                            newServerUrl = clip.trim()
                            Toast.makeText(context, "URL Pasted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSaveServer = {
                        if (newServerName.isBlank() || newServerUrl.isBlank()) {
                            Toast.makeText(context, "Please enter Server Name and M3U8 URL", Toast.LENGTH_SHORT).show()
                            return@AddM3u8ServerFormCard
                        }
                        viewModel.saveStream(
                            name = newServerName.trim(),
                            url = newServerUrl.trim(),
                            userAgent = newUserAgent.trim(),
                            referer = newReferer.trim()
                        )
                        newServerName = ""
                        newServerUrl = ""
                        newUserAgent = ""
                        newReferer = ""
                        showAdvancedHeaders = false
                        Toast.makeText(context, "Server saved successfully!", Toast.LENGTH_SHORT).show()
                    },
                    onLoadPreset = { name, url ->
                        newServerName = name
                        newServerUrl = url
                    }
                )
            }

            // 3. SAVED M3U8 SERVERS LIST HEADER
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SAVED M3U8 SERVERS (${uiState.savedStreams.size})",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "ONE-TAP RELAY",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // SAVED SERVERS ITEMS
            if (uiState.savedStreams.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        color = Color(0x80131A2A),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Dns, null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No M3U8 Servers Saved", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Add your favorite live HLS streams above for quick playback.", color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                items(uiState.savedStreams, key = { it.id }) { stream ->
                    val isCurrentActive = stream.url == uiState.m3u8Url && uiState.m3u8State != MainViewModel.M3u8PlaybackState.STOPPED
                    SavedStreamCard(
                        stream = stream,
                        isActive = isCurrentActive,
                        isPlaying = isCurrentActive && uiState.m3u8State == MainViewModel.M3u8PlaybackState.PLAYING,
                        onLoadAndPlay = {
                            viewModel.selectSavedStream(stream)
                            viewModel.toggleSource(StreamSource.M3U8)
                            Toast.makeText(context, "Loaded & Streaming: ${stream.name}", Toast.LENGTH_SHORT).show()
                            onNavigateToStudio()
                        },
                        onStop = {
                            viewModel.stopM3u8Stream()
                            Toast.makeText(context, "Stopped ${stream.name}", Toast.LENGTH_SHORT).show()
                        },
                        onEdit = { editingStream = stream },
                        onDelete = {
                            viewModel.deleteStream(stream.id)
                            Toast.makeText(context, "Server removed", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    // Edit Stream Dialog
    editingStream?.let { stream ->
        EditM3u8StreamDialog(
            stream = stream,
            onDismiss = { editingStream = null },
            onSave = { name, url, userAgent, referer ->
                viewModel.updateSavedStream(
                    id = stream.id,
                    name = name,
                    url = url,
                    userAgent = userAgent,
                    referer = referer
                )
                editingStream = null
                Toast.makeText(context, "Stream updated", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun ActiveStreamControllerCard(
    uiState: MainViewModel.UiState,
    onPlayResume: () -> Unit,
    onPause: () -> Unit,
    onForceStop: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onCopyUrl: () -> Unit
) {
    val statusColor = when (uiState.m3u8State) {
        MainViewModel.M3u8PlaybackState.PLAYING -> Color(0xFF10B981)
        MainViewModel.M3u8PlaybackState.PAUSED -> Color(0xFFF59E0B)
        MainViewModel.M3u8PlaybackState.STOPPED -> Color(0xFFEF4444)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xF20F1626),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Status badge and timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val pulseAlpha by rememberInfiniteTransition().animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "PulseM3u8"
                    )

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (uiState.m3u8State == MainViewModel.M3u8PlaybackState.PLAYING)
                                    statusColor.copy(alpha = pulseAlpha)
                                else statusColor
                            )
                    )

                    Text(
                        text = "STATUS: ${uiState.m3u8State.name}",
                        color = statusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }

                // Playback / Stream Timer
                Surface(
                    color = Color(0x3310B981),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Timer, null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                        Text(
                            text = formatTime(uiState.streamTime),
                            color = Color(0xFF10B981),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Active Stream Info
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = uiState.activeM3u8Name.ifBlank { "No Stream Loaded" },
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x60090D16))
                        .clickable { onCopyUrl() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.m3u8Url.ifBlank { "https://" },
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Default.ContentCopy, "Copy URL", tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                }
            }

            // Audio Volume Slider Control
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            if (uiState.isM3u8Muted || uiState.m3u8Volume == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            null,
                            tint = if (uiState.isM3u8Muted) Color(0xFFEF4444) else Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "AUDIO VOLUME",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (uiState.isM3u8Muted) "MUTED" else "${(uiState.m3u8Volume * 100).toInt()}%",
                            color = if (uiState.isM3u8Muted) Color(0xFFEF4444) else Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                        Surface(
                            onClick = onToggleMute,
                            color = if (uiState.isM3u8Muted) Color(0x33EF4444) else Color(0x3338BDF8),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (uiState.isM3u8Muted) "UNMUTE" else "MUTE",
                                color = if (uiState.isM3u8Muted) Color(0xFFEF4444) else Color(0xFF38BDF8),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Slider(
                    value = if (uiState.isM3u8Muted) 0f else uiState.m3u8Volume,
                    onValueChange = onVolumeChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF38BDF8),
                        activeTrackColor = Color(0xFF38BDF8),
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.fillMaxWidth().height(28.dp)
                )
            }

            // 3 Large Glowing Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button 1: PLAY / RESUME (Green Glow)
                Button(
                    onClick = onPlayResume,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .shadow(10.dp, RoundedCornerShape(12.dp), ambientColor = Color(0xFF10B981), spotColor = Color(0xFF10B981)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (uiState.m3u8State == MainViewModel.M3u8PlaybackState.PAUSED) "RESUME" else "PLAY",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Button 2: PAUSE (Yellow Glow)
                Button(
                    onClick = onPause,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .shadow(10.dp, RoundedCornerShape(12.dp), ambientColor = Color(0xFFF59E0B), spotColor = Color(0xFFF59E0B)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                    shape = RoundedCornerShape(12.dp),
                    enabled = uiState.m3u8State == MainViewModel.M3u8PlaybackState.PLAYING,
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(Icons.Default.Pause, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "PAUSE",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Button 3: FORCE STOP & KILL BACKGROUND AUDIO (Red Glow)
                Button(
                    onClick = onForceStop,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .shadow(12.dp, RoundedCornerShape(12.dp), ambientColor = Color(0xFFEF4444), spotColor = Color(0xFFEF4444)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "FORCE STOP",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
fun AddM3u8ServerFormCard(
    serverName: String,
    onServerNameChange: (String) -> Unit,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    userAgent: String,
    onUserAgentChange: (String) -> Unit,
    referer: String,
    onRefererChange: (String) -> Unit,
    showAdvanced: Boolean,
    onToggleAdvanced: () -> Unit,
    onPasteUrl: () -> Unit,
    onSaveServer: () -> Unit,
    onLoadPreset: (String, String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xF20F1626),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AddCircleOutline, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    Text(
                        text = "ADD NEW M3U8 SERVER",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Quick Preset Samples
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "QUICK TEST PRESETS (TAP TO LOAD)",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        onClick = { onLoadPreset("Big Buck Bunny HLS", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8") },
                        color = Color(0x3338BDF8),
                        shape = CircleShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Big Buck Bunny",
                            color = Color(0xFF38BDF8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                    Surface(
                        onClick = { onLoadPreset("Akamai Live HLS", "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8") },
                        color = Color(0x33818CF8),
                        shape = CircleShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Akamai Live",
                            color = Color(0xFF818CF8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                    Surface(
                        onClick = { onLoadPreset("Tears of Steel HLS", "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8") },
                        color = Color(0x3334D399),
                        shape = CircleShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Tears of Steel",
                            color = Color(0xFF34D399),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }

            // Server Name Input
            GlassTextField(
                value = serverName,
                onValueChange = onServerNameChange,
                label = "SERVER / STREAM NAME (e.g., Live Sport Feed)"
            )

            // Server URL Input with Paste & Clear Buttons
            GlassTextField(
                value = serverUrl,
                onValueChange = onServerUrlChange,
                label = "M3U8 / HLS URL (.m3u8)",
                trailingIcon = {
                    Row {
                        IconButton(onClick = onPasteUrl) {
                            Icon(Icons.Default.ContentPaste, "Paste", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                        }
                        if (serverUrl.isNotEmpty()) {
                            IconButton(onClick = { onServerUrlChange("") }) {
                                Icon(Icons.Default.Clear, "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            )

            // Advanced Headers Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleAdvanced() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "OPTIONAL HTTP HEADERS (USER-AGENT / REFERER)",
                    color = Color(0xFF818CF8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = Color(0xFF818CF8),
                    modifier = Modifier.size(16.dp)
                )
            }

            if (showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassTextField(
                        value = userAgent,
                        onValueChange = onUserAgentChange,
                        label = "USER-AGENT (OPTIONAL)"
                    )
                    GlassTextField(
                        value = referer,
                        onValueChange = onRefererChange,
                        label = "REFERER (OPTIONAL)"
                    )
                }
            }

            // Save Button
            Button(
                onClick = onSaveServer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .shadow(10.dp, RoundedCornerShape(12.dp), ambientColor = Color(0xFF38BDF8), spotColor = Color(0xFF38BDF8)),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null, tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "+ SAVE SERVER TO PLAYLIST",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun SavedStreamCard(
    stream: MainViewModel.SavedStreamItem,
    isActive: Boolean,
    isPlaying: Boolean,
    onLoadAndPlay: () -> Unit,
    onStop: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xF20F1626),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (isActive) Color(0xFF10B981) else Color.White.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Name, Active Indicator, Edit, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Dns,
                        null,
                        tint = if (isActive) Color(0xFF10B981) else Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = stream.name,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isActive) {
                            Text(
                                text = if (isPlaying) "CURRENTLY PLAYING" else "CURRENTLY LOADED",
                                color = Color(0xFF10B981),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, "Edit", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Stream URL
            Text(
                text = stream.url,
                color = Color.Gray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x40000000))
                    .padding(8.dp)
            )

            // Optional Header Chips
            if (stream.userAgent.isNotBlank() || stream.referer.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (stream.userAgent.isNotBlank()) {
                        Surface(
                            color = Color(0x33818CF8),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "UA: ${stream.userAgent}",
                                color = Color(0xFF818CF8),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (stream.referer.isNotBlank()) {
                        Surface(
                            color = Color(0x3334D399),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "REF: ${stream.referer}",
                                color = Color(0xFF34D399),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Action Buttons Row: Load & Play on Studio, Stop
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onLoadAndPlay,
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("LOAD & PLAY ON STUDIO", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }

                if (isActive) {
                    Button(
                        onClick = onStop,
                        modifier = Modifier.height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("STOP", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun EditM3u8StreamDialog(
    stream: MainViewModel.SavedStreamItem,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, userAgent: String, referer: String) -> Unit
) {
    var editName by remember { mutableStateOf(stream.name) }
    var editUrl by remember { mutableStateOf(stream.url) }
    var editUserAgent by remember { mutableStateOf(stream.userAgent) }
    var editReferer by remember { mutableStateOf(stream.referer) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF0F1626),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("EDIT M3U8 SERVER", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, null, tint = Color.Gray)
                    }
                }

                GlassTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = "SERVER NAME"
                )

                GlassTextField(
                    value = editUrl,
                    onValueChange = { editUrl = it },
                    label = "M3U8 URL"
                )

                GlassTextField(
                    value = editUserAgent,
                    onValueChange = { editUserAgent = it },
                    label = "USER-AGENT (OPTIONAL)"
                )

                GlassTextField(
                    value = editReferer,
                    onValueChange = { editReferer = it },
                    label = "REFERER (OPTIONAL)"
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("CANCEL", color = Color.Gray, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (editName.isNotBlank() && editUrl.isNotBlank()) {
                                onSave(editName.trim(), editUrl.trim(), editUserAgent.trim(), editReferer.trim())
                            }
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("SAVE CHANGES", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
