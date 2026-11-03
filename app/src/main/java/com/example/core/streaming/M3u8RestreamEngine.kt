package com.example.core.streaming

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.example.core.model.StreamConfig
import com.pedro.encoder.input.gl.render.filters.`object`.SurfaceFilterRender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class M3u8RestreamEngine(
    private val context: Context,
    private val streamManager: StreamEngineManager
) {
    private var exoPlayer: ExoPlayer? = null
    var surfaceFilter: SurfaceFilterRender? = null
        private set
    
    private val _isSourceLoading = MutableStateFlow(false)
    val isSourceLoading: StateFlow<Boolean> = _isSourceLoading

    private val _sourceError = MutableStateFlow<String?>(null)
    val sourceError: StateFlow<String?> = _sourceError

    private val _m3u8Fps = MutableStateFlow(0)
    val m3u8Fps: StateFlow<Int> = _m3u8Fps.asStateFlow()

    private val _m3u8Bitrate = MutableStateFlow(0)
    val m3u8Bitrate: StateFlow<Int> = _m3u8Bitrate.asStateFlow()

    private val _isRestreaming = MutableStateFlow(false)
    val isRestreaming: StateFlow<Boolean> = _isRestreaming.asStateFlow()

    private val _diagnosticMessage = MutableStateFlow("Ready")
    val diagnosticMessage: StateFlow<String> = _diagnosticMessage.asStateFlow()

    private val _diagnosticIsError = MutableStateFlow(false)
    val diagnosticIsError: StateFlow<Boolean> = _diagnosticIsError.asStateFlow()

    private var currentM3u8Url: String? = null
    private var customHeaders: Map<String, String> = emptyMap()
    private var ffmpegSession: FFmpegSession? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var statsJob: Job? = null

    fun setupRestream(
        url: String,
        headers: Map<String, String> = emptyMap()
    ) {
        this.currentM3u8Url = url
        this.customHeaders = headers
        if (Looper.myLooper() == Looper.getMainLooper()) {
            initializePlayer()
        } else {
            Handler(Looper.getMainLooper()).post {
                initializePlayer()
            }
        }
    }

    private fun initializePlayer() {
        releasePlayer()

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("LiveStreamPro/1.0")
            .setDefaultRequestProperties(customHeaders)
            .setAllowCrossProtocolRedirects(true)

        val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(Uri.parse(currentM3u8Url)))

        exoPlayer = ExoPlayer.Builder(context).build().apply {
            setMediaSource(mediaSource)
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
            
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> _isSourceLoading.value = true
                        Player.STATE_READY -> {
                            _isSourceLoading.value = false
                            _sourceError.value = null
                        }
                        Player.STATE_ENDED -> restartStream()
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e("M3u8Engine", "Player error: ${error.message}")
                    _sourceError.value = error.message
                    _isSourceLoading.value = false
                    recoverFromError()
                }
            })

            addAnalyticsListener(object : AnalyticsListener {
                override fun onVideoFrameProcessingOffset(
                    eventTime: AnalyticsListener.EventTime,
                    totalProcessingOffsetUs: Long,
                    frameCount: Int
                ) {
                    if (frameCount > 0 && _m3u8Fps.value == 0) {
                        _m3u8Fps.value = 30
                    }
                }

                override fun onRenderedFirstFrame(
                    eventTime: AnalyticsListener.EventTime,
                    output: Any,
                    renderTimeMs: Long
                ) {
                    if (_m3u8Fps.value == 0) {
                        _m3u8Fps.value = 30
                    }
                }

                override fun onVideoDisabled(
                    eventTime: AnalyticsListener.EventTime,
                    decoderCounters: DecoderCounters
                ) {
                    if (!_isRestreaming.value) {
                        _m3u8Fps.value = 0
                    }
                }
            })

            prepare()
        }
    }

    private fun startStatsMonitor(config: StreamConfig) {
        statsJob?.cancel()
        statsJob = scope.launch(Dispatchers.Main) {
            var lastRenderedCount = 0
            while (isActive) {
                delay(1000)
                val player = exoPlayer
                if (player != null && (player.isPlaying || _isRestreaming.value)) {
                    val counters = player.videoDecoderCounters
                    val currentRendered = counters?.renderedOutputBufferCount ?: 0
                    val delta = currentRendered - lastRenderedCount
                    lastRenderedCount = currentRendered

                    val formatFps = player.videoFormat?.frameRate?.toInt() ?: 0
                    val realFps = when {
                        delta in 1..120 -> delta
                        formatFps > 0 -> formatFps
                        else -> 30
                    }

                    val vBitrate = player.videoFormat?.bitrate ?: 0
                    val aBitrate = player.audioFormat?.bitrate ?: 0
                    val streamBitrateKbps = if (vBitrate > 0) {
                        ((vBitrate + aBitrate) / 1000).coerceAtLeast(2500)
                    } else {
                        (config.videoQuality.defaultBitrate / 1000).coerceAtLeast(2500)
                    }

                    _m3u8Fps.value = realFps
                    _m3u8Bitrate.value = streamBitrateKbps
                    
                    if (_isRestreaming.value) {
                        streamManager.updateStats(streamBitrateKbps, realFps)
                    }
                } else if (!_isRestreaming.value) {
                    _m3u8Fps.value = 0
                    _m3u8Bitrate.value = 0
                }
            }
        }
    }

    fun startRestreaming(
        config: StreamConfig,
        primaryUrl: String,
        allUrls: List<String> = emptyList(),
        hasOverlay: Boolean = false,
        preferStreamCopy: Boolean = true,
        canStreamCopyOverride: Boolean? = null
    ) {
        val m3u8 = currentM3u8Url
        if (m3u8.isNullOrBlank()) {
            Log.e("M3u8Engine", "Cannot start restreaming: M3U8 URL is empty")
            return
        }

        _isRestreaming.value = true
        _diagnosticMessage.value = "Connecting to RTMP..."
        _diagnosticIsError.value = false
        streamManager.setStreamingStatus(StreamingStatus.STREAMING)

        play()
        startStatsMonitor(config)

        val targets = if (allUrls.isNotEmpty()) allUrls else listOf(primaryUrl)
        val outArgs = if (targets.size == 1) {
            "-f flv \"${targets[0]}\""
        } else {
            val teeList = targets.joinToString("|") { "[f=flv:onfail=ignore]$it" }
            "-f tee -map 0:v -map 0:a? \"$teeList\""
        }

        val fps = if (config.videoQuality.fps > 0) config.videoQuality.fps else 30
        val bitrateKbps = (config.videoQuality.defaultBitrate / 1000).coerceAtLeast(2500)
        val gop = fps * 2

        // Check if stream is H.264 compatible without accessing exoPlayer on background threads
        val canStreamCopy = canStreamCopyOverride ?: run {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                (preferStreamCopy && isH264Source() && !hasOverlay)
            } else {
                false
            }
        }

        val headerPrefix = if (customHeaders.isNotEmpty()) {
            val hLines = customHeaders.entries.joinToString("\r\n") { "${it.key}: ${it.value}" } + "\r\n"
            "-headers \"$hLines\" "
        } else ""

        val vCodecArgs = if (canStreamCopy) {
            "-c:v copy"
        } else {
            "-c:v libx264 -preset ultrafast -tune zerolatency -pix_fmt yuv420p -r $fps -g $gop -b:v ${bitrateKbps}k"
        }

        val cmd = "$headerPrefix-rw_timeout 15000000 -re -i \"$m3u8\" $vCodecArgs -c:a aac -b:a 128k -ar 44100 -map 0:v:0 -map 0:a:0? $outArgs"
        Log.i("M3u8Engine", "Starting native FFmpeg M3U8 restream (canStreamCopy=$canStreamCopy): $cmd")

        stopFfmpeg()
        executeFfmpegCommand(cmd, canStreamCopy, config, outArgs, headerPrefix, m3u8, fps, gop, bitrateKbps)
    }

    private fun executeFfmpegCommand(
        cmd: String,
        allowFallbackToTranscode: Boolean,
        config: StreamConfig,
        outArgs: String,
        headerPrefix: String,
        m3u8: String,
        fps: Int,
        gop: Int,
        bitrateKbps: Int
    ) {
        ffmpegSession = FFmpegKit.executeAsync(cmd, { session ->
            Log.i("M3u8Engine", "FFmpeg restream completed with state: ${session.state}, code: ${session.returnCode}")
            val returnCode = session.returnCode
            if (com.arthenica.ffmpegkit.ReturnCode.isCancel(returnCode)) {
                Log.i("M3u8Engine", "FFmpeg session was cancelled.")
                return@executeAsync
            }
            if (_isRestreaming.value && allowFallbackToTranscode && session.returnCode?.isValueSuccess != true) {
                Log.w("M3u8Engine", "Stream copy failed. Falling back to libx264 transcode...")
                _diagnosticMessage.value = "Transcoder Fallback • Handshake..."
                _diagnosticIsError.value = false
                val transcodeCmd = "$headerPrefix-rw_timeout 15000000 -re -i \"$m3u8\" -c:v libx264 -preset ultrafast -tune zerolatency -pix_fmt yuv420p -r $fps -g $gop -b:v ${bitrateKbps}k -c:a aac -b:a 128k -ar 44100 -map 0:v:0 -map 0:a:0? $outArgs"
                executeFfmpegCommand(transcodeCmd, false, config, outArgs, headerPrefix, m3u8, fps, gop, bitrateKbps)
            } else if (_isRestreaming.value && session.returnCode?.isValueSuccess != true) {
                val logs = session.allLogsAsString ?: ""
                val meaningfulError = logs.lineSequence()
                    .map { it.trim() }
                    .filter { line ->
                        line.isNotEmpty() &&
                        !line.startsWith("configuration:", true) &&
                        !line.startsWith("libav", true) &&
                        !line.startsWith("Hyper Fast", true) &&
                        !line.startsWith("built with", true) &&
                        !line.contains("frame=", true)
                    }
                    .lastOrNull() ?: "Session Closed"
                val cleanError = if (meaningfulError.length > 50) meaningfulError.take(50) + "..." else meaningfulError
                _diagnosticMessage.value = "RTMP Error: $cleanError"
                _diagnosticIsError.value = true
            }
        }, { log ->
            val msg = log.message ?: ""
            Log.d("M3u8Engine", "FFmpeg: $msg")
            when {
                msg.contains("Opening 'rtmp", ignoreCase = true) || msg.contains("Opening 'rtmps", ignoreCase = true) -> {
                    _diagnosticMessage.value = "Connecting to RTMP..."
                    _diagnosticIsError.value = false
                }
                msg.contains("Handshake successful", ignoreCase = true) || msg.contains("Metadata:", ignoreCase = true) || msg.contains("Stream mapping", ignoreCase = true) -> {
                    _diagnosticMessage.value = "Handshake OK • Publishing"
                    _diagnosticIsError.value = false
                }
                msg.contains("BadName", ignoreCase = true) || msg.contains("Authentication failed", ignoreCase = true) || msg.contains("Auth error", ignoreCase = true) -> {
                    _diagnosticMessage.value = "Auth Failed: Invalid Stream Key"
                    _diagnosticIsError.value = true
                }
                msg.contains("Connection refused", ignoreCase = true) || msg.contains("Cannot open connection", ignoreCase = true) || msg.contains("Connection reset", ignoreCase = true) -> {
                    _diagnosticMessage.value = "RTMP Error: Connection Refused"
                    _diagnosticIsError.value = true
                }
                msg.contains("Stream copy", ignoreCase = true) -> {
                    _diagnosticMessage.value = "Handshake OK • Stream Copy"
                    _diagnosticIsError.value = false
                }
            }
        }, { stats ->
            if (_isRestreaming.value) {
                val reportedFps = stats.videoFps.toInt()
                val rawBitrate = stats.bitrate
                val kbps = when {
                    rawBitrate > 50000 -> (rawBitrate / 1000.0).toInt()
                    rawBitrate > 0 -> rawBitrate.toInt()
                    else -> bitrateKbps
                }
                val finalFps = if (reportedFps > 0) reportedFps else if (_m3u8Fps.value > 0) _m3u8Fps.value else fps
                _m3u8Fps.value = finalFps
                _m3u8Bitrate.value = kbps
                _diagnosticMessage.value = "Streaming Video ($finalFps FPS)"
                _diagnosticIsError.value = false
                streamManager.updateStats(kbps, finalFps)
            }
        })
    }

    fun setPiPMode(isEnabled: Boolean) {
        surfaceFilter?.let { filter ->
            if (isEnabled) {
                filter.setScale(30f, 30f)
                filter.setPosition(65f, 5f)
            } else {
                filter.setScale(100f, 100f)
                filter.setPosition(0f, 0f)
            }
        }
    }

    private fun restartStream() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            exoPlayer?.seekTo(0)
            exoPlayer?.play()
        } else {
            Handler(Looper.getMainLooper()).post {
                exoPlayer?.seekTo(0)
                exoPlayer?.play()
            }
        }
    }

    private fun recoverFromError() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (exoPlayer == null && currentM3u8Url != null) {
                initializePlayer()
            }
        }, 5000)
    }

    fun play() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            exoPlayer?.play()
        } else {
            Handler(Looper.getMainLooper()).post {
                exoPlayer?.play()
            }
        }
    }

    fun pause() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            exoPlayer?.pause()
        } else {
            Handler(Looper.getMainLooper()).post {
                exoPlayer?.pause()
            }
        }
    }

    fun isPlaying(): Boolean = if (Looper.myLooper() == Looper.getMainLooper()) {
        exoPlayer?.isPlaying == true
    } else {
        false
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    fun setM3u8Volume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            exoPlayer?.volume = clamped
        } else {
            Handler(Looper.getMainLooper()).post {
                exoPlayer?.volume = clamped
            }
        }
    }

    fun isH264Source(): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return false
        }
        return try {
            val playerFormat = exoPlayer?.videoFormat
            val mime = playerFormat?.sampleMimeType
            mime?.let { it.contains("avc", ignoreCase = true) || it.contains("h264", ignoreCase = true) } ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun stopPlayer() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            releasePlayer()
        } else {
            Handler(Looper.getMainLooper()).post {
                releasePlayer()
            }
        }
    }

    fun stopFfmpeg() {
        try {
            ffmpegSession?.let { session ->
                FFmpegKit.cancel(session.sessionId)
            }
            ffmpegSession = null
        } catch (e: Exception) {
            Log.e("M3u8Engine", "Error stopping FFmpeg", e)
        }
    }

    fun stop() {
        _isRestreaming.value = false
        stopFfmpeg()
        statsJob?.cancel()
        _m3u8Fps.value = 0
        _m3u8Bitrate.value = 0
        _diagnosticMessage.value = "Ready"
        _diagnosticIsError.value = false
        streamManager.setStreamingStatus(StreamingStatus.IDLE)
        streamManager.updateStats(0, 0)
        stopPlayer()
        streamManager.clearFilters()
        surfaceFilter = null
    }

    private fun releasePlayer() {
        try {
            exoPlayer?.setVideoSurface(null)
            exoPlayer?.pause()
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            exoPlayer?.release()
        } catch (e: Exception) {
            Log.e("M3u8Engine", "Error releasing player", e)
        } finally {
            exoPlayer = null
            _isSourceLoading.value = false
            _sourceError.value = null
        }
    }
}
