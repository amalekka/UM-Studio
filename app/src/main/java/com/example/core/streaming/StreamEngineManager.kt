package com.example.core.streaming

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.SurfaceView
import com.example.core.model.AspectRatioMode
import com.example.core.model.StreamConfig
import com.example.core.model.VideoQuality
import com.example.core.model.VideoCodec
import com.example.core.model.buildDestinationUrl
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
    M3U8,
    AUDIO
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

    private val _diagnosticMessage = MutableStateFlow("Ready")
    val diagnosticMessage: StateFlow<String> = _diagnosticMessage

    private val _diagnosticIsError = MutableStateFlow(false)
    val diagnosticIsError: StateFlow<Boolean> = _diagnosticIsError

    private val _bitrate = MutableStateFlow(0)
    val bitrate: StateFlow<Int> = _bitrate

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps

    fun setStreamingStatus(status: StreamingStatus) {
        _status.value = status
        if (status == StreamingStatus.IDLE) {
            _bitrate.value = 0
            _fps.value = 0
            _audioLevelL.value = 0f
            _audioLevelR.value = 0f
            _diagnosticMessage.value = "Ready"
            _diagnosticIsError.value = false
        }
    }

    fun updateStats(bitrateKbps: Int, fpsVal: Int) {
        if (!isStreaming() && _status.value != StreamingStatus.STREAMING) {
            _bitrate.value = 0
            _fps.value = 0
            return
        }
        if (bitrateKbps > 0) _bitrate.value = bitrateKbps
        if (fpsVal > 0) {
            _fps.value = fpsVal
            if (!_diagnosticIsError.value) {
                _diagnosticMessage.value = "Streaming Video ($fpsVal FPS)"
            }
        }
    }

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
    private val maxReconnectAttempts = 3
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
        if (currentSource != StreamSource.CAMERA) return
        try {
            if (openGlView.holder.surface?.isValid != true) {
                Log.w("StreamEngine", "Surface is not valid, skipping camera init")
                return
            }
            if (rtmpCamera2 == null) {
                rtmpCamera2 = RtmpCamera2(openGlView, connectChecker).apply {
                    setFpsListener { measuredFps ->
                        _fps.value = measuredFps
                    }
                    setCustomAudioEffect(audioEffect)
                }
            } else {
                try {
                    rtmpCamera2?.replaceView(openGlView)
                } catch (t: Throwable) {
                    Log.e("StreamEngine", "Error replacing view in initCamera: ${t.message}")
                }
            }
            _isFrontCamera.value = rtmpCamera2?.isFrontCamera == true
            _isFlashOn.value = rtmpCamera2?.isLanternEnabled == true
            if (currentSource == StreamSource.CAMERA) {
                startPreview()
            }
        } catch (t: Throwable) {
            Log.e("StreamEngine", "Error initializing camera: ${t.message}")
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
            if (openGlView.holder.surface?.isValid == true && currentSource == StreamSource.CAMERA) {
                rtmpCamera2?.replaceView(openGlView)
            }
        } catch (t: Throwable) {
            Log.e("StreamEngine", "Failed to set preview view: ${t.message}")
        }
    }

    fun initDisplay(openGlView: OpenGlView) {
        if (isReleased) return
        // Note: RtmpDisplay doesn't support direct preview view in this version
    }

    fun setSource(source: StreamSource) {
        currentSource = source
        when (source) {
            StreamSource.M3U8 -> {
                stopCameraHardware()
                stopDisplayStream()
            }
            StreamSource.SCREEN -> {
                stopCameraHardware()
            }
            StreamSource.CAMERA -> {
                stopDisplayStream()
                startPreview()
            }
            StreamSource.AUDIO -> {
                // Keep GL pipeline active for background image broadcast
                stopDisplayStream()
                startPreview()
            }
        }
    }

    fun stopCameraHardware() {
        if (isReleased) return
        try {
            val camera = rtmpCamera2
            if (camera != null) {
                if (camera.isStreaming) {
                    try { camera.stopStream() } catch (t: Throwable) { Log.e("StreamEngine", "stopStream error: ${t.message}") }
                }
                if (camera.isOnPreview) {
                    try { camera.stopPreview() } catch (t: Throwable) { Log.e("StreamEngine", "stopPreview error: ${t.message}") }
                }
            }
        } catch (t: Throwable) {
            Log.e("StreamEngine", "Error stopping camera hardware: ${t.message}")
        }
    }

    fun stopDisplayStream() {
        if (isReleased) return
        try {
            if (rtmpDisplay?.isStreaming == true) {
                rtmpDisplay?.stopStream()
            }
        } catch (t: Throwable) {
            Log.e("StreamEngine", "Error stopping display stream: ${t.message}")
        }
    }

    fun startPreview(w: Int = 0, h: Int = 0) {
        if (isReleased) return
        if (currentSource != StreamSource.CAMERA) return
        try {
            val camera = rtmpCamera2 ?: return
            if (!camera.isOnPreview) {
                val (defaultW, defaultH) = calculateResolution(currentConfig.videoQuality, currentConfig.aspectRatio)
                val targetW = if (w > 0) w else defaultW
                val targetH = if (h > 0) h else defaultH
                camera.startPreview(targetW, targetH)
            }
        } catch (t: Throwable) {
            Log.e("StreamEngine", "Failed to start preview (surface may be abandoned): ${t.message}")
        }
    }

    fun stopPreview() {
        if (isReleased) return
        try {
            val camera = rtmpCamera2
            if (camera != null && camera.isOnPreview && !camera.isStreaming) {
                camera.stopPreview()
            }
        } catch (t: Throwable) {
            Log.e("StreamEngine", "Error stopping preview: ${t.message}")
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
        val safeBitrate = quality.defaultBitrate.coerceIn(1_500_000, 4_500_000)
        
        val videoPrepared = if (currentSource == StreamSource.CAMERA || currentSource == StreamSource.M3U8 || currentSource == StreamSource.AUDIO) {
            rtmpCamera2?.setVideoCodec(codec)
            rtmpCamera2?.prepareVideo(width, height, quality.fps, safeBitrate, 2) ?: false
        } else {
            rtmpDisplay?.setVideoCodec(codec)
            rtmpDisplay?.prepareVideo(width, height, quality.fps, safeBitrate, 2, context.resources.configuration.densityDpi) ?: false
        }

        val audioPrepared = if (currentSource == StreamSource.CAMERA || currentSource == StreamSource.M3U8 || currentSource == StreamSource.AUDIO) {
            rtmpCamera2?.prepareAudio(128 * 1024, 44100, true, false, false) ?: false
        } else {
            rtmpDisplay?.prepareAudio(128 * 1024, 44100, true, false, false) ?: false
        }

        if (videoPrepared && audioPrepared) {
            bitrateAdapter = BitrateAdapter { bitrate ->
                val targetBitrate = bitrate.toInt().coerceIn(1_500_000, 4_500_000)
                if (currentSource == StreamSource.CAMERA || currentSource == StreamSource.M3U8 || currentSource == StreamSource.AUDIO) {
                    rtmpCamera2?.setVideoBitrateOnFly(targetBitrate)
                } else {
                    rtmpDisplay?.setVideoBitrateOnFly(targetBitrate)
                }
                _bitrate.value = (targetBitrate / 1000)
            }
            bitrateAdapter?.setMaxBitrate(safeBitrate)
            return true
        }
        return false
    }

    fun startStream(url: String, mediaProjectionResult: Intent? = null) {
        if (isReleased) return
        if (isStreaming()) {
            Log.w("StreamEngine", "Stream already running, ignoring startStream call")
            return
        }
        
        _status.value = StreamingStatus.CONNECTING
        _diagnosticMessage.value = "Connecting to RTMP..."
        _diagnosticIsError.value = false
        try {
            if (currentSource == StreamSource.CAMERA) {
                if (rtmpCamera2?.isOnPreview == false) {
                    startPreview()
                }
                prepareStream(currentConfig)
                if (rtmpCamera2?.isStreaming == false) {
                    rtmpCamera2?.startStream(url)
                }
            } else if (currentSource == StreamSource.M3U8 || currentSource == StreamSource.AUDIO) {
                if (currentSource == StreamSource.AUDIO) {
                    prepareStream(currentConfig)
                    if (rtmpCamera2?.isStreaming == false) {
                        rtmpCamera2?.startStream(url)
                    }
                }
                _status.value = StreamingStatus.STREAMING
            } else {
                mediaProjectionResult?.let {
                    rtmpDisplay?.setIntentResult(-1, it)
                    prepareStream(currentConfig)
                    if (rtmpDisplay?.isStreaming == false) {
                        rtmpDisplay?.startStream(url)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e("StreamEngine", "Fatal error starting stream codec: ${t.message}", t)
            _status.value = StreamingStatus.ERROR
            _diagnosticIsError.value = true
            _diagnosticMessage.value = "Start Failed: ${t.javaClass.simpleName}"
        }
    }

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    fun startRecord(filePath: String): Boolean {
        if (isReleased) return false
        try {
            when (currentSource) {
                StreamSource.CAMERA, StreamSource.AUDIO -> {
                    rtmpCamera2?.startRecord(filePath) ?: return false
                }
                StreamSource.SCREEN -> {
                    rtmpDisplay?.startRecord(filePath) ?: return false
                }
                else -> return false
            }
            
            _isRecording.value = true
            return true
        } catch (e: Exception) {
            Log.e("StreamEngine", "Failed to start local recording: ${e.message}")
            _isRecording.value = false
            return false
        }
    }

    fun stopRecord() {
        if (isReleased) return
        try {
            if (currentSource == StreamSource.CAMERA || currentSource == StreamSource.M3U8 || currentSource == StreamSource.AUDIO) {
                rtmpCamera2?.stopRecord()
            } else {
                rtmpDisplay?.stopRecord()
            }
        } catch (e: Exception) {
            Log.e("StreamEngine", "Error stopping local recording: ${e.message}")
        } finally {
            _isRecording.value = false
        }
    }

    fun stopStream() {
        if (isReleased) return
        try {
            if (_isRecording.value) {
                stopRecord()
            }
        } catch (t: Throwable) {
            Log.e("StreamEngine", "Error during stopRecord: ${t.message}")
        }
        
        try {
            if (currentSource == StreamSource.CAMERA || currentSource == StreamSource.M3U8 || currentSource == StreamSource.AUDIO) {
                rtmpCamera2?.stopStream()
            } else {
                rtmpDisplay?.stopStream()
            }
        } catch (t: Throwable) {
            Log.e("StreamEngine", "Error stopping RTMP engine: ${t.message}")
        } finally {
            bitrateAdapter = null
            reconnectJob?.cancel()
            reconnectAttempts = 0
            _status.value = StreamingStatus.IDLE
            _bitrate.value = 0
            _fps.value = 0
            _audioLevelL.value = 0f
            _audioLevelR.value = 0f
            _diagnosticMessage.value = "Ready"
            _diagnosticIsError.value = false
        }
    }

    fun isStreaming(): Boolean {
        if (isReleased) return false
        val cameraStreaming = rtmpCamera2?.isStreaming == true
        val displayStreaming = rtmpDisplay?.isStreaming == true
        val statusStreaming = _status.value == StreamingStatus.STREAMING
        return cameraStreaming || displayStreaming || statusStreaming
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
        if (!isStreaming()) {
            _bitrate.value = 0
            _fps.value = 0
            return
        }
        val kbps = (bitrateBps / 1000).toInt()
        if (kbps > 100) {
            _bitrate.value = kbps
        } else if (_bitrate.value <= 100) {
            _bitrate.value = (currentConfig.videoQuality.defaultBitrate / 1000).coerceAtLeast(2500)
        }
    }

    fun calculateResolution(quality: VideoQuality, aspect: AspectRatioMode): Pair<Int, Int> {
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

    fun onConnectionStarted(url: String) {
        if (isReleased) return
        _diagnosticMessage.value = "Connecting to RTMP..."
        _diagnosticIsError.value = false
    }

    fun onConnectionSuccess() {
        if (isReleased) return
        _status.value = StreamingStatus.STREAMING
        reconnectAttempts = 0
        _diagnosticMessage.value = "Handshake OK • Publishing"
        _diagnosticIsError.value = false
    }

    fun onConnectionFailed(reason: String) {
        if (isReleased) return
        Log.e("StreamEngine", "Connection failed: $reason")
        _diagnosticIsError.value = true
        _diagnosticMessage.value = when {
            reason.contains("auth", true) || reason.contains("badname", true) || reason.contains("key", true) -> {
                "Auth Failed: Invalid Stream Key"
            }
            reason.contains("Unimplemented message type", true) -> {
                "Stream Rejected: Check Stream Key or Ingest URL"
            }
            reason.contains("refused", true) || reason.contains("reset", true) -> {
                "RTMP Error: Connection Refused"
            }
            reason.contains("timeout", true) -> {
                "RTMP Timeout: Check Network"
            }
            else -> {
                "Connection Failed: $reason"
            }
        }
        if (currentConfig.isAutoReconnectEnabled && reconnectAttempts < maxReconnectAttempts) {
            attemptReconnect()
        } else {
            _status.value = StreamingStatus.ERROR
            _diagnosticIsError.value = true
            _diagnosticMessage.value = "Connection Lost: Maximum retries reached"
        }
    }

    fun onAuthError() {
        if (isReleased) return
        Log.e("StreamEngine", "Authentication rejected by RTMP server")
        _status.value = StreamingStatus.ERROR
        _diagnosticIsError.value = true
        _diagnosticMessage.value = "Auth Failed: Invalid Stream Key"
    }

    fun onDisconnect() {
        if (isReleased) return
        if (_status.value == StreamingStatus.STREAMING && currentConfig.isAutoReconnectEnabled && reconnectAttempts < maxReconnectAttempts) {
            attemptReconnect()
        } else if (_status.value != StreamingStatus.RECONNECTING) {
            _status.value = StreamingStatus.DISCONNECTED
            _diagnosticMessage.value = "Disconnected"
        }
    }

    private fun attemptReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            _status.value = StreamingStatus.RECONNECTING
            reconnectAttempts++
            _diagnosticMessage.value = "Reconnecting ($reconnectAttempts/$maxReconnectAttempts) in 2s..."
            _diagnosticIsError.value = false
            delay(2000)
            if (isReleased) return@launch
            currentConfig.endpoints.firstOrNull { it.isEnabled }?.let { endpoint ->
                val url = buildDestinationUrl(endpoint.url, endpoint.streamKey)
                prepareStream(currentConfig)
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
