package com.dingdong.notifications

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dingdong.data.RegexStore
import com.dingdong.media.AlertOrchestrator
import com.dingdong.data.EventLogger

class RegexNotificationListener : NotificationListenerService() {

  override fun onCreate() {
    super.onCreate()
    RegexStore.init(applicationContext)
  }

  override fun onNotificationPosted(sbn: StatusBarNotification) {
    val n: Notification = sbn.notification ?: return
    // Ongoing notifications (media playback, downloads) update constantly and
    // were flooding the event log; they're never alerts anyway.
    if (sbn.isOngoing) return
    val extras = n.extras
    val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
    val text  = extras?.getCharSequence(Notification.EXTRA_TEXT )?.toString().orEmpty()
    val big   = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
    val content = sequenceOf(title, text, big).filter { it.isNotBlank() }.joinToString(" • ")

    val appDisplayName = getAppDisplayName(sbn.packageName)

    val matched = content.isNotBlank() && RegexStore.notificationMatches(sbn.packageName, appDisplayName, content)
    val triggered = matched

    // Log the event
    if (content.isNotBlank()) {
      EventLogger.logEvent(
        context = this,
        type = "notification",
        source = appDisplayName ?: sbn.packageName,
        content = content,
        matched = matched,
        triggered = triggered,
        matchDetails = if (matched) "App: $appDisplayName, Content matched filters" else "No match"
      )
    }

    if (triggered) {
      AlertOrchestrator.trigger(applicationContext, reason = "NOTIFICATION",
        label = "${appDisplayName ?: sbn.packageName}: $title")
    }
  }

  private fun getAppDisplayName(packageName: String): String? {
    return try {
      val pm = packageManager
      val appInfo = pm.getApplicationInfo(packageName, 0)
      pm.getApplicationLabel(appInfo).toString()
    } catch (e: PackageManager.NameNotFoundException) {
      null
    }
  }
}