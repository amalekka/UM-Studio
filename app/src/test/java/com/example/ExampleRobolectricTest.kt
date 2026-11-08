package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @org.junit.After
  fun tearDown() {
    org.koin.core.context.GlobalContext.stopKoin()
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("UM Studio", appName)
  }

  @Test
  fun `test formatDecibels conversions`() {
    assertEquals("-60 dB", com.example.ui.components.formatDecibels(0f))
    assertEquals("-60 dB", com.example.ui.components.formatDecibels(0.001f))
    assertEquals("0.0 dB", com.example.ui.components.formatDecibels(1.0f))
    assertEquals("0.0 dB", com.example.ui.components.formatDecibels(0.999f))
  }

  @Test
  fun `test 8-pad instant sfx types count`() {
    val sfxValues = com.example.core.audio.AudioSfxManager.SfxType.values()
    assertEquals(8, sfxValues.size)
  }

  @Test
  fun `test studio compositor config models`() {
    val scoreboard = com.example.ui.MainViewModel.ScoreboardConfig()
    assertEquals("TEAM A", scoreboard.teamAName)
    assertEquals("TEAM B", scoreboard.teamBName)
    assertEquals(0, scoreboard.teamAScore)
    assertEquals(0, scoreboard.teamBScore)

    val ticker = com.example.ui.MainViewModel.TickerConfig()
    assertEquals(false, ticker.isEnabled)
    assertEquals(1.0f, ticker.speed)

    val watermark = com.example.ui.MainViewModel.WatermarkConfig()
    assertEquals(false, watermark.isEnabled)
    assertEquals(com.example.ui.MainViewModel.WatermarkPosition.TOP_RIGHT, watermark.position)
  }

  @Test
  fun `test pro encoder settings defaults`() {
    val config = com.example.core.model.StreamConfig()
    assertEquals(30, config.targetFps)
    assertEquals(2, config.keyframeIntervalSec)
    assertEquals(4500, config.videoBitrateKbps)
    assertEquals(true, config.isAdaptiveBitrateEnabled)
    assertEquals(com.example.core.model.EncoderEngine.HARDWARE_MEDIACODEC, config.encoderEngine)
    assertEquals(true, config.keepScreenAwake)
    assertEquals(true, config.backgroundStreamingEnabled)
  }

  @Test
  fun `test 9 default destinations include telegram`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val endpoints = com.example.core.repository.DestinationsRepository.loadDestinations(context)
    val hasTelegram = endpoints.any { it is com.example.core.model.PlatformEndpoint.Telegram }
    assertEquals(true, hasTelegram)

    val telegram = endpoints.first { it is com.example.core.model.PlatformEndpoint.Telegram }
    assertEquals("Telegram Live", telegram.name)
    assertEquals("rtmps://dc4-1.rtmp.t.me/s/", telegram.url)
  }

  @Test
  fun `test profile backup export and restore`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val initialList = com.example.core.repository.DestinationsRepository.loadDestinations(context)
    val yt = initialList.first { it is com.example.core.model.PlatformEndpoint.YouTube } as com.example.core.model.PlatformEndpoint.YouTube
    val tg = initialList.first { it is com.example.core.model.PlatformEndpoint.Telegram } as com.example.core.model.PlatformEndpoint.Telegram

    // Set custom keys
    com.example.core.repository.DestinationsRepository.saveDestination(context, yt.copy(streamKey = "yt-test-key-999"))
    com.example.core.repository.DestinationsRepository.saveDestination(context, tg.copy(streamKey = "tg-test-key-777"))

    // Export JSON
    val backupJson = com.example.core.repository.DestinationsRepository.exportProfileBackupJson(context)
    org.junit.Assert.assertTrue(backupJson.contains("yt-test-key-999"))
    org.junit.Assert.assertTrue(backupJson.contains("tg-test-key-777"))

    // Overwrite / clear
    com.example.core.repository.DestinationsRepository.saveDestination(context, yt.copy(streamKey = ""))
    com.example.core.repository.DestinationsRepository.saveDestination(context, tg.copy(streamKey = ""))

    // Restore from backup JSON
    val restored = com.example.core.repository.DestinationsRepository.restoreProfileBackupJson(context, backupJson)
    org.junit.Assert.assertTrue(restored.isNotEmpty())

    val restoredEndpoints = com.example.core.repository.DestinationsRepository.loadDestinations(context)
    val restoredYt = restoredEndpoints.first { it is com.example.core.model.PlatformEndpoint.YouTube }
    val restoredTg = restoredEndpoints.first { it is com.example.core.model.PlatformEndpoint.Telegram }

    assertEquals("yt-test-key-999", restoredYt.streamKey)
    assertEquals("tg-test-key-777", restoredTg.streamKey)
  }
}
