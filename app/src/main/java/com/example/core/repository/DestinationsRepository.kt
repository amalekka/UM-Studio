package com.example.core.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.core.model.PlatformEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

object DestinationsRepository {

    private const val PREFS_NAME = "livestream_destinations_store"
    private const val KEY_CUSTOM_DESTINATIONS = "custom_destinations_json"

    // Default Ingest URLs
    const val DEFAULT_YOUTUBE_URL = "rtmp://a.rtmp.youtube.com/live2"
    const val DEFAULT_FACEBOOK_URL = "rtmps://live-api-s.facebook.com:443/rtmp/"
    const val DEFAULT_TIKTOK_URL = "rtmp://live-push.tiktok.com/live/"
    const val DEFAULT_TWITCH_URL = "rtmp://live.twitch.tv/app/"
    const val DEFAULT_TWITTER_URL = "rtmps://prod-fastly-us-east-1.video.pscp.tv:443/x/"
    const val DEFAULT_INSTAGRAM_URL = "rtmps://live-upload.instagram.com:443/rtmp/"
    const val DEFAULT_KICK_URL = "rtmps://fa723fc1b171.global-contribute.live-video.net/app/"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Loads all pre-configured platforms and custom RTMP servers from persistent storage.
     */
    fun loadDestinations(context: Context): List<PlatformEndpoint> {
        val prefs = getPrefs(context)
        val list = mutableListOf<PlatformEndpoint>()

        // 1. YouTube Live
        val ytUrl = prefs.getString("platform_youtube_url", DEFAULT_YOUTUBE_URL) ?: DEFAULT_YOUTUBE_URL
        val ytKey = prefs.getString("platform_youtube_key", "") ?: ""
        val ytEnabled = prefs.getBoolean("platform_youtube_enabled", false)
        list.add(PlatformEndpoint.YouTube(streamKey = ytKey, isEnabled = ytEnabled, url = ytUrl))

        // 2. Facebook Live
        val fbUrl = prefs.getString("platform_facebook_url", DEFAULT_FACEBOOK_URL) ?: DEFAULT_FACEBOOK_URL
        val fbKey = prefs.getString("platform_facebook_key", "") ?: ""
        val fbEnabled = prefs.getBoolean("platform_facebook_enabled", false)
        list.add(PlatformEndpoint.Facebook(streamKey = fbKey, isEnabled = fbEnabled, url = fbUrl))

        // 3. TikTok Live
        val ttUrl = prefs.getString("platform_tiktok_url", DEFAULT_TIKTOK_URL) ?: DEFAULT_TIKTOK_URL
        val ttKey = prefs.getString("platform_tiktok_key", "") ?: ""
        val ttEnabled = prefs.getBoolean("platform_tiktok_enabled", false)
        list.add(PlatformEndpoint.TikTok(streamKey = ttKey, isEnabled = ttEnabled, url = ttUrl))

        // 4. Twitch
        val twUrl = prefs.getString("platform_twitch_url", DEFAULT_TWITCH_URL) ?: DEFAULT_TWITCH_URL
        val twKey = prefs.getString("platform_twitch_key", "") ?: ""
        val twEnabled = prefs.getBoolean("platform_twitch_enabled", false)
        list.add(PlatformEndpoint.Twitch(streamKey = twKey, isEnabled = twEnabled, url = twUrl))

        // 5. X (Twitter) / Periscope
        val xUrl = prefs.getString("platform_twitter_url", DEFAULT_TWITTER_URL) ?: DEFAULT_TWITTER_URL
        val xKey = prefs.getString("platform_twitter_key", "") ?: ""
        val xEnabled = prefs.getBoolean("platform_twitter_enabled", false)
        list.add(PlatformEndpoint.Twitter(streamKey = xKey, isEnabled = xEnabled, url = xUrl))

        // 6. Instagram Live
        val igUrl = prefs.getString("platform_instagram_url", DEFAULT_INSTAGRAM_URL) ?: DEFAULT_INSTAGRAM_URL
        val igKey = prefs.getString("platform_instagram_key", "") ?: ""
        val igEnabled = prefs.getBoolean("platform_instagram_enabled", false)
        list.add(PlatformEndpoint.Instagram(streamKey = igKey, isEnabled = igEnabled, url = igUrl))

        // 7. Kick
        val kickUrl = prefs.getString("platform_kick_url", DEFAULT_KICK_URL) ?: DEFAULT_KICK_URL
        val kickKey = prefs.getString("platform_kick_key", "") ?: ""
        val kickEnabled = prefs.getBoolean("platform_kick_enabled", false)
        list.add(PlatformEndpoint.Kick(streamKey = kickKey, isEnabled = kickEnabled, url = kickUrl))

        // 8. Custom RTMP Servers
        val customJson = prefs.getString(KEY_CUSTOM_DESTINATIONS, null)
        if (!customJson.isNullOrBlank()) {
            try {
                val array = JSONArray(customJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id", java.util.UUID.randomUUID().toString())
                    val name = obj.optString("name", "Custom RTMP")
                    val url = obj.optString("url", "rtmp://")
                    val streamKey = obj.optString("streamKey", "")
                    val isEnabled = obj.optBoolean("isEnabled", true)
                    list.add(
                        PlatformEndpoint.CustomRTMP(
                            id = id,
                            name = name,
                            url = url,
                            streamKey = streamKey,
                            isEnabled = isEnabled
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("DestinationsRepo", "Failed to parse custom destinations JSON", e)
            }
        }

        return list
    }

    /**
     * Persists a single endpoint into persistent storage.
     */
    fun saveDestination(context: Context, endpoint: PlatformEndpoint) {
        val prefs = getPrefs(context)
        when (endpoint) {
            is PlatformEndpoint.CustomRTMP -> {
                saveCustomDestination(context, endpoint)
            }
            else -> {
                prefs.edit()
                    .putString("platform_${endpoint.id}_url", endpoint.url.trim())
                    .putString("platform_${endpoint.id}_key", endpoint.streamKey.trim())
                    .putBoolean("platform_${endpoint.id}_enabled", endpoint.isEnabled)
                    .apply()
            }
        }
    }

    /**
     * Persists all endpoints (both preconfigured platforms and custom servers).
     */
    fun saveAllDestinations(context: Context, endpoints: List<PlatformEndpoint>) {
        val prefs = getPrefs(context)
        val editor = prefs.edit()
        val customArray = JSONArray()

        endpoints.forEach { endpoint ->
            if (endpoint is PlatformEndpoint.CustomRTMP) {
                val obj = JSONObject().apply {
                    put("id", endpoint.id)
                    put("name", endpoint.name.trim())
                    put("url", endpoint.url.trim())
                    put("streamKey", endpoint.streamKey.trim())
                    put("isEnabled", endpoint.isEnabled)
                }
                customArray.put(obj)
            } else {
                editor.putString("platform_${endpoint.id}_url", endpoint.url.trim())
                editor.putString("platform_${endpoint.id}_key", endpoint.streamKey.trim())
                editor.putBoolean("platform_${endpoint.id}_enabled", endpoint.isEnabled)
            }
        }

        editor.putString(KEY_CUSTOM_DESTINATIONS, customArray.toString())
        editor.apply()
    }

    /**
     * Adds or updates a custom RTMP destination.
     */
    fun saveCustomDestination(context: Context, custom: PlatformEndpoint.CustomRTMP) {
        val prefs = getPrefs(context)
        val customJson = prefs.getString(KEY_CUSTOM_DESTINATIONS, null)
        val array = if (!customJson.isNullOrBlank()) {
            try { JSONArray(customJson) } catch (e: Exception) { JSONArray() }
        } else {
            JSONArray()
        }

        var found = false
        val newArray = JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.optString("id") == custom.id) {
                newArray.put(
                    JSONObject().apply {
                        put("id", custom.id)
                        put("name", custom.name.trim())
                        put("url", custom.url.trim())
                        put("streamKey", custom.streamKey.trim())
                        put("isEnabled", custom.isEnabled)
                    }
                )
                found = true
            } else {
                newArray.put(obj)
            }
        }

        if (!found) {
            newArray.put(
                JSONObject().apply {
                    put("id", custom.id)
                    put("name", custom.name.trim())
                    put("url", custom.url.trim())
                    put("streamKey", custom.streamKey.trim())
                    put("isEnabled", custom.isEnabled)
                }
            )
        }

        prefs.edit().putString(KEY_CUSTOM_DESTINATIONS, newArray.toString()).apply()
    }

    /**
     * Deletes a custom RTMP destination by ID.
     */
    fun deleteCustomDestination(context: Context, id: String) {
        val prefs = getPrefs(context)
        val customJson = prefs.getString(KEY_CUSTOM_DESTINATIONS, null) ?: return
        try {
            val array = JSONArray(customJson)
            val newArray = JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.optString("id") != id) {
                    newArray.put(obj)
                }
            }
            prefs.edit().putString(KEY_CUSTOM_DESTINATIONS, newArray.toString()).apply()
        } catch (e: Exception) {
            Log.e("DestinationsRepo", "Failed to delete custom destination $id", e)
        }
    }

    /**
     * Resets a pre-configured platform to its official default URL and clears the key.
     */
    fun resetPlatform(context: Context, id: String): PlatformEndpoint {
        val defaultEndpoint = when (id) {
            "youtube" -> PlatformEndpoint.YouTube(streamKey = "", isEnabled = false, url = DEFAULT_YOUTUBE_URL)
            "facebook" -> PlatformEndpoint.Facebook(streamKey = "", isEnabled = false, url = DEFAULT_FACEBOOK_URL)
            "tiktok" -> PlatformEndpoint.TikTok(streamKey = "", isEnabled = false, url = DEFAULT_TIKTOK_URL)
            "twitch" -> PlatformEndpoint.Twitch(streamKey = "", isEnabled = false, url = DEFAULT_TWITCH_URL)
            "twitter" -> PlatformEndpoint.Twitter(streamKey = "", isEnabled = false, url = DEFAULT_TWITTER_URL)
            "instagram" -> PlatformEndpoint.Instagram(streamKey = "", isEnabled = false, url = DEFAULT_INSTAGRAM_URL)
            "kick" -> PlatformEndpoint.Kick(streamKey = "", isEnabled = false, url = DEFAULT_KICK_URL)
            else -> PlatformEndpoint.CustomRTMP(name = "Custom RTMP", url = "rtmp://", streamKey = "", isEnabled = false)
        }
        saveDestination(context, defaultEndpoint)
        return defaultEndpoint
    }

    /**
     * Real connection test for RTMP/RTMPS/SRT endpoints.
     * Validates protocol format and performs a TCP socket handshake test.
     */
    suspend fun testEndpointConnection(url: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            return@withContext Pair(false, "Server URL is required")
        }

        val lower = trimmed.lowercase()
        val isValidProtocol = lower.startsWith("rtmp://") ||
                lower.startsWith("rtmps://") ||
                lower.startsWith("srt://")

        if (!isValidProtocol) {
            return@withContext Pair(false, "URL must start with rtmp://, rtmps:// or srt://")
        }

        try {
            val isRtmps = lower.startsWith("rtmps://")
            val isSrt = lower.startsWith("srt://")
            val defaultPort = when {
                isRtmps -> 443
                isSrt -> 9998
                else -> 1935
            }

            // Extract host and port
            val hostAndRest = trimmed.substringAfter("://")
            val hostPart = hostAndRest.substringBefore("/").substringBefore("?")
            val host = if (hostPart.contains(":")) hostPart.substringBefore(":") else hostPart
            val port = if (hostPart.contains(":")) {
                hostPart.substringAfter(":").toIntOrNull() ?: defaultPort
            } else {
                defaultPort
            }

            if (host.isBlank()) {
                return@withContext Pair(false, "Invalid host in URL")
            }

            // Probe connection with timeout
            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 4000)
            }
            val elapsed = System.currentTimeMillis() - start
            Pair(true, "Connected in ${elapsed}ms • Port $port ready")
        } catch (e: java.net.UnknownHostException) {
            Pair(false, "DNS failed: Unknown host")
        } catch (e: java.net.SocketTimeoutException) {
            Pair(false, "Connection timed out (4s)")
        } catch (e: java.net.ConnectException) {
            Pair(false, "Connection refused on port")
        } catch (e: Exception) {
            Pair(false, e.localizedMessage ?: "Connection test failed")
        }
    }
}
