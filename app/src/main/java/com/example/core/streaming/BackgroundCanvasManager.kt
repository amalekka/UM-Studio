package com.example.core.streaming

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.ui.MainViewModel
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
            if (config.blurRadius != oldConfig.blurRadius ||
                config.brightness != oldConfig.brightness ||
                config.opacity != oldConfig.opacity
            ) {
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
                    when {
                        uri.startsWith("http://") || uri.startsWith("https://") -> {
                            val connection = URL(uri).openConnection()
                            connection.connectTimeout = 6000
                            connection.readTimeout = 6000
                            connection.getInputStream().use { BitmapFactory.decodeStream(it) }
                        }
                        uri.startsWith("/") -> {
                            val file = File(uri)
                            if (file.exists()) BitmapFactory.decodeFile(uri) else null
                        }
                        else -> {
                            service.contentResolver.openInputStream(Uri.parse(uri))?.use {
                                BitmapFactory.decodeStream(it)
                            }
                        }
                    }
                }
                if (currentConfig.imageUri == uri && bitmap != null) {
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
                applyEffects(base, currentConfig.blurRadius, currentConfig.brightness, currentConfig.opacity)
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

    private fun applyEffects(source: Bitmap, blurRadius: Float, brightness: Float, opacity: Float): Bitmap {
        var workingBitmap = source
        val blurInt = blurRadius.toInt().coerceIn(0, 30)
        if (blurInt > 0) {
            workingBitmap = fastBlur(workingBitmap, blurInt)
        }

        val matrix = android.graphics.ColorMatrix().apply {
            setScale(brightness, brightness, brightness, opacity)
        }
        val paint = android.graphics.Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
            alpha = (opacity * 255).toInt().coerceIn(0, 255)
        }
        
        val result = Bitmap.createBitmap(workingBitmap.width, workingBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        canvas.drawBitmap(workingBitmap, 0f, 0f, paint)
        return result
    }

    private fun fastBlur(sentBitmap: Bitmap, radius: Int): Bitmap {
        if (radius < 1) return sentBitmap
        val w = sentBitmap.width
        val h = sentBitmap.height
        val scale = if (w > 640 || h > 640) 0.5f else 1.0f
        val scaledW = (w * scale).toInt().coerceAtLeast(1)
        val scaledH = (h * scale).toInt().coerceAtLeast(1)
        val input = Bitmap.createScaledBitmap(sentBitmap, scaledW, scaledH, true)

        val pix = IntArray(scaledW * scaledH)
        input.getPixels(pix, 0, scaledW, 0, 0, scaledW, scaledH)

        val wm = scaledW - 1
        val hm = scaledH - 1
        val wh = scaledW * scaledH
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var p: Int
        val vmin = IntArray(maxOf(scaledW, scaledH))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (idx in 0 until 256 * divsum) {
            dv[idx] = idx / divsum
        }

        var yw = 0
        var yi = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int; var goutsum: Int; var boutsum: Int
        var rinsum: Int; var ginsum: Int; var binsum: Int
        var rsum: Int; var gsum: Int; var bsum: Int

        for (curY in 0 until scaledH) {
            rinsum = 0; ginsum = 0; binsum = 0
            routsum = 0; goutsum = 0; boutsum = 0
            rsum = 0; gsum = 0; bsum = 0
            for (curI in -radius..radius) {
                p = pix[yi + minOf(wm, maxOf(curI, 0))]
                sir = stack[curI + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = (p and 0x0000ff)
                rbs = r1 - kotlin.math.abs(curI)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (curI > 0) {
                    rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                } else {
                    routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
                }
            }
            stackpointer = radius

            for (curX in 0 until scaledW) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum; gsum -= goutsum; bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]

                if (curY == 0) {
                    vmin[curX] = minOf(curX + radius + 1, wm)
                }
                p = pix[yw + vmin[curX]]

                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = (p and 0x0000ff)

                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]

                rsum += rinsum; gsum += ginsum; bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]

                yi++
            }
            yw += scaledW
        }

        for (curX in 0 until scaledW) {
            rinsum = 0; ginsum = 0; binsum = 0
            routsum = 0; goutsum = 0; boutsum = 0
            rsum = 0; gsum = 0; bsum = 0
            var yp = -radius * scaledW
            for (curI in -radius..radius) {
                yi = maxOf(0, yp) + curX
                sir = stack[curI + radius]
                sir[0] = r[yi]; sir[1] = g[yi]; sir[2] = b[yi]
                rbs = r1 - kotlin.math.abs(curI)
                rsum += r[yi] * rbs; gsum += g[yi] * rbs; bsum += b[yi] * rbs
                if (curI > 0) {
                    rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                } else {
                    routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
                }
                if (curI < hm) {
                    yp += scaledW
                }
            }
            yi = curX
            stackpointer = radius
            for (curY in 0 until scaledH) {
                pix[yi] = (0xff000000.toInt() and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]

                if (curX == 0) {
                    vmin[curY] = minOf(curY + r1, hm) * scaledW
                }
                p = curX + vmin[curY]

                sir[0] = r[p]; sir[1] = g[p]; sir[2] = b[p]

                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]

                rsum += rinsum; gsum += ginsum; bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]

                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]

                yi += scaledW
            }
        }
        input.setPixels(pix, 0, scaledW, 0, 0, scaledW, scaledH)
        return Bitmap.createScaledBitmap(input, w, h, true)
    }

    private fun applyFilterTransforms() {
        backgroundFilter?.let { filter ->
            val zoom = currentConfig.zoom.coerceIn(0.5f, 2.0f)
            val offX = currentConfig.offsetX
            val offY = currentConfig.offsetY

            when (currentConfig.scaleMode) {
                MainViewModel.BackgroundScaleMode.COVER -> {
                    filter.setScale(100f * zoom, 100f * zoom)
                    filter.setPosition(offX, offY)
                }
                MainViewModel.BackgroundScaleMode.CONTAIN -> {
                    filter.setScale(80f * zoom, 80f * zoom)
                    filter.setPosition(10f + offX, 10f + offY)
                }
                MainViewModel.BackgroundScaleMode.TOP_BANNER -> {
                    filter.setScale(100f * zoom, 28f * zoom)
                    filter.setPosition(offX, offY)
                }
                MainViewModel.BackgroundScaleMode.BOTTOM_BANNER -> {
                    filter.setScale(100f * zoom, 28f * zoom)
                    filter.setPosition(offX, 72f + offY)
                }
                MainViewModel.BackgroundScaleMode.STRETCH -> {
                    filter.setScale(100f * zoom, 100f * zoom)
                    filter.setPosition(offX, offY)
                }
            }
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
