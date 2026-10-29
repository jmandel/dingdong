package com.dingdong.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.PowerManager
import android.os.Vibrator
import com.dingdong.data.RegexStore
import kotlin.math.max
import kotlin.math.roundToInt

class AudioControllerMediaOnly(private val ctx: Context) {
  private val am = ctx.getSystemService(AudioManager::class.java)

  private var savedMusicVol: Int = am.getStreamVolume(AudioManager.STREAM_MUSIC)
  private val savedMute: Boolean = am.isStreamMute(AudioManager.STREAM_MUSIC)
  private var focus: AudioFocusRequest? = null
  private var wake: PowerManager.WakeLock? = null

  fun prepare(targetMinPercent: Int? = null) {
    val pm = ctx.getSystemService(PowerManager::class.java)
    val wakeDuration = RegexStore.getWakeDuration() * 1000L
    wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dingdong:play").apply {
      setReferenceCounted(false); acquire(wakeDuration)
    }

    // Vibrate if enabled
    if (RegexStore.getVibrateEnabled()) {
      val vibrator = ctx.getSystemService(Vibrator::class.java)
      vibrator?.vibrate(longArrayOf(0, 200, 100, 200), -1)
    }

    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val minVolumePercent = targetMinPercent ?: RegexStore.getMinVolumePercent()
    val minTarget = max(1, (maxVol * (minVolumePercent / 100.0)).roundToInt())
    // Media is often turned down or muted at night; raise it to the floor so
    // the alert is actually audible, not only when it sits at exactly zero.
    if (am.isStreamMute(AudioManager.STREAM_MUSIC)) {
      am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
    }
    if (am.getStreamVolume(AudioManager.STREAM_MUSIC) < minTarget) {
      am.setStreamVolume(AudioManager.STREAM_MUSIC, minTarget, 0)
    }

    val attrs = AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_MEDIA)
      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
      .build()
    val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
      .setAudioAttributes(attrs)
      .setOnAudioFocusChangeListener { }
      .build()
    if (am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      focus = req
    }
  }

  fun restore() {
    if (RegexStore.getRestoreVolume()) {
      // Delay before restoring volume
      Thread.sleep(RegexStore.getVolumeRestoreDelay() * 1000L)
      am.setStreamVolume(AudioManager.STREAM_MUSIC, savedMusicVol, 0)
      if (savedMute) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
    }
    focus?.let { am.abandonAudioFocusRequest(it) }
    wake?.let { if (it.isHeld) it.release() }
  }
}