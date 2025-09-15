package com.dingdong.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.dingdong.data.EventLogger
import com.dingdong.databinding.ActivityEventLogBinding

class EventLogActivity : ComponentActivity() {
    private lateinit var vb: ActivityEventLogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityEventLogBinding.inflate(layoutInflater)
        setContentView(vb.root)

        loadEvents()

        vb.btnClearLog.setOnClickListener {
            EventLogger.clearEvents(this)
            loadEvents()
        }
    }

    private fun loadEvents() {
        val events = EventLogger.getEvents(this)
        vb.logContainer.removeAllViews()

        if (events.isEmpty()) {
            vb.textNoEvents.visibility = View.VISIBLE
            vb.logContainer.addView(vb.textNoEvents)
        } else {
            vb.textNoEvents.visibility = View.GONE

            events.forEach { event ->
                val eventView = createEventView(event)
                vb.logContainer.addView(eventView)
            }
        }
    }

    private fun createEventView(event: com.dingdong.data.EventLog): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.WHITE)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 8)
            }
            layoutParams = params
        }

        // Header row with time and type
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val timeText = TextView(this).apply {
            text = EventLogger.formatTimestamp(event.timestamp)
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val typeText = TextView(this).apply {
            text = event.type.uppercase()
            textSize = 14f
            setTextColor(if (event.type == "call") Color.parseColor("#1976D2") else Color.parseColor("#388E3C"))
            gravity = Gravity.END
        }

        headerRow.addView(timeText)
        headerRow.addView(typeText)
        container.addView(headerRow)

        // Source
        val sourceText = TextView(this).apply {
            text = event.source
            textSize = 16f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 4, 0, 0)
        }
        container.addView(sourceText)

        // Content
        if (event.content.isNotEmpty()) {
            val contentText = TextView(this).apply {
                text = event.content
                textSize = 14f
                setTextColor(Color.parseColor("#333333"))
                setPadding(0, 4, 0, 0)
            }
            container.addView(contentText)
        }

        // Status
        val statusText = TextView(this).apply {
            text = when {
                event.triggered -> "✓ TRIGGERED ALERT"
                event.matched -> "• Matched filters (no alert)"
                else -> "• No match"
            }
            textSize = 14f
            setTextColor(when {
                event.triggered -> Color.parseColor("#F44336")
                event.matched -> Color.parseColor("#FF9800")
                else -> Color.parseColor("#757575")
            })
            setPadding(0, 4, 0, 0)
        }
        container.addView(statusText)

        // Match details if present
        if (event.matchDetails.isNotEmpty()) {
            val detailsText = TextView(this).apply {
                text = event.matchDetails
                textSize = 12f
                setTextColor(Color.parseColor("#999999"))
                setPadding(0, 2, 0, 0)
            }
            container.addView(detailsText)
        }

        return container
    }
}