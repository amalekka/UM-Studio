package com.example.core.streaming

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.example.core.model.StreamConfig
import com.pedro.encoder.input.gl.render.filters.`object`.SurfaceFilterRender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

    private var currentM3u8Url: String? = null
    private var customHeaders: Map<String, String> = emptyMap()

    fun setupRestream(
        url: String,
        headers: Map<String, String> = emptyMap()
    ) {
        this.currentM3u8Url = url
        this.customHeaders = headers
        initializePlayer()
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
            prepare()
        }
    }

    fun startRestreaming(config: StreamConfig, rtmpUrl: String) {
        if (streamManager.prepareStream(config)) {
            setupVideoBridge()
            streamManager.startStream(rtmpUrl)
        }
    }

    private fun setupVideoBridge() {
        surfaceFilter = SurfaceFilterRender { surfaceTexture ->
            if (exoPlayer != null) {
                val surface = Surface(surfaceTexture)
                exoPlayer?.setVideoSurface(surface)
                Log.d("M3u8Engine", "Surface created and linked to ExoPlayer")
            }
        }
        
        // Trigger refresh in BackgroundCanvasManager which will include this filter
        (context as? LiveStreamService)?.getBackgroundManager()?.refreshFilters()
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
        exoPlayer?.seekTo(0)
        exoPlayer?.play()
    }

    private fun recoverFromError() {
        android.os.Handler(context.mainLooper).postDelayed({
            if (exoPlayer == null && currentM3u8Url != null) {
                initializePlayer()
            }
        }, 5000)
    }

    fun setM3u8Volume(volume: Float) {
        exoPlayer?.volume = volume.coerceIn(0f, 1f)
    }

    fun stop() {
        releasePlayer()
        streamManager.clearFilters()
        surfaceFilter = null
    }

    private fun releasePlayer() {
        exoPlayer?.setVideoSurface(null)
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
    }
}
