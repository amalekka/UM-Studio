package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.AspectRatioMode
import com.example.core.model.PlatformEndpoint
import com.example.core.model.VideoCodec
import com.example.core.model.VideoQuality
import com.example.ui.MainViewModel

import com.example.ui.components.*

@Composable
fun EndpointsConfigScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    SettingsBackground {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                "DESTINATIONS", 
                style = MaterialTheme.typography.headlineSmall, 
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 2.sp
            )
            Text(
                "MULTI-PLATFORM SIMULCAST ACTIVE", 
                style = MaterialTheme.typography.labelSmall, 
                color = Color(0xFF818CF8)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Multi-Platform Section
                item {
                    GlassSectionCard(title = "SIMULTANEOUS RESTREAMING") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.endpoints.filter { it !is PlatformEndpoint.CustomRTMP }.forEach { endpoint ->
                                PlatformToggleRow(
                                    endpoint = endpoint,
                                    onToggle = { enabled ->
                                        val newList = uiState.endpoints.map { if (it.name == endpoint.name) updateEndpointEnabled(it, enabled) else it }
                                        viewModel.updateEndpoints(newList)
                                    },
                                    onKeyChange = { key ->
                                        val newList = uiState.endpoints.map { if (it.name == endpoint.name) updateEndpointKey(it, key) else it }
                                        viewModel.updateEndpoints(newList)
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Custom RTMP Section
                val customRtmps = uiState.endpoints.filterIsInstance<PlatformEndpoint.CustomRTMP>()
                if (customRtmps.isNotEmpty()) {
                    item {
                        GlassSectionCard(title = "RTMP CONFIGURATION") {
                            customRtmps.forEach { rtmp ->
                                CustomRtmpBlock(
                                    rtmp = rtmp,
                                    onUpdate = { updated ->
                                        val newList = uiState.endpoints.map { if (it.name == rtmp.name) updated else it }
                                        viewModel.updateEndpoints(newList)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdvancedSettingsScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    SettingsBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "STREAM ENGINE", 
                style = MaterialTheme.typography.headlineSmall, 
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 2.sp
            )
            Text(
                "HARDWARE OPTIMIZED BROADCAST", 
                style = MaterialTheme.typography.labelSmall, 
                color = Color(0xFF38BDF8)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Video Quality Card
            GlassSectionCard(title = "VIDEO QUALITY") {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    // Resolution Segmented
                    Text("RESOLUTION & FRAME RATE", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    PillSelector(
                        options = VideoQuality.entries.map { it.name.replace("_", " ") },
                        selectedIndex = VideoQuality.entries.indexOf(uiState.streamConfig.videoQuality),
                        onSelect = { index ->
                            viewModel.updateConfig(uiState.streamConfig.copy(videoQuality = VideoQuality.entries[index]))
                        }
                    )
                    
                    // Aspect Ratio Segmented
                    Text("ASPECT RATIO", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    val ratios = listOf(
                        AspectRatioMode.LANDSCAPE_16_9 to "16:9",
                        AspectRatioMode.PORTRAIT_9_16 to "9:16",
                        AspectRatioMode.SQUARE_1_1 to "1:1",
                        AspectRatioMode.CLASSIC_4_3 to "4:3"
                    )
                    PillSelector(
                        options = ratios.map { it.second },
                        selectedIndex = ratios.indexOfFirst { it.first == uiState.streamConfig.aspectRatio },
                        onSelect = { index ->
                            viewModel.updateConfig(uiState.streamConfig.copy(aspectRatio = ratios[index].first))
                        }
                    )
                    
                    // Bitrate Slider
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("TARGET BITRATE", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("${uiState.streamConfig.videoQuality.defaultBitrate / 1000} KBPS", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                        GlowingSlider(
                            value = uiState.streamConfig.videoQuality.defaultBitrate.toFloat(),
                            onValueChange = { /* In real app, update specific config value */ },
                            valueRange = 2000000f..12000000f
                        )
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("2000", color = Color.Gray, fontSize = 9.sp)
                            Text("12000", color = Color.Gray, fontSize = 9.sp)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Hardware Encoder Card
            GlassSectionCard(
                title = "HARDWARE ENCODER",
                trailing = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x2E10B981))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("RECOMMENDED", color = Color(0xFF34D399), fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            ) {
                GlassDropdown(
                    label = "ENCODING STANDARD",
                    value = uiState.streamConfig.videoCodec.name,
                    options = VideoCodec.entries.map { it.name },
                    onSelect = { index ->
                        viewModel.updateConfig(uiState.streamConfig.copy(videoCodec = VideoCodec.entries[index]))
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Pro Features Card
            GlassSectionCard(title = "PRO OVERLAYS") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FeatureToggleRow(
                        title = "WATERMARK LOGO",
                        checked = uiState.isWatermarkEnabled,
                        onCheckedChange = { viewModel.toggleWatermark(it, context) }
                    )
                    FeatureToggleRow(
                        title = "SCROLLING TICKER",
                        checked = uiState.isTickerEnabled,
                        onCheckedChange = { viewModel.toggleTicker(it) }
                    )
                    FeatureToggleRow(
                        title = "STEALTH BACKGROUND STREAM",
                        checked = true,
                        onCheckedChange = { /* viewModel.toggleBackgroundMode(it) */ }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Footer
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "VERSION 2.4.5 PRO BUILD", 
                    modifier = Modifier.clickable { viewModel.onVersionClick() },
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.3f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                if (uiState.developerModeEnabled) {
                    Text("DEV MODE UNLOCKED", color = Color(0xFFEF4444), fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
            
            if (uiState.developerModeEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                // Advanced Pro Menu
                GlassSectionCard(title = "HIDDEN PRO MENU") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("ENGINE OVERRIDES", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("FORCED GOP (KEYFRAMES)", color = Color.White, fontSize = 13.sp)
                            Text("2.0s", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                        }
                        
                        FeatureToggleRow(
                            title = "DNS OVER HTTPS (DoH)",
                            checked = true,
                            onCheckedChange = {}
                        )
                        
                        FeatureToggleRow(
                            title = "UDP PACKET SPOOFING",
                            checked = false,
                            onCheckedChange = {}
                        )

                        Text("AUDIO DSP", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        
                        FeatureToggleRow(
                            title = "NOISE GATE (AI)",
                            checked = true,
                            onCheckedChange = {}
                        )
                        
                        FeatureToggleRow(
                            title = "ECHO CANCELLATION",
                            checked = true,
                            onCheckedChange = {}
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("REAL-TIME SOCKET LOGS", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Box(modifier = Modifier.height(120.dp).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                            LazyColumn {
                                items(uiState.logs) { log ->
                                    Text(log, color = Color(0xFF34D399), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun PlatformToggleRow(
    endpoint: PlatformEndpoint,
    onToggle: (Boolean) -> Unit,
    onKeyChange: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when(endpoint) {
                    is PlatformEndpoint.YouTube -> Icons.Default.PlayCircle
                    is PlatformEndpoint.Facebook -> Icons.Default.Facebook
                    is PlatformEndpoint.Twitch -> Icons.Default.Tv
                    else -> Icons.Default.Link
                }
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(endpoint.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            GlowingToggle(checked = endpoint.isEnabled, onCheckedChange = onToggle)
        }
        
        if (isExpanded) {
            Spacer(modifier = Modifier.height(8.dp))
            GlassTextField(
                value = endpoint.streamKey,
                onValueChange = onKeyChange,
                label = "STREAM KEY",
                isPassword = true
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun CustomRtmpBlock(rtmp: PlatformEndpoint.CustomRTMP, onUpdate: (PlatformEndpoint.CustomRTMP) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassTextField(
            value = rtmp.url,
            onValueChange = { onUpdate(rtmp.copy(url = it)) },
            label = "SERVER URL"
        )
        GlassTextField(
            value = rtmp.streamKey,
            onValueChange = { onUpdate(rtmp.copy(streamKey = it)) },
            label = "STREAM KEY",
            isPassword = true
        )
    }
}


private fun updateEndpointEnabled(endpoint: PlatformEndpoint, enabled: Boolean): PlatformEndpoint = when (endpoint) {
    is PlatformEndpoint.YouTube -> endpoint.copy(isEnabled = enabled)
    is PlatformEndpoint.Facebook -> endpoint.copy(isEnabled = enabled)
    is PlatformEndpoint.Twitch -> endpoint.copy(isEnabled = enabled)
    is PlatformEndpoint.TikTok -> endpoint.copy(isEnabled = enabled)
    is PlatformEndpoint.Twitter -> endpoint.copy(isEnabled = enabled)
    is PlatformEndpoint.CustomRTMP -> endpoint.copy(isEnabled = enabled)
}

private fun updateEndpointKey(endpoint: PlatformEndpoint, key: String): PlatformEndpoint = when (endpoint) {
    is PlatformEndpoint.YouTube -> endpoint.copy(streamKey = key)
    is PlatformEndpoint.Facebook -> endpoint.copy(streamKey = key)
    is PlatformEndpoint.Twitch -> endpoint.copy(streamKey = key)
    is PlatformEndpoint.TikTok -> endpoint.copy(streamKey = key)
    is PlatformEndpoint.Twitter -> endpoint.copy(streamKey = key)
    is PlatformEndpoint.CustomRTMP -> endpoint.copy(streamKey = key)
}
