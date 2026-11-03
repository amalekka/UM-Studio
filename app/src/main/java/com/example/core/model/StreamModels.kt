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
    abstract val id: String
    abstract val name: String
    abstract val url: String
    abstract val streamKey: String
    abstract val isEnabled: Boolean
    abstract val viewerCount: Int
    open val defaultUrl: String get() = url
    open val isCustom: Boolean get() = false

    data class YouTube(
        override val streamKey: String = "",
        override val isEnabled: Boolean = false,
        override val url: String = "rtmp://a.rtmp.youtube.com/live2",
        override val viewerCount: Int = 0
    ) : PlatformEndpoint() {
        override val id = "youtube"
        override val name = "YouTube Live"
        override val defaultUrl = "rtmp://a.rtmp.youtube.com/live2"
    }

    data class Facebook(
        override val streamKey: String = "",
        override val isEnabled: Boolean = false,
        override val url: String = "rtmps://live-api-s.facebook.com:443/rtmp/",
        override val viewerCount: Int = 0
    ) : PlatformEndpoint() {
        override val id = "facebook"
        override val name = "Facebook Live"
        override val defaultUrl = "rtmps://live-api-s.facebook.com:443/rtmp/"
    }

    data class TikTok(
        override val streamKey: String = "",
        override val isEnabled: Boolean = false,
        override val url: String = "rtmp://live-push.tiktok.com/live/",
        override val viewerCount: Int = 0
    ) : PlatformEndpoint() {
        override val id = "tiktok"
        override val name = "TikTok Live"
        override val defaultUrl = "rtmp://live-push.tiktok.com/live/"
    }

    data class Twitch(
        override val streamKey: String = "",
        override val isEnabled: Boolean = false,
        override val url: String = "rtmp://live.twitch.tv/app/",
        override val viewerCount: Int = 0
    ) : PlatformEndpoint() {
        override val id = "twitch"
        override val name = "Twitch"
        override val defaultUrl = "rtmp://live.twitch.tv/app/"
    }

    data class Twitter(
        override val streamKey: String = "",
        override val isEnabled: Boolean = false,
        override val url: String = "rtmps://prod-fastly-us-east-1.video.pscp.tv:443/x/",
        override val viewerCount: Int = 0
    ) : PlatformEndpoint() {
        override val id = "twitter"
        override val name = "X (Twitter) / Periscope"
        override val defaultUrl = "rtmps://prod-fastly-us-east-1.video.pscp.tv:443/x/"
    }

    data class Instagram(
        override val streamKey: String = "",
        override val isEnabled: Boolean = false,
        override val url: String = "rtmps://live-upload.instagram.com:443/rtmp/",
        override val viewerCount: Int = 0
    ) : PlatformEndpoint() {
        override val id = "instagram"
        override val name = "Instagram Live"
        override val defaultUrl = "rtmps://live-upload.instagram.com:443/rtmp/"
    }

    data class Kick(
        override val streamKey: String = "",
        override val isEnabled: Boolean = false,
        override val url: String = "rtmps://fa723fc1b171.global-contribute.live-video.net/app/",
        override val viewerCount: Int = 0
    ) : PlatformEndpoint() {
        override val id = "kick"
        override val name = "Kick"
        override val defaultUrl = "rtmps://fa723fc1b171.global-contribute.live-video.net/app/"
    }

    data class CustomRTMP(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val name: String = "Custom RTMP",
        override val url: String = "rtmp://",
        override val streamKey: String = "",
        override val isEnabled: Boolean = true,
        override val viewerCount: Int = 0
    ) : PlatformEndpoint() {
        override val isCustom: Boolean = true
        override val defaultUrl: String = "rtmp://"
    }
}
