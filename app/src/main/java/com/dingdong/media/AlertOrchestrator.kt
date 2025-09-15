package com.dingdong.media

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AlertOrchestrator {
  private val scope = CoroutineScope(Dispatchers.Default)
  private val gate = Mutex()

  fun trigger(context: Context, reason: String, label: String) {
    scope.launch {
      gate.withLock {
        val audio = AudioControllerMediaOnly(context)
        try {
          audio.prepare()
          SoundPlayer.playOnce(context)
        } finally {
          audio.restore()
        }
      }
    }
  }
}