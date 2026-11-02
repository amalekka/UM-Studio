package com.example.core.streaming

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.ui.MainViewModel
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.SurfaceFilterRender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URL

class BackgroundCanvasManager(
    private val service: LiveStreamService,
    private val streamManager: StreamEngineManager
) {
    private var backgroundImage: Bitmap? = null
    private var processedBackground: Bitmap? = null
    
    private var backgroundFilter: ImageObjectFilterRender? = null
    private var pauseShieldFilter: ImageObjectFilterRender? = null
    
    private var currentConfig = MainViewModel.BackgroundConfig()
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    fun updateConfig(config: MainViewModel.BackgroundConfig) {
        val oldConfig = currentConfig
        currentConfig = config
        
        if (config.imageUri != oldConfig.imageUri) {
            loadImage(config.imageUri)
        } else {
            if (config.blurRadius != oldConfig.blurRadius || config.brightness != oldConfig.brightness) {
                processAndApply()
            } else {
                applyFilterTransforms()
            }
        }
        
        if (config.isPauseShieldActive != oldConfig.isPauseShieldActive) {
            togglePauseShield(config.isPauseShieldActive)
        }
    }

    private fun loadImage(uri: String?) {
        if (uri == null) {
            backgroundImage = null
            processedBackground = null
            backgroundFilter = null
            refreshFilters()
            return
        }

        scope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    if (uri.startsWith("http")) {
                        val connection = URL(uri).openConnection()
                        connection.doInput = true
                        connection.connect()
                        val input: InputStream = connection.getInputStream()
                        BitmapFactory.decodeStream(input)
                    } else {
                        val input = service.contentResolver.openInputStream(Uri.parse(uri))
                        BitmapFactory.decodeStream(input)
                    }
                }
                if (currentConfig.imageUri == uri) { // Ensure still relevant
                    backgroundImage = bitmap
                    processAndApply()
                }
            } catch (e: Exception) {
                Log.e("BackgroundManager", "Failed to load image: ${e.message}")
            }
        }
    }

    private fun processAndApply() {
        val base = backgroundImage ?: return
        scope.launch {
            val processed = withContext(Dispatchers.Default) {
                applyEffects(base, currentConfig.blurRadius, currentConfig.brightness)
            }
            processedBackground = processed
            
            if (backgroundFilter == null) {
                backgroundFilter = ImageObjectFilterRender()
            }
            backgroundFilter?.setImage(processed)
            applyFilterTransforms()
            refreshFilters()
        }
    }

    private fun applyEffects(source: Bitmap, blurRadius: Float, brightness: Float): Bitmap {
        val matrix = android.graphics.ColorMatrix().apply {
            setScale(brightness, brightness, brightness, 1f)
        }
        val paint = android.graphics.Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
        }
        
        val result = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    private fun applyFilterTransforms() {
        backgroundFilter?.let { filter ->
            when (currentConfig.scaleMode) {
                MainViewModel.BackgroundScaleMode.COVER -> filter.setScale(100f, 100f)
                MainViewModel.BackgroundScaleMode.CONTAIN -> filter.setScale(80f, 80f)
                MainViewModel.BackgroundScaleMode.STRETCH -> filter.setScale(100f, 100f)
            }
            filter.setPosition(0f, 0f)
        }
        
        // Update M3U8 PiP Transform
        service.getRestreamEngine()?.surfaceFilter?.let { filter ->
            val scale = currentConfig.foregroundScale * 100f
            filter.setScale(scale, scale)
            
            when (currentConfig.foregroundPosition) {
                MainViewModel.ForegroundPosition.CENTER -> filter.setPosition(50f - scale/2f, 50f - scale/2f)
                MainViewModel.ForegroundPosition.TOP -> filter.setPosition(50f - scale/2f, 5f)
                MainViewModel.ForegroundPosition.BOTTOM -> filter.setPosition(50f - scale/2f, 95f - scale)
                MainViewModel.ForegroundPosition.FREE -> filter.setPosition(10f, 10f)
            }
        }
        
        refreshFilters()
    }

    private fun togglePauseShield(active: Boolean) {
        if (active) {
            if (pauseShieldFilter == null && processedBackground != null) {
                pauseShieldFilter = ImageObjectFilterRender().apply {
                    setImage(processedBackground!!)
                    setScale(100f, 100f)
                    setPosition(0f, 0f)
                }
            }
        } else {
            pauseShieldFilter = null
        }
        refreshFilters()
    }

    fun refreshFilters() {
        val filterList = mutableListOf<com.pedro.encoder.input.gl.render.filters.BaseFilterRender>()
        
        // 1. Background layer
        backgroundFilter?.let { filterList.add(it) }
        
        // 2. Main video layer (M3U8)
        service.getRestreamEngine()?.surfaceFilter?.let { filterList.add(it) }
        
        // 3. Pro Overlay layers (Watermark, Ticker)
        service.getProManager()?.let { pro ->
            pro.getWatermarkFilter()?.let { filterList.add(it) }
            pro.getTickerFilter()?.let { filterList.add(it) }
        }
        
        // 4. Pause shield (Topmost)
        pauseShieldFilter?.let { filterList.add(it) }
        
        if (filterList.isNotEmpty()) {
            streamManager.setFilters(filterList)
        } else {
            streamManager.clearFilters()
        }
    }
}
