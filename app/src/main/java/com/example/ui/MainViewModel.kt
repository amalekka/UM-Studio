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
import com.example.core.streaming.LiveStreamService
import com.example.core.streaming.StreamSource
import com.example.core.streaming.StreamingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pedro.library.view.OpenGlView
import java.io.File
import java.net.URL
import java.util.UUID

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var service: LiveStreamService? = null

    enum class M3u8PlaybackState { STOPPED, PLAYING, PAUSED }
    enum class ImageDisplayMode { FULL_IMAGE, PIP_OVERLAY, OFF }

    data class BackgroundConfig(
        val imageUri: String? = null,
        val displayMode: ImageDisplayMode = ImageDisplayMode.FULL_IMAGE,
        val blurRadius: Float = 0f,
        val brightness: Float = 1f,
        val opacity: Float = 1f,
        val zoom: Float = 1f,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val scaleMode: BackgroundScaleMode = BackgroundScaleMode.COVER,
        val foregroundScale: Float = 1f,
        val foregroundPosition: ForegroundPosition = ForegroundPosition.CENTER,
        val isPauseShieldActive: Boolean = false
    )

    enum class BackgroundScaleMode { COVER, CONTAIN, TOP_BANNER, BOTTOM_BANNER, STRETCH }
    enum class ForegroundPosition { CENTER, TOP, BOTTOM, FREE }

    data class SavedStreamItem(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val url: String,
        val userAgent: String = "",
        val referer: String = ""
    )

    data class ChatMessage(
        val id: String = UUID.randomUUID().toString(),
        val platform: String,
        val author: String,
        val message: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class UiState(
        val isStreaming: Boolean = false,
        val streamingStatus: StreamingStatus = StreamingStatus.IDLE,
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
            PlatformEndpoint.Kick()
        ),
        val m3u8Url: String = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_adv_example_hevc/master.m3u8",
        val m3u8Headers: Map<String, String> = emptyMap(),
        val m3u8State: M3u8PlaybackState = M3u8PlaybackState.STOPPED,
        val isM3u8Muted: Boolean = false,
        val m3u8Volume: Float = 1.0f,
        val activeM3u8Name: String = "Apple BipBop HLS",
        val showScreenCapturePrompt: Boolean = false,
        val savedStreams: List<SavedStreamItem> = listOf(
            SavedStreamItem(name = "Apple BipBop HLS", url = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_adv_example_hevc/master.m3u8"),
            SavedStreamItem(name = "Apple Advanced HLS", url = "https://devstreaming-cdn.apple.com/videos/streaming/examples/adv_dv_atmos/master.m3u8"),
            SavedStreamItem(name = "Big Buck Bunny HLS", url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
        ),
        val isMicEnabled: Boolean = true,
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
        val currentTab: Int = 0,
        val chatMessages: List<ChatMessage> = emptyList()
    )

    private var pendingView: OpenGlView? = null

    fun onServiceConnected(service: LiveStreamService) {
        this.service = service
        pendingView?.let { view ->
            initPreview(view)
        }
        viewModelScope.launch {
            service.getStreamManager()?.let { manager ->
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
                    _uiState.value = _uiState.value.copy(currentBitrate = bitrate)
                }}
                launch { manager.fps.collect { measuredFps ->
                    _uiState.value = _uiState.value.copy(fps = measuredFps)
                }}
                launch { manager.audioLevelL.collect { levelL ->
                    _uiState.value = _uiState.value.copy(
                        audioLevelL = if (_uiState.value.isStreaming && _uiState.value.isMicEnabled) levelL else 0f
                    )
                }}
                launch { manager.audioLevelR.collect { levelR ->
                    _uiState.value = _uiState.value.copy(
                        audioLevelR = if (_uiState.value.isStreaming && _uiState.value.isMicEnabled) levelR else 0f
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
        val validEndpoints = _uiState.value.endpoints.filter { it.isEnabled && it.streamKey.isNotBlank() }
        if (validEndpoints.isEmpty()) {
            Toast.makeText(context, "Please enable at least one destination and enter a Stream Key in Destinations tab", Toast.LENGTH_LONG).show()
            return false
        }

        val config = _uiState.value.streamConfig.copy(endpoints = validEndpoints)
        val state = _uiState.value

        service?.getStreamManager()?.let { manager ->
            if (manager.prepareStream(config)) {
                val primary = validEndpoints.first()
                val rawUrl = primary.url.trim()
                val key = primary.streamKey.trim()
                val url = when {
                    rawUrl.contains(key) && key.isNotBlank() -> rawUrl
                    rawUrl.endsWith("/") -> "$rawUrl$key"
                    else -> "$rawUrl/$key"
                }
                
                Log.i("MainViewModel", "Starting RTMP Stream to: $url")
                
                when (state.currentSource) {
                    StreamSource.SCREEN -> manager.startStream(url, mediaProjectionResult)
                    StreamSource.CAMERA -> manager.startStream(url)
                    StreamSource.M3U8 -> {
                        service?.getRestreamEngine()?.startRestreaming(config, url)
                    }
                }

                if (validEndpoints.size > 1) {
                    service?.getProManager()?.let { pro ->
                        validEndpoints.drop(1).forEach { extra ->
                            val extraRaw = extra.url.trim()
                            val extraKey = extra.streamKey.trim()
                            val extraUrl = when {
                                extraRaw.contains(extraKey) && extraKey.isNotBlank() -> extraRaw
                                extraRaw.endsWith("/") -> "$extraRaw$extraKey"
                                else -> "$extraRaw/$extraKey"
                            }
                            pro.addExtraEndpoint(extra.copyWithUrl(extraUrl), service!!)
                        }
                        pro.startMultiStreaming()
                    }
                }

                Toast.makeText(context, "Broadcasting live to ${primary.name}...", Toast.LENGTH_SHORT).show()
                return true
            } else {
                Toast.makeText(context, "Failed to initialize video/audio encoder for streaming.", Toast.LENGTH_LONG).show()
            }
        }
        return false
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
            is PlatformEndpoint.CustomRTMP -> this.copy(url = newUrl)
        }
    }

    fun stopStream() {
        try {
            _uiState.value = _uiState.value.copy(
                isStreaming = false,
                streamingStatus = StreamingStatus.IDLE,
                currentBitrate = 0,
                fps = 0
            )
            stopTimer()
            stopM3u8Stream()
            service?.getStreamManager()?.stopStream()
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error stopping stream", e)
        }
    }

    fun stopPreview() {
        pendingView = null
        service?.getStreamManager()?.stopPreview()
    }

    fun toggleSource(source: StreamSource) {
        if (source != StreamSource.M3U8) {
            stopM3u8Stream()
        }
        val showPrompt = (source == StreamSource.SCREEN)
        _uiState.value = _uiState.value.copy(
            currentSource = source,
            showScreenCapturePrompt = showPrompt
        )
        service?.getStreamManager()?.setSource(source)
        if (source == StreamSource.M3U8) {
            service?.getStreamManager()?.stopPreview()
        } else {
            service?.getStreamManager()?.startPreview()
        }
    }

    fun dismissScreenCapturePrompt() {
        _uiState.value = _uiState.value.copy(showScreenCapturePrompt = false)
    }

    fun playM3u8(url: String? = null, name: String? = null, headers: Map<String, String>? = null) {
        val targetUrl = url ?: _uiState.value.m3u8Url
        val targetHeaders = headers ?: _uiState.value.m3u8Headers
        val targetName = name ?: _uiState.value.activeM3u8Name
        _uiState.value = _uiState.value.copy(
            m3u8Url = targetUrl,
            m3u8Headers = targetHeaders,
            activeM3u8Name = targetName,
            m3u8State = M3u8PlaybackState.PLAYING,
            currentSource = StreamSource.M3U8
        )
        service?.getStreamManager()?.setSource(StreamSource.M3U8)
        service?.getRestreamEngine()?.setupRestream(targetUrl, targetHeaders)
        service?.getRestreamEngine()?.play()
    }

    fun pauseM3u8() {
        service?.getRestreamEngine()?.pause()
        _uiState.value = _uiState.value.copy(m3u8State = M3u8PlaybackState.PAUSED)
    }

    fun resumeM3u8() {
        service?.getRestreamEngine()?.play()
        _uiState.value = _uiState.value.copy(m3u8State = M3u8PlaybackState.PLAYING)
    }

    fun stopM3u8Stream() {
        service?.getRestreamEngine()?.stop()
        _uiState.value = _uiState.value.copy(m3u8State = M3u8PlaybackState.STOPPED)
    }

    fun toggleM3u8Mute() {
        val newMuted = !_uiState.value.isM3u8Muted
        val targetVolume = if (newMuted) 0f else 1f
        _uiState.value = _uiState.value.copy(isM3u8Muted = newMuted, m3u8Volume = targetVolume)
        service?.getRestreamEngine()?.setM3u8Volume(targetVolume)
    }

    fun setM3u8Volume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _uiState.value = _uiState.value.copy(
            m3u8Volume = clamped,
            isM3u8Muted = clamped == 0f
        )
        service?.getRestreamEngine()?.setM3u8Volume(clamped)
    }

    fun selectSavedStream(stream: SavedStreamItem) {
        val headers = mutableMapOf<String, String>()
        if (stream.userAgent.isNotBlank()) headers["User-Agent"] = stream.userAgent
        if (stream.referer.isNotBlank()) headers["Referer"] = stream.referer
        playM3u8(url = stream.url, name = stream.name, headers = headers)
        setCurrentTab(0)
    }

    fun setCurrentTab(tab: Int) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
    }

    fun getRestreamEngine(): com.example.core.streaming.M3u8RestreamEngine? = service?.getRestreamEngine()

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

    fun saveStream(name: String, url: String, userAgent: String = "", referer: String = "") {
        if (name.isBlank() || url.isBlank()) return
        val current = _uiState.value.savedStreams.toMutableList()
        current.add(SavedStreamItem(
            name = name.trim(),
            url = url.trim(),
            userAgent = userAgent.trim(),
            referer = referer.trim()
        ))
        _uiState.value = _uiState.value.copy(savedStreams = current)
    }

    fun updateSavedStream(id: String, name: String, url: String, userAgent: String = "", referer: String = "") {
        val updated = _uiState.value.savedStreams.map { item ->
            if (item.id == id) {
                item.copy(
                    name = name.trim().ifEmpty { item.name },
                    url = url.trim().ifEmpty { item.url },
                    userAgent = userAgent.trim(),
                    referer = referer.trim()
                )
            } else item
        }
        _uiState.value = _uiState.value.copy(savedStreams = updated)
    }

    fun deleteStream(id: String) {
        val filtered = _uiState.value.savedStreams.filterNot { it.id == id }
        _uiState.value = _uiState.value.copy(savedStreams = filtered)
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
}
