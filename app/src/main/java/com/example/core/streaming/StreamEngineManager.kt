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

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps

    private val _audioLevelL = MutableStateFlow(0f)
    val audioLevelL: StateFlow<Float> = _audioLevelL

    private val _audioLevelR = MutableStateFlow(0f)
    val audioLevelR: StateFlow<Float> = _audioLevelR

    private val _isFlashOn = MutableStateFlow(false)
    val isFlashOn: StateFlow<Boolean> = _isFlashOn

    private val _isFrontCamera = MutableStateFlow(false)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera

    private var isAudioMutedInternal = false

    private var currentConfig: StreamConfig = StreamConfig()
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val audioEffect = object : com.pedro.encoder.input.audio.CustomAudioEffect() {
        override fun process(pcmBuffer: ByteArray): ByteArray {
            calculateAudioLevels(pcmBuffer)
            return pcmBuffer
        }
    }

    init {
        rtmpDisplay = RtmpDisplay(context, true, connectChecker).apply {
            setFpsListener { measuredFps ->
                _fps.value = measuredFps
            }
            setCustomAudioEffect(audioEffect)
        }
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
        if (currentSource == StreamSource.M3U8) return
        try {
            if (rtmpCamera2 == null) {
                rtmpCamera2 = RtmpCamera2(openGlView, connectChecker).apply {
                    setFpsListener { measuredFps ->
                        _fps.value = measuredFps
                    }
                    setCustomAudioEffect(audioEffect)
                }
            } else {
                rtmpCamera2?.replaceView(openGlView)
            }
            _isFrontCamera.value = rtmpCamera2?.isFrontCamera == true
            _isFlashOn.value = rtmpCamera2?.isLanternEnabled == true
            if (currentSource == StreamSource.CAMERA) {
                startPreview()
            }
        } catch (e: Exception) {
            Log.e("StreamEngine", "Error initializing camera: ${e.message}")
        }
    }

    private fun calculateAudioLevels(buffer: ByteArray) {
        if (!isStreaming() || isAudioMutedInternal) {
            _audioLevelL.value = 0f
            _audioLevelR.value = 0f
            return
        }
        val sampleCount = buffer.size / 2
        if (sampleCount <= 0) return

        var sumL = 0.0
        var sumR = 0.0
        var countL = 0
        var countR = 0

        for (i in 0 until buffer.size - 1 step 2) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
            if ((i / 2) % 2 == 0) {
                sumL += sample * sample
                countL++
            } else {
                sumR += sample * sample
                countR++
            }
        }
        if (countR == 0) {
            sumR = sumL
            countR = countL
        }
        val rmsL = kotlin.math.sqrt(sumL / countL.coerceAtLeast(1)) / 32768.0
        val rmsR = kotlin.math.sqrt(sumR / countR.coerceAtLeast(1)) / 32768.0

        val targetL = (rmsL * 4.5).coerceIn(0.0, 1.0).toFloat()
        val targetR = (rmsR * 4.5).coerceIn(0.0, 1.0).toFloat()

        val prevL = _audioLevelL.value
        val prevR = _audioLevelR.value
        _audioLevelL.value = if (targetL > prevL) targetL else (prevL * 0.85f).coerceAtLeast(0f)
        _audioLevelR.value = if (targetR > prevR) targetR else (prevR * 0.85f).coerceAtLeast(0f)
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
        if (source == StreamSource.M3U8) {
            stopPreview()
        }
    }

    fun startPreview(w: Int = 0, h: Int = 0) {
        if (isReleased) return
        if (currentSource == StreamSource.M3U8) return
        try {
            if (rtmpCamera2?.isOnPreview == false) {
                val (defaultW, defaultH) = calculateResolution(currentConfig.videoQuality, currentConfig.aspectRatio)
                val targetW = if (w > 0) w else defaultW
                val targetH = if (h > 0) h else defaultH
                rtmpCamera2?.startPreview(targetW, targetH)
            }
        } catch (e: Exception) {
            Log.e("StreamEngine", "Failed to start preview (surface may be abandoned)", e)
        }
    }

    fun stopPreview() {
        if (isReleased) return
        try {
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
        _bitrate.value = 0
        _fps.value = 0
        _audioLevelL.value = 0f
        _audioLevelR.value = 0f
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
        try {
            if (_isFlashOn.value) {
                rtmpCamera2?.disableLantern()
                _isFlashOn.value = false
            }
            rtmpCamera2?.switchCamera()
            _isFrontCamera.value = rtmpCamera2?.isFrontCamera == true
        } catch (e: Exception) {
            Log.e("StreamEngine", "Error switching camera", e)
        }
    }

    fun muteAudio() {
        isAudioMutedInternal = true
        _audioLevelL.value = 0f
        _audioLevelR.value = 0f
        if (isReleased) return
        rtmpCamera2?.disableAudio()
        rtmpDisplay?.disableAudio()
    }

    fun unmuteAudio() {
        isAudioMutedInternal = false
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

    fun toggleFlash(): Boolean {
        if (isReleased) return false
        val camera = rtmpCamera2 ?: return false
        if (camera.isFrontCamera) {
            _isFlashOn.value = false
            return false
        }
        return try {
            if (camera.isLanternEnabled) {
                camera.disableLantern()
                _isFlashOn.value = false
            } else {
                camera.enableLantern()
                _isFlashOn.value = true
            }
            true
        } catch (e: Exception) {
            Log.e("StreamEngine", "Failed to toggle lantern: ${e.message}")
            _isFlashOn.value = false
            false
        }
    }

    fun onNewBitrate(bitrateBps: Long) {
        if (isReleased) return
        _bitrate.value = (bitrateBps / 1000).toInt()
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
