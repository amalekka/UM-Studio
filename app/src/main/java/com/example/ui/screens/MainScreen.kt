@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.media.projection.MediaProjectionManager
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import com.example.MainActivity
import com.example.core.streaming.StreamSource
import com.example.ui.MainViewModel
import com.example.ui.components.*
import com.pedro.library.view.OpenGlView
import java.io.File

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTab = uiState.currentTab
    val context = LocalContext.current
    var showExitDialog by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler(enabled = true) {
        if (currentTab != 0) {
            viewModel.setCurrentTab(0)
        } else {
            showExitDialog = true
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit Application", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Do you want to stop streaming, stop background services, and exit the app completely?", color = Color.White.copy(alpha = 0.8f)) },
            containerColor = Color(0xFF0D111C),
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    viewModel.shutdownApp(context)
                }) {
                    Text("EXIT & STOP", color = Color(0xFFEF4444), fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("CANCEL", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

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
                    onClick = { viewModel.setCurrentTab(0) },
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
                    onClick = { viewModel.setCurrentTab(1) },
                    icon = { Icon(Icons.Default.Dns, "M3U8 & Streams") },
                    label = { Text("Streams", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF10B981),
                        selectedTextColor = Color(0xFF10B981),
                        indicatorColor = Color(0x3310B981),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { viewModel.setCurrentTab(2) },
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
                    selected = currentTab == 3,
                    onClick = { viewModel.setCurrentTab(3) },
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
                0 -> StudioPreviewScreen(
                    viewModel = viewModel,
                    onNavigateToM3u8 = { viewModel.setCurrentTab(1) },
                    onNavigateToDestinations = { viewModel.setCurrentTab(2) },
                    onNavigateToSettings = { viewModel.setCurrentTab(3) }
                )
                1 -> M3u8StreamsScreen(
                    viewModel = viewModel,
                    onNavigateToStudio = { viewModel.setCurrentTab(0) }
                )
                2 -> DestinationsScreen(viewModel)
                3 -> AdvancedSettingsScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioPreviewScreen(
    viewModel: MainViewModel,
    onNavigateToM3u8: () -> Unit = {},
    onNavigateToDestinations: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    var isOverlayVisible by remember { mutableStateOf(true) }
    var showSourceSheet by remember { mutableStateOf(false) }
    var showM3u8Dialog by remember { mutableStateOf(false) }
    var showBackgroundSheet by remember { mutableStateOf(false) }
    var isChatVisible by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState()
    val bgSheetState = rememberModalBottomSheetState()

    val projectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            viewModel.onScreenCapturePermissionGranted(result.data!!)
            Toast.makeText(context, "Screen Capture permission granted!", Toast.LENGTH_SHORT).show()
            if (uiState.isStreaming) {
                viewModel.startStream(context, result.data)
            }
        } else {
            Toast.makeText(context, "Screen Capture permission declined", Toast.LENGTH_SHORT).show()
        }
    }

    val launchScreenCapture: () -> Unit = {
        projectionManager?.let { pm ->
            try {
                screenCaptureLauncher.launch(pm.createScreenCaptureIntent())
            } catch (e: Exception) {
                (context as? MainActivity)?.requestScreenCapture()
            }
        } ?: run {
            (context as? MainActivity)?.requestScreenCapture()
        }
    }

    // 1. OpenGL Preview Area (Persistent)
    val openGlView = remember {
        OpenGlView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setZOrderMediaOverlay(false)
            setZOrderOnTop(false)
        }
    }

    DisposableEffect(openGlView) {
        val callback = object : android.view.SurfaceHolder.Callback {
            override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                openGlView.post {
                    try {
                        if (holder.surface?.isValid == true) {
                            viewModel.initPreview(openGlView)
                        }
                    } catch (e: Exception) {
                        // ignore surface errors
                    }
                }
            }
            override fun surfaceChanged(h: android.view.SurfaceHolder, f: Int, w: Int, h1: Int) {}
            override fun surfaceDestroyed(h: android.view.SurfaceHolder) {
                try {
                    viewModel.stopPreview()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
        openGlView.holder.addCallback(callback)
        onDispose {
            try {
                openGlView.holder.removeCallback(callback)
                viewModel.stopPreview()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    val bgConfig = uiState.bgConfig
    val hasCustomImage = bgConfig.imageUri != null && bgConfig.displayMode != MainViewModel.ImageDisplayMode.OFF
    val bgBitmap = remember(bgConfig.imageUri) {
        bgConfig.imageUri?.let { path ->
            val file = File(path)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 1. INTERACTIVE BACKGROUND IMAGE LAYER (WITH DIRECT PINCH-TO-ZOOM & PAN GESTURES)
        if (hasCustomImage && bgBitmap != null && bgConfig.displayMode != MainViewModel.ImageDisplayMode.OFF) {
            var bgScale by remember(bgConfig.zoom) { mutableFloatStateOf(bgConfig.zoom) }
            var bgOffsetX by remember(bgConfig.offsetX) { mutableFloatStateOf(bgConfig.offsetX) }
            var bgOffsetY by remember(bgConfig.offsetY) { mutableFloatStateOf(bgConfig.offsetY) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            bgScale = (bgScale * zoom).coerceIn(0.5f, 4.0f)
                            bgOffsetX = (bgOffsetX + pan.x * 0.15f).coerceIn(-100f, 100f)
                            bgOffsetY = (bgOffsetY + pan.y * 0.15f).coerceIn(-100f, 100f)
                            viewModel.updateBackgroundConfig(
                                bgConfig.copy(zoom = bgScale, offsetX = bgOffsetX, offsetY = bgOffsetY)
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bgBitmap.asImageBitmap(),
                    contentDescription = "Interactive Studio Canvas Background",
                    contentScale = when (bgConfig.scaleMode) {
                        MainViewModel.BackgroundScaleMode.CONTAIN -> ContentScale.Fit
                        MainViewModel.BackgroundScaleMode.COVER -> ContentScale.Crop
                        MainViewModel.BackgroundScaleMode.TOP_BANNER -> ContentScale.FillWidth
                        MainViewModel.BackgroundScaleMode.BOTTOM_BANNER -> ContentScale.FillWidth
                        MainViewModel.BackgroundScaleMode.STRETCH -> ContentScale.FillBounds
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = bgScale
                            scaleY = bgScale
                            translationX = bgOffsetX * 5f
                            translationY = bgOffsetY * 5f
                            alpha = bgConfig.opacity.coerceIn(0.1f, 1f)
                        }
                )
            }
        }

        // 2. PiP OVERLAY MODE: Interactive background is full canvas, active feed (CAM, M3U8, or SCREEN) floats in PiP Window (zIndex 10f)
        if (hasCustomImage && bgBitmap != null && bgConfig.displayMode == MainViewModel.ImageDisplayMode.PIP_OVERLAY) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 92.dp, end = 16.dp)
                    .width(140.dp)
                    .aspectRatio(uiState.streamConfig.aspectRatio.getRatio())
                    .zIndex(10f)
                    .shadow(16.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(2.dp, Color(0xFF38BDF8)),
                color = Color.Black
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (uiState.currentSource) {
                        StreamSource.CAMERA -> {
                            AndroidView(
                                factory = { openGlView },
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                modifier = Modifier.padding(4.dp).align(Alignment.TopStart),
                                color = Color.Black.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "LIVE CAM",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        StreamSource.M3U8 -> {
                            val m3u8Player = remember(uiState.m3u8Url) {
                                viewModel.getRestreamEngine()?.getPlayer()
                            }
                            if (m3u8Player != null) {
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            player = m3u8Player
                                            useController = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Surface(
                                modifier = Modifier.padding(4.dp).align(Alignment.TopStart),
                                color = Color.Black.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "LIVE M3U8",
                                    color = Color(0xFF10B981),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        StreamSource.SCREEN -> {
                            ScreenCapturePreviewCard(
                                isStreaming = uiState.isStreaming,
                                isReady = viewModel.cachedMediaProjectionResult != null,
                                onRequestCapture = launchScreenCapture,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // 3. FULL SOURCE RENDERING (When PiP is not active and Background Image is not in FULL_IMAGE mode)
        val isFullImageActive = hasCustomImage && bgBitmap != null && bgConfig.displayMode == MainViewModel.ImageDisplayMode.FULL_IMAGE
        val isPipActive = hasCustomImage && bgBitmap != null && bgConfig.displayMode == MainViewModel.ImageDisplayMode.PIP_OVERLAY

        if (!isFullImageActive && !isPipActive) {
            when (uiState.currentSource) {
                StreamSource.M3U8 -> {
                    if (uiState.m3u8State != MainViewModel.M3u8PlaybackState.STOPPED || uiState.isStreaming) {
                        val m3u8Player = remember(uiState.m3u8Url, uiState.isStreaming) {
                            viewModel.getRestreamEngine()?.getPlayer()
                        }
                        if (m3u8Player != null) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = m3u8Player
                                        useController = false
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(0f)
                            )
                        }
                    }
                }
                StreamSource.CAMERA -> {
                    AndroidView(
                        factory = { openGlView },
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(0f)
                    )
                }
                StreamSource.SCREEN -> {
                    ScreenCapturePreviewCard(
                        isStreaming = uiState.isStreaming,
                        isReady = viewModel.cachedMediaProjectionResult != null,
                        onRequestCapture = launchScreenCapture,
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(0f)
                    )
                }
            }
        }

        // FLOATING IMAGE MODE SWITCHER (Z-INDEX 60f)
        if (hasCustomImage && bgBitmap != null && bgConfig.displayMode != MainViewModel.ImageDisplayMode.OFF) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 58.dp)
                    .zIndex(60f),
                color = Color(0xEE0D111C),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("BG IMG:", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Surface(
                        onClick = { viewModel.setImageDisplayMode(MainViewModel.ImageDisplayMode.PIP_OVERLAY) },
                        color = if (bgConfig.displayMode == MainViewModel.ImageDisplayMode.PIP_OVERLAY) Color(0xFF38BDF8) else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "PiP OVERLAY",
                            color = if (bgConfig.displayMode == MainViewModel.ImageDisplayMode.PIP_OVERLAY) Color.Black else Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        onClick = { viewModel.setImageDisplayMode(MainViewModel.ImageDisplayMode.FULL_IMAGE) },
                        color = if (bgConfig.displayMode == MainViewModel.ImageDisplayMode.FULL_IMAGE) Color(0xFF38BDF8) else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "FULL BG",
                            color = if (bgConfig.displayMode == MainViewModel.ImageDisplayMode.FULL_IMAGE) Color.Black else Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        onClick = {
                            val newMode = if (bgConfig.scaleMode == MainViewModel.BackgroundScaleMode.COVER) {
                                MainViewModel.BackgroundScaleMode.CONTAIN
                            } else {
                                MainViewModel.BackgroundScaleMode.COVER
                            }
                            viewModel.updateBackgroundConfig(bgConfig.copy(scaleMode = newMode))
                        },
                        color = Color(0x3338BDF8),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            if (bgConfig.scaleMode == MainViewModel.BackgroundScaleMode.CONTAIN) "FIT" else "CROP",
                            color = Color(0xFF38BDF8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.setImageDisplayMode(MainViewModel.ImageDisplayMode.OFF) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Close, "Hide image", tint = Color.Gray, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }

        // FLOATING STOP SCREEN SHARE BUTTON (Z-INDEX 60f)
        if (uiState.currentSource == StreamSource.SCREEN) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp)
                    .zIndex(60f),
                color = Color(0xDDEF4444),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFFF87171)),
                onClick = { viewModel.toggleSource(StreamSource.CAMERA) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.StopScreenShare, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text("STOP SCREEN SHARE (BACK TO CAM)", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        // Top & Bottom Vignette (Z-INDEX 2f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .zIndex(2f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .zIndex(2f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                    )
                )
        )

        // MAIN OVERLAY - ALWAYS ON TOP (Z-INDEX 50f)
        if (!isOverlayVisible) {
            Surface(
                onClick = { isOverlayVisible = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .zIndex(70f),
                color = Color(0xEE0D111C),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFF38BDF8))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    Text("SHOW CONTROLS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .zIndex(50f)
        ) {
            // ROW 1: LIVE BADGE & SOURCE & DESTINATIONS BADGES (SCROLLABLE & COMPACT)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassPill(glowColor = if (uiState.isStreaming) Color(0xFFEF4444) else Color.Transparent) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            val pulseAlpha by rememberInfiniteTransition().animateFloat(
                                initialValue = 0.4f,
                                targetValue = 1.0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(600, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "Pulse"
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (uiState.isStreaming) Color(0xFFEF4444).copy(alpha = pulseAlpha)
                                        else Color.Gray
                                    )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (uiState.isStreaming) "LIVE (${formatTime(uiState.streamTime)})" else "READY",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Source Badge
                    val sourceText = when (uiState.currentSource) {
                        StreamSource.M3U8 -> "SRC: M3U8"
                        StreamSource.CAMERA -> "SRC: CAM"
                        StreamSource.SCREEN -> "SRC: SCREEN"
                    }
                    val sourceColor = when (uiState.currentSource) {
                        StreamSource.M3U8 -> Color(0xFF10B981)
                        StreamSource.CAMERA -> Color(0xFF38BDF8)
                        StreamSource.SCREEN -> Color(0xFF818CF8)
                    }
                    Surface(
                        color = sourceColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, sourceColor.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = sourceText,
                            color = sourceColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }

                    // Destinations Badge
                    val enabledDestsCount = uiState.endpoints.count { it.isEnabled && it.streamKey.isNotBlank() }
                    Surface(
                        color = Color(0xFFA855F7).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.6f)),
                        onClick = { viewModel.setCurrentTab(2) }
                    ) {
                        Text(
                            text = "DESTS: $enabledDestsCount",
                            color = Color(0xFFA855F7),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Settings gear button
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xBF1E2330))
                    ) {
                        Icon(Icons.Default.Settings, "Settings", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
                    }
                    // Collapse / Hide overlay button with arrow
                    IconButton(
                        onClick = { isOverlayVisible = false },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xBF1E2330))
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, "Hide controls", tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ROW 2: STATS PILLS
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill(Icons.Default.SignalCellularAlt, if (uiState.isStreaming) "Stable" else "Ready")
                StatPill(Icons.Default.Speed, if (uiState.isStreaming) "${uiState.currentBitrate} kbps" else "0 kbps")
                StatPill(Icons.Default.GraphicEq, if (uiState.isStreaming) "${uiState.fps} FPS" else "0 FPS")
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ROW 3: REAL-TIME RTMP CONNECTION DIAGNOSTIC PILL
            RtmpDiagnosticPill(
                message = if (uiState.isStreaming || uiState.streamingStatus == com.example.core.streaming.StreamingStatus.CONNECTING || uiState.diagnosticIsError) {
                    uiState.diagnosticMessage
                } else {
                    "RTMP: Ready to broadcast"
                },
                isError = uiState.diagnosticIsError,
                isStreaming = uiState.isStreaming,
                isConnecting = uiState.streamingStatus == com.example.core.streaming.StreamingStatus.CONNECTING,
                onClick = {
                    if (uiState.diagnosticIsError) {
                        Toast.makeText(context, "RTMP Error: ${uiState.diagnosticMessage}. Check Stream Key in Destinations.", Toast.LENGTH_LONG).show()
                        onNavigateToDestinations()
                    }
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            // MIDDLE: AUDIO VU METER (LEFT) - REAL HARDWARE/AUDIO MONITOR
            Box(modifier = Modifier.fillMaxWidth()) {
                DualAudioVU(
                    modifier = Modifier.align(Alignment.CenterStart),
                    isStreaming = uiState.isStreaming,
                    isMuted = !uiState.isMicEnabled,
                    levelL = uiState.audioLevelL,
                    levelR = uiState.audioLevelR
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // PERSISTENT M3U8 & LIVE STREAM CONTROL DOCK (ALWAYS VISIBLE ABOVE ACTIONS)
            M3u8ControlDock(
                viewModel = viewModel,
                uiState = uiState,
                onOpenManager = onNavigateToM3u8,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // BOTTOM DECK: CHAT & ACTIONS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(245.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // LEFT: CHAT (45%)
                if (isChatVisible) {
                    GlassChat(viewModel = viewModel, modifier = Modifier.weight(0.45f))
                } else {
                    Spacer(modifier = Modifier.weight(0.01f))
                }

                // RIGHT: ACTION DECK (55%)
                Column(
                    modifier = Modifier.weight(0.55f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // GO LIVE CAPSULE (VALIDATES DESTINATIONS)
                    GoLiveButton(
                        isStreaming = uiState.isStreaming,
                        onClick = {
                            if (uiState.isStreaming) {
                                viewModel.stopStream()
                            } else {
                                if (!viewModel.hasValidDestination()) {
                                    Toast.makeText(
                                        context,
                                        "Please configure at least one Destination Stream Key to go live!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onNavigateToDestinations()
                                } else {
                                    if (uiState.currentSource == StreamSource.SCREEN) {
                                        if (viewModel.cachedMediaProjectionResult != null) {
                                            viewModel.startStream(context, viewModel.cachedMediaProjectionResult)
                                        } else {
                                            launchScreenCapture()
                                        }
                                    } else {
                                        viewModel.startStream(context)
                                    }
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
                        isFlashOn = uiState.isFlashOn,
                        onFlash = { viewModel.toggleFlash(context) },
                        onChat = { isChatVisible = !isChatVisible },
                        onBg = { showBackgroundSheet = true }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    }

    // Background Studio Sheet
    if (showBackgroundSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBackgroundSheet = false },
            sheetState = bgSheetState,
            containerColor = Color(0xFF0F1422)
        ) {
            BackgroundStudioSheet(
                viewModel = viewModel,
                onDismiss = { showBackgroundSheet = false }
            )
        }
    }

    // Input Source Sheet
    if (showSourceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSourceSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF0F1422)
        ) {
            Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
                Text("INPUT SOURCE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                SourceOptionItem("CAMERA", "Direct broadcast camera", Icons.Default.CameraAlt, uiState.currentSource == StreamSource.CAMERA) { 
                    viewModel.toggleSource(StreamSource.CAMERA)
                    showSourceSheet = false 
                }
                SourceOptionItem("SCREEN", "Capture device display", Icons.Default.ScreenShare, uiState.currentSource == StreamSource.SCREEN) { 
                    viewModel.toggleSource(StreamSource.SCREEN)
                    showSourceSheet = false 
                    if (viewModel.cachedMediaProjectionResult == null) {
                        launchScreenCapture()
                    }
                }
                SourceOptionItem("REMOTE URL", "Restream M3U8/HLS stream", Icons.Default.Link, uiState.currentSource == StreamSource.M3U8) { 
                    viewModel.toggleSource(StreamSource.M3U8)
                    showSourceSheet = false
                    showM3u8Dialog = true 
                }
            }
        }
    }

    // Advanced M3U8 Stream Manager Dialog
    if (showM3u8Dialog) {
        M3u8StreamManagerDialog(
            viewModel = viewModel,
            onDismiss = { showM3u8Dialog = false }
        )
    }

    // High-Priority Screen Capture Activation Modal
    if (uiState.showScreenCapturePrompt) {
        ScreenCapturePromptDialog(
            viewModel = viewModel,
            onStartScreenCapture = {
                launchScreenCapture()
            },
            onDismiss = { viewModel.dismissScreenCapturePrompt() }
        )
    }
}

@Composable
fun GlassPill(
    glowColor: Color = Color.Transparent,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .then(if (glowColor != Color.Transparent) Modifier.shadow(10.dp, CircleShape, ambientColor = glowColor, spotColor = glowColor) else Modifier),
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
fun RtmpDiagnosticPill(
    message: String,
    isError: Boolean,
    isStreaming: Boolean,
    isConnecting: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val pillBgColor = when {
        isError -> Color(0x33EF4444)
        isConnecting -> Color(0x33F59E0B)
        isStreaming -> Color(0x2210B981)
        else -> Color(0x221E2330)
    }
    val pillBorderColor = when {
        isError -> Color(0xFFEF4444)
        isConnecting -> Color(0xFFF59E0B)
        isStreaming -> Color(0xFF10B981)
        else -> Color(0x4438BDF8)
    }
    val pillTextColor = when {
        isError -> Color(0xFFFCA5A5)
        isConnecting -> Color(0xFFFDE68A)
        isStreaming -> Color(0xFF6EE7B7)
        else -> Color(0xFF93C5FD)
    }

    val pulseAlpha by rememberInfiniteTransition(label = "DiagPulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isConnecting) 400 else 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DiagAlpha"
    )

    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        color = pillBgColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, pillBorderColor.copy(alpha = if (isConnecting || isStreaming) pulseAlpha else 0.8f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isError -> Color(0xFFEF4444)
                            isConnecting -> Color(0xFFF59E0B).copy(alpha = pulseAlpha)
                            isStreaming -> Color(0xFF10B981).copy(alpha = pulseAlpha)
                            else -> Color(0xFF38BDF8)
                        }
                    )
            )
            Icon(
                imageVector = when {
                    isError -> Icons.Default.WarningAmber
                    isConnecting -> Icons.Default.Sync
                    isStreaming -> Icons.Default.Sensors
                    else -> Icons.Default.Lan
                },
                contentDescription = null,
                tint = pillTextColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = message,
                color = pillTextColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DualAudioVU(
    modifier: Modifier = Modifier,
    isStreaming: Boolean,
    isMuted: Boolean,
    levelL: Float,
    levelR: Float
) {
    val barHeight = 100.dp
    // When offline or muted, levels MUST strictly remain at 0% (empty)
    val actualL = if (!isStreaming || isMuted) 0f else levelL.coerceIn(0f, 1f)
    val actualR = if (!isStreaming || isMuted) 0f else levelR.coerceIn(0f, 1f)
    
    val smoothL by animateFloatAsState(targetValue = actualL, animationSpec = tween(50, easing = LinearEasing), label = "VU_L")
    val smoothR by animateFloatAsState(targetValue = actualR, animationSpec = tween(50, easing = LinearEasing), label = "VU_R")

    Surface(
        modifier = modifier
            .width(44.dp)
            .height(barHeight + 32.dp)
            .padding(start = 4.dp),
        color = Color(0xBF1E2330),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("L", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("R", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VuBar(level = smoothL)
                VuBar(level = smoothR)
            }
        }
    }
}

@Composable
fun VuBar(level: Float) {
    val height = 85.dp
    Box(
        modifier = Modifier
            .width(6.dp)
            .height(height)
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
    ) {
        if (level > 0.005f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(level.coerceIn(0.01f, 1f))
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
fun GlassChat(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    val chatMessages = uiState.chatMessages
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var inputMessage by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Surface(
        modifier = modifier.fillMaxHeight(),
        color = Color(0xBF1E2330),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("LIVE CHAT (${chatMessages.size})", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                if (uiState.isStreaming) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (chatMessages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (uiState.isStreaming) "Listening for real comments from YouTube / Active platforms..." else "Go Live to receive real-time comments...",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(chatMessages, key = { it.id }) { msg ->
                            val platformColor = when (msg.platform.uppercase()) {
                                "YOUTUBE" -> Color(0xFFEF4444)
                                "FACEBOOK" -> Color(0xFF3B82F6)
                                "TWITCH" -> Color(0xFF8B5CF6)
                                "TIKTOK" -> Color(0xFFEC4899)
                                "KICK" -> Color(0xFF10B981)
                                else -> Color(0xFF38BDF8)
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0x33000000), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = platformColor.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, platformColor.copy(alpha = 0.6f))
                                    ) {
                                        Text(
                                            text = msg.platform.uppercase(),
                                            color = platformColor,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = msg.author,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = msg.message,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Real comment input / test sender
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedTextField(
                    value = inputMessage,
                    onValueChange = { inputMessage = it },
                    placeholder = { Text("Add real/incoming comment...", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedContainerColor = Color(0x33000000),
                        unfocusedContainerColor = Color(0x33000000)
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
                IconButton(
                    onClick = {
                        if (inputMessage.isNotBlank()) {
                            val activeEndpoints = uiState.endpoints.filter { it.isEnabled && it.streamKey.isNotBlank() }
                            val platform = activeEndpoints.firstOrNull()?.name ?: "YouTube"
                            viewModel.addIncomingChatMessage(platform = platform, author = "Live Viewer", message = inputMessage.trim())
                            inputMessage = ""
                        }
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0xFF38BDF8), RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.Default.Send, "Send", tint = Color.Black, modifier = Modifier.size(16.dp))
                }
            }
        }
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
    isFlashOn: Boolean,
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
            ActionIcon(Icons.Default.FlashOn, "FLASH", isFlashOn, Color(0xFFFFD700), onFlash)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionIcon(Icons.Default.ChatBubble, "CHAT", true, Color(0xFFA855F7), onChat)
            ActionIcon(Icons.Default.Image, "BG IMG", true, Color(0xFF34D399), onBg)
        }
    }
}

@Composable
fun ActionIcon(icon: ImageVector, label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(height = 54.dp, width = 74.dp)
            .then(
                if (active && accent == Color(0xFFFFD700)) Modifier.shadow(8.dp, RoundedCornerShape(12.dp), ambientColor = accent, spotColor = accent)
                else Modifier
            ),
        color = Color(0xBF1E2330),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (active) accent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.1f)
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundStudioSheet(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val config = uiState.bgConfig
    val context = LocalContext.current
    var urlInput by remember { mutableStateOf("") }
    
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let { viewModel.setBackgroundImageFromUri(context, it) }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("BACKGROUND STUDIO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
        Text("PERSISTENT STREAM CANVAS & PiP COMPOSITOR", fontSize = 10.sp, color = Color(0xFF38BDF8))
        Spacer(modifier = Modifier.height(16.dp))

        // Image Dropzone / Picker
        Surface(
            onClick = {
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            color = Color(0xFF0D111C),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A324B).copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().height(110.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.AddPhotoAlternate, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (config.imageUri != null) "IMAGE LOADED (TAP TO REPLACE)" else "CHOOSE GALLERY IMAGE (PNG / JPG / WEBP)",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Direct URL Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = "DIRECT IMAGE URL",
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (urlInput.isNotBlank()) {
                        viewModel.setBackgroundImageFromUrl(context, urlInput.trim())
                    }
                },
                modifier = Modifier.height(44.dp).padding(top = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("LOAD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (config.imageUri != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text("STUDIO DISPLAY MODE", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            PillSelector(
                options = listOf("Full Image", "PiP Overlay", "Hidden"),
                selectedIndex = when (config.displayMode) {
                    MainViewModel.ImageDisplayMode.FULL_IMAGE -> 0
                    MainViewModel.ImageDisplayMode.PIP_OVERLAY -> 1
                    MainViewModel.ImageDisplayMode.OFF -> 2
                },
                onSelect = { idx ->
                    val mode = when (idx) {
                        0 -> MainViewModel.ImageDisplayMode.FULL_IMAGE
                        1 -> MainViewModel.ImageDisplayMode.PIP_OVERLAY
                        else -> MainViewModel.ImageDisplayMode.OFF
                    }
                    viewModel.setImageDisplayMode(mode)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("PERSISTENT ON STORAGE", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                TextButton(
                    onClick = { viewModel.removeBackground(context) },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("REMOVE / RESET BACKGROUND", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Interactive Preview Card with Touch Gesture (Drag & Pinch to Zoom)
        if (config.imageUri != null) {
            GlassSectionCard(title = "INTERACTIVE CANVAS PREVIEW (DRAG & PINCH)") {
                val file = File(config.imageUri)
                val bitmap = remember(config.imageUri) {
                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newZoom = (config.zoom * zoom).coerceIn(0.5f, 2.0f)
                                val newX = (config.offsetX + pan.x * 0.1f).coerceIn(-50f, 50f)
                                val newY = (config.offsetY + pan.y * 0.1f).coerceIn(-50f, 50f)
                                viewModel.updateBackgroundConfig(
                                    config.copy(zoom = newZoom, offsetX = newX, offsetY = newY)
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Preview",
                            contentScale = when (config.scaleMode) {
                                MainViewModel.BackgroundScaleMode.COVER -> ContentScale.Crop
                                MainViewModel.BackgroundScaleMode.CONTAIN -> ContentScale.Fit
                                MainViewModel.BackgroundScaleMode.STRETCH -> ContentScale.FillBounds
                                MainViewModel.BackgroundScaleMode.TOP_BANNER -> ContentScale.FillWidth
                                MainViewModel.BackgroundScaleMode.BOTTOM_BANNER -> ContentScale.FillWidth
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Text(
                        "Touch to Pan / Pinch to Zoom",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Layer Modes & Controls
        GlassSectionCard(title = "CANVAS CONTROLS") {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                // Scale Mode
                Column {
                    Text("PRESET LAYOUT MODE", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    PillSelector(
                        options = listOf("Cover", "Contain", "Top Banner", "Bottom", "Stretch"),
                        selectedIndex = when (config.scaleMode) {
                            MainViewModel.BackgroundScaleMode.COVER -> 0
                            MainViewModel.BackgroundScaleMode.CONTAIN -> 1
                            MainViewModel.BackgroundScaleMode.TOP_BANNER -> 2
                            MainViewModel.BackgroundScaleMode.BOTTOM_BANNER -> 3
                            MainViewModel.BackgroundScaleMode.STRETCH -> 4
                        },
                        onSelect = { index ->
                            val mode = when (index) {
                                0 -> MainViewModel.BackgroundScaleMode.COVER
                                1 -> MainViewModel.BackgroundScaleMode.CONTAIN
                                2 -> MainViewModel.BackgroundScaleMode.TOP_BANNER
                                3 -> MainViewModel.BackgroundScaleMode.BOTTOM_BANNER
                                else -> MainViewModel.BackgroundScaleMode.STRETCH
                            }
                            viewModel.updateBackgroundConfig(config.copy(scaleMode = mode))
                        }
                    )
                }

                // Zoom Slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ZOOM / SCALE", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("${(config.zoom * 100).toInt()}%", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    GlowingSlider(
                        value = config.zoom,
                        onValueChange = { viewModel.updateBackgroundConfig(config.copy(zoom = it)) },
                        valueRange = 0.5f..2.0f
                    )
                }

                // X & Y Offset Sliders
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("X OFFSET", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("${config.offsetX.toInt()}", color = Color(0xFF818CF8), fontSize = 10.sp)
                        }
                        GlowingSlider(
                            value = config.offsetX,
                            onValueChange = { viewModel.updateBackgroundConfig(config.copy(offsetX = it)) },
                            valueRange = -50f..50f
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Y OFFSET", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("${config.offsetY.toInt()}", color = Color(0xFF818CF8), fontSize = 10.sp)
                        }
                        GlowingSlider(
                            value = config.offsetY,
                            onValueChange = { viewModel.updateBackgroundConfig(config.copy(offsetY = it)) },
                            valueRange = -50f..50f
                        )
                    }
                }

                // Blur Slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("GAUSSIAN BLUR", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("${config.blurRadius.toInt()}px", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                        Text("CANVAS BRIGHTNESS", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("${(config.brightness * 100).toInt()}%", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    GlowingSlider(
                        value = config.brightness,
                        onValueChange = { viewModel.updateBackgroundConfig(config.copy(brightness = it)) },
                        valueRange = 0.1f..1f
                    )
                }

                // Opacity Slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("CANVAS OPACITY", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("${(config.opacity * 100).toInt()}%", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    GlowingSlider(
                        value = config.opacity,
                        onValueChange = { viewModel.updateBackgroundConfig(config.copy(opacity = it)) },
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
                    Text("VIDEO SCALE", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("${(config.foregroundScale * 100).toInt()}%", color = Color(0xFF818CF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                GlowingSlider(
                    value = config.foregroundScale,
                    onValueChange = { viewModel.updateBackgroundConfig(config.copy(foregroundScale = it)) },
                    valueRange = 0.5f..1f
                )

                Text("VIDEO POSITION", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                PillSelector(
                    options = listOf("Center", "Top", "Bottom", "Free"),
                    selectedIndex = when (config.foregroundPosition) {
                        MainViewModel.ForegroundPosition.CENTER -> 0
                        MainViewModel.ForegroundPosition.TOP -> 1
                        MainViewModel.ForegroundPosition.BOTTOM -> 2
                        MainViewModel.ForegroundPosition.FREE -> 3
                    },
                    onSelect = { index ->
                        val pos = when (index) {
                            0 -> MainViewModel.ForegroundPosition.CENTER
                            1 -> MainViewModel.ForegroundPosition.TOP
                            2 -> MainViewModel.ForegroundPosition.BOTTOM
                            else -> MainViewModel.ForegroundPosition.FREE
                        }
                        viewModel.updateBackgroundConfig(config.copy(foregroundPosition = pos))
                    }
                )

                FeatureToggleRow(
                    title = "BRB / PAUSE SHIELD OVERLAY",
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

@OptIn(UnstableApi::class)
@Composable
fun M3u8StreamManagerDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    var url by remember { mutableStateOf(uiState.m3u8Url) }
    var userAgent by remember { mutableStateOf(uiState.m3u8Headers["User-Agent"] ?: "") }
    var referer by remember { mutableStateOf(uiState.m3u8Headers["Referer"] ?: "") }
    var origin by remember { mutableStateOf(uiState.m3u8Headers["Origin"] ?: "") }
    var streamNameToSave by remember { mutableStateOf("") }
    
    // ExoPlayer test preview state
    var isTestingPlayback by remember { mutableStateOf(false) }
    var playerStatusText by remember { mutableStateOf("IDLE") }
    
    val testPlayer = remember {
        ExoPlayer.Builder(context).build()
    }
    
    val handleDismiss = {
        try {
            testPlayer.pause()
            testPlayer.stop()
            testPlayer.clearMediaItems()
        } catch (_: Exception) {}
        onDismiss()
    }

    DisposableEffect(testPlayer) {
        onDispose {
            try {
                testPlayer.pause()
                testPlayer.stop()
                testPlayer.clearMediaItems()
                testPlayer.release()
            } catch (_: Exception) {}
        }
    }

    Dialog(onDismissRequest = handleDismiss) {
        Surface(
            color = Color(0xFF0F1422),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A324B)),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("ADVANCED M3U8 MANAGER", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        Text("HLS INGESTION & PLAYLIST REPOSITORY", color = Color(0xFF38BDF8), fontSize = 10.sp)
                    }
                    IconButton(onClick = handleDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, null, tint = Color.Gray)
                    }
                }

                // Live Test Player Window
                if (isTestingPlayback) {
                    Surface(
                        color = Color.Black,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .border(1.dp, Color(0xFF10B981), RoundedCornerShape(12.dp))
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = testPlayer
                                    useController = true
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Player Controls & Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isTestingPlayback) Color(0xFF10B981) else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(playerStatusText, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (isTestingPlayback) {
                                testPlayer.stop()
                                isTestingPlayback = false
                                playerStatusText = "STOPPED"
                            } else {
                                if (url.isBlank()) {
                                    Toast.makeText(context, "Please enter a valid M3U8 URL", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val headers = mutableMapOf<String, String>()
                                if (userAgent.isNotBlank()) headers["User-Agent"] = userAgent
                                if (referer.isNotBlank()) headers["Referer"] = referer
                                if (origin.isNotBlank()) headers["Origin"] = origin
                                
                                val dataSourceFactory = DefaultHttpDataSource.Factory()
                                    .setUserAgent(if (userAgent.isNotBlank()) userAgent else "LiveStreamPro/1.0")
                                    .setDefaultRequestProperties(headers)
                                    .setAllowCrossProtocolRedirects(true)
                                
                                val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
                                    .createMediaSource(MediaItem.fromUri(url.trim()))
                                
                                testPlayer.addListener(object : Player.Listener {
                                    override fun onPlaybackStateChanged(state: Int) {
                                        playerStatusText = when (state) {
                                            Player.STATE_BUFFERING -> "BUFFERING..."
                                            Player.STATE_READY -> "PLAYING (OK)"
                                            Player.STATE_ENDED -> "ENDED"
                                            else -> "IDLE"
                                        }
                                    }
                                })
                                testPlayer.setMediaSource(mediaSource)
                                testPlayer.prepare()
                                testPlayer.playWhenReady = true
                                isTestingPlayback = true
                                playerStatusText = "CONNECTING..."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTestingPlayback) Color(0xFFEF4444) else Color(0xFF10B981)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(if (isTestingPlayback) Icons.Default.Stop else Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isTestingPlayback) "STOP TEST" else "TEST PREVIEW", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Sample Streams Pills
                Column {
                    Text("WORKING SAMPLE STREAMS (TAP TO LOAD)", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = { url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" },
                            color = Color(0x3338BDF8),
                            shape = CircleShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Big Buck Bunny", color = Color(0xFF38BDF8), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp))
                        }
                        Surface(
                            onClick = { url = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8" },
                            color = Color(0x33818CF8),
                            shape = CircleShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Akamai Live", color = Color(0xFF818CF8), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp))
                        }
                        Surface(
                            onClick = { url = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8" },
                            color = Color(0x3334D399),
                            shape = CircleShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Tears of Steel", color = Color(0xFF34D399), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp))
                        }
                    }
                }

                // Stream URL Input
                GlassTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = "STREAM URL (M3U8 / HLS / DASH / TS)",
                    trailingIcon = {
                        IconButton(onClick = { url = "" }) {
                            Icon(Icons.Default.Clear, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                )

                // Advanced HTTP Headers
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
                GlassTextField(
                    value = origin,
                    onValueChange = { origin = it },
                    label = "ORIGIN HEADER"
                )

                // Saved Playlist Section
                Text("SAVED STREAMS PLAYLIST", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    uiState.savedStreams.forEach { stream ->
                        Surface(
                            color = Color(0xFF131A2A),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).clickable { url = stream.url }) {
                                    Text(stream.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(stream.url, color = Color.Gray, fontSize = 10.sp, maxLines = 1)
                                }
                                IconButton(
                                    onClick = { viewModel.deleteStream(stream.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // Save New Stream Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassTextField(
                            value = streamNameToSave,
                            onValueChange = { streamNameToSave = it },
                            label = "SAVE CURRENT TO PLAYLIST (NAME)",
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (streamNameToSave.isNotBlank() && url.isNotBlank()) {
                                    viewModel.saveStream(streamNameToSave, url)
                                    streamNameToSave = ""
                                    Toast.makeText(context, "Stream saved to playlist!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.height(44.dp).padding(top = 16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("SAVE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons
                Button(
                    onClick = {
                        try {
                            testPlayer.pause()
                            testPlayer.stop()
                            testPlayer.clearMediaItems()
                        } catch (_: Exception) {}
                        val headers = mutableMapOf<String, String>()
                        if (userAgent.isNotEmpty()) headers["User-Agent"] = userAgent
                        if (referer.isNotEmpty()) headers["Referer"] = referer
                        if (origin.isNotEmpty()) headers["Origin"] = origin
                        viewModel.setM3u8Source(url, headers)
                        viewModel.toggleSource(StreamSource.M3U8)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("INJECT TO BROADCAST ENGINE", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }

                TextButton(
                    onClick = handleDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("CANCEL", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }
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

@Composable
fun ScreenCapturePromptDialog(
    viewModel: MainViewModel,
    onStartScreenCapture: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF0F1422),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0x3338BDF8), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ScreenShare,
                        null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    "SCREEN BROADCAST MODE",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Broadcast your entire device display in real-time. Share games, mobile applications, demos, or tutorials directly to all active destinations.",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        onStartScreenCapture()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "START SCREEN CAPTURE",
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                }

                Button(
                    onClick = {
                        viewModel.toggleSource(StreamSource.CAMERA)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x3338BDF8)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "RETURN TO CAMERA",
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun M3u8ControlDock(
    viewModel: MainViewModel,
    uiState: MainViewModel.UiState,
    onOpenManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedDropdown by remember { mutableStateOf(false) }
    var isDockCollapsed by remember { mutableStateOf(true) }

    val statusColor = when (uiState.m3u8State) {
        MainViewModel.M3u8PlaybackState.PLAYING -> Color(0xFF10B981)
        MainViewModel.M3u8PlaybackState.PAUSED -> Color(0xFFF59E0B)
        MainViewModel.M3u8PlaybackState.STOPPED -> Color(0xFFEF4444)
    }

    if (isDockCollapsed) {
        // Sleek floating frosted-glass tab docked on the left edge
        Surface(
            onClick = { isDockCollapsed = false },
            modifier = modifier.padding(start = 0.dp),
            color = Color(0xF20D1322),
            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusColor, CircleShape)
                )
                Text(
                    text = "▶ M3U8",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
                Icon(
                    Icons.Default.ChevronRight,
                    null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    } else {
        // Full M3U8 Control Dock
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = Color(0xF20D1322),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header Row: Collapse Button, Status Indicator, Stream Name, Quick Switch Dropdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Collapse Button (◀)
                        IconButton(
                            onClick = { isDockCollapsed = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.ChevronLeft,
                                "Collapse Dock",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(statusColor, CircleShape)
                        )
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "M3U8 / HLS DOCK: ",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    uiState.m3u8State.name,
                                    color = statusColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Text(
                                uiState.activeM3u8Name,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Dropdown to switch saved streams quickly without opening dialog
                    Box {
                        Surface(
                            onClick = { expandedDropdown = true },
                            color = Color(0x3338BDF8),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("SWITCH", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                            }
                        }

                        DropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false },
                            modifier = Modifier.background(Color(0xFF0F1422))
                        ) {
                            uiState.savedStreams.forEach { stream ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(stream.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(stream.url, color = Color.Gray, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    },
                                    onClick = {
                                        expandedDropdown = false
                                        viewModel.selectSavedStream(stream)
                                    }
                                )
                            }
                        }
                    }
                }

                // Action Row: Play/Pause, HARD STOP, Mute, Advanced Dialog
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play / Pause
                    if (uiState.m3u8State == MainViewModel.M3u8PlaybackState.PLAYING) {
                        Button(
                            onClick = { viewModel.pauseM3u8() },
                            modifier = Modifier.weight(1f).height(38.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.Pause, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PAUSE", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.resumeM3u8() },
                            modifier = Modifier.weight(1f).height(38.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (uiState.m3u8State == MainViewModel.M3u8PlaybackState.PAUSED) "RESUME" else "PLAY", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    // HARD STOP Button (Kills audio and connection)
                    Button(
                        onClick = { viewModel.stopM3u8Stream() },
                        modifier = Modifier.weight(1.2f).height(38.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("HARD STOP", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }

                    // Mute / Unmute
                    IconButton(
                        onClick = { viewModel.toggleM3u8Mute() },
                        modifier = Modifier
                            .size(38.dp)
                            .background(if (uiState.isM3u8Muted) Color(0x33EF4444) else Color(0x3338BDF8), RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            if (uiState.isM3u8Muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            null,
                            tint = if (uiState.isM3u8Muted) Color(0xFFEF4444) else Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Manage M3U8 & Streams Screen
                    IconButton(
                        onClick = onOpenManager,
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0x33818CF8), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Dns, "Manage M3U8 Streams", tint = Color(0xFF818CF8), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ScreenCapturePreviewCard(
    isStreaming: Boolean,
    isReady: Boolean,
    onRequestCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF090D16)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            val pulseAlpha by rememberInfiniteTransition().animateFloat(
                initialValue = 0.5f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ScreenPulse"
            )

            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(if (isStreaming) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFF818CF8).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(if (isStreaming) Color(0xFFEF4444).copy(alpha = 0.35f * pulseAlpha) else Color(0xFF818CF8).copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ScreenShare,
                        contentDescription = "Screen Capture Active",
                        tint = if (isStreaming) Color(0xFFEF4444) else Color(0xFF818CF8),
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                    color = if (isStreaming) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFF818CF8).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (isStreaming) Color(0xFFEF4444) else Color(0xFF818CF8))
                ) {
                    Text(
                        text = if (isStreaming) "LIVE SCREEN BROADCASTING" else if (isReady) "SCREEN READY TO BROADCAST" else "SCREEN CAPTURE PENDING",
                        color = if (isStreaming) Color(0xFFEF4444) else Color(0xFF818CF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "Entire Android Display Feed",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Games, tutorials, and applications will broadcast directly to active destinations.",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }

            if (!isReady && !isStreaming) {
                Button(
                    onClick = onRequestCapture,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF818CF8)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(Icons.Default.Security, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("GRANT SCREEN PERMISSION", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }
        }
    }
}

