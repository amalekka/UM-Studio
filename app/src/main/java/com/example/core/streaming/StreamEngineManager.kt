package com.example.core.streaming

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.SurfaceView
import com.example.core.model.AspectRatioMode
import com.example.core.model.StreamConfig
import com.example.core.model.VideoQuality
import com.example.core.model.VideoCodec
import com.pedro.encoder.input.video.CameraOpenException
import com.pedro.common.VideoCodec as RootVideoCodec
import com.pedro.common.ConnectChecker
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.rtmp.RtmpDisplay
import com.pedro.library.util.BitrateAdapter
import com.pedro.library.view.OpenGlView
import com.pedro.encoder.input.gl.render.filters.NoFilterRender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class StreamingStatus {
    IDLE,
    CONNECTING,
    STREAMING,
    RECONNECTING,
    ERROR,
    DISCONNECTED
}

enum class StreamSource {
    CAMERA,
    SCREEN,
    M3U8
}

class StreamEngineManager(
    private val context: Context,
    private val connectChecker: ConnectChecker
) {
    internal var rtmpCamera2: RtmpCamera2? = null
    internal var rtmpDisplay: RtmpDisplay? = null
    private var bitrateAdapter: BitrateAdapter? = null
    private var currentSource = StreamSource.CAMERA
    
    private val _status = MutableStateFlow(StreamingStatus.IDLE)
    val status: StateFlow<StreamingStatus> = _status

    private val _bitrate = MutableStateFlow(0)
    val bitrate: StateFlow<Int> = _bitrate

    private var currentConfig: StreamConfig = StreamConfig()
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    init {
        rtmpDisplay = RtmpDisplay(context, true, connectChecker)
    }

    fun setConfig(config: StreamConfig) {
        currentConfig = config
    }

    fun getGlInterface() = if (currentSource == StreamSource.CAMERA) {
        rtmpCamera2?.getGlInterface()
    } else {
        rtmpDisplay?.getGlInterface()
    }

    private val filters = mutableListOf<com.pedro.encoder.input.gl.render.filters.BaseFilterRender>()
    private var isReleased = false

    fun setFilter(filter: com.pedro.encoder.input.gl.render.filters.BaseFilterRender) {
        filters.clear()
        filters.add(filter)
        applyFilters()
    }

    fun addFilter(filter: com.pedro.encoder.input.gl.render.filters.BaseFilterRender) {
        filters.add(filter)
        applyFilters()
    }

    fun setFilters(newFilters: List<com.pedro.encoder.input.gl.render.filters.BaseFilterRender>) {
        filters.clear()
        filters.addAll(newFilters)
        applyFilters()
    }

    private fun applyFilters() {
        if (isReleased) return
        val glInterface = getGlInterface()
        if (glInterface != null && isActive()) {
            try {
                glInterface.clearFilters()
                filters.forEach { glInterface.addFilter(it) }
            } catch (e: Exception) {
                Log.e("StreamEngine", "Failed to apply filters: ${e.message}")
            }
        }
    }

    private fun isActive(): Boolean {
        if (isReleased) return false
        val cameraActive = rtmpCamera2?.isOnPreview == true || rtmpCamera2?.isStreaming == true
        val displayActive = rtmpDisplay?.isStreaming == true
        return cameraActive || displayActive
    }

    fun initCamera(openGlView: OpenGlView) {
        if (isReleased) return
        if (rtmpCamera2 == null) {
            rtmpCamera2 = RtmpCamera2(openGlView, connectChecker)
        } else {
            try {
                rtmpCamera2?.replaceView(openGlView)
            } catch (e: Exception) {
                Log.e("StreamEngine", "Failed to replace view: ${e.message}")
            }
        }
        startPreview()
    }

    fun setPreviewView(openGlView: OpenGlView?) {
        if (isReleased || openGlView == null) return
        try {
            rtmpCamera2?.replaceView(openGlView)
        } catch (e: Exception) {
            Log.e("StreamEngine", "Failed to set preview view: ${e.message}")
        }
    }

    fun setSource(source: StreamSource) {
        currentSource = source
    }

    fun startPreview(w: Int = 0, h: Int = 0) {
        if (isReleased) return
        if (rtmpCamera2?.isOnPreview == false) {
            try {
                val (defaultW, defaultH) = calculateResolution(currentConfig.videoQuality, currentConfig.aspectRatio)
                val targetW = if (w > 0) w else defaultW
                val targetH = if (h > 0) h else defaultH
                rtmpCamera2?.startPreview(targetW, targetH)
            } catch (e: Exception) {
                Log.e("StreamEngine", "Failed to start preview", e)
            }
        }
    }

    fun stopPreview() {
        if (isReleased) return
        try {
            // Only stop preview if we are NOT streaming and preview is active.
            // Do NOT call replaceView(null) as that forcibly destroys the GL context prematurely.
            if (rtmpCamera2?.isStreaming == false && rtmpCamera2?.isOnPreview == true) {
                rtmpCamera2?.stopPreview()
            }
        } catch (e: Exception) {
            Log.e("StreamEngine", "Error stopping preview", e)
        }
    }

    fun prepareStream(config: StreamConfig): Boolean {
        if (isReleased) return false
        currentConfig = config
        val quality = config.videoQuality
        val aspect = config.aspectRatio
        val codec = when (config.videoCodec) {
            VideoCodec.H264 -> RootVideoCodec.H264
            VideoCodec.HEVC -> RootVideoCodec.H265
        }

        val (width, height) = calculateResolution(quality, aspect)
        
        val videoPrepared = if (currentSource == StreamSource.CAMERA || currentSource == StreamSource.M3U8) {
            rtmpCamera2?.setVideoCodec(codec)
            rtmpCamera2?.prepareVideo(width, height, quality.fps, quality.defaultBitrate, 2) ?: false
        } else {
            rtmpDisplay?.setVideoCodec(codec)
            rtmpDisplay?.prepareVideo(width, height, quality.fps, quality.defaultBitrate, 2, context.resources.configuration.densityDpi) ?: false
        }

        val audioPrepared = if (currentSource == StreamSource.CAMERA || currentSource == StreamSource.M3U8) {
            rtmpCamera2?.prepareAudio() ?: false
        } else {
            rtmpDisplay?.prepareAudio() ?: false
        }

        if (videoPrepared && audioPrepared) {
            bitrateAdapter = BitrateAdapter { bitrate ->
                if (currentSource == StreamSource.CAMERA || currentSource == StreamSource.M3U8) {
                    rtmpCamera2?.setVideoBitrateOnFly(bitrate.toInt())
                } else {
                    rtmpDisplay?.setVideoBitrateOnFly(bitrate.toInt())
                }
                _bitrate.value = bitrate.toInt()
            }
            bitrateAdapter?.setMaxBitrate(quality.defaultBitrate)
            return true
        }
        return false
    }

    fun startStream(url: String, mediaProjectionResult: Intent? = null) {
        if (isReleased || isStreaming()) return
        
        _status.value = StreamingStatus.CONNECTING
        if (currentSource == StreamSource.CAMERA || currentSource == StreamSource.M3U8) {
            rtmpCamera2?.startStream(url)
        } else {
            mediaProjectionResult?.let {
                rtmpDisplay?.setIntentResult(-1, it)
                rtmpDisplay?.startStream(url)
            }
        }
    }

    fun stopStream() {
        if (isReleased) return
        if (currentSource == StreamSource.CAMERA || currentSource == StreamSource.M3U8) {
            rtmpCamera2?.stopStream()
        } else {
            rtmpDisplay?.stopStream()
        }
        bitrateAdapter = null
        reconnectJob?.cancel()
        reconnectAttempts = 0
        _status.value = StreamingStatus.IDLE
    }

    private fun isStreaming(): Boolean {
        if (isReleased) return false
        return if (currentSource == StreamSource.CAMERA || currentSource == StreamSource.M3U8) {
            rtmpCamera2?.isStreaming == true
        } else {
            rtmpDisplay?.isStreaming == true
        }
    }

    fun switchCamera() {
        if (isReleased) return
        rtmpCamera2?.switchCamera()
    }

    fun muteAudio() {
        if (isReleased) return
        rtmpCamera2?.disableAudio()
        rtmpDisplay?.disableAudio()
    }

    fun unmuteAudio() {
        if (isReleased) return
        rtmpCamera2?.enableAudio()
        rtmpDisplay?.enableAudio()
    }

    fun clearFilters() {
        if (isReleased) return
        val glInterface = getGlInterface()
        if (glInterface != null && isActive()) {
            try {
                glInterface.clearFilters()
            } catch (e: Exception) {
                Log.e("StreamEngine", "Error clearing filters: ${e.message}")
            }
        }
    }

    fun toggleFlash() {
        if (isReleased) return
        if (rtmpCamera2?.isFrontCamera == false) {
            if (rtmpCamera2?.isLanternEnabled == true) {
                rtmpCamera2?.disableLantern()
            } else {
                rtmpCamera2?.enableLantern()
            }
        }
    }

    private fun calculateResolution(quality: VideoQuality, aspect: AspectRatioMode): Pair<Int, Int> {
        return when (aspect) {
            AspectRatioMode.LANDSCAPE_16_9 -> quality.width to quality.height
            AspectRatioMode.PORTRAIT_9_16 -> quality.height to quality.width
            AspectRatioMode.SQUARE_1_1 -> {
                val size = minOf(quality.width, quality.height)
                size to size
            }
            AspectRatioMode.CLASSIC_4_3 -> {
                val h = quality.height
                val w = (h * 4) / 3
                w to h
            }
            is AspectRatioMode.Custom -> aspect.width to aspect.height
        }
    }

    fun onConnectionSuccess() {
        if (isReleased) return
        _status.value = StreamingStatus.STREAMING
        reconnectAttempts = 0
    }

    fun onConnectionFailed(reason: String) {
        if (isReleased) return
        Log.e("StreamEngine", "Connection failed: $reason")
        if (currentConfig?.isAutoReconnectEnabled == true && reconnectAttempts < maxReconnectAttempts) {
            attemptReconnect()
        } else {
            _status.value = StreamingStatus.ERROR
        }
    }

    fun onDisconnect() {
        if (isReleased) return
        if (_status.value != StreamingStatus.RECONNECTING) {
            _status.value = StreamingStatus.DISCONNECTED
        }
    }

    private fun attemptReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            _status.value = StreamingStatus.RECONNECTING
            reconnectAttempts++
            delay(3000)
            currentConfig?.endpoints?.firstOrNull { it.isEnabled }?.let { endpoint ->
                val url = "${endpoint.url}/${endpoint.streamKey}"
                startStream(url)
            }
        }
    }

    fun release() {
        isReleased = true
        stopStream()
        stopPreview()
        rtmpCamera2 = null
        rtmpDisplay = null
    }
}
