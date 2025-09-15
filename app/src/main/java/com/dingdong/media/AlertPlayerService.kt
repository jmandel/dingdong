package com.dingdong.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dingdong.R

class AlertPlayerService : Service() {
  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    startForeground(42, buildNotification())
    Thread {
      val audio = AudioControllerMediaOnly(this)
      try {
        audio.prepare()
        SoundPlayer.playOnce(this)
      } finally {
        audio.restore()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
      }
    }.start()
    return START_NOT_STICKY
  }

  private fun buildNotification(): Notification {
    val nm = getSystemService(NotificationManager::class.java)
    val channelId = "alert_playback"
    if (Build.VERSION.SDK_INT >= 26) {
      nm.createNotificationChannel(
        NotificationChannel(channelId, "Alert playback", NotificationManager.IMPORTANCE_LOW)
      )
    }
    return NotificationCompat.Builder(this, channelId)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(getString(R.string.app_name))
      .setContentText("Playing alert sound…")
      .setOngoing(true)
      .build()
  }
}