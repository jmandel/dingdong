package com.dingdong.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class EventLog(
    val timestamp: Long,
    val type: String, // "call", "notification"
    val source: String, // phone number or app name
    val content: String,
    val matched: Boolean,
    val triggered: Boolean,
    val matchDetails: String
)

object EventLogger {
    private const val PREFS_NAME = "event_log_prefs"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 100

    private val dateFormat = SimpleDateFormat("MM/dd HH:mm:ss", Locale.US)

    fun logEvent(
        context: Context,
        type: String,
        source: String,
        content: String,
        matched: Boolean,
        triggered: Boolean,
        matchDetails: String = ""
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val eventsJson = prefs.getString(KEY_EVENTS, "[]") ?: "[]"
        val events = JSONArray(eventsJson)

        val event = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("type", type)
            put("source", source)
            put("content", content.take(100)) // Limit content length
            put("matched", matched)
            put("triggered", triggered)
            put("matchDetails", matchDetails)
        }

        // Add new event at beginning
        val newEvents = JSONArray()
        newEvents.put(event)

        // Copy existing events up to MAX_EVENTS - 1
        for (i in 0 until minOf(events.length(), MAX_EVENTS - 1)) {
            newEvents.put(events.getJSONObject(i))
        }

        prefs.edit().putString(KEY_EVENTS, newEvents.toString()).apply()
    }

    fun getEvents(context: Context): List<EventLog> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val eventsJson = prefs.getString(KEY_EVENTS, "[]") ?: "[]"
        val events = JSONArray(eventsJson)

        val result = mutableListOf<EventLog>()
        for (i in 0 until events.length()) {
            val event = events.getJSONObject(i)
            result.add(
                EventLog(
                    timestamp = event.getLong("timestamp"),
                    type = event.getString("type"),
                    source = event.getString("source"),
                    content = event.getString("content"),
                    matched = event.getBoolean("matched"),
                    triggered = event.getBoolean("triggered"),
                    matchDetails = event.optString("matchDetails", "")
                )
            )
        }
        return result
    }

    fun clearEvents(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_EVENTS, "[]").apply()
    }

    fun formatTimestamp(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }
}