package com.dingdong.calls

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CallLog
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.dingdong.data.RegexStore
import com.dingdong.media.AlertOrchestrator
import com.dingdong.data.EventLogger
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicLong

class PhoneStateReceiver : BroadcastReceiver() {
  companion object {
    private val lastRingingAt = AtomicLong(0L)
  }

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

    RegexStore.init(context)

    when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
      TelephonyManager.EXTRA_STATE_RINGING -> {
        lastRingingAt.set(System.currentTimeMillis())

        val hasCallLog = ContextCompat.checkSelfPermission(
          context, Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

        val incoming = if (hasCallLog)
          intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) else null

        if (!incoming.isNullOrBlank()) {
          handleIncoming(context, incoming, source = "BROADCAST")
        }
      }

      TelephonyManager.EXTRA_STATE_IDLE -> {
        if (lastRingingAt.get() > 0L) {
          val pending = goAsync()
          CoroutineScope(Dispatchers.IO).launch {
            try {
              val number = queryRecentIncomingNumber(context, since = lastRingingAt.get())
              if (!number.isNullOrBlank()) {
                handleIncoming(context, number, source = "CALL_LOG")
              }
            } finally {
              lastRingingAt.set(0L)
              pending.finish()
            }
          }
        }
      }
    }
  }

  private fun handleIncoming(ctx: Context, raw: String, source: String) {
    val number = raw.filterNot { it.isWhitespace() }

    val matched = RegexStore.phoneMatches(number)
    val triggered = matched

    // Log the event
    EventLogger.logEvent(
      context = ctx,
      type = "call",
      source = number,
      content = "Incoming call ($source)",
      matched = matched,
      triggered = triggered,
      matchDetails = if (matched) "Phone number matched filters" else "No match"
    )

    if (triggered) {
      AlertOrchestrator.trigger(ctx.applicationContext, reason = "CALL", label = number)
    }
  }

  private fun queryRecentIncomingNumber(ctx: Context, since: Long): String? {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG)
        != PackageManager.PERMISSION_GRANTED) return null

    val uri = CallLog.Calls.CONTENT_URI
    val cols = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE)
    val sel = "${CallLog.Calls.DATE}>=? AND ${CallLog.Calls.TYPE} IN (?,?,?)"
    val args = arrayOf(
      since.toString(),
      CallLog.Calls.INCOMING_TYPE.toString(),
      CallLog.Calls.MISSED_TYPE.toString(),
      CallLog.Calls.REJECTED_TYPE.toString()
    )
    val sort = "${CallLog.Calls.DATE} DESC"

    ctx.contentResolver.query(uri, cols, sel, args, sort)?.use { c ->
      return if (c.moveToFirst()) c.getString(0) else null
    }
    return null
  }
}