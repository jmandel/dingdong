package com.dingdong.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.dingdong.data.Policy
import com.dingdong.data.PolicyStore

/**
 * Shows what a proposed policy would change, in plain English, and applies it
 * only on Accept. Opened with pasted text, or from a link:
 * https://dingdong.config.joshuamandel.com/dingdong#<urlencoded JSON> (or ?json=),
 * or dingdong://policy?json=<urlencoded JSON>.
 */
class PolicyReviewActivity : Activity() {
  companion object {
    private const val EXTRA_TEXT = "text"
    fun intent(ctx: Context, text: String) = Intent(ctx, PolicyReviewActivity::class.java).putExtra(EXTRA_TEXT, text)

    /** The policy JSON carried by a link, from its fragment or its json= parameter. */
    fun fromLink(uri: Uri): String? =
      uri.getQueryParameter("json") ?: uri.fragment?.takeIf { it.isNotBlank() }
  }

  override fun onCreate(state: Bundle?) {
    super.onCreate(state)
    PolicyStore.init(applicationContext)
    val pad = (16 * resources.displayMetrics.density).toInt()
    val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad * 2, pad, pad) }
    fun text(s: String, size: Float = 16f) = TextView(this).apply {
      text = s; textSize = size; setPadding(0, pad / 4, 0, pad / 4); root.addView(this)
    }
    setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(root) })

    val raw = intent.getStringExtra(EXTRA_TEXT) ?: intent.data?.let { fromLink(it) }
    val current = PolicyStore.policy
    val proposed = try {
      raw ?: throw IllegalArgumentException("Nothing to review")
      // Pasted text may wrap the JSON in a reply; a link carries only the JSON.
      Policy.find(raw) ?: Policy.parse(raw)
    } catch (e: IllegalArgumentException) {
      text("Proposed settings", 22f)
      text("These aren't valid DingDong settings, so nothing was changed:\n\n${e.message}")
      root.addView(Button(this).apply { text = "Close"; setOnClickListener { finish() } })
      return
    }

    text("Proposed settings", 22f)
    val changes = current.changesTo(proposed)
    if (changes.isEmpty()) {
      text("These are the same as your current settings.")
      root.addView(Button(this).apply { text = "Close"; setOnClickListener { finish() } })
      return
    }
    text("Accepting will:")
    changes.forEach { text("• $it", 15f) }
    text("\nRules afterwards:", 16f)
    if (proposed.rules.isEmpty()) text("None, so nothing will alert.", 14f)
    proposed.rules.forEach { text("${if (it.enabled) "✓" else "–"} ${it.name}: ${it.describe()}", 14f) }

    val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, pad, 0, 0) }
    buttons.addView(Button(this).apply {
      text = "Discard"
      setOnClickListener { finish() }
    }, LinearLayout.LayoutParams(0, -2, 1f))
    buttons.addView(Button(this).apply {
      text = "Accept"
      setOnClickListener {
        PolicyStore.save(proposed)
        Toast.makeText(this@PolicyReviewActivity, "Settings updated", Toast.LENGTH_SHORT).show()
        finish()
      }
    }, LinearLayout.LayoutParams(0, -2, 1f))
    root.addView(buttons)
  }
}
