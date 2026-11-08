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
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.unit.IntOffset
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
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.example.ui.localizedString
import com.example.ui.getLineHeight
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import com.example.MainActivity
import com.example.core.streaming.StreamSource
import com.example.core.model.AspectRatioMode
import com.example.ui.MainViewModel
import com.example.ui.components.*
import com.example.ui.theme.Obsidian
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.GlassDark
import com.pedro.library.view.OpenGlView
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    DisposableEffect(uiState.streamConfig.keepScreenAwake) {
        if (uiState.streamConfig.keepScreenAwake) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    var showExitDialog by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler(enabled = true) {
        showExitDialog = true
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(localizedString("EXIT_APP", viewModel), color = Color.White, fontWeight = FontWeight.Bold, lineHeight = getLineHeight(viewModel)) },
            text = { Text(localizedString("EXIT_DESC", viewModel), color = Color.White.copy(alpha = 0.8f), lineHeight = getLineHeight(viewModel)) },
            containerColor = Color(0xFF0D111C),
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    viewModel.shutdownApp(context)
                }) {
                    Text(localizedString("STOP", viewModel), color = Color(0xFFEF4444), fontWeight = FontWeight.Black, lineHeight = getLineHeight(viewModel))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(localizedString("BACK_TO_STUDIO", viewModel).take(6), color = Color.Gray, fontWeight = FontWeight.Bold, lineHeight = getLineHeight(viewModel))
                }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            StudioPreviewScreen(viewModel = viewModel)
        }
    }
}

@Composable
fun VideoSourceContent(
    currentSource: StreamSource,
    openGlView: OpenGlView,
    m3u8Url: String,
    viewModel: MainViewModel,
    uiState: MainViewModel.UiState,
    launchScreenCapture: () -> Unit,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when (currentSource) {
            StreamSource.CAMERA -> AndroidView(
                factory = { openGlView },
                modifier = Modifier.fillMaxSize()
            )
            StreamSource.M3U8 -> {
                val m3u8Player = remember(m3u8Url) { viewModel.getRestreamEngine()?.getPlayer() }
                if (m3u8Player != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = m3u8Player
                                useController = false
                                // Force 9:16 Full-Height Vertical Fill
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            StreamSource.SCREEN -> {
                if (viewModel.cachedMediaProjectionResult != null) {
                    AndroidView(
                        factory = { openGlView },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ScreenCapturePreviewCard(
                        isStreaming = isStreaming,
                        isReady = false,
                        onRequestCapture = launchScreenCapture,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            StreamSource.AUDIO -> {
                Box(
                    modifier = Modifier.fillMaxSize().background(Obsidian),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.bgConfig.imageUri != null) {
                        AsyncImage(
                            model = uiState.bgConfig.imageUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Mic, null, tint = CyberCyan.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            localizedString("AUDIO", viewModel),
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            lineHeight = getLineHeight(viewModel)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioPreviewScreen(
    viewModel: MainViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    var showM3u8Dialog by remember { mutableStateOf(false) }
    var showBackgroundSheet by remember { mutableStateOf(false) }
    var showAudioMixer by remember { mutableStateOf(false) }
    var showDestinationsSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showChatHubSheet by remember { mutableStateOf(false) }
    var editingEndpoint by remember { mutableStateOf<com.example.core.model.PlatformEndpoint?>(null) }
    var isChatVisible by remember { mutableStateOf(true) }
    var showAddSceneDialog by remember { mutableStateOf(false) }
    val bgSheetState = rememberModalBottomSheetState()

    var pendingPermissionType by remember { mutableStateOf<com.example.ui.components.PermissionType?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var amberBannerMessage by remember { mutableStateOf<String?>(null) }

    val singlePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        try {
            if (isGranted) {
                amberBannerMessage = null
                pendingAction?.invoke()
            } else {
                amberBannerMessage = if (uiState.appLang == "ar") {
                    "⚠️ تم رفض الإذن. يمكنك منحه لاحقاً لتشغيل الميزة."
                } else {
                    "⚠️ Permission denied. You can grant it later to use feature."
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("MainScreen", "Permission result error: ${t.message}")
        } finally {
            pendingAction = null
        }
    }

    val requestPermissionSafely: (com.example.ui.components.PermissionType, () -> Unit) -> Unit = { type, action ->
        amberBannerMessage = null
        val permString = when (type) {
            com.example.ui.components.PermissionType.CAMERA -> android.Manifest.permission.CAMERA
            com.example.ui.components.PermissionType.MICROPHONE -> android.Manifest.permission.RECORD_AUDIO
            com.example.ui.components.PermissionType.NOTIFICATIONS -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) android.Manifest.permission.POST_NOTIFICATIONS else null
            com.example.ui.components.PermissionType.OVERLAY -> null
        }

        if (type == com.example.ui.components.PermissionType.OVERLAY) {
            if (android.provider.Settings.canDrawOverlays(context)) {
                action()
            } else {
                pendingAction = action
                pendingPermissionType = com.example.ui.components.PermissionType.OVERLAY
            }
        } else if (permString == null) {
            action()
        } else {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, permString) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                action()
            } else {
                pendingAction = action
                pendingPermissionType = type
            }
        }
    }

    val projectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            viewModel.onScreenCapturePermissionGranted(result.data!!)
            Toast.makeText(context, if (uiState.appLang == "ar") "تم منح إذن تصوير الشاشة!" else "Screen Capture permission granted!", Toast.LENGTH_SHORT).show()
            if (uiState.isStreaming) {
                viewModel.startStream(context, result.data)
            }
        } else {
            Toast.makeText(context, if (uiState.appLang == "ar") "تم رفض إذن تصوير الشاشة" else "Screen Capture permission declined", Toast.LENGTH_SHORT).show()
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

    val openGlView = remember {
        OpenGlView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Force 9:16 Full-Height Vertical Fill
            setAspectRatioMode(com.pedro.encoder.utils.gl.AspectRatioMode.Fill)
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
                    } catch (e: Throwable) {
                        android.util.Log.e("MainScreen", "surfaceCreated initPreview error: ${e.message}")
                    }
                }
            }
            override fun surfaceChanged(h: android.view.SurfaceHolder, f: Int, w: Int, h1: Int) {}
            override fun surfaceDestroyed(h: android.view.SurfaceHolder) {
                try {
                    viewModel.stopPreview()
                } catch (e: Throwable) {
                    android.util.Log.e("MainScreen", "surfaceDestroyed stopPreview error: ${e.message}")
                }
            }
        }
        openGlView.holder.addCallback(callback)
        if (openGlView.holder.surface?.isValid == true) {
            try {
                viewModel.initPreview(openGlView)
            } catch (e: Throwable) {
                android.util.Log.e("MainScreen", "initial initPreview error: ${e.message}")
            }
        }
        onDispose {
            try {
                openGlView.holder.removeCallback(callback)
                viewModel.stopPreview()
            } catch (e: Throwable) {
                android.util.Log.e("MainScreen", "onDispose stopPreview error: ${e.message}")
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "borderGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val borderColor = if (uiState.isStreaming) CrimsonRed else CyberCyan
    val targetRatio = uiState.streamConfig.aspectRatio.getRatio()
    val isPortrait916 = uiState.streamConfig.aspectRatio == AspectRatioMode.PORTRAIT_9_16 ||
                        uiState.canvasScaleMode == MainViewModel.CanvasScaleMode.STRETCH

    val bgModifier = when (uiState.bgConfig.presetGradient) {
        "Cyber Neon" -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFF030A14), Color(0xFF062233), Color(0xFF011627))))
        "Midnight" -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFF050811), Color(0xFF0F172A), Color(0xFF020617))))
        "Sunset Violet" -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFF140824), Color(0xFF2E1065), Color(0xFF090314))))
        "Emerald Matrix" -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFF02160E), Color(0xFF064E3B), Color(0xFF02120B))))
        "Crimson Blood" -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFF1A050A), Color(0xFF450A0A), Color(0xFF0B0103))))
        else -> Modifier.background(Color(0xFF070A12))
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(bgModifier)
    ) {
        // 1. Stage Canvas (Full 100% Edge-to-Edge or Centered 16:9 Stage with Ambient Fullscreen Backdrop)
        if (isPortrait916) {
            // Full-Screen Edge-to-Edge Stage (Zero dead space)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = uiState.canvasZoom,
                        scaleY = uiState.canvasZoom,
                        translationX = uiState.canvasOffsetX,
                        translationY = uiState.canvasOffsetY
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newZoom = (uiState.canvasZoom * zoom).coerceIn(0.5f, 5.0f)
                            val newX = uiState.canvasOffsetX + pan.x
                            val newY = uiState.canvasOffsetY + pan.y
                            viewModel.updateCanvasTransform(newZoom, newX, newY)
                        }
                    }
            ) {
                // Background Layer (Image or Preset)
                if (uiState.bgConfig.imageUri != null) {
                    AsyncImage(
                        model = uiState.bgConfig.imageUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop // 9:16 Full Vertical Fill
                    )
                }

                VideoSourceContent(
                    currentSource = uiState.currentSource,
                    openGlView = openGlView,
                    m3u8Url = uiState.m3u8Url,
                    viewModel = viewModel,
                    uiState = uiState,
                    launchScreenCapture = launchScreenCapture,
                    isStreaming = uiState.isStreaming,
                    modifier = Modifier.fillMaxSize()
                )

                val activeScene = uiState.scenes.find { it.id == uiState.activeSceneId }
                if (activeScene != null && (activeScene.type == MainViewModel.SceneType.STARTING_SOON || activeScene.type == MainViewModel.SceneType.BE_RIGHT_BACK)) {
                    SceneBroadcastOverlay(
                        sceneType = activeScene.type,
                        startingSoonCountdown = uiState.startingSoonCountdown,
                        startingSoonTitle = uiState.startingSoonTitle,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (uiState.isStreaming) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                BorderStroke((2.dp * glowAlpha), CrimsonRed),
                                shape = RoundedCornerShape(0.dp)
                            )
                    )
                }
            }
        } else {
            // Ambient Backdrop filling 100% of the screen (eliminating dead black bars)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                borderColor.copy(alpha = 0.22f),
                                Color(0xFF0F172A),
                                Color(0xFF070A12)
                            )
                        )
                    )
            )

            // Centered 16:9 / Custom Broadcast Stage with Sleek Cyber Bezel
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .aspectRatio(targetRatio)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(borderColor, borderColor.copy(alpha = 0.35f), borderColor)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .border(
                        BorderStroke(
                            (2.dp * glowAlpha),
                            Brush.sweepGradient(listOf(Color.Transparent, borderColor, Color.Transparent))
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .graphicsLayer(
                        scaleX = uiState.canvasZoom,
                        scaleY = uiState.canvasZoom,
                        translationX = uiState.canvasOffsetX,
                        translationY = uiState.canvasOffsetY
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newZoom = (uiState.canvasZoom * zoom).coerceIn(0.5f, 5.0f)
                            val newX = uiState.canvasOffsetX + pan.x
                            val newY = uiState.canvasOffsetY + pan.y
                            viewModel.updateCanvasTransform(newZoom, newX, newY)
                        }
                    }
            ) {
                VideoSourceContent(
                    currentSource = uiState.currentSource,
                    openGlView = openGlView,
                    m3u8Url = uiState.m3u8Url,
                    viewModel = viewModel,
                    uiState = uiState,
                    launchScreenCapture = launchScreenCapture,
                    isStreaming = uiState.isStreaming,
                    modifier = Modifier.fillMaxSize()
                )

                val activeScene = uiState.scenes.find { it.id == uiState.activeSceneId }
                if (activeScene != null && (activeScene.type == MainViewModel.SceneType.STARTING_SOON || activeScene.type == MainViewModel.SceneType.BE_RIGHT_BACK)) {
                    SceneBroadcastOverlay(
                        sceneType = activeScene.type,
                        startingSoonCountdown = uiState.startingSoonCountdown,
                        startingSoonTitle = uiState.startingSoonTitle,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Aspect ratio badge pill at top center of the stage
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp),
                    color = Color(0xCC090D16),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, borderColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(borderColor))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = when (uiState.streamConfig.aspectRatio) {
                                AspectRatioMode.LANDSCAPE_16_9 -> localizedString("STAGE_CINEMA", viewModel)
                                AspectRatioMode.PORTRAIT_9_16 -> localizedString("STAGE_VERTICAL", viewModel)
                                AspectRatioMode.SQUARE_1_1 -> localizedString("STAGE_SQUARE", viewModel)
                                else -> localizedString("STAGE_CUSTOM", viewModel)
                            },
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            lineHeight = getLineHeight(viewModel)
                        )
                    }
                }
            }
        }

        // 2. Immersive Overlays with Safe Areas
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: Pro Telemetry Header with Status Bar Padding
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                ProTelemetryHeader(
                    isLive = uiState.isStreaming,
                    isPaused = uiState.isPaused,
                    isRecording = uiState.isRecording,
                    streamTime = uiState.streamTime,
                    bitrate = uiState.currentBitrate,
                    fps = uiState.fps,
                    droppedFramesPercent = uiState.droppedFramesPercent,
                    cpuTemp = uiState.cpuTemperature,
                    batteryPercent = uiState.batteryLevel,
                    endpoints = uiState.endpoints,
                    onToggleRecord = {
                        requestPermissionSafely(com.example.ui.components.PermissionType.MICROPHONE) {
                            viewModel.toggleRecording(context)
                        }
                    },
                    onSettingsClick = { showSettingsSheet = true },
                    onDestinationsClick = { showDestinationsSheet = true },
                    onTelemetryClick = {
                        Toast.makeText(context, uiState.diagnosticMessage, Toast.LENGTH_SHORT).show()
                    },
                    viewModel = viewModel
                )
            }

            // Middle: Floating Quick Controls
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Chat toggle
                    QuickGlassAction(
                        icon = if (isChatVisible) Icons.Default.ChatBubble else Icons.Default.ChatBubbleOutline,
                        label = localizedString("CHAT", viewModel),
                        viewModel = viewModel,
                        onClick = { isChatVisible = !isChatVisible }
                    )

                    // Flip camera
                    QuickGlassAction(
                        icon = Icons.Default.FlipCameraAndroid,
                        label = localizedString("FLIP", viewModel),
                        viewModel = viewModel,
                        onClick = {
                            requestPermissionSafely(com.example.ui.components.PermissionType.CAMERA) {
                                viewModel.switchCamera()
                            }
                        }
                    )

                    // Facecam PiP toggle
                    QuickGlassAction(
                        icon = Icons.Default.Face,
                        label = localizedString("PIP", viewModel),
                        viewModel = viewModel,
                        onClick = {
                            requestPermissionSafely(com.example.ui.components.PermissionType.CAMERA) {
                                viewModel.setFacecamEnabled(!uiState.isFacecamEnabled)
                            }
                        }
                    )

                    // Reset Preview Transform
                    QuickGlassAction(
                        icon = Icons.Default.Refresh,
                        label = localizedString("RESET", viewModel),
                        viewModel = viewModel,
                        onClick = { viewModel.updateCanvasTransform(1f, 0f, 0f) }
                    )
                }
            }

            // Bottom: Non-Overlapping Two-Tier Ergonomic Control Dock
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Frameless TikTok/Twitch Live Chat Overlay
                if (isChatVisible) {
                    FramelessLiveChatOverlay(
                        messages = uiState.chatMessages,
                        onPinComment = { viewModel.pinComment(it) },
                        onOpenChatHub = { showChatHubSheet = true },
                        viewModel = viewModel,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                // Live Multi-Scene Switcher Bar
                MultiSceneSelectorBar(
                    scenes = uiState.scenes,
                    activeSceneId = uiState.activeSceneId,
                    onSelectScene = { sceneId -> viewModel.selectScene(sceneId) },
                    onAddScene = { showAddSceneDialog = true },
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxWidth()
                )

                // Tier 1 (Source & Canvas Quick Bar): Sources + Aspect Ratios + Fit/Fill
                Tier1SourceAndCanvasBar(
                    currentSource = uiState.currentSource,
                    onSelectSource = { source ->
                        when (source) {
                            StreamSource.CAMERA -> {
                                requestPermissionSafely(com.example.ui.components.PermissionType.CAMERA) {
                                    viewModel.toggleSource(StreamSource.CAMERA)
                                }
                            }
                            StreamSource.M3U8 -> {
                                viewModel.toggleSource(StreamSource.M3U8)
                                showM3u8Dialog = true
                            }
                            StreamSource.SCREEN -> {
                                viewModel.toggleSource(StreamSource.SCREEN)
                                if (viewModel.cachedMediaProjectionResult == null) {
                                    launchScreenCapture()
                                }
                            }
                            StreamSource.AUDIO -> {
                                requestPermissionSafely(com.example.ui.components.PermissionType.MICROPHONE) {
                                    viewModel.toggleSource(StreamSource.AUDIO)
                                }
                            }
                        }
                    },
                    currentAspect = uiState.streamConfig.aspectRatio,
                    onSelectAspect = { aspect ->
                        viewModel.updateConfig(uiState.streamConfig.copy(aspectRatio = aspect))
                    },
                    canvasScaleMode = uiState.canvasScaleMode,
                    onToggleScaleMode = {
                        val modes = MainViewModel.CanvasScaleMode.entries
                        val nextIndex = (modes.indexOf(uiState.canvasScaleMode) + 1) % modes.size
                        viewModel.setCanvasScaleMode(modes[nextIndex])
                    },
                    viewModel = viewModel
                )

                // Tier 2 (Main Broadcast Dock): Mic Level, Mixer, Go Live/Stop (+Pause), Layers, Destinations
                ErgonomicBroadcastDock(
                    isLive = uiState.isStreaming,
                    isPaused = uiState.isPaused,
                    isMicOn = uiState.isMicEnabled,
                    audioLevel = (uiState.audioLevelL + uiState.audioLevelR) / 2f,
                    onToggleMic = {
                        if (!uiState.isMicEnabled) {
                            requestPermissionSafely(com.example.ui.components.PermissionType.MICROPHONE) {
                                viewModel.toggleMic(true)
                            }
                        } else {
                            viewModel.toggleMic(false)
                        }
                    },
                    onOpenMixer = { showAudioMixer = true },
                    onGoLive = {
                        if (uiState.isStreaming) {
                            viewModel.stopStream()
                        } else {
                            if (!viewModel.hasValidDestination()) {
                                Toast.makeText(context, if (uiState.appLang == "ar") "يرجى إعداد المنصات أولاً!" else "Configure destinations first!", Toast.LENGTH_SHORT).show()
                                showDestinationsSheet = true
                            } else {
                                val requiredPerm = if (uiState.currentSource == StreamSource.CAMERA) com.example.ui.components.PermissionType.CAMERA else com.example.ui.components.PermissionType.MICROPHONE
                                requestPermissionSafely(requiredPerm) {
                                    if (uiState.currentSource == StreamSource.SCREEN && viewModel.cachedMediaProjectionResult == null) {
                                        launchScreenCapture()
                                    } else {
                                        viewModel.startStream(context, viewModel.cachedMediaProjectionResult)
                                    }
                                }
                            }
                        }
                    },
                    onTogglePause = { viewModel.togglePauseStream() },
                    onOpenLayers = { showBackgroundSheet = true },
                    onOpenDestinations = { showDestinationsSheet = true },
                    activeDestinationsCount = uiState.endpoints.count { it.isEnabled },
                    viewModel = viewModel
                )
            }
        }

        // Persistent Channel Watermark Logo
        if (uiState.bgConfig.watermark.isEnabled) {
            val wm = uiState.bgConfig.watermark
            val wmAlign = when (wm.position) {
                MainViewModel.WatermarkPosition.TOP_LEFT -> Alignment.TopStart
                MainViewModel.WatermarkPosition.TOP_RIGHT -> Alignment.TopEnd
                MainViewModel.WatermarkPosition.BOTTOM_LEFT -> Alignment.BottomStart
                MainViewModel.WatermarkPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
            }
            val logoBitmap = remember(wm.logoUri) {
                wm.logoUri?.let { uriStr ->
                    try {
                        if (uriStr.startsWith("content://")) {
                            context.contentResolver.openInputStream(android.net.Uri.parse(uriStr))?.use {
                                BitmapFactory.decodeStream(it)
                            }
                        } else {
                            BitmapFactory.decodeFile(uriStr)
                        }
                    } catch (_: Exception) { null }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = if (wm.position == MainViewModel.WatermarkPosition.TOP_LEFT || wm.position == MainViewModel.WatermarkPosition.TOP_RIGHT) 56.dp else 16.dp,
                        bottom = if (wm.position == MainViewModel.WatermarkPosition.BOTTOM_LEFT || wm.position == MainViewModel.WatermarkPosition.BOTTOM_RIGHT) 140.dp else 16.dp,
                        start = 14.dp,
                        end = 14.dp
                    ),
                contentAlignment = wmAlign
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.45f * wm.opacity),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f * wm.opacity)),
                    modifier = Modifier.alpha(wm.opacity)
                ) {
                    if (logoBitmap != null) {
                        Image(
                            bitmap = logoBitmap.asImageBitmap(),
                            contentDescription = "Channel Logo",
                            modifier = Modifier
                                .size(wm.sizeDp.dp)
                                .padding(4.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("LIVE", color = CrimsonRed, fontWeight = FontWeight.Black, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PRO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Live Match Scoreboard Widget
        if (uiState.bgConfig.scoreboard.isEnabled) {
            val sb = uiState.bgConfig.scoreboard
            DraggableOverlay(
                x = sb.x,
                y = sb.y,
                onPositionChange = { nx, ny -> viewModel.updateScoreboard(sb.copy(x = nx, y = ny)) }
            ) {
                Surface(
                    color = Color(0xF2090D16),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, CyberCyan.copy(alpha = 0.6f)),
                    shadowElevation = 10.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = CrimsonRed.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    sb.period,
                                    color = CrimsonRed,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            val minutes = sb.matchTimeSeconds / 60
                            val seconds = sb.matchTimeSeconds % 60
                            Text(
                                String.format(Locale.US, "%02d:%02d", minutes, seconds),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Icon(
                                if (sb.isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Timer Toggle",
                                tint = CyberCyan,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { viewModel.toggleMatchTimer() }
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                sb.teamAName.uppercase(),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                            Surface(
                                color = CyberCyan.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, CyberCyan)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "${sb.teamAScore}",
                                        color = CyberCyan,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "-",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "${sb.teamBScore}",
                                        color = Color(0xFFA855F7),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            Text(
                                sb.teamBName.uppercase(),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                ScoreMiniButton("-") { viewModel.adjustScoreA(-1) }
                                ScoreMiniButton("+") { viewModel.adjustScoreA(1) }
                            }
                            Spacer(modifier = Modifier.width(18.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                ScoreMiniButton("-") { viewModel.adjustScoreB(-1) }
                                ScoreMiniButton("+") { viewModel.adjustScoreB(1) }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Breaking News Scrolling Ticker Banner
        if (uiState.bgConfig.ticker.isEnabled) {
            val ticker = uiState.bgConfig.ticker
            val scrollState = rememberScrollState()
            LaunchedEffect(ticker.speed, ticker.isEnabled) {
                while (ticker.isEnabled) {
                    val max = scrollState.maxValue
                    if (max > 0) {
                        val duration = (max * 18 / ticker.speed).toInt().coerceAtLeast(1000)
                        scrollState.scrollTo(0)
                        scrollState.animateScrollTo(
                            max,
                            animationSpec = tween(durationMillis = duration, easing = LinearEasing)
                        )
                    }
                    delay(200)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 126.dp)
            ) {
                Surface(
                    color = Color(ticker.backgroundColor),
                    modifier = Modifier.fillMaxWidth().height(30.dp),
                    border = BorderStroke(1.dp, Color(ticker.accentColor).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(scrollState),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${ticker.text}      •      ${ticker.text}      •      ${ticker.text}",
                            color = Color(ticker.textColor),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        }

        // Draggable Overlays (Text & Emojis)
        uiState.bgConfig.textOverlays.forEach { overlay ->
            DraggableOverlay(
                x = overlay.x,
                y = overlay.y,
                onPositionChange = { nx, ny -> viewModel.updateTextOverlay(overlay.copy(x = nx, y = ny)) }
            ) {
                Surface(
                    color = Color(overlay.backgroundColor),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(overlay.glowColor).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = overlay.text,
                        color = Color(overlay.textColor),
                        fontSize = (overlay.fontSize / 3).sp, // Scaled for preview
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        uiState.bgConfig.emojiOverlays.forEach { overlay ->
            DraggableOverlay(
                x = overlay.x,
                y = overlay.y,
                onPositionChange = { nx, ny -> viewModel.updateEmojiOverlay(overlay.copy(x = nx, y = ny)) }
            ) {
                Text(overlay.emoji, fontSize = 40.sp)
            }
        }

        // Draggable Facecam
        if (uiState.isFacecamEnabled && uiState.currentSource != StreamSource.CAMERA) {
            DraggableFacecam(
                initialX = uiState.facecamX,
                initialY = uiState.facecamY,
                onPositionChange = { x, y -> viewModel.updateFacecamPosition(x, y) }
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Face, null, tint = CyberCyan, modifier = Modifier.size(40.dp))
                }
            }
        }
    }

    // Modal Components
    if (showBackgroundSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBackgroundSheet = false },
            sheetState = bgSheetState,
            containerColor = Color(0xFF090D16),
            scrimColor = Color.Black.copy(alpha = 0.85f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 0.dp,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
        ) {
            BackgroundStudioSheet(viewModel = viewModel, onDismiss = { showBackgroundSheet = false })
        }
    }

    if (showAudioMixer) {
        ModalBottomSheet(
            onDismissRequest = { showAudioMixer = false },
            containerColor = Color(0xFF090D16),
            scrimColor = Color.Black.copy(alpha = 0.85f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 0.dp,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
        ) {
            AudioMixerSheet(
                viewModel = viewModel,
                onDismiss = { showAudioMixer = false }
            )
        }
    }

    if (showChatHubSheet) {
        ModalBottomSheet(
            onDismissRequest = { showChatHubSheet = false },
            containerColor = Color(0xFF090D16),
            scrimColor = Color.Black.copy(alpha = 0.85f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 0.dp,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
        ) {
            LiveChatHubSheet(
                twitchChannel = uiState.twitchChannel,
                onConnectTwitch = { viewModel.connectTwitchChat(it) },
                youtubeChannel = uiState.youtubeChannel,
                onConnectYouTube = { viewModel.connectYouTubeChat(it) },
                pinnedComment = uiState.pinnedComment,
                onUnpinComment = { viewModel.unpinComment() },
                onDismiss = { showChatHubSheet = false }
            )
        }
    }

    if (showM3u8Dialog) {
        ModalBottomSheet(
            onDismissRequest = { showM3u8Dialog = false },
            containerColor = Color(0xFF090D16),
            scrimColor = Color.Black.copy(alpha = 0.85f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 0.dp,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
        ) {
            M3u8GlassSheet(
                viewModel = viewModel,
                onDismiss = { showM3u8Dialog = false }
            )
        }
    }

    if (showDestinationsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDestinationsSheet = false },
            containerColor = Color(0xFF090D16),
            scrimColor = Color.Black.copy(alpha = 0.85f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 0.dp,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
        ) {
            DestinationsGlassSheet(
                endpoints = uiState.endpoints,
                isStreaming = uiState.isStreaming,
                currentBitrate = uiState.currentBitrate,
                fps = uiState.fps,
                diagnosticMessage = uiState.diagnosticMessage,
                onToggle = { id, enabled -> viewModel.toggleDestination(context, id, enabled) },
                onSaveCredentials = { id, url, key -> viewModel.updateDestinationCredentials(context, id, url, key) },
                onExportBackup = {
                    val json = viewModel.exportProfileBackup(context)
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("StreamProfiles", json))
                    Toast.makeText(context, "Stream profile backup copied to clipboard & saved!", Toast.LENGTH_SHORT).show()
                },
                onRestoreBackup = { json ->
                    val ok = viewModel.restoreProfileBackup(context, json)
                    if (ok) Toast.makeText(context, "Stream profiles restored successfully!", Toast.LENGTH_SHORT).show()
                    else Toast.makeText(context, "Failed to restore: invalid JSON", Toast.LENGTH_SHORT).show()
                },
                onResetPlatform = { id -> viewModel.resetDestination(context, id) },
                onAddCustom = { 
                    viewModel.addCustomDestination(context, "Custom RTMP", "rtmp://", "")
                },
                onDeleteCustom = { id -> viewModel.deleteCustomDestination(context, id) },
                viewModel = viewModel,
                onDismiss = { showDestinationsSheet = false }
            )
        }
    }

    if (editingEndpoint != null) {
        ModalBottomSheet(
            onDismissRequest = { editingEndpoint = null },
            containerColor = Color(0xFF090D16),
            scrimColor = Color.Black.copy(alpha = 0.85f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 0.dp,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
        ) {
            DestinationEditGlassSheet(
                endpoint = editingEndpoint!!,
                onSave = { updated ->
                    viewModel.saveDestination(context, updated)
                    editingEndpoint = null
                },
                onDelete = { id ->
                    viewModel.deleteCustomDestination(context, id)
                    editingEndpoint = null
                },
                onDismiss = { editingEndpoint = null }
            )
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = Color(0xFF090D16),
            scrimColor = Color.Black.copy(alpha = 0.85f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 0.dp,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
        ) {
            SettingsGlassSheet(
                config = uiState.streamConfig,
                onConfigChange = { viewModel.updateConfig(it) },
                isWatermarkEnabled = uiState.bgConfig.watermark.isEnabled,
                onWatermarkToggle = { viewModel.setWatermarkEnabled(it) },
                isTickerEnabled = uiState.bgConfig.ticker.isEnabled,
                onTickerToggle = { viewModel.setTickerEnabled(it) },
                isScoreboardEnabled = uiState.bgConfig.scoreboard.isEnabled,
                onScoreboardToggle = { viewModel.setScoreboardEnabled(it) },
                viewModel = viewModel,
                onDismiss = { showSettingsSheet = false }
            )
        }
    }

    if (showAddSceneDialog) {
        AddSceneDialog(
            viewModel = viewModel,
            onAddScene = { title, icon ->
                viewModel.addCustomScene(title, icon)
                showAddSceneDialog = false
            },
            onDismiss = { showAddSceneDialog = false }
        )
    }

    if (amberBannerMessage != null) {
        com.example.ui.components.AmberPermissionBanner(
            message = amberBannerMessage!!,
            onRetry = {
                pendingPermissionType?.let { type ->
                    val permString = when (type) {
                        com.example.ui.components.PermissionType.CAMERA -> android.Manifest.permission.CAMERA
                        com.example.ui.components.PermissionType.MICROPHONE -> android.Manifest.permission.RECORD_AUDIO
                        com.example.ui.components.PermissionType.NOTIFICATIONS -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) android.Manifest.permission.POST_NOTIFICATIONS else null
                        else -> null
                    }
                    if (permString != null) {
                        singlePermissionLauncher.launch(permString)
                    }
                }
            },
            onDismiss = { amberBannerMessage = null },
            modifier = Modifier.zIndex(99f)
        )
    }

    if (pendingPermissionType != null) {
        com.example.ui.components.CyberGlassPermissionSheet(
            permissionType = pendingPermissionType!!,
            viewModel = viewModel,
            onGrantRequested = {
                val type = pendingPermissionType
                if (type == com.example.ui.components.PermissionType.OVERLAY) {
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        context.startActivity(intent)
                    } catch (e: Exception) {}
                } else if (type != null) {
                    val permString = when (type) {
                        com.example.ui.components.PermissionType.CAMERA -> android.Manifest.permission.CAMERA
                        com.example.ui.components.PermissionType.MICROPHONE -> android.Manifest.permission.RECORD_AUDIO
                        com.example.ui.components.PermissionType.NOTIFICATIONS -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) android.Manifest.permission.POST_NOTIFICATIONS else null
                        else -> null
                    }
                    if (permString != null) {
                        singlePermissionLauncher.launch(permString)
                    }
                }
            },
            onDismiss = {
                pendingPermissionType = null
            }
        )
    }
}

@Composable
fun DestinationEditGlassSheet(
    endpoint: com.example.core.model.PlatformEndpoint,
    onSave: (com.example.core.model.PlatformEndpoint) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(endpoint.name) }
    var url by remember { mutableStateOf(endpoint.url) }
    var key by remember { mutableStateOf(endpoint.streamKey) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "EDIT ${endpoint.name.uppercase()}",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        GlassTextField(
            value = name,
            onValueChange = { name = it },
            label = "FRIENDLY NAME",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        GlassTextField(
            value = url,
            onValueChange = { url = it },
            label = "RTMP SERVER URL",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        GlassTextField(
            value = key,
            onValueChange = { key = it },
            label = "STREAM KEY",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (endpoint is com.example.core.model.PlatformEndpoint.CustomRTMP) {
                OutlinedButton(
                    onClick = { onDelete(endpoint.id) },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("DELETE", color = CrimsonRed, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = {
                    val updated = when (endpoint) {
                        is com.example.core.model.PlatformEndpoint.YouTube -> endpoint.copy(name = name, url = url, streamKey = key)
                        is com.example.core.model.PlatformEndpoint.Facebook -> endpoint.copy(name = name, url = url, streamKey = key)
                        is com.example.core.model.PlatformEndpoint.TikTok -> endpoint.copy(name = name, url = url, streamKey = key)
                        is com.example.core.model.PlatformEndpoint.Twitch -> endpoint.copy(name = name, url = url, streamKey = key)
                        is com.example.core.model.PlatformEndpoint.Twitter -> endpoint.copy(name = name, url = url, streamKey = key)
                        is com.example.core.model.PlatformEndpoint.Instagram -> endpoint.copy(name = name, url = url, streamKey = key)
                        is com.example.core.model.PlatformEndpoint.Kick -> endpoint.copy(name = name, url = url, streamKey = key)
                        is com.example.core.model.PlatformEndpoint.Telegram -> endpoint.copy(name = name, url = url, streamKey = key)
                        is com.example.core.model.PlatformEndpoint.CustomRTMP -> endpoint.copy(name = name, url = url, streamKey = key)
                    }
                    onSave(updated)
                },
                modifier = Modifier.weight(if (endpoint is com.example.core.model.PlatformEndpoint.CustomRTMP) 1.5f else 1f),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("SAVE DESTINATION", color = Color.Black, fontWeight = FontWeight.Black)
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
            .then(if (glowColor != Color.Transparent) Modifier.shadow(10.dp, CircleShape, ambientColor = glowColor, spotColor = glowColor) else Modifier),
        color = Color(0xBF1E2330),
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        content()
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

@Composable
fun ScoreMiniButton(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.White.copy(alpha = 0.12f),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.size(22.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundStudioSheet(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val config = uiState.bgConfig
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var bgUrlInput by remember { mutableStateOf("") }
    
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let { viewModel.setBackgroundImageFromUri(context, it) }
        }
    )

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let { viewModel.updateWatermark(config.watermark.copy(logoUri = it.toString(), isEnabled = true)) }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
            .heightIn(max = 720.dp)
    ) {
        // Sheet Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    localizedString("STUDIO_LAYERS", viewModel),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    color = Color.White
                )
                Text(
                    localizedString("STUDIO_LAYERS_SUB", viewModel),
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, null, tint = Color.Gray)
            }
        }

        // 4-Tab Navigation Selector
        PillSelector(
            options = listOf(
                localizedString("TAB_BG", viewModel),
                localizedString("TAB_OVERLAYS", viewModel),
                localizedString("TAB_TEXT", viewModel),
                localizedString("TAB_EMOJI", viewModel)
            ),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it }
        )

        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedTab) {
                // TAB 0: FRAME & BACKGROUND
                0 -> {
                    // Image Source Card
                    GlassSectionCard(title = localizedString("IMAGE_SOURCE", viewModel)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(
                                onClick = {
                                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                color = Color(0xFF0D111C),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color(0xFF2A324B).copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, null, tint = CyberCyan, modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        localizedString("CHOOSE_GALLERY", viewModel),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = getLineHeight(viewModel)
                                    )
                                }
                            }

                            // Remote Image URL Input
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = bgUrlInput,
                                    onValueChange = { bgUrlInput = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("https://image-url.com/bg.jpg", color = Color.Gray, fontSize = 11.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = CyberCyan,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                                    )
                                )
                                Button(
                                    onClick = {
                                        if (bgUrlInput.isNotBlank()) {
                                            viewModel.setBackgroundImageFromUrl(context, bgUrlInput)
                                            bgUrlInput = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(localizedString("LOAD_PLAY_STUDIO", viewModel).take(4), color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp, lineHeight = getLineHeight(viewModel))
                                }
                            }

                            if (config.imageUri != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(localizedString("PRESET_LAYOUT_MODE", viewModel), color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = getLineHeight(viewModel))
                                    TextButton(onClick = { viewModel.removeBackground(context) }) {
                                        Text(localizedString("DELETE", viewModel), color = CrimsonRed, fontSize = 11.sp, fontWeight = FontWeight.Black, lineHeight = getLineHeight(viewModel))
                                    }
                                }
                                PillSelector(
                                    options = listOf(
                                        localizedString("MODE_COVER", viewModel),
                                        localizedString("MODE_CONTAIN", viewModel),
                                        localizedString("MODE_TOP", viewModel),
                                        localizedString("MODE_BOTTOM", viewModel),
                                        localizedString("MODE_STRETCH", viewModel)
                                    ),
                                    selectedIndex = when (config.displayMode) {
                                        MainViewModel.ImageDisplayMode.FULL_IMAGE -> 0
                                        MainViewModel.ImageDisplayMode.PIP_OVERLAY -> 1
                                        else -> 2
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
                            }
                        }
                    }

                    // Preset Dark Gradients
                    GlassSectionCard(title = localizedString("PRESET_LAYOUT_MODE", viewModel)) {
                        val gradientPresets = listOf("Cyber Neon", "Midnight", "Sunset Violet", "Emerald Matrix", "Crimson Blood", "None")
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                gradientPresets.forEach { preset ->
                                    val isSelected = (config.presetGradient == preset) || (preset == "None" && config.presetGradient == null)
                                    Surface(
                                        onClick = {
                                            viewModel.setPresetGradient(if (preset == "None") null else preset)
                                        },
                                        color = if (isSelected) CyberCyan.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, if (isSelected) CyberCyan else Color.White.copy(alpha = 0.12f))
                                    ) {
                                        Text(
                                            text = preset,
                                            color = if (isSelected) CyberCyan else Color.White.copy(alpha = 0.8f),
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            lineHeight = getLineHeight(viewModel)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Interactive Dimension & Transform Controls
                    GlassSectionCard(title = localizedString("CANVAS_CONTROLS", viewModel)) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            CompositorSlider(localizedString("ZOOM_SCALE", viewModel), config.zoom, 0.1f..3f) { viewModel.updateBackgroundConfig(config.copy(zoom = it)) }
                            CompositorSlider(localizedString("X_OFFSET", viewModel), config.offsetX, -500f..500f, suffix = "px") { viewModel.updateBackgroundConfig(config.copy(offsetX = it)) }
                            CompositorSlider(localizedString("Y_OFFSET", viewModel), config.offsetY, -500f..500f, suffix = "px") { viewModel.updateBackgroundConfig(config.copy(offsetY = it)) }
                            CompositorSlider(localizedString("GAUSSIAN_BLUR", viewModel), config.blurRadius, 0f..30f, suffix = "px") { viewModel.updateBackgroundConfig(config.copy(blurRadius = it)) }
                            CompositorSlider(localizedString("OPACITY", viewModel), config.opacity, 0f..1f) { viewModel.updateBackgroundConfig(config.copy(opacity = it)) }
                        }
                    }
                }

                // TAB 1: TEXT, TICKER & ALERTS
                1 -> {
                    // Section A: Multiple Custom Text Overlays
                    GlassSectionCard(title = localizedString("CUSTOM_TEXT_OVERLAYS", viewModel)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { viewModel.addTextOverlay() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x3338BDF8)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.TextFields, null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(localizedString("ADD_TEXT_LAYER", viewModel), color = CyberCyan, fontWeight = FontWeight.Black, fontSize = 11.sp, lineHeight = getLineHeight(viewModel))
                            }

                            if (config.textOverlays.isEmpty()) {
                                Text(localizedString("NO_ACTIVE_TEXT", viewModel), color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, lineHeight = getLineHeight(viewModel))
                            }

                            config.textOverlays.forEach { overlay ->
                                OverlayItemCard(
                                    title = "TEXT: ${overlay.text.take(15)}...",
                                    icon = Icons.Default.TextFields,
                                    color = CyberCyan,
                                    onDelete = { viewModel.removeTextOverlay(overlay.id) }
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        GlassTextField(
                                            value = overlay.text,
                                            onValueChange = { viewModel.updateTextOverlay(overlay.copy(text = it)) },
                                            label = localizedString("CONTENT_LABEL", viewModel)
                                        )

                                        Text(localizedString("TEXT_COLOR", viewModel), color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = getLineHeight(viewModel))
                                        ColorRow(selectedColor = overlay.textColor, onSelect = { viewModel.updateTextOverlay(overlay.copy(textColor = it)) })

                                        Text(localizedString("NEON_GLOW_COLOR", viewModel), color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = getLineHeight(viewModel))
                                        ColorRow(selectedColor = overlay.glowColor, onSelect = { viewModel.updateTextOverlay(overlay.copy(glowColor = it)) })

                                        Text(localizedString("PILL_BG_COLOR", viewModel), color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = getLineHeight(viewModel))
                                        ColorRow(selectedColor = overlay.backgroundColor, onSelect = { viewModel.updateTextOverlay(overlay.copy(backgroundColor = it)) })

                                        CompositorSlider(localizedString("FONT_SIZE", viewModel), overlay.fontSize.toFloat(), 12f..80f) { viewModel.updateTextOverlay(overlay.copy(fontSize = it.toInt())) }
                                        CompositorSlider(localizedString("OPACITY", viewModel), overlay.opacity, 0f..1f) { viewModel.updateTextOverlay(overlay.copy(opacity = it)) }
                                    }
                                }
                            }
                        }
                    }

                    // Section B: Bottom Breaking News Scrolling Ticker
                    GlassSectionCard(title = localizedString("BOTTOM_NEWS_TICKER", viewModel)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            FeatureToggleRow(
                                title = localizedString("ENABLE_NEWS_TICKER", viewModel),
                                checked = config.ticker.isEnabled,
                                onCheckedChange = { viewModel.setTickerEnabled(it) }
                            )

                            if (config.ticker.isEnabled) {
                                GlassTextField(
                                    value = config.ticker.text,
                                    onValueChange = { viewModel.updateTicker(config.ticker.copy(text = it)) },
                                    label = localizedString("TICKER_HEADLINE", viewModel)
                                )

                                Text(localizedString("SCROLL_SPEED", viewModel), color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = getLineHeight(viewModel))
                                PillSelector(
                                    options = listOf("Slow (0.5x)", "Normal (1.0x)", "Fast (2.0x)"),
                                    selectedIndex = when {
                                        config.ticker.speed < 0.8f -> 0
                                        config.ticker.speed > 1.5f -> 2
                                        else -> 1
                                    },
                                    onSelect = { idx ->
                                        val spd = when (idx) {
                                            0 -> 0.5f
                                            2 -> 2.0f
                                            else -> 1.0f
                                        }
                                        viewModel.updateTicker(config.ticker.copy(speed = spd))
                                    }
                                )

                                Text(localizedString("ACCENT_GLOW_COLOR", viewModel), color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = getLineHeight(viewModel))
                                ColorRow(
                                    selectedColor = config.ticker.accentColor,
                                    onSelect = { viewModel.updateTicker(config.ticker.copy(accentColor = it)) }
                                )

                                Text(localizedString("BACKGROUND_COLOR", viewModel), color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = getLineHeight(viewModel))
                                ColorRow(
                                    selectedColor = config.ticker.backgroundColor,
                                    onSelect = { viewModel.updateTicker(config.ticker.copy(backgroundColor = it)) }
                                )
                            }
                        }
                    }
                }

                // TAB 2: LIVE MATCH SCOREBOARD / TIMER WIDGET
                2 -> {
                    GlassSectionCard(title = "LIVE MATCH SCOREBOARD OVERLAY") {
                        val sb = config.scoreboard
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            FeatureToggleRow(
                                title = "ENABLE LIVE MATCH SCOREBOARD",
                                checked = sb.isEnabled,
                                onCheckedChange = { viewModel.setScoreboardEnabled(it) }
                            )

                            if (sb.isEnabled) {
                                Text("MATCH PERIOD", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                PillSelector(
                                    options = listOf("1ST HALF", "2ND HALF", "EXTRA", "LIVE", "FINAL"),
                                    selectedIndex = when (sb.period) {
                                        "1ST HALF" -> 0
                                        "2ND HALF" -> 1
                                        "EXTRA" -> 2
                                        "LIVE" -> 3
                                        else -> 4
                                    },
                                    onSelect = { idx ->
                                        val period = listOf("1ST HALF", "2ND HALF", "EXTRA", "LIVE", "FINAL")[idx]
                                        viewModel.updateScoreboard(sb.copy(period = period))
                                    }
                                )

                                // Team A and Team B inputs
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Team A
                                    Surface(
                                        color = Color(0xFF0F1422),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text("TEAM A (HOME)", color = CyberCyan, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                            OutlinedTextField(
                                                value = sb.teamAName,
                                                onValueChange = { viewModel.updateScoreboard(sb.copy(teamAName = it)) },
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White,
                                                    focusedBorderColor = CyberCyan,
                                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                                ),
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                ScoreMiniButton("-") { viewModel.adjustScoreA(-1) }
                                                Text("${sb.teamAScore}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                                ScoreMiniButton("+") { viewModel.adjustScoreA(1) }
                                            }
                                        }
                                    }

                                    // Team B
                                    Surface(
                                        color = Color(0xFF0F1422),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.4f)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text("TEAM B (AWAY)", color = Color(0xFFA855F7), fontSize = 9.sp, fontWeight = FontWeight.Black)
                                            OutlinedTextField(
                                                value = sb.teamBName,
                                                onValueChange = { viewModel.updateScoreboard(sb.copy(teamBName = it)) },
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White,
                                                    focusedBorderColor = Color(0xFFA855F7),
                                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                                ),
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                ScoreMiniButton("-") { viewModel.adjustScoreB(-1) }
                                                Text("${sb.teamBScore}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                                ScoreMiniButton("+") { viewModel.adjustScoreB(1) }
                                            }
                                        }
                                    }
                                }

                                // Match Timer Control Row
                                Surface(
                                    color = Color(0xFF0D1220),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val minutes = sb.matchTimeSeconds / 60
                                        val seconds = sb.matchTimeSeconds % 60
                                        Column {
                                            Text("MATCH TIMER", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                String.format(Locale.US, "%02d:%02d", minutes, seconds),
                                                color = Color.White,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Button(
                                                onClick = { viewModel.toggleMatchTimer() },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (sb.isTimerRunning) Color(0xFFF59E0B) else Color(0xFF10B981)
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    if (sb.isTimerRunning) "PAUSE" else "START",
                                                    color = Color.Black,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 10.sp
                                                )
                                            }

                                            OutlinedButton(
                                                onClick = { viewModel.resetMatchTimer() },
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                            ) {
                                                Text("RESET", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 3: WATERMARK LOGO & EMOJI BURSTS
                3 -> {
                    // Section A: Watermark Channel Logo
                    GlassSectionCard(title = "PERSISTENT CHANNEL WATERMARK LOGO") {
                        val wm = config.watermark
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            FeatureToggleRow(
                                title = "ENABLE CHANNEL LOGO",
                                checked = wm.isEnabled,
                                onCheckedChange = { viewModel.setWatermarkEnabled(it) }
                            )

                            if (wm.isEnabled) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            logoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        },
                                        modifier = Modifier.weight(1f).height(44.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x3310B981)),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                                    ) {
                                        Icon(Icons.Default.Upload, null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            if (wm.logoUri != null) "REPLACE LOGO" else "UPLOAD PNG LOGO",
                                            color = Color(0xFF10B981),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 10.sp
                                        )
                                    }

                                    if (wm.logoUri != null) {
                                        OutlinedButton(
                                            onClick = { viewModel.updateWatermark(wm.copy(logoUri = null)) },
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.5f))
                                        ) {
                                            Text("DEFAULT", color = CrimsonRed, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }

                                Text("CORNER POSITION", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                PillSelector(
                                    options = listOf("TOP-LEFT", "TOP-RIGHT", "BOTTOM-LEFT", "BOTTOM-RIGHT"),
                                    selectedIndex = when (wm.position) {
                                        MainViewModel.WatermarkPosition.TOP_LEFT -> 0
                                        MainViewModel.WatermarkPosition.TOP_RIGHT -> 1
                                        MainViewModel.WatermarkPosition.BOTTOM_LEFT -> 2
                                        MainViewModel.WatermarkPosition.BOTTOM_RIGHT -> 3
                                    },
                                    onSelect = { idx ->
                                        val pos = when (idx) {
                                            0 -> MainViewModel.WatermarkPosition.TOP_LEFT
                                            1 -> MainViewModel.WatermarkPosition.TOP_RIGHT
                                            2 -> MainViewModel.WatermarkPosition.BOTTOM_LEFT
                                            else -> MainViewModel.WatermarkPosition.BOTTOM_RIGHT
                                        }
                                        viewModel.updateWatermark(wm.copy(position = pos))
                                    }
                                )

                                CompositorSlider("LOGO SIZE", wm.sizeDp.toFloat(), 24f..110f, suffix = "dp") {
                                    viewModel.updateWatermark(wm.copy(sizeDp = it.toInt()))
                                }
                                CompositorSlider("LOGO OPACITY", wm.opacity, 0.1f..1f) {
                                    viewModel.updateWatermark(wm.copy(opacity = it))
                                }
                            }
                        }
                    }

                    // Section B: Floating Emoji Bursts & Draggable Stickers
                    GlassSectionCard(title = "FLOATING EMOJI PARTICLES & STICKERS") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("TAP TO TRIGGER 2-SECOND LIVE EMOJI BURST", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                            EmojiPickerBar(onSelect = { viewModel.triggerEmojiBurst(it) })

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = { viewModel.addEmojiOverlay() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33A855F7)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.Face, null, tint = Color(0xFFA855F7), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ADD RESIZABLE EMOJI STICKER", color = Color(0xFFA855F7), fontWeight = FontWeight.Black, fontSize = 11.sp)
                            }

                            config.emojiOverlays.forEach { overlay ->
                                OverlayItemCard(
                                    title = "STICKER: ${overlay.emoji}",
                                    icon = Icons.Default.Face,
                                    color = Color(0xFFA855F7),
                                    onDelete = { viewModel.removeEmojiOverlay(overlay.id) }
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        EmojiPickerBar(onSelect = { viewModel.updateEmojiOverlay(overlay.copy(emoji = it)) })
                                        CompositorSlider("SCALE", overlay.scale, 0.5f..5f) { viewModel.updateEmojiOverlay(overlay.copy(scale = it)) }
                                        CompositorSlider("ROTATION", overlay.rotation, 0f..360f, suffix = "°") { viewModel.updateEmojiOverlay(overlay.copy(rotation = it)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("DONE", color = Color.Black, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun CompositorSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, suffix: String = "", onValueChange: (Float) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("${if (range.endInclusive <= 1f) (value * 100).toInt() else value.toInt()}$suffix", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        GlowingSlider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
fun OverlayItemCard(title: String, icon: ImageVector, color: Color, onDelete: () -> Unit, content: @Composable () -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    Surface(
        color = Color(0xBF1E2330),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isExpanded) color.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Row {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, null, tint = CrimsonRed, modifier = Modifier.size(16.dp))
                    }
                    Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Color.Gray)
                }
            }
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                content()
            }
        }
    }
}

@Composable
fun EmojiPickerBar(onSelect: (String) -> Unit) {
    val emojis = listOf("🔥", "❤️", "⚽", "🎙️", "🔴", "👏", "⚡", "🌟", "🎮", "👑")
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        emojis.forEach { emoji ->
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable { onSelect(emoji) },
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 20.sp)
            }
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
    viewModel: MainViewModel,
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

            Column(
                horizontalAlignment = Alignment.CenterHorizontally, 
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(end = 76.dp) // Avoid colliding with right-side FABs
            ) {
                Surface(
                    color = if (isStreaming) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFF818CF8).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (isStreaming) Color(0xFFEF4444) else Color(0xFF818CF8))
                ) {
                    Text(
                        text = if (isStreaming) localizedString("LIVE", viewModel) else if (isReady) localizedString("READY", viewModel) else localizedString("CONNECTING", viewModel),
                        color = if (isStreaming) Color(0xFFEF4444) else Color(0xFF818CF8),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        lineHeight = getLineHeight(viewModel)
                    )
                }

                Text(
                    text = if (isStreaming) localizedString("LIVE", viewModel) else localizedString("SCREEN_BROADCAST_SUB", viewModel),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = getLineHeight(viewModel)
                )
                Text(
                    text = localizedString("SCREEN_SHARE_DESC", viewModel),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = getLineHeight(viewModel)
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
                    Text(localizedString("START_SCREEN_SHARE", viewModel), color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp, lineHeight = getLineHeight(viewModel))
                }
            }
        }
    }
}

@Composable
fun QuickGlassAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(64.dp)
            .shadow(4.dp, CircleShape, ambientColor = Color.Cyan.copy(alpha = 0.2f), spotColor = Color.Cyan.copy(alpha = 0.2f)),
        color = Color(0x990F172A),
        shape = CircleShape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            Icon(icon, null, tint = CyberCyan, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = getLineHeight(viewModel)
            )
        }
    }
}

@Composable
fun DraggableOverlay(
    x: Float,
    y: Float,
    onPositionChange: (Float, Float) -> Unit,
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(x) }
    var offsetY by remember { mutableFloatStateOf(y) }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
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
fun ColorRow(selectedColor: Int, onSelect: (Int) -> Unit) {
    val colors = listOf(
        0xFFFFFFFF.toInt(), // White
        0xFF00F2FF.toInt(), // Cyan
        0xFFFF2A54.toInt(), // Crimson
        0xFF53FC18.toInt(), // Green
        0xFF9146FF.toInt(), // Purple
        0xFFF59E0B.toInt(), // Orange
        0xFF000000.toInt(), // Black
        0x80000000.toInt()  // Transparent Black
    )
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        colors.forEach { colorInt ->
            val color = Color(colorInt)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(2.dp, if (selectedColor == colorInt) Color.White else Color.Transparent, CircleShape)
                    .clickable { onSelect(colorInt) }
            )
        }
    }
}
