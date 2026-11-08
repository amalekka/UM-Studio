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

    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 3
    private var reconnectRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    private var currentM3u8Url: String? = null
    private var customHeaders: Map<String, String> = emptyMap()
    private var ffmpegSession: FFmpegSession? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var statsJob: Job? = null

    fun setupRestream(
        url: String,
        headers: Map<String, String> = emptyMap()
    ) {
        val clean = url.trim().removeSurrounding("\"").removeSurrounding("'")
        if (clean.isBlank()) {
            Log.e("M3u8Engine", "Cannot setup restream: URL is blank")
            return
        }
        val isSameUrl = (this.currentM3u8Url == clean)
        this.currentM3u8Url = clean
        this.customHeaders = headers

        val task = {
            try {
                if (isSameUrl && exoPlayer != null) {
                    exoPlayer?.play()
                } else {
                    initializePlayer()
                }
            } catch (e: Throwable) {
                Log.e("M3u8Engine", "Error setting up player: ${e.message}", e)
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            task()
        } else {
            Handler(Looper.getMainLooper()).post {
                task()
            }
        }
    }

    private fun initializePlayer() {
        val url = currentM3u8Url
        if (url.isNullOrBlank()) return

        try {
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .setDefaultRequestProperties(customHeaders)
                .setAllowCrossProtocolRedirects(true)

            val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(Uri.parse(url)))

            if (exoPlayer == null) {
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
            } else {
                exoPlayer?.let { player ->
                    player.setMediaSource(mediaSource)
                    player.prepare()
                    player.playWhenReady = true
                }
            }
        } catch (e: Throwable) {
            Log.e("M3u8Engine", "Failed to initialize player: ${e.message}", e)
            _sourceError.value = e.message
            _isSourceLoading.value = false
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
                        if (!_diagnosticIsError.value && _diagnosticMessage.value == "Connecting to RTMP...") {
                            _diagnosticMessage.value = "Streaming Video ($realFps FPS)"
                        }
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
        try {
            val rawM3u8 = currentM3u8Url
            val cleanUrl = (rawM3u8 ?: "").trim().removeSurrounding("\"").removeSurrounding("'")
            if (cleanUrl.isEmpty()) {
                Log.e("M3u8Engine", "Cannot start restreaming: M3U8 URL is empty or null")
                _diagnosticMessage.value = "Invalid M3U8 URL"
                _diagnosticIsError.value = true
                return
            }

            _isRestreaming.value = true
            _diagnosticMessage.value = "Connecting to RTMP..."
            _diagnosticIsError.value = false
            streamManager.setStreamingStatus(StreamingStatus.STREAMING)

            try { play() } catch (e: Throwable) {}
            try { startStatsMonitor(config) } catch (e: Throwable) {}
            
            this.lastConfig = config
            this.lastPrimaryUrl = primaryUrl
            this.lastAllUrls = allUrls

            val rtmpTargets = if (allUrls.isNotEmpty()) allUrls else listOf(primaryUrl)
            val flvFlags = "no_duration_filesize"
            val rtmpFlags = "-rtmp_buffer 1000 -rtmp_live live"
            
            val outArgs = if (!isRecording || recordPath == null) {
                if (rtmpTargets.size == 1) {
                    val url = rtmpTargets[0].trim().removeSurrounding("\"").removeSurrounding("'")
                    "$rtmpFlags -flvflags $flvFlags -f flv \"$url\""
                } else {
                    val teeList = rtmpTargets.joinToString("|") { 
                        val url = it.trim().removeSurrounding("\"").removeSurrounding("'")
                        "[f=flv:flvflags=$flvFlags:onfail=ignore]$url"
                    }
                    "-f tee -map 0:v -map 0:a? \"$teeList\""
                }
            } else {
                // Recording + RTMP(s)
                val outputs = mutableListOf<String>()
                rtmpTargets.forEach { 
                    val url = it.trim().removeSurrounding("\"").removeSurrounding("'")
                    outputs.add("[f=flv:flvflags=$flvFlags:onfail=ignore]$url")
                }
                outputs.add("[f=mp4]\"$recordPath\"")
                "-f tee -map 0:v -map 0:a? \"${outputs.joinToString("|")}\""
            }

            val fps = if (config.targetFps > 0) config.targetFps else if (config.videoQuality.fps > 0) config.videoQuality.fps else 30
            val bitrateKbps = (if (config.videoBitrateKbps > 0) config.videoBitrateKbps else (config.videoQuality.defaultBitrate / 1000)).coerceIn(1500, 6000)
            val gop = fps * (if (config.keyframeIntervalSec > 0) config.keyframeIntervalSec else 2)
            val gopMin = fps

            val hasAudio = hasAudioTrack()
            // Check if stream is H.264 compatible and has audio without accessing exoPlayer on background threads
            val canStreamCopy = canStreamCopyOverride ?: run {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    (preferStreamCopy && isH264Source() && !hasOverlay && hasAudio)
                } else {
                    false
                }
            }

            val headerPrefix = if (customHeaders.isNotEmpty()) {
                val hLines = customHeaders.entries.joinToString("\r\n") { "${it.key}: ${it.value}" } + "\r\n"
                "-headers \"$hLines\" "
            } else ""

            val encoderCodec = if (config.encoderEngine == com.example.core.model.EncoderEngine.HARDWARE_MEDIACODEC) "h264_mediacodec" else "libx264"
            val vCodecArgs = if (canStreamCopy) {
                "-c:v copy"
            } else {
                "-rtmp_live live -flvflags no_duration_filesize -pix_fmt yuv420p -r $fps -g $gop -keyint_min $gopMin -c:v $encoderCodec -preset ultrafast -tune zerolatency -profile:v main -b:v ${bitrateKbps}k -minrate ${bitrateKbps}k -maxrate ${bitrateKbps}k -bufsize ${bitrateKbps * 2}k"
            }

            val inputArgs = "-user_agent \"LiveStreamPro/1.0\" -probesize 2M -analyzeduration 2M -fflags +nobuffer+genpts+discardcorrupt+flush_packets -flags +low_delay -timeout 30000000 -rw_timeout 30000000 -reconnect 1 -reconnect_at_eof 1 -reconnect_streamed 1 -reconnect_delay_max 5 -re"
            val cmd = if (hasAudio) {
                "$headerPrefix-protocol_whitelist file,http,https,tcp,tls,rtmp,rtmps,crypto,pipe $inputArgs -i \"$cleanUrl\" $vCodecArgs -af aresample=async=1 -c:a aac -b:a 128k -ar 44100 -ac 2 $outArgs"
            } else {
                "$headerPrefix-protocol_whitelist file,http,https,tcp,tls,rtmp,rtmps,crypto,pipe $inputArgs -i \"$cleanUrl\" -f lavfi -i anullsrc=channel_layout=stereo:sample_rate=44100 $vCodecArgs -map 0:v:0 -map 1:a:0 -c:a aac -b:a 128k -ar 44100 -ac 2 -shortest $outArgs"
            }
            Log.i("M3u8Engine", "Starting native FFmpeg M3U8 restream (canStreamCopy=$canStreamCopy, hasAudio=$hasAudio): $cmd")

            try { stopFfmpeg() } catch (e: Throwable) {}
            executeFfmpegCommand(cmd, canStreamCopy, config, outArgs, headerPrefix, cleanUrl, fps, gop, bitrateKbps)
        } catch (e: Throwable) {
            Log.e("M3u8Engine", "Fatal error in startRestreaming", e)
            _diagnosticMessage.value = "Restream Error: ${e.message}"
            _diagnosticIsError.value = true
        }
    }

    fun hasAudioTrack(): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return true
        }
        return try {
            val tracks = exoPlayer?.currentTracks
            if (tracks != null) {
                tracks.groups.any { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }
            } else {
                exoPlayer?.audioFormat != null
            }
        } catch (e: Exception) {
            true
        }
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
        try {
            ffmpegSession = FFmpegKit.executeAsync(cmd, { session ->
                try {
                    Log.i("M3u8Engine", "FFmpeg restream completed with state: ${session.state}, code: ${session.returnCode}")
                    val returnCode = session.returnCode
                    if (com.arthenica.ffmpegkit.ReturnCode.isCancel(returnCode)) {
                        Log.i("M3u8Engine", "FFmpeg session was cancelled.")
                        reconnectRunnable?.let { handler.removeCallbacks(it) }
                        return@executeAsync
                    }

                    if (_isRestreaming.value && session.returnCode?.isValueSuccess != true) {
                        val logs = session.allLogsAsString ?: ""
                        
                        // 1. Handle HTTPS fallback if needed
                        if (logs.contains("Did you mean file:https", ignoreCase = true) && m3u8.startsWith("https://")) {
                            Log.w("M3u8Engine", "HTTPS protocol failed. Retrying with HTTP fallback...")
                            _diagnosticMessage.value = "Protocol Fallback • Connecting..."
                            _diagnosticIsError.value = false
                            val httpUrl = m3u8.replaceFirst("https://", "http://")
                            val vCodecArgs = "-rtmp_live live -flvflags no_duration_filesize -pix_fmt yuv420p -r $fps -g $gop -keyint_min $fps -c:v libx264 -preset ultrafast -tune zerolatency -profile:v main -b:v ${bitrateKbps}k -maxrate ${bitrateKbps}k -bufsize ${bitrateKbps * 2}k"
                            val inputArgs = "-user_agent \"LiveStreamPro/1.0\" -probesize 2M -analyzeduration 2M -fflags +nobuffer+genpts+flush_packets -flags +low_delay -timeout 30000000 -rw_timeout 30000000 -reconnect 1 -reconnect_at_eof 1 -reconnect_streamed 1 -reconnect_delay_max 5 -re"
                            val httpCmd = "$headerPrefix-protocol_whitelist file,http,https,tcp,tls,rtmp,rtmps,crypto,pipe $inputArgs -i \"$httpUrl\" $vCodecArgs -af aresample=async=1 -c:a aac -b:a 128k -ar 44100 -ac 2 $outArgs"
                            executeFfmpegCommand(httpCmd, false, config, outArgs, headerPrefix, httpUrl, fps, gop, bitrateKbps)
                            return@executeAsync
                        }

                        // 2. Handle Stream Copy failure fallback
                        if (allowFallbackToTranscode) {
                            Log.w("M3u8Engine", "Stream copy failed. Falling back to libx264 transcode...")
                            _diagnosticMessage.value = "Transcoder Fallback • Handshake..."
                            _diagnosticIsError.value = false
                            val inputArgs = "-user_agent \"LiveStreamPro/1.0\" -probesize 2M -analyzeduration 2M -fflags +nobuffer+genpts+flush_packets -flags +low_delay -timeout 30000000 -rw_timeout 30000000 -reconnect 1 -reconnect_at_eof 1 -reconnect_streamed 1 -reconnect_delay_max 5 -re"
                            val transcodeCmd = "$headerPrefix-protocol_whitelist file,http,https,tcp,tls,rtmp,rtmps,crypto,pipe $inputArgs -i \"$m3u8\" -rtmp_live live -flvflags no_duration_filesize -pix_fmt yuv420p -r $fps -g $gop -keyint_min $fps -c:v libx264 -preset ultrafast -tune zerolatency -profile:v main -b:v ${bitrateKbps}k -maxrate ${bitrateKbps}k -bufsize ${bitrateKbps * 2}k -af aresample=async=1 -c:a aac -b:a 128k -ar 44100 -ac 2 $outArgs"
                            executeFfmpegCommand(transcodeCmd, false, config, outArgs, headerPrefix, m3u8, fps, gop, bitrateKbps)
                            return@executeAsync
                        }

                        // 3. Robust Auto-Restart on unexpected disconnection with 2-second backoff
                        val rawError = logs.lineSequence()
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
                        
                        val redactedError = rawError
                            .replace(Regex("rtmp[s]?://[^ ]+"), "[RTMP URL]")
                            .replace(Regex("http[s]?://[^ ]+"), "[M3U8 URL]")
                            .removePrefix("RTMP Error:")
                            .trim()
                            
                        val finalError = if (redactedError.length > 70) redactedError.take(70) + "..." else redactedError

                        if (reconnectAttempts < maxReconnectAttempts) {
                            reconnectAttempts++
                            Log.e("M3u8Engine", "Stream disconnected ($finalError). Retry $reconnectAttempts/$maxReconnectAttempts in 2s...")
                            _diagnosticMessage.value = "Reconnecting ($reconnectAttempts/$maxReconnectAttempts) in 2s..."
                            _diagnosticIsError.value = false
                            streamManager.setStreamingStatus(StreamingStatus.RECONNECTING)
                            
                            reconnectRunnable?.let { handler.removeCallbacks(it) }
                            reconnectRunnable = Runnable {
                                if (_isRestreaming.value) {
                                    Log.i("M3u8Engine", "Executing auto-restart attempt $reconnectAttempts...")
                                    executeFfmpegCommand(cmd, allowFallbackToTranscode, config, outArgs, headerPrefix, m3u8, fps, gop, bitrateKbps)
                                }
                            }
                            handler.postDelayed(reconnectRunnable!!, 2000)
                        } else {
                            Log.e("M3u8Engine", "Max reconnect attempts reached. Stopping.")
                            _diagnosticMessage.value = "Connection Lost: $finalError"
                            _diagnosticIsError.value = true
                            _isRestreaming.value = false
                            streamManager.setStreamingStatus(StreamingStatus.ERROR)
                        }
                    }
                } catch (e: Throwable) {
                    Log.e("M3u8Engine", "Error in FFmpeg completion callback", e)
                }
            }, { log ->
            val msg = log.message ?: ""
            Log.d("M3u8Engine", "FFmpeg: $msg")
            when {
                msg.contains("Opening 'rtmp", ignoreCase = true) || msg.contains("Opening 'rtmps", ignoreCase = true) -> {
                    if (!_diagnosticIsError.value) {
                        _diagnosticMessage.value = "Connecting to RTMP..."
                    }
                }
                msg.contains("Output #0", ignoreCase = true) ||
                msg.contains("Press [q] to stop", ignoreCase = true) ||
                msg.contains("Stream mapping", ignoreCase = true) ||
                msg.contains("frame=", ignoreCase = true) ||
                msg.contains("fps=", ignoreCase = true) ||
                msg.contains("Handshake successful", ignoreCase = true) ||
                msg.contains("Metadata:", ignoreCase = true) ||
                msg.contains("speed=", ignoreCase = true) ||
                msg.contains("bitrate=", ignoreCase = true) -> {
                    _diagnosticMessage.value = "Handshake OK • Publishing"
                    _diagnosticIsError.value = false
                    reconnectAttempts = 0
                    streamManager.setStreamingStatus(StreamingStatus.STREAMING)
                }
                msg.contains("BadName", ignoreCase = true) ||
                msg.contains("Authentication failed", ignoreCase = true) ||
                msg.contains("Auth error", ignoreCase = true) ||
                msg.contains("Invalid stream", ignoreCase = true) ||
                msg.contains("Access denied", ignoreCase = true) -> {
                    _diagnosticMessage.value = "Auth Failed: Invalid Stream Key"
                    _diagnosticIsError.value = true
                }
                msg.contains("Connection refused", ignoreCase = true) ||
                msg.contains("Cannot open connection", ignoreCase = true) ||
                msg.contains("Connection reset", ignoreCase = true) ||
                msg.contains("Server error", ignoreCase = true) ||
                msg.contains("Failed to connect", ignoreCase = true) -> {
                    _diagnosticMessage.value = "RTMP Error: Connection Refused"
                    _diagnosticIsError.value = true
                }
                msg.contains("Stream copy", ignoreCase = true) -> {
                    _diagnosticMessage.value = "Handshake OK • Stream Copy"
                    _diagnosticIsError.value = false
                    reconnectAttempts = 0
                    streamManager.setStreamingStatus(StreamingStatus.STREAMING)
                }
            }
        }, { stats ->
            if (_isRestreaming.value) {
                val reportedFps = stats.videoFps.toInt()
                val rawBitrate = stats.bitrate
                val kbps = when {
                    rawBitrate > 50000 -> (rawBitrate / 1000.0).toInt().coerceIn(1500, 4500)
                    rawBitrate > 0 -> rawBitrate.toInt().coerceIn(1500, 4500)
                    else -> bitrateKbps
                }
                val finalFps = if (reportedFps > 0) reportedFps else if (_m3u8Fps.value > 0) _m3u8Fps.value else fps
                _m3u8Fps.value = finalFps
                _m3u8Bitrate.value = kbps
                
                if (!_diagnosticIsError.value) {
                    _diagnosticMessage.value = "Streaming Video ($finalFps FPS)"
                }
                streamManager.updateStats(kbps, finalFps)
            }
        })
        } catch (e: Throwable) {
            Log.e("M3u8Engine", "Fatal error executing FFmpeg command", e)
            val errMsg = if (e is NoClassDefFoundError || e is ExceptionInInitializerError || e.message?.contains("FFmpegKitConfig") == true) {
                "Engine Init Error: Try Restarting App"
            } else {
                "FFmpeg Start Error: ${e.message}"
            }
            _diagnosticMessage.value = errMsg
            _diagnosticIsError.value = true
        }
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

    data class StreamHealth(
        val resolution: String = "Probing...",
        val codec: String = "Probing...",
        val audioTrack: String = "Probing...",
        val isReady: Boolean = false,
        val isPlaying: Boolean = false
    )

    fun getStreamHealth(): StreamHealth {
        val player = exoPlayer ?: return StreamHealth()
        return try {
            val vf = player.videoFormat
            val af = player.audioFormat
            val res = if (vf != null && vf.width > 0 && vf.height > 0) {
                "${vf.width}x${vf.height} (${if (vf.height >= 1080) "1080p FHD" else if (vf.height >= 720) "720p HD" else "${vf.height}p"})"
            } else if (player.playbackState == Player.STATE_READY) "Standard Stream" else "Probing..."

            val vCodec = if (vf != null) {
                val mime = vf.sampleMimeType ?: ""
                when {
                    mime.contains("hevc", ignoreCase = true) || mime.contains("h265", ignoreCase = true) -> "HEVC (H.265)"
                    mime.contains("avc", ignoreCase = true) || mime.contains("h264", ignoreCase = true) -> "H.264 (AVC)"
                    mime.contains("av1", ignoreCase = true) -> "AV1"
                    mime.isNotBlank() -> mime.substringAfterLast('/')
                    else -> "H.264"
                }
            } else if (player.playbackState == Player.STATE_READY) "H.264" else "Probing..."

            val aStatus = if (af != null) {
                val ch = if (af.channelCount > 1) "Stereo (${af.channelCount}ch)" else "Mono (1ch)"
                val sr = if (af.sampleRate > 0) "${af.sampleRate / 1000}kHz" else "44.1kHz"
                "AAC • $ch • $sr"
            } else if (player.playbackState == Player.STATE_READY) "No Audio / Silent" else "Probing audio..."

            StreamHealth(
                resolution = res,
                codec = vCodec,
                audioTrack = aStatus,
                isReady = player.playbackState == Player.STATE_READY,
                isPlaying = player.isPlaying
            )
        } catch (e: Exception) {
            StreamHealth()
        }
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

    private var isRecording = false
    private var recordPath: String? = null
    private var lastConfig: StreamConfig? = null
    private var lastPrimaryUrl: String? = null
    private var lastAllUrls: List<String> = emptyList()

    fun startRecord(filePath: String): Boolean {
        this.recordPath = filePath
        this.isRecording = true
        
        if (_isRestreaming.value && lastConfig != null) {
            // Restart with recording output
            startRestreaming(
                lastConfig!!,
                lastPrimaryUrl ?: "",
                lastAllUrls
            )
            return true
        } else {
            // Not restreaming, just mark it so next time it starts with recording
            // Or start a dedicated recording session?
            // For now, let's just mark it.
            return true
        }
    }

    fun stopRecord() {
        this.isRecording = false
        this.recordPath = null
        if (_isRestreaming.value && lastConfig != null) {
            // Restart without recording output
            startRestreaming(
                lastConfig!!,
                lastPrimaryUrl ?: "",
                lastAllUrls
            )
        }
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
