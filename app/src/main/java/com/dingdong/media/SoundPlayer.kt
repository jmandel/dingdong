package com.dingdong.media

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import com.dingdong.data.PolicyStore

object SoundPlayer {
  fun playOnce(ctx: Context) {
    val selectedSound = SoundManager.getSelectedSound(ctx)
    val repeatCount = PolicyStore.getRepeatCount()

    val mp = MediaPlayer()
    mp.setAudioAttributes(
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    )

    try {
      when {
        selectedSound.resourceId != null -> {
          val afd = ctx.resources.openRawResourceFd(selectedSound.resourceId)
          mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
          afd.close()
        }
        selectedSound.customUri != null -> {
          mp.setDataSource(ctx, Uri.parse(selectedSound.customUri))
        }
        else -> {
          val afd = ctx.resources.openRawResourceFd(SoundManager.builtInSounds.first().resourceId!!)
          mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
          afd.close()
        }
      }

      mp.prepare()

      // Play the sound multiple times based on repeat count
      for (i in 1..repeatCount) {
        if (i > 1) {
          Thread.sleep(500) // Brief pause between repeats
          mp.seekTo(0)
        }
        mp.start()
        while (mp.isPlaying) Thread.sleep(30)
      }

      mp.release()
    } catch (e: Exception) {
      e.printStackTrace()
      mp.release()
    }
  }
}