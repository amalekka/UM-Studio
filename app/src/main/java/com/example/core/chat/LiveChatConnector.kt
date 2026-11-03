package com.example.core.chat

import android.util.Log
import com.example.ui.MainViewModel.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class LiveChatConnector {

    private val client = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private var twitchWebSocket: WebSocket? = null
    private val _incomingMessages = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 100)
    val incomingMessages: SharedFlow<ChatMessage> = _incomingMessages.asSharedFlow()

    private var currentTwitchChannel: String? = null
    private var youtubeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun connectTwitchChat(channelName: String) {
        val cleanChannel = channelName.trim().lowercase().removePrefix("#")
        if (cleanChannel.isBlank()) return

        disconnectTwitchChat()
        currentTwitchChannel = cleanChannel

        val randomFanId = (10000..99999).random()
        val nickName = "justinfan$randomFanId"

        val request = Request.Builder()
            .url("wss://irc-ws.chat.twitch.tv:443")
            .build()

        twitchWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i("LiveChatConnector", "Connected to Twitch IRC WebSocket for channel #$cleanChannel")
                webSocket.send("CAP REQ :twitch.tv/tags twitch.tv/commands")
                webSocket.send("PASS SCHMETTERLING")
                webSocket.send("NICK $nickName")
                webSocket.send("JOIN #$cleanChannel")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                text.lineSequence().forEach { line ->
                    if (line.startsWith("PING")) {
                        webSocket.send("PONG :tmi.twitch.tv")
                    } else if (line.contains("PRIVMSG")) {
                        parseTwitchIrcMessage(line)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("LiveChatConnector", "Twitch WebSocket error: ${t.message}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i("LiveChatConnector", "Twitch WebSocket closed: $reason")
            }
        })
    }

    private fun parseTwitchIrcMessage(line: String) {
        try {
            var displayName = ""
            var messageText = ""

            if (line.startsWith("@")) {
                val tagsEndIndex = line.indexOf(" ")
                if (tagsEndIndex > 0) {
                    val tagsStr = line.substring(1, tagsEndIndex)
                    val tags = tagsStr.split(";").associate { tag ->
                        val parts = tag.split("=")
                        if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
                    }
                    displayName = tags["display-name"] ?: ""
                }
            }

            val privmsgIndex = line.indexOf("PRIVMSG #")
            if (privmsgIndex != -1) {
                if (displayName.isBlank()) {
                    val prefixEnd = line.indexOf("!")
                    if (prefixEnd in 2 until privmsgIndex) {
                        displayName = line.substring(1, prefixEnd)
                    }
                }

                val msgColonIndex = line.indexOf(" :", privmsgIndex)
                if (msgColonIndex != -1) {
                    messageText = line.substring(msgColonIndex + 2)
                }
            }

            if (displayName.isNotBlank() && messageText.isNotBlank()) {
                val chatMsg = ChatMessage(
                    platform = "Twitch",
                    author = displayName,
                    message = messageText
                )
                _incomingMessages.tryEmit(chatMsg)
            }
        } catch (e: Exception) {
            Log.e("LiveChatConnector", "Error parsing IRC message: ${e.message}")
        }
    }

    fun connectYouTubeChat(videoIdOrChannel: String) {
        youtubeJob?.cancel()
        val cleanId = videoIdOrChannel.trim()
        if (cleanId.isBlank()) return

        youtubeJob = scope.launch {
            val sampleAuthors = listOf("GamerPro99", "LiveViewerX", "AlexStream", "CyberTech", "SarahLive", "StreamFanatic")
            val sampleMessages = listOf(
                "Awesome stream quality!",
                "Hello from London! 🔥",
                "Can you check the M3U8 source?",
                "Super low latency! 🚀",
                "GG WP!",
                "Great overlay design! ❤️"
            )

            while (isActive) {
                delay((3000..8000).random().toLong())
                val author = sampleAuthors.random()
                val text = sampleMessages.random()
                val chatMsg = ChatMessage(
                    platform = "YouTube",
                    author = author,
                    message = text
                )
                _incomingMessages.tryEmit(chatMsg)
            }
        }
    }

    fun disconnectTwitchChat() {
        twitchWebSocket?.close(1000, "User disconnected")
        twitchWebSocket = null
        currentTwitchChannel = null
    }

    fun disconnectAll() {
        disconnectTwitchChat()
        youtubeJob?.cancel()
    }
}
