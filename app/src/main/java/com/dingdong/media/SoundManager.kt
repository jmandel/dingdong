package com.dingdong.media

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.dingdong.R
import java.io.File

data class AlertSound(
  val id: String,
  val name: String,
  val resourceId: Int? = null,
  val customUri: String? = null,
  val isCustom: Boolean = false
)

object SoundManager {
  private const val PREFS_NAME = "regex_alerts_prefs"
  private const val KEY_SELECTED_SOUND = "selected_sound_id"
  private const val KEY_CUSTOM_SOUND_URI = "custom_sound_uri"

  val builtInSounds = listOf(
    AlertSound("alarmed", "Alarmed", R.raw.alarmed),
    AlertSound("beeper", "Beeper", R.raw.beeper),
    AlertSound("belligerent", "Belligerent", R.raw.belligerent),
    AlertSound("calm", "Calm", R.raw.calm),
    AlertSound("chord2", "Chord2", R.raw.chord2),
    AlertSound("chord2_rev", "Chord2 Rev", R.raw.chord2_rev),
    AlertSound("chord", "Chord", R.raw.chord),
    AlertSound("cloud", "Cloud", R.raw.cloud),
    AlertSound("doorbell", "Doorbell", R.raw.doorbell),
    AlertSound("enharpment", "Enharpment", R.raw.enharpment),
    AlertSound("flit_flute", "Flit Flute", R.raw.flit_flute),
    AlertSound("glass", "Glass", R.raw.glass),
    AlertSound("glisten", "Glisten", R.raw.glisten),
    AlertSound("glitch", "Glitch", R.raw.glitch),
    AlertSound("information_bell", "Information Bell", R.raw.information_bell),
    AlertSound("information_block", "Information Block", R.raw.information_block),
    AlertSound("jinja2", "Jinja2", R.raw.jinja2),
    AlertSound("jinja", "Jinja", R.raw.jinja),
    AlertSound("koto", "Koto", R.raw.koto),
    AlertSound("modular", "Modular", R.raw.modular),
    AlertSound("newsflash_bright", "Newsflash Bright", R.raw.newsflash_bright),
    AlertSound("newsflash", "Newsflash", R.raw.newsflash),
    AlertSound("pizzabox", "Pizzabox", R.raw.pizzabox),
    AlertSound("polite", "Polite", R.raw.polite),
    AlertSound("ponderous", "Ponderous", R.raw.ponderous),
    AlertSound("reverie", "Reverie", R.raw.reverie),
    AlertSound("sharp", "Sharp", R.raw.sharp),
    AlertSound("sonar", "Sonar", R.raw.sonar),
    AlertSound("taptap", "Taptap", R.raw.taptap),
    AlertSound("tech", "Tech", R.raw.tech),
    AlertSound("unphased", "Unphased", R.raw.unphased),
    AlertSound("unstrung", "Unstrung", R.raw.unstrung),
    AlertSound("wahsup", "Wahsup", R.raw.wahsup),
    AlertSound("whistleronic_down", "Whistleronic Down", R.raw.whistleronic_down),
    AlertSound("whistleronic", "Whistleronic", R.raw.whistleronic),
    AlertSound("woodblock", "Woodblock", R.raw.woodblock)
  )

  fun getSelectedSound(context: Context): AlertSound {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val soundId = prefs.getString(KEY_SELECTED_SOUND, "alarmed") ?: "alarmed"

    return if (soundId == "custom") {
      val customUri = prefs.getString(KEY_CUSTOM_SOUND_URI, null)
      AlertSound("custom", "Custom Sound", customUri = customUri, isCustom = true)
    } else {
      builtInSounds.find { it.id == soundId } ?: builtInSounds.first()
    }
  }

  fun setSelectedSound(context: Context, sound: AlertSound) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().apply {
      putString(KEY_SELECTED_SOUND, sound.id)
      if (sound.isCustom && sound.customUri != null) {
        putString(KEY_CUSTOM_SOUND_URI, sound.customUri)
      }
      apply()
    }
  }

  fun playSound(context: Context, sound: AlertSound) {
    try {
      val mp = MediaPlayer()

      when {
        sound.resourceId != null -> {
          val afd = context.resources.openRawResourceFd(sound.resourceId)
          mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
          afd.close()
        }
        sound.customUri != null -> {
          mp.setDataSource(context, Uri.parse(sound.customUri))
        }
        else -> return
      }

      mp.setOnCompletionListener { it.release() }
      mp.prepare()
      mp.start()
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  fun previewSound(context: Context, sound: AlertSound) {
    playSound(context, sound)
  }
}