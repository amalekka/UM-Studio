package com.example.core.model

enum class VideoCodec {
    H264,
    HEVC // H.265
}

data class StreamConfig(
    val title: String = "",
    val description: String = "",
    val videoQuality: VideoQuality = VideoQuality.HD_720P,
    val videoCodec: VideoCodec = VideoCodec.H264,
    val aspectRatio: AspectRatioMode = AspectRatioMode.LANDSCAPE_16_9,
    val endpoints: List<PlatformEndpoint> = emptyList(),
    val isRecordingEnabled: Boolean = false,
    val isAutoReconnectEnabled: Boolean = true
)

sealed class AspectRatioMode {
    data object LANDSCAPE_16_9 : AspectRatioMode()
    data object PORTRAIT_9_16 : AspectRatioMode()
    data object SQUARE_1_1 : AspectRatioMode()
    data object CLASSIC_4_3 : AspectRatioMode()
    data class Custom(val width: Int, val height: Int) : AspectRatioMode()

    fun getRatio(): Float = when (this) {
        LANDSCAPE_16_9 -> 16f / 9f
        PORTRAIT_9_16 -> 9f / 16f
        SQUARE_1_1 -> 1f
        CLASSIC_4_3 -> 4f / 3f
        is Custom -> width.toFloat() / height.toFloat()
    }
}

enum class VideoQuality(
    val width: Int,
    val height: Int,
    val defaultBitrate: Int, // in bps
    val fps: Int = 30
) {
    SD_480P(854, 480, 2_000_000),
    HD_720P(1280, 720, 4_500_000),
    FHD_1080P(1920, 1080, 8_000_000),
    QHD_1440P(2560, 1440, 15_000_000),
    UHD_4K(3840, 2160, 35_000_000);

    fun getBitrateForFps(targetFps: Int): Int {
        return (defaultBitrate * (targetFps.toFloat() / fps.toFloat())).toInt()
    }
}

sealed class PlatformEndpoint {
    abstract val name: String
    abstract val url: String
    abstract val streamKey: String
    abstract val isEnabled: Boolean

    data class YouTube(
        override val streamKey: String,
        override val isEnabled: Boolean = true
    ) : PlatformEndpoint() {
        override val name = "YouTube"
        override val url = "rtmp://a.rtmp.youtube.com/live2"
    }

    data class Facebook(
        override val streamKey: String,
        override val isEnabled: Boolean = true
    ) : PlatformEndpoint() {
        override val name = "Facebook"
        override val url = "rtmps://live-api-s.facebook.com:443/rtmp/"
    }

    data class Twitch(
        override val streamKey: String,
        override val isEnabled: Boolean = true
    ) : PlatformEndpoint() {
        override val name = "Twitch"
        override val url = "rtmp://ingest.twitch.tv/app/"
    }

    data class TikTok(
        override val streamKey: String,
        override val isEnabled: Boolean = true
    ) : PlatformEndpoint() {
        override val name = "TikTok"
        override val url = "rtmp://push-rtmp-f5.tiktokcdn.com/stage/"
    }

    data class Twitter(
        override val streamKey: String,
        override val isEnabled: Boolean = true
    ) : PlatformEndpoint() {
        override val name = "X (Twitter)"
        override val url = "rtmps://publish.twitter.com/live"
    }

    data class CustomRTMP(
        override val name: String,
        override val url: String,
        override val streamKey: String,
        override val isEnabled: Boolean = true
    ) : PlatformEndpoint()
}
