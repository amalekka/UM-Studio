package com.example.core.streaming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.pedro.common.ConnectChecker

class LiveStreamService : Service(), ConnectChecker {

    private val binder = LocalBinder()
    private var streamEngineManager: StreamEngineManager? = null
    private var m3u8RestreamEngine: M3u8RestreamEngine? = null
    private var proFeaturesManager: ProFeaturesManager? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    
    private var backgroundCanvasManager: BackgroundCanvasManager? = null
    
    private val notificationId = 1234
    private val channelId = "live_stream_channel"

    inner class LocalBinder : Binder() {
        fun getService(): LiveStreamService = this@LiveStreamService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        streamEngineManager = StreamEngineManager(this, this)
        m3u8RestreamEngine = M3u8RestreamEngine(this, streamEngineManager!!)
        proFeaturesManager = ProFeaturesManager(this, streamEngineManager!!)
        backgroundCanvasManager = BackgroundCanvasManager(this, streamEngineManager!!)
    }

    fun getBackgroundManager(): BackgroundCanvasManager? = backgroundCanvasManager

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "LiveStreamPro::StreamingWakeLock"
        ).apply {
            acquire()
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Ready to stream")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                notificationId, 
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(notificationId, notification)
        }
        return START_STICKY
    }

    /**
     * Upgrades the foreground service to include Media Projection type.
     * This must be called AFTER the user has granted projection permission.
     */
    fun upgradeToMediaProjection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val notification = createNotification("Screen Capture Active")
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun getStreamManager(): StreamEngineManager? = streamEngineManager
    fun getRestreamEngine(): M3u8RestreamEngine? = m3u8RestreamEngine
    fun getProManager(): ProFeaturesManager? = proFeaturesManager

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Live Stream Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, createNotification(content))
    }

    // ConnectChecker implementation
    override fun onConnectionStarted(url: String) {
        updateNotification("Connecting to stream...")
    }

    override fun onConnectionSuccess() {
        streamEngineManager?.onConnectionSuccess()
        updateNotification("Streaming Live")
    }

    override fun onConnectionFailed(reason: String) {
        streamEngineManager?.onConnectionFailed(reason)
        updateNotification("Connection Failed: $reason")
    }

    override fun onNewBitrate(bitrate: Long) {
        streamEngineManager?.onNewBitrate(bitrate)
    }

    override fun onDisconnect() {
        streamEngineManager?.onDisconnect()
        updateNotification("Disconnected")
    }

    override fun onAuthError() {
        streamEngineManager?.onConnectionFailed("Auth Error")
        updateNotification("Auth Error")
    }

    override fun onAuthSuccess() {
        // Handled internally
    }

    override fun onDestroy() {
        m3u8RestreamEngine?.stop()
        proFeaturesManager?.stopAll()
        streamEngineManager?.release()
        releaseWakeLock()
        super.onDestroy()
    }
}
