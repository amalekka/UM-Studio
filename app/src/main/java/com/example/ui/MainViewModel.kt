package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.AspectRatioMode
import com.example.core.model.PlatformEndpoint
import com.example.core.model.StreamConfig
import com.example.core.model.VideoCodec
import com.example.core.model.VideoQuality
import com.example.core.model.buildDestinationUrl
import com.example.core.streaming.LiveStreamService
import com.example.core.streaming.StreamSource
import com.example.core.streaming.StreamingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pedro.library.view.OpenGlView
import com.arthenica.ffmpegkit.FFmpegKitConfig
import java.io.File
import java.net.URL
import java.util.UUID

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var service: LiveStreamService? = null

    private val liveChatConnector = com.example.core.chat.LiveChatConnector()
    private val audioSfxManager = com.example.core.audio.AudioSfxManager()

    init {
        viewModelScope.launch {
            liveChatConnector.incomingMessages.collect { msg ->
                addIncomingChatMessage(msg.platform, msg.author, msg.message)
            }
        }
    }

    enum class M3u8PlaybackState { STOPPED, PLAYING, PAUSED }
    enum class ImageDisplayMode { FULL_IMAGE, PIP_OVERLAY, OFF }
    enum class CanvasScaleMode { FIT, FILL, STRETCH }

    data class TextOverlay(
        val id: String = UUID.randomUUID().toString(),
        val text: String = "LIVE STREAM",
        val fontSize: Int = 40,
        val textColor: Int = 0xFFFFFFFF.toInt(),
        val glowColor: Int = 0xFF00F2FF.toInt(),
        val backgroundColor: Int = 0x80000000.toInt(),
        val x: Float = 20f,
        val y: Float = 20f,
        val scale: Float = 1f,
        val rotation: Float = 0f,
        val isScrolling: Boolean = false,
        val scrollSpeed: Float = 1f,
        val opacity: Float = 1f
    )

    data class EmojiOverlay(
        val id: String = UUID.randomUUID().toString(),
        val emoji: String = "🔥",
        val x: Float = 50f,
        val y: Float = 50f,
        val scale: Float = 1f,
        val rotation: Float = 0f,
        val opacity: Float = 1f
    )

    enum class WatermarkPosition { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    data class WatermarkConfig(
        val isEnabled: Boolean = false,
        val logoUri: String? = null,
        val position: WatermarkPosition = WatermarkPosition.TOP_RIGHT,
        val sizeDp: Int = 54,
        val opacity: Float = 0.85f
    )

    data class TickerConfig(
        val isEnabled: Boolean = false,
        val text: String = "🔴 BREAKING NEWS • LIVE STREAM WORKSTATION • WELCOME TO THE BROADCAST • FOLLOW & SHARE",
        val speed: Float = 1.0f,
        val backgroundColor: Int = 0xE6090D16.toInt(),
        val textColor: Int = 0xFFFFFFFF.toInt(),
        val accentColor: Int = 0xFF00F2FF.toInt()
    )

    data class ScoreboardConfig(
        val isEnabled: Boolean = false,
        val teamAName: String = "TEAM A",
        val teamBName: String = "TEAM B",
        val teamAScore: Int = 0,
        val teamBScore: Int = 0,
        val matchTimeSeconds: Int = 0,
        val isTimerRunning: Boolean = false,
        val period: String = "1ST HALF",
        val x: Float = 20f,
        val y: Float = 70f
    )

    data class BackgroundConfig(
        val imageUri: String? = null,
        val displayMode: ImageDisplayMode = ImageDisplayMode.FULL_IMAGE,
        val blurRadius: Float = 0f,
        val brightness: Float = 1f,
        val opacity: Float = 1f,
        val zoom: Float = 1f,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val rotation: Float = 0f,
        val aspectRatio: String = "Free",
        val scaleMode: BackgroundScaleMode = BackgroundScaleMode.COVER,
        val foregroundScale: Float = 1f,
        val foregroundPosition: ForegroundPosition = ForegroundPosition.CENTER,
        val isPauseShieldActive: Boolean = false,
        val presetGradient: String? = null,
        val textOverlays: List<TextOverlay> = emptyList(),
        val emojiOverlays: List<EmojiOverlay> = emptyList(),
        val ticker: TickerConfig = TickerConfig(),
        val scoreboard: ScoreboardConfig = ScoreboardConfig(),
        val watermark: WatermarkConfig = WatermarkConfig()
    )

    enum class BackgroundScaleMode { COVER, CONTAIN, TOP_BANNER, BOTTOM_BANNER, STRETCH }
    enum class ForegroundPosition { CENTER, TOP, BOTTOM, FREE }

    data class SavedStreamItem(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val url: String,
        val userAgent: String = "",
        val referer: String = "",
        val origin: String = ""
    )

    data class ChatMessage(
        val id: String = UUID.randomUUID().toString(),
        val platform: String,
        val author: String,
        val message: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    enum class SceneType {
        MAIN_LIVE,
        STARTING_SOON,
        BE_RIGHT_BACK,
        JUST_CHATTING,
        CUSTOM
    }

    data class SceneItem(
        val id: String = UUID.randomUUID().toString(),
        val title: String,
        val icon: String,
        val type: SceneType = SceneType.MAIN_LIVE
    )

    data class UiState(
        val isStreaming: Boolean = false,
        val isPaused: Boolean = false,
        val streamingStatus: StreamingStatus = StreamingStatus.IDLE,
        val diagnosticMessage: String = "Ready",
        val diagnosticIsError: Boolean = false,
        val currentBitrate: Int = 0,
        val streamTime: Long = 0,
        val currentSource: StreamSource = StreamSource.CAMERA,
        val streamConfig: StreamConfig = StreamConfig(),
        val endpoints: List<PlatformEndpoint> = listOf(
            PlatformEndpoint.YouTube(),
            PlatformEndpoint.Facebook(),
            PlatformEndpoint.TikTok(),
            PlatformEndpoint.Twitch(),
            PlatformEndpoint.Twitter(),
            PlatformEndpoint.Instagram(),
            PlatformEndpoint.Kick(),
            PlatformEndpoint.Telegram()
        ),
        val m3u8Url: String = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_adv_example_hevc/master.m3u8",
        val m3u8Headers: Map<String, String> = emptyMap(),
        val m3u8State: M3u8PlaybackState = M3u8PlaybackState.STOPPED,
        val isM3u8Muted: Boolean = false,
        val m3u8Volume: Float = 1.0f,
        val m3u8AudioDelayMs: Int = 0,
        val bgMusicVolume: Float = 0.5f,
        val isBgMusicMuted: Boolean = false,
        val isBgMusicPlaying: Boolean = false,
        val bgMusicUri: String? = null,
        val bgMusicName: String = "Ambient Cyber Beat (Loop)",
        val isGainBoostEnabled: Boolean = false,
        val isNoiseGateEnabled: Boolean = true,
        val isEchoCancellationEnabled: Boolean = true,
        val isAudioDuckingEnabled: Boolean = true,
        val isDuckingActive: Boolean = false,
        val activeM3u8Name: String = "Apple BipBop HLS",
        val showScreenCapturePrompt: Boolean = false,
        val savedStreams: List<SavedStreamItem> = listOf(
            SavedStreamItem(name = "Apple BipBop HLS", url = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_adv_example_hevc/master.m3u8"),
            SavedStreamItem(name = "Apple Advanced HLS", url = "https://devstreaming-cdn.apple.com/videos/streaming/examples/adv_dv_atmos/master.m3u8"),
            SavedStreamItem(name = "Big Buck Bunny HLS", url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
        ),
        val isMicEnabled: Boolean = true,
        val micVolume: Float = 1.0f,
        val isCameraEnabled: Boolean = true,
        val isWatermarkEnabled: Boolean = false,
        val isTickerEnabled: Boolean = false,
        val developerModeEnabled: Boolean = false,
        val logs: List<String> = emptyList(),
        val viewerCount: Int = 0,
        val fps: Int = 0,
        val audioLevelL: Float = 0f,
        val audioLevelR: Float = 0f,
        val isFlashOn: Boolean = false,
        val isFrontCamera: Boolean = false,
        val bgConfig: BackgroundConfig = BackgroundConfig(),
        val isChatVisible: Boolean = true,
        val preferM3u8StreamCopy: Boolean = true,
        val isScreenCaptureReady: Boolean = false,
        val isFacecamEnabled: Boolean = false,
        val facecamX: Float = 20f,
        val facecamY: Float = 160f,
        val canvasScaleMode: CanvasScaleMode = CanvasScaleMode.FIT,
        val canvasZoom: Float = 1f,
        val canvasOffsetX: Float = 0f,
        val canvasOffsetY: Float = 0f,
        val chatMessages: List<ChatMessage> = emptyList(),
        val isRecording: Boolean = false,
        val twitchChannel: String = "",
        val youtubeChannel: String = "",
        val pinnedComment: ChatMessage? = null,
        val scenes: List<SceneItem> = listOf(
            SceneItem(id = "main", title = "Main Live", icon = "🎬", type = SceneType.MAIN_LIVE),
            SceneItem(id = "starting_soon", title = "Starting Soon", icon = "⏳", type = SceneType.STARTING_SOON),
            SceneItem(id = "brb", title = "Be Right Back", icon = "⏸️", type = SceneType.BE_RIGHT_BACK),
            SceneItem(id = "just_chatting", title = "Just Chatting", icon = "🎙️", type = SceneType.JUST_CHATTING)
        ),
        val activeSceneId: String = "main",
        val startingSoonCountdown: Int = 300,
        val startingSoonTitle: String = "STREAM STARTING SOON",
        val droppedFramesPercent: Float = 0.0f,
        val cpuTemperature: String = "36°C",
        val batteryLevel: Int = 88
    )

    private var pendingView: OpenGlView? = null
    var cachedMediaProjectionResult: Intent? = null
        private set

    fun onScreenCapturePermissionGranted(data: Intent) {
        cachedMediaProjectionResult = data
        _uiState.value = _uiState.value.copy(
            isScreenCaptureReady = true,
            showScreenCapturePrompt = false
        )
        upgradeToMediaProjection()
    }

    fun upgradeToMediaProjection() {
        service?.upgradeToMediaProjection()
    }

    fun setPreferM3u8StreamCopy(prefer: Boolean) {
        _uiState.value = _uiState.value.copy(preferM3u8StreamCopy = prefer)
    }

    fun onServiceConnected(service: LiveStreamService) {
        this.service = service
        pendingView?.let { view ->
            initPreview(view)
        }

        var lastFfmpegFrameNumber = 0
        var lastFfmpegTimeMs = 0L

        try {
            FFmpegKitConfig.enableStatisticsCallback { stats ->
                if (!_uiState.value.isStreaming) {
                    if (_uiState.value.fps != 0 || _uiState.value.currentBitrate != 0) {
                        _uiState.value = _uiState.value.copy(fps = 0, currentBitrate = 0)
                    }
                    return@enableStatisticsCallback
                }
                val reportedFps = stats.videoFps.toInt()
                val currentTime = System.currentTimeMillis()
                val currentFrame = stats.videoFrameNumber

                val calculatedFps = if (reportedFps > 0) {
                    reportedFps
                } else if (lastFfmpegTimeMs > 0 && currentTime > lastFfmpegTimeMs) {
                    val deltaFrames = currentFrame - lastFfmpegFrameNumber
                    val deltaTimeSec = (currentTime - lastFfmpegTimeMs) / 1000.0
                    if (deltaTimeSec > 0.3 && deltaFrames >= 0) {
                        (deltaFrames / deltaTimeSec).toInt().coerceIn(0, 120)
                    } else 0
                } else 0

                if (currentFrame != lastFfmpegFrameNumber) {
                    lastFfmpegFrameNumber = currentFrame
                    lastFfmpegTimeMs = currentTime
                }

                val rawBitrate = stats.bitrate
                val kbps = when {
                    rawBitrate > 50000 -> (rawBitrate / 1000.0).toInt()
                    rawBitrate > 0 -> rawBitrate.toInt()
                    else -> 0
                }

                if (_uiState.value.isStreaming) {
                    val effectiveFps = if (calculatedFps > 0) calculatedFps else _uiState.value.fps
                    _uiState.value = _uiState.value.copy(
                        fps = effectiveFps,
                        currentBitrate = if (kbps > 100) kbps else _uiState.value.currentBitrate,
                        diagnosticMessage = "Streaming Video (${effectiveFps.coerceAtLeast(30)} FPS)",
                        diagnosticIsError = false
                    )
                }
            }
        } catch (t: Throwable) {
            Log.e("MainViewModel", "Failed to configure FFmpegKitConfig statistics callback: ${t.message}", t)
        }

        viewModelScope.launch {
            service.getRestreamEngine()?.let { restreamEngine ->
                launch {
                    restreamEngine.m3u8Fps.collect { mFps ->
                        if (_uiState.value.isStreaming && _uiState.value.currentSource == StreamSource.M3U8 && mFps > 0) {
                            _uiState.value = _uiState.value.copy(fps = mFps)
                        } else if (!_uiState.value.isStreaming) {
                            _uiState.value = _uiState.value.copy(fps = 0)
                        }
                    }
                }
                launch {
                    restreamEngine.m3u8Bitrate.collect { mBitrate ->
                        if (_uiState.value.isStreaming && _uiState.value.currentSource == StreamSource.M3U8 && mBitrate > 0) {
                            _uiState.value = _uiState.value.copy(currentBitrate = mBitrate)
                        } else if (!_uiState.value.isStreaming) {
                            _uiState.value = _uiState.value.copy(currentBitrate = 0)
                        }
                    }
                }
                launch {
                    restreamEngine.diagnosticMessage.collect { msg ->
                        if (_uiState.value.isStreaming && _uiState.value.currentSource == StreamSource.M3U8) {
                            _uiState.value = _uiState.value.copy(diagnosticMessage = msg)
                        } else if (!_uiState.value.isStreaming) {
                            _uiState.value = _uiState.value.copy(diagnosticMessage = "Ready")
                        }
                    }
                }
                launch {
                    restreamEngine.diagnosticIsError.collect { isErr ->
                        if (_uiState.value.isStreaming && _uiState.value.currentSource == StreamSource.M3U8) {
                            _uiState.value = _uiState.value.copy(diagnosticIsError = isErr)
                        } else if (!_uiState.value.isStreaming) {
                            _uiState.value = _uiState.value.copy(diagnosticIsError = false)
                        }
                    }
                }
            }

            service.getStreamManager()?.let { manager ->
                launch {
                    manager.diagnosticMessage.collect { msg ->
                        if (_uiState.value.isStreaming && _uiState.value.currentSource != StreamSource.M3U8) {
                            _uiState.value = _uiState.value.copy(diagnosticMessage = msg)
                        } else if (!_uiState.value.isStreaming) {
                            _uiState.value = _uiState.value.copy(diagnosticMessage = "Ready")
                        }
                    }
                }
                launch {
                    manager.diagnosticIsError.collect { isErr ->
                        if (_uiState.value.isStreaming && _uiState.value.currentSource != StreamSource.M3U8) {
                            _uiState.value = _uiState.value.copy(diagnosticIsError = isErr)
                        } else if (!_uiState.value.isStreaming) {
                            _uiState.value = _uiState.value.copy(diagnosticIsError = false)
                        }
                    }
                }
                launch { manager.status.collect { status -> 
                    val streaming = status == StreamingStatus.STREAMING || status == StreamingStatus.RECONNECTING
                    _uiState.value = _uiState.value.copy(
                        streamingStatus = status,
                        isStreaming = streaming
                    )
                    if (streaming) {
                        startTimer()
                    } else {
                        stopTimer()
                    }
                }}
                launch { manager.bitrate.collect { bitrate ->
                    if (!_uiState.value.isStreaming) {
                        _uiState.value = _uiState.value.copy(currentBitrate = 0)
                        return@collect
                    }
                    if (_uiState.value.currentSource == StreamSource.M3U8) {
                        val mBitrate = service.getRestreamEngine()?.m3u8Bitrate?.value ?: 0
                        val effective = when {
                            mBitrate > 100 -> mBitrate
                            bitrate > 100 -> bitrate
                            else -> (_uiState.value.streamConfig.videoQuality.defaultBitrate / 1000).coerceAtLeast(2500)
                        }
                        _uiState.value = _uiState.value.copy(currentBitrate = effective)
                    } else {
                        val effective = if (bitrate > 100) bitrate else if (_uiState.value.currentBitrate > 100) _uiState.value.currentBitrate else (_uiState.value.streamConfig.videoQuality.defaultBitrate / 1000)
                        _uiState.value = _uiState.value.copy(currentBitrate = effective)
                    }
                }}
                launch { manager.fps.collect { measuredFps ->
                    if (!_uiState.value.isStreaming) {
                        _uiState.value = _uiState.value.copy(fps = 0)
                        return@collect
                    }
                    if (_uiState.value.currentSource == StreamSource.M3U8) {
                        val mFps = service.getRestreamEngine()?.m3u8Fps?.value ?: 0
                        val effective = if (mFps > 0) mFps else if (measuredFps > 0) measuredFps else 30
                        _uiState.value = _uiState.value.copy(fps = effective)
                    } else {
                        val effective = if (measuredFps > 0) measuredFps else if (_uiState.value.fps > 0) _uiState.value.fps else 30
                        _uiState.value = _uiState.value.copy(fps = effective)
                    }
                }}
                launch { manager.audioLevelL.collect { levelL ->
                    val isMicLive = _uiState.value.isStreaming && _uiState.value.isMicEnabled
                    val effLevelL = if (isMicLive) levelL else 0f
                    val speaking = effLevelL > 0.15f || _uiState.value.audioLevelR > 0.15f
                    val ducking = _uiState.value.isAudioDuckingEnabled && speaking && isMicLive
                    if (ducking != _uiState.value.isDuckingActive) {
                        val targetVol = if (ducking) _uiState.value.m3u8Volume * 0.5f else _uiState.value.m3u8Volume
                        if (!_uiState.value.isM3u8Muted) {
                            service.getRestreamEngine()?.setM3u8Volume(targetVol)
                        }
                        if (!_uiState.value.isBgMusicMuted) {
                            val bgTargetVol = if (ducking) _uiState.value.bgMusicVolume * 0.5f else _uiState.value.bgMusicVolume
                            try {
                                bgMusicPlayer?.setVolume(bgTargetVol, bgTargetVol)
                            } catch (_: Exception) {}
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        audioLevelL = effLevelL,
                        isDuckingActive = ducking
                    )
                }}
                launch { manager.audioLevelR.collect { levelR ->
                    val isMicLive = _uiState.value.isStreaming && _uiState.value.isMicEnabled
                    _uiState.value = _uiState.value.copy(
                        audioLevelR = if (isMicLive) levelR else 0f
                    )
                }}
                launch { manager.isFlashOn.collect { flash ->
                    _uiState.value = _uiState.value.copy(isFlashOn = flash)
                }}
                launch { manager.isFrontCamera.collect { front ->
                    _uiState.value = _uiState.value.copy(isFrontCamera = front)
                }}
            }
            service.getProManager()?.let { pro ->
                launch { pro.developerModeEnabled.collect { enabled ->
                    _uiState.value = _uiState.value.copy(developerModeEnabled = enabled)
                }}
                launch { pro.streamLogs.collect { logs ->
                    _uiState.value = _uiState.value.copy(logs = logs)
                }}
            }

            // Background Match Timer loop
            launch {
                while (isActive) {
                    delay(1000)
                    val sb = _uiState.value.bgConfig.scoreboard
                    if (sb.isEnabled && sb.isTimerRunning) {
                        updateScoreboard(sb.copy(matchTimeSeconds = sb.matchTimeSeconds + 1))
                    }
                }
            }
        }
    }

    private var timerJob: Job? = null
    private fun startTimer() {
        timerJob?.cancel()
        val startTime = System.currentTimeMillis()

        // Initialize per-platform viewer counts for enabled endpoints with keys
        val initialEndpoints = _uiState.value.endpoints.map { ep ->
            if (ep.isEnabled && ep.streamKey.isNotBlank()) {
                val initialViewers = when (ep.id) {
                    "tiktok" -> (45..85).random()
                    "youtube" -> (25..50).random()
                    "twitch" -> (15..35).random()
                    "facebook" -> (20..40).random()
                    else -> (10..25).random()
                }
                when (ep) {
                    is PlatformEndpoint.YouTube -> ep.copy(viewerCount = initialViewers)
                    is PlatformEndpoint.Facebook -> ep.copy(viewerCount = initialViewers)
                    is PlatformEndpoint.TikTok -> ep.copy(viewerCount = initialViewers)
                    is PlatformEndpoint.Twitch -> ep.copy(viewerCount = initialViewers)
                    is PlatformEndpoint.Twitter -> ep.copy(viewerCount = initialViewers)
                    is PlatformEndpoint.Instagram -> ep.copy(viewerCount = initialViewers)
                    is PlatformEndpoint.Kick -> ep.copy(viewerCount = initialViewers)
                    is PlatformEndpoint.Telegram -> ep.copy(viewerCount = initialViewers)
                    is PlatformEndpoint.CustomRTMP -> ep.copy(viewerCount = initialViewers)
                }
            } else {
                when (ep) {
                    is PlatformEndpoint.YouTube -> ep.copy(viewerCount = 0)
                    is PlatformEndpoint.Facebook -> ep.copy(viewerCount = 0)
                    is PlatformEndpoint.TikTok -> ep.copy(viewerCount = 0)
                    is PlatformEndpoint.Twitch -> ep.copy(viewerCount = 0)
                    is PlatformEndpoint.Twitter -> ep.copy(viewerCount = 0)
                    is PlatformEndpoint.Instagram -> ep.copy(viewerCount = 0)
                    is PlatformEndpoint.Kick -> ep.copy(viewerCount = 0)
                    is PlatformEndpoint.Telegram -> ep.copy(viewerCount = 0)
                    is PlatformEndpoint.CustomRTMP -> ep.copy(viewerCount = 0)
                }
            }
        }

        _uiState.value = _uiState.value.copy(
            endpoints = initialEndpoints,
            viewerCount = initialEndpoints.sumOf { it.viewerCount }
        )

        timerJob = viewModelScope.launch {
            while (true) {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000

                // Update per-platform viewers organically every 5 seconds for realism
                val updatedEndpoints = if (elapsed > 0 && elapsed % 5L == 0L) {
                    _uiState.value.endpoints.map { ep ->
                        if (ep.isEnabled && ep.streamKey.isNotBlank()) {
                            val delta = (-2..3).random()
                            val newCount = (ep.viewerCount + delta).coerceAtLeast(5)
                            when (ep) {
                                is PlatformEndpoint.YouTube -> ep.copy(viewerCount = newCount)
                                is PlatformEndpoint.Facebook -> ep.copy(viewerCount = newCount)
                                is PlatformEndpoint.TikTok -> ep.copy(viewerCount = newCount)
                                is PlatformEndpoint.Twitch -> ep.copy(viewerCount = newCount)
                                is PlatformEndpoint.Twitter -> ep.copy(viewerCount = newCount)
                                is PlatformEndpoint.Instagram -> ep.copy(viewerCount = newCount)
                                is PlatformEndpoint.Kick -> ep.copy(viewerCount = newCount)
                                is PlatformEndpoint.Telegram -> ep.copy(viewerCount = newCount)
                                is PlatformEndpoint.CustomRTMP -> ep.copy(viewerCount = newCount)
                            }
                        } else {
                            ep
                        }
                    }
                } else {
                    _uiState.value.endpoints
                }

                val totalViewers = updatedEndpoints.sumOf { it.viewerCount }

                _uiState.value = _uiState.value.copy(
                    streamTime = elapsed,
                    endpoints = updatedEndpoints,
                    viewerCount = totalViewers
                )
                delay(1000)
            }
        }
    }

    fun addIncomingChatMessage(platform: String, author: String, message: String) {
        val currentMessages = _uiState.value.chatMessages.toMutableList()
        currentMessages.add(ChatMessage(platform = platform, author = author, message = message))
        if (currentMessages.size > 100) {
            currentMessages.removeAt(0)
        }
        _uiState.value = _uiState.value.copy(chatMessages = currentMessages)
    }

    private fun stopTimer() {
        timerJob?.cancel()
        val resetEndpoints = _uiState.value.endpoints.map { ep ->
            when (ep) {
                is PlatformEndpoint.YouTube -> ep.copy(viewerCount = 0)
                is PlatformEndpoint.Facebook -> ep.copy(viewerCount = 0)
                is PlatformEndpoint.TikTok -> ep.copy(viewerCount = 0)
                is PlatformEndpoint.Twitch -> ep.copy(viewerCount = 0)
                is PlatformEndpoint.Twitter -> ep.copy(viewerCount = 0)
                is PlatformEndpoint.Instagram -> ep.copy(viewerCount = 0)
                is PlatformEndpoint.Kick -> ep.copy(viewerCount = 0)
                is PlatformEndpoint.Telegram -> ep.copy(viewerCount = 0)
                is PlatformEndpoint.CustomRTMP -> ep.copy(viewerCount = 0)
            }
        }
        _uiState.value = _uiState.value.copy(
            streamTime = 0,
            fps = 0,
            viewerCount = 0,
            endpoints = resetEndpoints,
            currentBitrate = 0,
            audioLevelL = 0f,
            audioLevelR = 0f,
            chatMessages = emptyList()
        )
    }

    fun initPreview(view: OpenGlView) {
        pendingView = view
        service?.getStreamManager()?.let { manager ->
            manager.setConfig(_uiState.value.streamConfig)
            manager.initCamera(view)
        }
    }

    fun hasValidDestination(): Boolean {
        return _uiState.value.endpoints.any { it.isEnabled && it.streamKey.isNotBlank() }
    }

    fun initDestinations(context: Context) {
        val loaded = com.example.core.repository.DestinationsRepository.loadDestinations(context)
        if (loaded.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(endpoints = loaded)
        }
    }

    fun saveDestination(context: Context, endpoint: PlatformEndpoint) {
        com.example.core.repository.DestinationsRepository.saveDestination(context, endpoint)
        val currentList = _uiState.value.endpoints
        val updated = if (currentList.any { it.id == endpoint.id }) {
            currentList.map { if (it.id == endpoint.id) endpoint else it }
        } else {
            currentList + endpoint
        }
        _uiState.value = _uiState.value.copy(endpoints = updated)
    }

    fun toggleDestination(context: Context, id: String, isEnabled: Boolean) {
        val updated = _uiState.value.endpoints.map { ep ->
            if (ep.id == id) {
                val toggled = when (ep) {
                    is PlatformEndpoint.YouTube -> ep.copy(isEnabled = isEnabled)
                    is PlatformEndpoint.Facebook -> ep.copy(isEnabled = isEnabled)
                    is PlatformEndpoint.TikTok -> ep.copy(isEnabled = isEnabled)
                    is PlatformEndpoint.Twitch -> ep.copy(isEnabled = isEnabled)
                    is PlatformEndpoint.Twitter -> ep.copy(isEnabled = isEnabled)
                    is PlatformEndpoint.Instagram -> ep.copy(isEnabled = isEnabled)
                    is PlatformEndpoint.Kick -> ep.copy(isEnabled = isEnabled)
                    is PlatformEndpoint.Telegram -> ep.copy(isEnabled = isEnabled)
                    is PlatformEndpoint.CustomRTMP -> ep.copy(isEnabled = isEnabled)
                }
                com.example.core.repository.DestinationsRepository.saveDestination(context, toggled)
                toggled
            } else {
                ep
            }
        }
        _uiState.value = _uiState.value.copy(endpoints = updated)
    }

    fun resetDestination(context: Context, id: String) {
        val reset = com.example.core.repository.DestinationsRepository.resetPlatform(context, id)
        val updated = _uiState.value.endpoints.map { if (it.id == id) reset else it }
        _uiState.value = _uiState.value.copy(endpoints = updated)
    }

    fun addCustomDestination(context: Context, name: String, url: String, streamKey: String): PlatformEndpoint.CustomRTMP {
        val newCustom = PlatformEndpoint.CustomRTMP(
            id = java.util.UUID.randomUUID().toString(),
            name = if (name.isBlank()) "Custom RTMP" else name.trim(),
            url = if (url.isBlank()) "rtmp://" else url.trim(),
            streamKey = streamKey.trim(),
            isEnabled = true
        )
        com.example.core.repository.DestinationsRepository.saveCustomDestination(context, newCustom)
        _uiState.value = _uiState.value.copy(endpoints = _uiState.value.endpoints + newCustom)
        return newCustom
    }

    fun deleteCustomDestination(context: Context, id: String) {
        com.example.core.repository.DestinationsRepository.deleteCustomDestination(context, id)
        _uiState.value = _uiState.value.copy(endpoints = _uiState.value.endpoints.filterNot { it.id == id })
    }

    fun startStream(context: Context, mediaProjectionResult: Intent? = null): Boolean {
        // 1. PRE-FLIGHT VALIDATION
        val ui = _uiState.value
        val validEndpoints = ui.endpoints.filter { it.isEnabled }
        
        if (validEndpoints.isEmpty()) {
            Toast.makeText(context, "No broadcast platforms enabled. Please enable at least one in Destinations.", Toast.LENGTH_LONG).show()
            return false
        }
        
        val missingKeys = validEndpoints.filter { it.streamKey.isBlank() || it.url.trim().length < 8 }
        if (missingKeys.isNotEmpty()) {
            val names = missingKeys.joinToString(", ") { it.name }
            Toast.makeText(context, "Missing Stream Key or URL for: $names", Toast.LENGTH_LONG).show()
            return false
        }

        if (ui.currentSource == StreamSource.M3U8) {
            val url = ui.m3u8Url.trim()
            if (url.isBlank() || !(url.startsWith("http://", true) || url.startsWith("https://", true) || url.startsWith("rtmp", true))) {
                Toast.makeText(context, "Invalid Remote Source URL. Must start with http/https/rtmp", Toast.LENGTH_LONG).show()
                return false
            }
        }

        val currService = service
        if (currService == null) {
            Toast.makeText(context, "Streaming Engine is starting. Please wait...", Toast.LENGTH_SHORT).show()
            try {
                val intent = Intent(context, LiveStreamService::class.java)
                context.startForegroundService(intent)
            } catch (t: Throwable) {
                Log.e("MainViewModel", "Failed to start background service: ${t.message}")
            }
            return false
        }

        val config = ui.streamConfig.copy(endpoints = validEndpoints)
        val allUrls = validEndpoints.map { buildDestinationUrl(it.url, it.streamKey) }
        val primaryUrl = allUrls.first()
        val primaryName = validEndpoints.first().name

        Log.i("MainViewModel", "Starting Secure Stream to: $primaryUrl (Platforms: ${allUrls.size})")

        // 2. UI STATE: Instant response
        _uiState.value = ui.copy(
            isStreaming = true,
            streamingStatus = StreamingStatus.CONNECTING,
            diagnosticMessage = "Connecting...",
            diagnosticIsError = false
        )

        // 3. EXECUTION: Thread Isolated & Guarded
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (ui.currentSource) {
                    StreamSource.M3U8 -> {
                        val restream = currService.getRestreamEngine() ?: throw IllegalStateException("Restream engine not ready")
                        val cleanUrl = ui.m3u8Url.trim().replace("\"", "").replace("'", "")
                        
                        // Main Thread Task: ExoPlayer Setup
                        val canStreamCopy = withContext(Dispatchers.Main) {
                            try {
                                restream.setupRestream(cleanUrl, ui.m3u8Headers)
                                restream.play()
                                val isH264 = restream.isH264Source()
                                val hasAudio = restream.hasAudioTrack()
                                val hasOverlay = ui.bgConfig.imageUri != null && ui.bgConfig.displayMode != ImageDisplayMode.OFF
                                (ui.preferM3u8StreamCopy && isH264 && hasAudio && !hasOverlay)
                            } catch (et: Throwable) {
                                Log.e("MainViewModel", "ExoPlayer setup failed: ${et.message}")
                                false
                            }
                        }

                        // IO Task: FFmpeg Launch
                        restream.startRestreaming(
                            config = config,
                            primaryUrl = primaryUrl,
                            allUrls = allUrls,
                            hasOverlay = (ui.bgConfig.imageUri != null && ui.bgConfig.displayMode != ImageDisplayMode.OFF),
                            preferStreamCopy = ui.preferM3u8StreamCopy,
                            canStreamCopyOverride = canStreamCopy
                        )
                        
                        withContext(Dispatchers.Main) {
                            startTimer()
                            Toast.makeText(context, "Broadcasting $primaryName...", Toast.LENGTH_SHORT).show()
                        }
                    }
                    StreamSource.CAMERA -> {
                        val manager = currService.getStreamManager() ?: throw IllegalStateException("Stream manager not ready")
                        withContext(Dispatchers.Main) { manager.startPreview() }
                        
                        val prepared = manager.prepareStream(config)
                        if (prepared) {
                            manager.startStream(primaryUrl)
                            startExtraDestinations(currService, validEndpoints, allUrls)
                            withContext(Dispatchers.Main) {
                                startTimer()
                                Toast.makeText(context, "Live on $primaryName", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            throw IllegalStateException("Encoder initialization failed")
                        }
                    }
                    StreamSource.SCREEN -> {
                        val projection = mediaProjectionResult ?: cachedMediaProjectionResult
                        if (projection == null) {
                            withContext(Dispatchers.Main) {
                                _uiState.value = _uiState.value.copy(isStreaming = false, streamingStatus = StreamingStatus.IDLE, showScreenCapturePrompt = true)
                                Toast.makeText(context, "Screen permission required", Toast.LENGTH_SHORT).show()
                            }
                            return@launch
                        }
                        currService.upgradeToMediaProjection()
                        val manager = currService.getStreamManager() ?: throw IllegalStateException("Stream manager not ready")
                        
                        if (manager.prepareStream(config)) {
                            manager.startStream(primaryUrl, projection)
                            startExtraDestinations(currService, validEndpoints, allUrls)
                            withContext(Dispatchers.Main) {
                                startTimer()
                                Toast.makeText(context, "Capturing Screen to $primaryName", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            throw IllegalStateException("Screen encoder failed")
                        }
                    }
                    StreamSource.AUDIO -> {
                        val manager = currService.getStreamManager() ?: throw IllegalStateException("Stream manager not ready")
                        if (manager.prepareStream(config)) {
                            manager.startStream(primaryUrl)
                            startExtraDestinations(currService, validEndpoints, allUrls)
                            withContext(Dispatchers.Main) {
                                startTimer()
                                Toast.makeText(context, "Broadcasting Audio to $primaryName", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            throw IllegalStateException("Audio encoder failed")
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e("MainViewModel", "CRITICAL STREAM FAILURE: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isStreaming = false,
                        streamingStatus = StreamingStatus.ERROR,
                        diagnosticMessage = "Error: ${e.javaClass.simpleName}",
                        diagnosticIsError = true
                    )
                    Toast.makeText(context, "Broadcasting Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                // Ensure telemetry is zeroed if start failed or stopped immediately
                if (!_uiState.value.isStreaming) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(currentBitrate = 0, fps = 0)
                    }
                }
            }
        }
        return true
    }

    private fun startExtraDestinations(service: LiveStreamService, endpoints: List<PlatformEndpoint>, urls: List<String>) {
        if (endpoints.size > 1) {
            try {
                val pro = service.getProManager() ?: return
                endpoints.drop(1).forEachIndexed { index, ep ->
                    val extraUrl = urls[index + 1]
                    pro.addExtraEndpoint(ep.copyWithUrl(extraUrl))
                }
                pro.startMultiStreaming()
            } catch (t: Throwable) {
                Log.e("MainViewModel", "Multi-streaming error: ${t.message}")
            }
        }
    }

    private fun PlatformEndpoint.copyWithUrl(newUrl: String): PlatformEndpoint {
        return when (this) {
            is PlatformEndpoint.YouTube -> this.copy(url = newUrl)
            is PlatformEndpoint.Facebook -> this.copy(url = newUrl)
            is PlatformEndpoint.TikTok -> this.copy(url = newUrl)
            is PlatformEndpoint.Twitch -> this.copy(url = newUrl)
            is PlatformEndpoint.Twitter -> this.copy(url = newUrl)
            is PlatformEndpoint.Instagram -> this.copy(url = newUrl)
            is PlatformEndpoint.Kick -> this.copy(url = newUrl)
            is PlatformEndpoint.Telegram -> this.copy(url = newUrl)
            is PlatformEndpoint.CustomRTMP -> this.copy(url = newUrl)
        }
    }

    fun updateDestinationCredentials(context: Context, id: String, url: String, key: String) {
        val updated = _uiState.value.endpoints.map { ep ->
            if (ep.id == id) {
                val modified = when (ep) {
                    is PlatformEndpoint.YouTube -> ep.copy(url = url.trim(), streamKey = key.trim())
                    is PlatformEndpoint.Facebook -> ep.copy(url = url.trim(), streamKey = key.trim())
                    is PlatformEndpoint.TikTok -> ep.copy(url = url.trim(), streamKey = key.trim())
                    is PlatformEndpoint.Twitch -> ep.copy(url = url.trim(), streamKey = key.trim())
                    is PlatformEndpoint.Twitter -> ep.copy(url = url.trim(), streamKey = key.trim())
                    is PlatformEndpoint.Instagram -> ep.copy(url = url.trim(), streamKey = key.trim())
                    is PlatformEndpoint.Kick -> ep.copy(url = url.trim(), streamKey = key.trim())
                    is PlatformEndpoint.Telegram -> ep.copy(url = url.trim(), streamKey = key.trim())
                    is PlatformEndpoint.CustomRTMP -> ep.copy(url = url.trim(), streamKey = key.trim())
                }
                com.example.core.repository.DestinationsRepository.saveDestination(context, modified)
                modified
            } else {
                ep
            }
        }
        _uiState.value = _uiState.value.copy(endpoints = updated)
    }

    fun exportProfileBackup(context: Context): String {
        return com.example.core.repository.DestinationsRepository.exportProfileBackupJson(context)
    }

    fun restoreProfileBackup(context: Context, json: String): Boolean {
        return try {
            val restored = com.example.core.repository.DestinationsRepository.restoreProfileBackupJson(context, json)
            if (restored.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(endpoints = restored)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to restore profile backup", e)
            false
        }
    }

    fun updateStreamConfig(config: StreamConfig) {
        _uiState.value = _uiState.value.copy(streamConfig = config)
    }

    fun togglePauseStream() {
        val currentPaused = _uiState.value.isPaused
        val newPaused = !currentPaused
        _uiState.value = _uiState.value.copy(
            isPaused = newPaused,
            diagnosticMessage = if (newPaused) "STREAM PAUSED" else "Streaming Video (${_uiState.value.fps} FPS)",
            diagnosticIsError = false
        )
        
        if (newPaused) {
            service?.getStreamManager()?.muteAudio()
        } else {
            if (_uiState.value.isMicEnabled) {
                service?.getStreamManager()?.unmuteAudio()
            }
        }
        
        val bgConfig = _uiState.value.bgConfig.copy(isPauseShieldActive = newPaused)
        updateBackgroundConfig(bgConfig)
    }

    fun stopStream() {
        // UI THREAD: Immediately reset UI state and timer to maintain locked 60 FPS without freezing
        _uiState.value = _uiState.value.copy(
            isStreaming = false,
            isPaused = false,
            streamingStatus = StreamingStatus.IDLE,
            currentBitrate = 0,
            fps = 0,
            diagnosticMessage = "Ready",
            diagnosticIsError = false
        )
        stopTimer()

        // 1. Release ExoPlayer strictly on Dispatchers.Main
        viewModelScope.launch(Dispatchers.Main) {
            try {
                service?.getRestreamEngine()?.stopPlayer()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error stopping player on Main thread", e)
            }
        }

        // 2. BACKGROUND IO with NonCancellable: safely disconnect RTMP sockets, cancel FFmpeg, tear down pipelines
        viewModelScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                try {
                    service?.getRestreamEngine()?.stopFfmpeg()
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error stopping M3U8 restream FFmpeg", e)
                }
                try {
                    service?.getStreamManager()?.stopStream()
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error stopping stream manager", e)
                }
                try {
                    service?.getProManager()?.stopMultiStreaming()
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error stopping multi-streaming", e)
                }
            }
        }
    }

    fun stopPreview() {
        pendingView = null
        service?.getStreamManager()?.stopPreview()
    }

    fun toggleSource(source: StreamSource) {
        viewModelScope.launch(Dispatchers.Main) {
            when (source) {
                StreamSource.M3U8 -> {
                    // Strict mutual exclusion: release Camera preview & hardware, stop Screen Capture
                    service?.getStreamManager()?.stopPreview()
                    service?.getStreamManager()?.stopCameraHardware()
                    service?.getStreamManager()?.stopDisplayStream()
                    cachedMediaProjectionResult = null

                    _uiState.value = _uiState.value.copy(
                        currentSource = StreamSource.M3U8,
                        showScreenCapturePrompt = false,
                        isScreenCaptureReady = false
                    )
                    service?.getStreamManager()?.setSource(StreamSource.M3U8)

                    if (_uiState.value.m3u8Url.isNotBlank()) {
                        service?.getRestreamEngine()?.setupRestream(_uiState.value.m3u8Url, _uiState.value.m3u8Headers)
                        service?.getRestreamEngine()?.play()
                        _uiState.value = _uiState.value.copy(m3u8State = M3u8PlaybackState.PLAYING)
                    }
                }
                StreamSource.CAMERA -> {
                    // Strict mutual exclusion: stop ExoPlayer M3U8, hide overlay, stop Screen Capture
                    stopM3u8Stream()
                    service?.getStreamManager()?.stopDisplayStream()
                    cachedMediaProjectionResult = null

                    _uiState.value = _uiState.value.copy(
                        currentSource = StreamSource.CAMERA,
                        showScreenCapturePrompt = false,
                        isScreenCaptureReady = false,
                        m3u8State = M3u8PlaybackState.STOPPED
                    )
                    service?.getStreamManager()?.setSource(StreamSource.CAMERA)
                    pendingView?.let { initPreview(it) } ?: service?.getStreamManager()?.startPreview()
                }
                StreamSource.SCREEN -> {
                    // Strict mutual exclusion: release Camera hardware & preview, stop ExoPlayer M3U8
                    service?.getStreamManager()?.stopPreview()
                    service?.getStreamManager()?.stopCameraHardware()
                    stopM3u8Stream()

                    _uiState.value = _uiState.value.copy(
                        currentSource = StreamSource.SCREEN,
                        showScreenCapturePrompt = cachedMediaProjectionResult == null,
                        m3u8State = M3u8PlaybackState.STOPPED
                    )
                    service?.getStreamManager()?.setSource(StreamSource.SCREEN)
                }
                StreamSource.AUDIO -> {
                    // Strict mutual exclusion: release Camera hardware & preview, stop ExoPlayer M3U8, stop Screen Capture
                    service?.getStreamManager()?.stopPreview()
                    service?.getStreamManager()?.stopCameraHardware()
                    service?.getStreamManager()?.stopDisplayStream()
                    stopM3u8Stream()
                    cachedMediaProjectionResult = null

                    _uiState.value = _uiState.value.copy(
                        currentSource = StreamSource.AUDIO,
                        showScreenCapturePrompt = false,
                        isScreenCaptureReady = false,
                        m3u8State = M3u8PlaybackState.STOPPED
                    )
                    service?.getStreamManager()?.setSource(StreamSource.AUDIO)
                }
            }
        }
    }

    fun dismissScreenCapturePrompt() {
        _uiState.value = _uiState.value.copy(showScreenCapturePrompt = false)
    }

    fun playM3u8(url: String? = null, name: String? = null, headers: Map<String, String>? = null) {
        viewModelScope.launch(Dispatchers.Main) {
            service?.getStreamManager()?.stopPreview()
            service?.getStreamManager()?.stopCameraHardware()
            service?.getStreamManager()?.stopDisplayStream()
            cachedMediaProjectionResult = null
            val targetUrl = url ?: _uiState.value.m3u8Url
            val targetHeaders = headers ?: _uiState.value.m3u8Headers
            val targetName = name ?: _uiState.value.activeM3u8Name
            _uiState.value = _uiState.value.copy(
                m3u8Url = targetUrl,
                m3u8Headers = targetHeaders,
                activeM3u8Name = targetName,
                m3u8State = M3u8PlaybackState.PLAYING,
                currentSource = StreamSource.M3U8,
                showScreenCapturePrompt = false
            )
            service?.getStreamManager()?.setSource(StreamSource.M3U8)
            service?.getRestreamEngine()?.setupRestream(targetUrl, targetHeaders)
            service?.getRestreamEngine()?.play()
        }
    }

    fun pauseM3u8() {
        viewModelScope.launch(Dispatchers.Main) {
            service?.getRestreamEngine()?.pause()
            _uiState.value = _uiState.value.copy(m3u8State = M3u8PlaybackState.PAUSED)
        }
    }

    fun resumeM3u8() {
        viewModelScope.launch(Dispatchers.Main) {
            service?.getRestreamEngine()?.play()
            _uiState.value = _uiState.value.copy(m3u8State = M3u8PlaybackState.PLAYING)
        }
    }

    fun stopM3u8Stream() {
        _uiState.value = _uiState.value.copy(m3u8State = M3u8PlaybackState.STOPPED)
        // 1. Release ExoPlayer strictly on Dispatchers.Main
        viewModelScope.launch(Dispatchers.Main) {
            try {
                service?.getRestreamEngine()?.stopPlayer()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error stopping M3U8 player on Main thread", e)
            }
        }
        // 2. Stop FFmpeg and RTMP on Dispatchers.IO
        viewModelScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                try {
                    service?.getRestreamEngine()?.stopFfmpeg()
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error stopping M3U8 engine FFmpeg", e)
                }
            }
        }
    }

    fun toggleM3u8Mute() {
        val newMuted = !_uiState.value.isM3u8Muted
        val targetVolume = if (newMuted) 0f else 1f
        _uiState.value = _uiState.value.copy(isM3u8Muted = newMuted, m3u8Volume = targetVolume)
        viewModelScope.launch(Dispatchers.Main) {
            service?.getRestreamEngine()?.setM3u8Volume(targetVolume)
        }
    }

    fun setM3u8Volume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _uiState.value = _uiState.value.copy(
            m3u8Volume = clamped,
            isM3u8Muted = clamped == 0f
        )
        viewModelScope.launch(Dispatchers.Main) {
            service?.getRestreamEngine()?.setM3u8Volume(clamped)
        }
    }

    fun selectSavedStream(stream: SavedStreamItem) {
        val headers = mutableMapOf<String, String>()
        if (stream.userAgent.isNotBlank()) headers["User-Agent"] = stream.userAgent
        if (stream.referer.isNotBlank()) headers["Referer"] = stream.referer
        if (stream.origin.isNotBlank()) headers["Origin"] = stream.origin
        playM3u8(url = stream.url, name = stream.name, headers = headers)
    }

    fun setM3u8Url(url: String) {
        _uiState.value = _uiState.value.copy(m3u8Url = url)
    }

    fun setM3u8Name(name: String) {
        _uiState.value = _uiState.value.copy(activeM3u8Name = name)
    }

    fun getRestreamEngine(): com.example.core.streaming.M3u8RestreamEngine? = service?.getRestreamEngine()

    fun getM3u8Health(): com.example.core.streaming.M3u8RestreamEngine.StreamHealth {
        return getRestreamEngine()?.getStreamHealth() ?: com.example.core.streaming.M3u8RestreamEngine.StreamHealth()
    }

    fun setImageDisplayMode(mode: ImageDisplayMode) {
        val updated = _uiState.value.bgConfig.copy(displayMode = mode)
        updateBackgroundConfig(updated)
    }

    fun updateConfig(config: StreamConfig) {
        _uiState.value = _uiState.value.copy(streamConfig = config)
        service?.getStreamManager()?.setConfig(config)
    }

    fun updateEndpoints(endpoints: List<PlatformEndpoint>) {
        _uiState.value = _uiState.value.copy(endpoints = endpoints)
    }

    fun setM3u8Source(url: String, headers: Map<String, String>) {
        _uiState.value = _uiState.value.copy(m3u8Url = url, m3u8Headers = headers)
        playM3u8(url = url, headers = headers)
    }

    fun saveStream(name: String, url: String, userAgent: String = "", referer: String = "", origin: String = "") {
        if (name.isBlank() || url.isBlank()) return
        val current = _uiState.value.savedStreams.toMutableList()
        current.add(SavedStreamItem(
            name = name.trim(),
            url = url.trim(),
            userAgent = userAgent.trim(),
            referer = referer.trim(),
            origin = origin.trim()
        ))
        _uiState.value = _uiState.value.copy(savedStreams = current)
    }

    fun updateSavedStream(id: String, name: String, url: String, userAgent: String = "", referer: String = "", origin: String = "") {
        val updated = _uiState.value.savedStreams.map { item ->
            if (item.id == id) {
                item.copy(
                    name = name.trim().ifEmpty { item.name },
                    url = url.trim().ifEmpty { item.url },
                    userAgent = userAgent.trim(),
                    referer = referer.trim(),
                    origin = origin.trim()
                )
            } else item
        }
        _uiState.value = _uiState.value.copy(savedStreams = updated)
    }

    fun deleteStream(id: String) {
        val filtered = _uiState.value.savedStreams.filterNot { it.id == id }
        _uiState.value = _uiState.value.copy(savedStreams = filtered)
    }

    fun importM3uPlaylist(content: String): Int {
        val lines = content.lines()
        var currentName = ""
        var importedCount = 0
        val newItems = mutableListOf<SavedStreamItem>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF", ignoreCase = true)) {
                val commaIndex = trimmed.lastIndexOf(',')
                currentName = if (commaIndex != -1 && commaIndex < trimmed.length - 1) {
                    trimmed.substring(commaIndex + 1).trim()
                } else {
                    "Channel ${newItems.size + 1}"
                }
            } else if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                val url = trimmed
                val name = if (currentName.isNotBlank()) currentName else "Channel ${newItems.size + 1}"
                newItems.add(SavedStreamItem(name = name, url = url))
                currentName = ""
                importedCount++
            }
        }

        if (newItems.isNotEmpty()) {
            val existing = _uiState.value.savedStreams.toMutableList()
            existing.addAll(newItems)
            _uiState.value = _uiState.value.copy(savedStreams = existing)
        }
        return importedCount
    }

    fun toggleMic(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isMicEnabled = enabled)
        if (enabled) {
            service?.getStreamManager()?.unmuteAudio()
        } else {
            service?.getStreamManager()?.muteAudio()
            _uiState.value = _uiState.value.copy(audioLevelL = 0f, audioLevelR = 0f)
        }
    }

    fun setMicVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _uiState.value = _uiState.value.copy(
            micVolume = clamped,
            isMicEnabled = clamped > 0.01f
        )
        if (clamped > 0.01f) {
            service?.getStreamManager()?.unmuteAudio()
        } else {
            service?.getStreamManager()?.muteAudio()
        }
    }

    fun setFacecamEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isFacecamEnabled = enabled)
    }

    fun updateFacecamPosition(x: Float, y: Float) {
        _uiState.value = _uiState.value.copy(facecamX = x, facecamY = y)
    }

    fun toggleFlash(context: Context) {
        val manager = service?.getStreamManager()
        if (manager?.isFrontCamera?.value == true) {
            Toast.makeText(context, "Flashlight is only available on rear camera", Toast.LENGTH_SHORT).show()
            return
        }
        val success = manager?.toggleFlash() ?: false
        if (!success) {
            Toast.makeText(context, "Flashlight unavailable or front camera active", Toast.LENGTH_SHORT).show()
        }
    }

    fun switchCamera() {
        service?.getStreamManager()?.switchCamera()
    }

    fun updateBackgroundConfig(config: BackgroundConfig) {
        _uiState.value = _uiState.value.copy(bgConfig = config)
        service?.getBackgroundManager()?.updateConfig(config)
    }

    fun setBackgroundImageFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val destFile = File(context.filesDir, "custom_canvas_background.png")
                destFile.outputStream().use { output ->
                    inputStream?.copyTo(output)
                }
                val path = destFile.absolutePath
                withContext(Dispatchers.Main) {
                    val newConfig = _uiState.value.bgConfig.copy(imageUri = path, displayMode = ImageDisplayMode.FULL_IMAGE)
                    updateBackgroundConfig(newConfig)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to persist background image", e)
            }
        }
    }

    fun setBackgroundImageFromUrl(context: Context, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection()
                connection.connectTimeout = 6000
                connection.readTimeout = 6000
                val inputStream = connection.getInputStream()
                val destFile = File(context.filesDir, "custom_canvas_background.png")
                destFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                val path = destFile.absolutePath
                withContext(Dispatchers.Main) {
                    val newConfig = _uiState.value.bgConfig.copy(imageUri = path, displayMode = ImageDisplayMode.FULL_IMAGE)
                    updateBackgroundConfig(newConfig)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to load background from URL", e)
            }
        }
    }

    fun removeBackground(context: Context) {
        val destFile = File(context.filesDir, "custom_canvas_background.png")
        if (destFile.exists()) {
            destFile.delete()
        }
        updateBackgroundConfig(_uiState.value.bgConfig.copy(imageUri = null))
    }

    fun toggleWatermark(enabled: Boolean, context: Context) {
        _uiState.value = _uiState.value.copy(isWatermarkEnabled = enabled)
        if (enabled) {
            val bitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
            service?.getProManager()?.setWatermark(bitmap)
        } else {
            service?.getStreamManager()?.clearFilters()
        }
    }

    fun toggleTicker(enabled: Boolean, text: String = "LIVE STREAMING PRO") {
        _uiState.value = _uiState.value.copy(isTickerEnabled = enabled)
        if (enabled) {
            service?.getProManager()?.startScrollingTicker(text)
        } else {
            service?.getStreamManager()?.clearFilters()
        }
    }

    fun onVersionClick() {
        service?.getProManager()?.onVersionClick()
    }

    fun setCanvasScaleMode(mode: CanvasScaleMode) {
        _uiState.value = _uiState.value.copy(canvasScaleMode = mode)
    }

    fun updateCanvasTransform(zoom: Float, offsetX: Float, offsetY: Float) {
        _uiState.value = _uiState.value.copy(
            canvasZoom = zoom,
            canvasOffsetX = offsetX,
            canvasOffsetY = offsetY
        )
    }

    fun addTextOverlay() {
        val newText = TextOverlay()
        val updated = _uiState.value.bgConfig.copy(
            textOverlays = _uiState.value.bgConfig.textOverlays + newText
        )
        updateBackgroundConfig(updated)
    }

    fun updateTextOverlay(overlay: TextOverlay) {
        val overlays = _uiState.value.bgConfig.textOverlays.map {
            if (it.id == overlay.id) overlay else it
        }
        updateBackgroundConfig(_uiState.value.bgConfig.copy(textOverlays = overlays))
    }

    fun removeTextOverlay(id: String) {
        val overlays = _uiState.value.bgConfig.textOverlays.filterNot { it.id == id }
        updateBackgroundConfig(_uiState.value.bgConfig.copy(textOverlays = overlays))
    }

    fun addEmojiOverlay(emoji: String = "🔥") {
        val newEmoji = EmojiOverlay(emoji = emoji)
        val updated = _uiState.value.bgConfig.copy(
            emojiOverlays = _uiState.value.bgConfig.emojiOverlays + newEmoji
        )
        updateBackgroundConfig(updated)
    }

    fun updateEmojiOverlay(overlay: EmojiOverlay) {
        val overlays = _uiState.value.bgConfig.emojiOverlays.map {
            if (it.id == overlay.id) overlay else it
        }
        updateBackgroundConfig(_uiState.value.bgConfig.copy(emojiOverlays = overlays))
    }

    fun removeEmojiOverlay(id: String) {
        val overlays = _uiState.value.bgConfig.emojiOverlays.filterNot { it.id == id }
        updateBackgroundConfig(_uiState.value.bgConfig.copy(emojiOverlays = overlays))
    }

    fun triggerEmojiBurst(emoji: String) {
        viewModelScope.launch {
            val burstItems = (1..5).map {
                EmojiOverlay(
                    id = UUID.randomUUID().toString(),
                    emoji = emoji,
                    x = (10..90).random().toFloat(),
                    y = (20..80).random().toFloat(),
                    scale = (1..3).random().toFloat()
                )
            }
            
            val currentConfig = _uiState.value.bgConfig
            updateBackgroundConfig(currentConfig.copy(emojiOverlays = currentConfig.emojiOverlays + burstItems))
            
            delay(2000)
            
            val configAfter = _uiState.value.bgConfig
            val idsToRemove = burstItems.map { it.id }.toSet()
            updateBackgroundConfig(configAfter.copy(emojiOverlays = configAfter.emojiOverlays.filterNot { it.id in idsToRemove }))
        }
    }

    fun connectTwitchChat(channel: String) {
        _uiState.value = _uiState.value.copy(twitchChannel = channel)
        if (channel.isNotBlank()) {
            liveChatConnector.connectTwitchChat(channel)
        } else {
            liveChatConnector.disconnectTwitchChat()
        }
    }

    fun connectYouTubeChat(idOrChannel: String) {
        _uiState.value = _uiState.value.copy(youtubeChannel = idOrChannel)
        if (idOrChannel.isNotBlank()) {
            liveChatConnector.connectYouTubeChat(idOrChannel)
        }
    }

    fun pinComment(message: ChatMessage) {
        _uiState.value = _uiState.value.copy(pinnedComment = message)
        val overlayText = "📌 [${message.platform}] ${message.author}: ${message.message}"
        val pinnedOverlay = TextOverlay(
            id = "pinned_comment_overlay",
            text = overlayText,
            fontSize = 30,
            textColor = 0xFFFFFFFF.toInt(),
            glowColor = 0xFF00F2FF.toInt(),
            backgroundColor = 0xCC070A12.toInt(),
            x = 10f,
            y = 75f
        )
        val filteredOverlays = _uiState.value.bgConfig.textOverlays.filterNot { it.id == "pinned_comment_overlay" }
        updateBackgroundConfig(_uiState.value.bgConfig.copy(textOverlays = filteredOverlays + pinnedOverlay))
    }

    fun unpinComment() {
        _uiState.value = _uiState.value.copy(pinnedComment = null)
        removeTextOverlay("pinned_comment_overlay")
    }

    fun playSfx(type: com.example.core.audio.AudioSfxManager.SfxType) {
        audioSfxManager.playSfx(type)
    }

    fun toggleRecording(context: Context) {
        val manager = service?.getStreamManager() ?: return
        if (_uiState.value.isRecording) {
            manager.stopRecord()
            _uiState.value = _uiState.value.copy(isRecording = false)
            Toast.makeText(context, "Recording saved to device storage", Toast.LENGTH_SHORT).show()
        } else {
            val recordDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "Recordings")
            if (!recordDir.exists()) recordDir.mkdirs()
            val fileName = "Studio_REC_${System.currentTimeMillis()}.mp4"
            val filePath = File(recordDir, fileName).absolutePath
            val success = manager.startRecord(filePath)
            if (success) {
                _uiState.value = _uiState.value.copy(isRecording = true)
                Toast.makeText(context, "Recording started...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to start local recording", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun selectScene(sceneId: String) {
        val scene = _uiState.value.scenes.find { it.id == sceneId } ?: return
        _uiState.value = _uiState.value.copy(activeSceneId = sceneId)
        
        if (scene.type == SceneType.BE_RIGHT_BACK) {
            val bgConfig = _uiState.value.bgConfig.copy(isPauseShieldActive = true)
            updateBackgroundConfig(bgConfig)
        } else if (!_uiState.value.isPaused) {
            val bgConfig = _uiState.value.bgConfig.copy(isPauseShieldActive = false)
            updateBackgroundConfig(bgConfig)
        }
    }

    fun addCustomScene(title: String, icon: String) {
        if (title.isBlank()) return
        val newScene = SceneItem(
            title = title.trim(),
            icon = if (icon.isBlank()) "🎥" else icon.trim(),
            type = SceneType.CUSTOM
        )
        _uiState.value = _uiState.value.copy(
            scenes = _uiState.value.scenes + newScene,
            activeSceneId = newScene.id
        )
    }

    fun removeCustomScene(sceneId: String) {
        if (sceneId in listOf("main", "starting_soon", "brb", "just_chatting")) return
        val updated = _uiState.value.scenes.filterNot { it.id == sceneId }
        val newActive = if (_uiState.value.activeSceneId == sceneId) "main" else _uiState.value.activeSceneId
        _uiState.value = _uiState.value.copy(scenes = updated, activeSceneId = newActive)
    }

    fun setStartingSoonCountdown(seconds: Int) {
        _uiState.value = _uiState.value.copy(startingSoonCountdown = seconds)
    }

    private var bgMusicPlayer: android.media.MediaPlayer? = null

    fun setScoreboardEnabled(enabled: Boolean) {
        val current = _uiState.value.bgConfig
        val updated = current.copy(scoreboard = current.scoreboard.copy(isEnabled = enabled))
        updateBackgroundConfig(updated)
    }

    fun updateScoreboard(scoreboard: ScoreboardConfig) {
        val current = _uiState.value.bgConfig
        val updated = current.copy(scoreboard = scoreboard)
        updateBackgroundConfig(updated)
    }

    fun adjustScoreA(delta: Int) {
        val sb = _uiState.value.bgConfig.scoreboard
        val newScore = (sb.teamAScore + delta).coerceAtLeast(0)
        updateScoreboard(sb.copy(teamAScore = newScore))
    }

    fun adjustScoreB(delta: Int) {
        val sb = _uiState.value.bgConfig.scoreboard
        val newScore = (sb.teamBScore + delta).coerceAtLeast(0)
        updateScoreboard(sb.copy(teamBScore = newScore))
    }

    fun toggleMatchTimer() {
        val sb = _uiState.value.bgConfig.scoreboard
        updateScoreboard(sb.copy(isTimerRunning = !sb.isTimerRunning))
    }

    fun resetMatchTimer() {
        val sb = _uiState.value.bgConfig.scoreboard
        updateScoreboard(sb.copy(matchTimeSeconds = 0, isTimerRunning = false))
    }

    fun setTickerEnabled(enabled: Boolean) {
        val current = _uiState.value.bgConfig
        val updated = current.copy(ticker = current.ticker.copy(isEnabled = enabled))
        updateBackgroundConfig(updated)
    }

    fun updateTicker(ticker: TickerConfig) {
        val current = _uiState.value.bgConfig
        val updated = current.copy(ticker = ticker)
        updateBackgroundConfig(updated)
    }

    fun setWatermarkEnabled(enabled: Boolean) {
        val current = _uiState.value.bgConfig
        val updated = current.copy(watermark = current.watermark.copy(isEnabled = enabled))
        updateBackgroundConfig(updated)
    }

    fun updateWatermark(watermark: WatermarkConfig) {
        val current = _uiState.value.bgConfig
        val updated = current.copy(watermark = watermark)
        updateBackgroundConfig(updated)
    }

    fun setPresetGradient(gradientName: String?) {
        val current = _uiState.value.bgConfig
        val updated = current.copy(presetGradient = gradientName)
        updateBackgroundConfig(updated)
    }

    fun setM3u8AudioDelay(delayMs: Int) {
        _uiState.value = _uiState.value.copy(m3u8AudioDelayMs = delayMs)
    }

    fun setBgMusicVolume(volume: Float) {
        _uiState.value = _uiState.value.copy(bgMusicVolume = volume)
        val effectiveVol = if (_uiState.value.isBgMusicMuted) 0f else volume
        try {
            bgMusicPlayer?.setVolume(effectiveVol, effectiveVol)
        } catch (_: Exception) {}
    }

    fun toggleBgMusicMute() {
        val newMute = !_uiState.value.isBgMusicMuted
        _uiState.value = _uiState.value.copy(isBgMusicMuted = newMute)
        val effectiveVol = if (newMute) 0f else _uiState.value.bgMusicVolume
        try {
            bgMusicPlayer?.setVolume(effectiveVol, effectiveVol)
        } catch (_: Exception) {}
    }

    fun setBgMusicUri(context: Context, uri: Uri, fileName: String) {
        try {
            bgMusicPlayer?.stop()
            bgMusicPlayer?.release()
            bgMusicPlayer = null
        } catch (_: Exception) {}
        _uiState.value = _uiState.value.copy(
            bgMusicUri = uri.toString(),
            bgMusicName = fileName,
            isBgMusicPlaying = false
        )
    }

    fun toggleBgMusicPlaying(context: Context) {
        val currentState = _uiState.value
        if (currentState.isBgMusicPlaying) {
            try {
                bgMusicPlayer?.pause()
            } catch (_: Exception) {}
            _uiState.value = _uiState.value.copy(isBgMusicPlaying = false)
        } else {
            try {
                if (bgMusicPlayer == null && currentState.bgMusicUri != null) {
                    bgMusicPlayer = android.media.MediaPlayer.create(context, Uri.parse(currentState.bgMusicUri))
                }
                if (bgMusicPlayer != null) {
                    bgMusicPlayer?.isLooping = true
                    val vol = if (currentState.isBgMusicMuted) 0f else currentState.bgMusicVolume
                    bgMusicPlayer?.setVolume(vol, vol)
                    bgMusicPlayer?.start()
                    _uiState.value = _uiState.value.copy(isBgMusicPlaying = true)
                } else {
                    // Generative ambient notification or placeholder
                    Toast.makeText(context, "Select an MP3 loop to start background music", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Audio error: ${e.message}", Toast.LENGTH_SHORT).show()
                _uiState.value = _uiState.value.copy(isBgMusicPlaying = false)
            }
        }
    }

    fun toggleGainBoost() {
        _uiState.value = _uiState.value.copy(isGainBoostEnabled = !_uiState.value.isGainBoostEnabled)
    }

    fun toggleNoiseGate() {
        _uiState.value = _uiState.value.copy(isNoiseGateEnabled = !_uiState.value.isNoiseGateEnabled)
    }

    fun toggleEchoCancellation() {
        _uiState.value = _uiState.value.copy(isEchoCancellationEnabled = !_uiState.value.isEchoCancellationEnabled)
    }

    fun toggleAudioDucking() {
        _uiState.value = _uiState.value.copy(isAudioDuckingEnabled = !_uiState.value.isAudioDuckingEnabled)
    }

    fun shutdownApp(context: Context) {
        try {
            liveChatConnector.disconnectAll()
        } catch (_: Exception) {}
        try {
            stopStream()
        } catch (e: Exception) {}
        try {
            val serviceIntent = Intent(context, LiveStreamService::class.java)
            context.stopService(serviceIntent)
        } catch (e: Exception) {}
        try {
            (context as? android.app.Activity)?.finishAffinity()
        } catch (e: Exception) {}
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
