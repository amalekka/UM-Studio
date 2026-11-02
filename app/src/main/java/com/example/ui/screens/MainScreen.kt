package com.example.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.streaming.StreamSource
import com.example.core.streaming.StreamingStatus
import com.example.ui.MainViewModel
import com.example.ui.components.*
import com.pedro.library.view.OpenGlView

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var currentTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0D111C),
                contentColor = Color.White,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Dvr, "Studio") },
                    label = { Text("Studio") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF38BDF8),
                        selectedTextColor = Color(0xFF38BDF8),
                        indicatorColor = Color(0x3338BDF8),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Share, "Destinations") },
                    label = { Text("Destinations") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF818CF8),
                        selectedTextColor = Color(0xFF818CF8),
                        indicatorColor = Color(0x33818CF8),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.Settings, "Settings") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFA855F7),
                        selectedTextColor = Color(0xFFA855F7),
                        indicatorColor = Color(0x33A855F7),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentTab) {
                0 -> StudioPreviewScreen(viewModel)
                1 -> EndpointsConfigScreen(viewModel)
                2 -> AdvancedSettingsScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioPreviewScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    var showSourceSheet by remember { mutableStateOf(false) }
    var showM3u8Dialog by remember { mutableStateOf(false) }
    var showBackgroundSheet by remember { mutableStateOf(false) }
    var isChatVisible by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState()
    val bgSheetState = rememberModalBottomSheetState()

    // 1. OpenGL Preview Area (Persistent)
    val openGlView = remember {
        OpenGlView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    DisposableEffect(openGlView) {
        val callback = object : android.view.SurfaceHolder.Callback {
            override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                openGlView.post {
                    viewModel.initPreview(openGlView)
                }
            }
            override fun surfaceChanged(h: android.view.SurfaceHolder, f: Int, w: Int, h1: Int) {}
            override fun surfaceDestroyed(h: android.view.SurfaceHolder) {
                viewModel.stopPreview()
            }
        }
        openGlView.holder.addCallback(callback)
        onDispose {
            openGlView.holder.removeCallback(callback)
            viewModel.stopPreview()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Video Viewport
        AndroidView(
            factory = { openGlView },
            modifier = Modifier.fillMaxSize()
        )

        // Top & Bottom Vignette
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                    )
                )
        )

        // MAIN OVERLAY
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ROW 1: LIVE BADGE & SETTINGS
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassPill(glowColor = if (uiState.isStreaming) Color(0xFFEF4444) else Color.Gray) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isStreaming) Color(0xFFEF4444) else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.isStreaming) "LIVE" else "OFFLINE",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = formatTime(uiState.streamTime),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassPill {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Icon(Icons.Default.CloudQueue, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { /* Settings Action */ },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xBF1E2330))
                    ) {
                        Icon(Icons.Default.Settings, null, tint = Color.White.copy(alpha = 0.8f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ROW 2: STATS PILLS
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill(Icons.Default.Person, "${uiState.viewerCount}")
                StatPill(Icons.Default.Speed, "${uiState.currentBitrate} kbps")
                StatPill(Icons.Default.GraphicEq, "${uiState.fps} FPS")
            }

            Spacer(modifier = Modifier.weight(1f))

            // MIDDLE: AUDIO VU METER (LEFT)
            Box(modifier = Modifier.fillMaxWidth().weight(2f)) {
                DualAudioVU(
                    modifier = Modifier.align(Alignment.CenterStart),
                    isMuted = !uiState.isMicEnabled
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // BOTTOM DECK: CHAT & ACTIONS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // LEFT: CHAT (45%)
                if (isChatVisible) {
                    GlassChat(modifier = Modifier.weight(0.45f))
                } else {
                    Spacer(modifier = Modifier.weight(0.01f))
                }

                // RIGHT: ACTION DECK (55%)
                Column(
                    modifier = Modifier.weight(0.55f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // GO LIVE CAPSULE
                    GoLiveButton(
                        isStreaming = uiState.isStreaming,
                        onClick = {
                            if (uiState.isStreaming) {
                                viewModel.stopStream()
                            } else {
                                if (uiState.currentSource == StreamSource.SCREEN) {
                                    (context as? com.example.MainActivity)?.requestScreenCapture()
                                } else {
                                    viewModel.startStream(context)
                                }
                            }
                        }
                    )

                    // ACTION GRID 2x3
                    ActionGrid(
                        isMicEnabled = uiState.isMicEnabled,
                        onMicToggle = { viewModel.toggleMic(!uiState.isMicEnabled) },
                        onFlip = { viewModel.switchCamera() },
                        onSource = { showSourceSheet = true },
                        onFlash = { /* Flash Toggle if needed */ },
                        onChat = { isChatVisible = !isChatVisible },
                        onBg = { showBackgroundSheet = true }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Reuse Sheets & Dialogs
    if (showBackgroundSheet) {
        ModalBottomSheet(onDismissRequest = { showBackgroundSheet = false }, sheetState = bgSheetState) {
            BackgroundStudioSheet(viewModel = viewModel, onDismiss = { showBackgroundSheet = false })
        }
    }
    if (showSourceSheet) {
        ModalBottomSheet(onDismissRequest = { showSourceSheet = false }, sheetState = sheetState) {
            Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
                Text("INPUT SOURCE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(16.dp))
                SourceOptionItem("CAMERA", "Direct broadcast", Icons.Default.CameraAlt, uiState.currentSource == StreamSource.CAMERA) { 
                    viewModel.toggleSource(StreamSource.CAMERA); showSourceSheet = false 
                }
                SourceOptionItem("SCREEN", "Capture device display", Icons.Default.Screenshot, uiState.currentSource == StreamSource.SCREEN) { 
                    viewModel.toggleSource(StreamSource.SCREEN); showSourceSheet = false 
                }
                SourceOptionItem("REMOTE URL", "Restream M3U8/HLS", Icons.Default.Link, uiState.currentSource == StreamSource.M3U8) { 
                    viewModel.toggleSource(StreamSource.M3U8); showSourceSheet = false; showM3u8Dialog = true 
                }
            }
        }
    }

    if (showM3u8Dialog) {
        var url by remember { mutableStateOf(uiState.m3u8Url) }
        var userAgent by remember { mutableStateOf("") }
        var referer by remember { mutableStateOf("") }
        
        androidx.compose.ui.window.Dialog(onDismissRequest = { showM3u8Dialog = false }) {
            GlassSectionCard(
                title = "REMOTE STREAM INGESTION",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    GlassTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = "STREAM URL (M3U8 / HLS / DASH / TS)",
                        trailingIcon = {
                            Icon(Icons.Default.Link, null, tint = Color.Cyan.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                        }
                    )
                    
                    Text("ADVANCED HTTP HEADERS", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassTextField(
                            value = userAgent,
                            onValueChange = { userAgent = it },
                            label = "USER-AGENT",
                            modifier = Modifier.weight(1f)
                        )
                        GlassTextField(
                            value = referer,
                            onValueChange = { referer = it },
                            label = "REFERER",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    FeatureToggleRow(
                        title = "REACTION CAMERA OVERLAY",
                        checked = true,
                        onCheckedChange = {}
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = { 
                            val headers = mutableMapOf<String, String>()
                            if (userAgent.isNotEmpty()) headers["User-Agent"] = userAgent
                            if (referer.isNotEmpty()) headers["Referer"] = referer
                            viewModel.setM3u8Source(url, headers)
                            showM3u8Dialog = false 
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("INJECT ENGINE", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                    
                    TextButton(
                        onClick = { showM3u8Dialog = false },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("CANCEL", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun GlassPill(
    glowColor: Color = Color.Transparent,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .then(if (glowColor != Color.Transparent) Modifier.shadow(8.dp, CircleShape, ambientColor = glowColor, spotColor = glowColor) else Modifier),
        color = Color(0xBF1E2330),
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        content()
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
fun DualAudioVU(modifier: Modifier = Modifier, isMuted: Boolean) {
    val barHeight = 160.dp
    val barWidth = 6.dp
    
    Surface(
        modifier = modifier
            .width(42.dp)
            .height(barHeight + 40.dp)
            .padding(start = 4.dp),
        color = Color(0xBF1E2330),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("L", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                Text("R", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VuBar(isMuted)
                VuBar(isMuted)
            }
        }
    }
}

@Composable
fun VuBar(isMuted: Boolean) {
    val height = 140.dp
    Box(
        modifier = Modifier
            .width(6.dp)
            .height(height)
            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
    ) {
        if (!isMuted) {
            // Animated level
            val level by rememberInfiniteTransition().animateFloat(
                initialValue = 0.3f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(level)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFF10B981))
                        ),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

@Composable
fun GlassChat(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = Color(0xBF1E2330),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("LIVE CHAT", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Waiting for live comments...",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ChatItem(user: String, message: String, color: Color) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(user, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(message, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}

@Composable
fun GoLiveButton(isStreaming: Boolean, onClick: () -> Unit) {
    val glowColor = if (isStreaming) Color.Gray else Color(0xFFEF4444)
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(12.dp, RoundedCornerShape(28.dp), ambientColor = glowColor, spotColor = glowColor),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isStreaming) Color(0xFF374151) else Color(0xFFEF4444)
        ),
        shape = CircleShape
    ) {
        Icon(if (isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow, null, tint = Color.White)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            if (isStreaming) "TERMINATE" else "GO LIVE",
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = Color.White
        )
    }
}

@Composable
fun ActionGrid(
    isMicEnabled: Boolean,
    onMicToggle: () -> Unit,
    onFlip: () -> Unit,
    onSource: () -> Unit,
    onFlash: () -> Unit,
    onChat: () -> Unit,
    onBg: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionIcon(Icons.Default.Mic, "MIC", isMicEnabled, Color(0xFF06B6D4), onMicToggle)
            ActionIcon(Icons.Default.FlipCameraAndroid, "FLIP", true, Color(0xFF10B981), onFlip)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionIcon(Icons.Default.VideoLibrary, "SRC", true, Color(0xFFF59E0B), onSource)
            ActionIcon(Icons.Default.FlashOn, "FLASH", false, Color(0xFFFFD700), onFlash)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionIcon(Icons.Default.ChatBubble, "CHAT", true, Color(0xFFA855F7), onChat)
            ActionIcon(Icons.Default.Image, "BG IMG", true, Color(0xFF34D399), onBg)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundStudioSheet(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val config = uiState.bgConfig
    
    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let { viewModel.updateBackgroundConfig(config.copy(imageUri = it.toString())) }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("BACKGROUND STUDIO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(16.dp))

        // Image Dropzone / Picker
        Surface(
            onClick = { photoPickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            color = Color(0xFF0D111C),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A324B).copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().height(120.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.AddPhotoAlternate, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(if (config.imageUri != null) "CHANGE BACKGROUND" else "UPLOAD CANVAS IMAGE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (config.imageUri != null) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { viewModel.updateBackgroundConfig(config.copy(imageUri = null)) }, modifier = Modifier.align(Alignment.End)) {
                Text("REMOVE BACKGROUND", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Layer Modes & Controls
        GlassSectionCard(title = "CANVAS CONTROLS") {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                // Scale Mode
                Column {
                    Text("SCALE MODE", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    PillSelector(
                        options = MainViewModel.BackgroundScaleMode.entries.map { it.name },
                        selectedIndex = MainViewModel.BackgroundScaleMode.entries.indexOf(config.scaleMode),
                        onSelect = { index ->
                            viewModel.updateBackgroundConfig(config.copy(scaleMode = MainViewModel.BackgroundScaleMode.entries[index]))
                        }
                    )
                }

                // Blur Slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("GAUSSIAN BLUR", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("${config.blurRadius.toInt()}px", color = Color(0xFF38BDF8), fontSize = 11.sp)
                    }
                    GlowingSlider(
                        value = config.blurRadius,
                        onValueChange = { viewModel.updateBackgroundConfig(config.copy(blurRadius = it)) },
                        valueRange = 0f..30f
                    )
                }

                // Brightness Slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("CANVAS BRIGHTNESS", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("${(config.brightness * 100).toInt()}%", color = Color(0xFF38BDF8), fontSize = 11.sp)
                    }
                    GlowingSlider(
                        value = config.brightness,
                        onValueChange = { viewModel.updateBackgroundConfig(config.copy(brightness = it)) },
                        valueRange = 0.1f..1f
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // PiP Controls
        GlassSectionCard(title = "FOREGROUND VIDEO PiP") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("VIDEO SCALE", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("${(config.foregroundScale * 100).toInt()}%", color = Color(0xFF818CF8), fontSize = 11.sp)
                }
                GlowingSlider(
                    value = config.foregroundScale,
                    onValueChange = { viewModel.updateBackgroundConfig(config.copy(foregroundScale = it)) },
                    valueRange = 0.5f..1f
                )

                FeatureToggleRow(
                    title = "BRB / PAUSE SHIELD",
                    checked = config.isPauseShieldActive,
                    onCheckedChange = { viewModel.updateBackgroundConfig(config.copy(isPauseShieldActive = it)) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0x3338BDF8)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("CLOSE STUDIO", color = Color(0xFF38BDF8), fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun ActionIcon(icon: ImageVector, label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(height = 54.dp, width = 74.dp),
        color = Color(0xBF1E2330),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) accent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = if (active) accent else Color.Gray, modifier = Modifier.size(18.dp))
            Text(label, color = if (active) Color.White else Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}

@Composable
fun HudStatRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun ControlButton(icon: ImageVector, label: String, active: Boolean = true, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (active) MaterialTheme.colorScheme.primary else Color.Gray
            )
        ) {
            Icon(icon, contentDescription = label)
        }
        Text(label, fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
fun SourceOptionItem(title: String, subtitle: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) Color(0xFF10B981).copy(alpha = 0.2f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (selected) Color(0xFF10B981) else Color.Gray)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Black, color = if (selected) Color(0xFF10B981) else Color.White)
                Text(subtitle, fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}
