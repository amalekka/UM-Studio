package com.example.ui

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.pedro.library.view.OpenGlView

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var service: LiveStreamService? = null

    data class BackgroundConfig(
        val imageUri: String? = null,
        val blurRadius: Float = 0f,
        val brightness: Float = 1f,
        val scaleMode: BackgroundScaleMode = BackgroundScaleMode.COVER,
        val foregroundScale: Float = 1f,
        val foregroundPosition: ForegroundPosition = ForegroundPosition.CENTER,
        val isPauseShieldActive: Boolean = false
    )

    enum class BackgroundScaleMode { COVER, CONTAIN, STRETCH }
    enum class ForegroundPosition { CENTER, TOP, BOTTOM, FREE }

    data class UiState(
        val isStreaming: Boolean = false,
        val streamingStatus: StreamingStatus = StreamingStatus.IDLE,
        val currentBitrate: Int = 0,
        val streamTime: Long = 0,
        val currentSource: StreamSource = StreamSource.CAMERA,
        val streamConfig: StreamConfig = StreamConfig(),
        val endpoints: List<PlatformEndpoint> = listOf(
            PlatformEndpoint.YouTube(""),
            PlatformEndpoint.Facebook(""),
            PlatformEndpoint.Twitch(""),
            PlatformEndpoint.CustomRTMP("Custom", "", "")
        ),
        val m3u8Url: String = "",
        val m3u8Headers: Map<String, String> = emptyMap(),
        val isMicEnabled: Boolean = true,
        val isCameraEnabled: Boolean = true,
        val isWatermarkEnabled: Boolean = false,
        val isTickerEnabled: Boolean = false,
        val developerModeEnabled: Boolean = false,
        val logs: List<String> = emptyList(),
        val viewerCount: Int = 0,
        val fps: Int = 0,
        val bgConfig: BackgroundConfig = BackgroundConfig(),
        val isChatVisible: Boolean = true
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
                    _uiState.value = _uiState.value.copy(
                        streamingStatus = status,
                        isStreaming = status == StreamingStatus.STREAMING || status == StreamingStatus.RECONNECTING
                    )
                    if (status == StreamingStatus.STREAMING) {
                        startTimer()
                    } else {
                        stopTimer()
                    }
                }}
                launch { manager.bitrate.collect { bitrate ->
                    _uiState.value = _uiState.value.copy(currentBitrate = bitrate / 1000) // kbps
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
        timerJob = viewModelScope.launch {
            while (true) {
                _uiState.value = _uiState.value.copy(
                    streamTime = (System.currentTimeMillis() - startTime) / 1000
                )
                delay(1000)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        _uiState.value = _uiState.value.copy(streamTime = 0, fps = 0, viewerCount = 0, currentBitrate = 0)
    }

    fun initPreview(view: OpenGlView) {
        pendingView = view
        service?.getStreamManager()?.let { manager ->
            manager.setConfig(_uiState.value.streamConfig)
            manager.initCamera(view)
        }
    }

    fun startStream(context: Context, mediaProjectionResult: Intent? = null) {
        val config = _uiState.value.streamConfig.copy(
            endpoints = _uiState.value.endpoints.filter { it.isEnabled && it.streamKey.isNotEmpty() }
        )
        
        if (config.endpoints.isEmpty()) return

        val state = _uiState.value
        service?.getStreamManager()?.let { manager ->
            if (manager.prepareStream(config)) {
                val primary = config.endpoints.first()
                val url = "${primary.url}/${primary.streamKey}"
                
                when (state.currentSource) {
                    StreamSource.SCREEN -> manager.startStream(url, mediaProjectionResult)
                    StreamSource.CAMERA -> manager.startStream(url)
                    StreamSource.M3U8 -> {
                        service?.getRestreamEngine()?.startRestreaming(config, url)
                    }
                }
            }
        }
    }

    fun stopStream() {
        service?.getStreamManager()?.stopStream()
        service?.getRestreamEngine()?.stop()
    }

    fun stopPreview() {
        pendingView = null
        service?.getStreamManager()?.stopPreview()
    }

    fun toggleSource(source: StreamSource) {
        _uiState.value = _uiState.value.copy(currentSource = source)
        service?.getStreamManager()?.setSource(source)
        // Keep preview active so GL engine can render filters (M3U8/Background)
        service?.getStreamManager()?.startPreview()
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
        service?.getRestreamEngine()?.setupRestream(url, headers)
    }

    fun toggleMic(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isMicEnabled = enabled)
        if (enabled) {
            service?.getStreamManager()?.unmuteAudio()
        } else {
            service?.getStreamManager()?.muteAudio()
        }
    }

    fun toggleWatermark(enabled: Boolean, context: Context) {
        _uiState.value = _uiState.value.copy(isWatermarkEnabled = enabled)
        if (enabled) {
            // In a real app, we'd load a bitmap. Here we'll use a placeholder or dummy bitmap
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

    fun switchCamera() {
        service?.getStreamManager()?.switchCamera()
    }

    fun updateBackgroundConfig(config: BackgroundConfig) {
        _uiState.value = _uiState.value.copy(bgConfig = config)
        service?.getBackgroundManager()?.updateConfig(config)
    }

    fun onVersionClick() {
        service?.getProManager()?.onVersionClick()
    }
}
