package com.dingdong.ui

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.dingdong.R
import com.dingdong.data.Policy
import com.dingdong.data.PolicyStore
import com.dingdong.databinding.ActivityMainBinding
import com.dingdong.media.AlertSound
import com.dingdong.media.SoundManager
import com.dingdong.data.EventLogger
import android.widget.SeekBar
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity
import android.content.ClipData
import android.content.ClipboardManager
import androidx.appcompat.widget.SwitchCompat

class MainActivity : ComponentActivity() {
  private lateinit var vb: ActivityMainBinding
  private var customSoundUri: Uri? = null

  private val requestNotifications =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

  private val requestPhoneState = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) {
      Toast.makeText(this, "Phone state permission granted!", Toast.LENGTH_SHORT).show()
    }
  }

  private val requestCallLog = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) {
      Toast.makeText(this, "Call log permission granted!", Toast.LENGTH_SHORT).show()
    }
  }

  private val requestContacts = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) {
      Toast.makeText(this, "Contacts permission granted!", Toast.LENGTH_SHORT).show()
    }
  }

  private val pickAudioFile = registerForActivityResult(
    ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    uri?.let {
      contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
      customSoundUri = it
      val customSound = AlertSound("custom", "Custom Sound", customUri = it.toString(), isCustom = true)
      SoundManager.setSelectedSound(this, customSound)
      setupSoundSpinner()
      Toast.makeText(this, "Custom sound selected!", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    vb = ActivityMainBinding.inflate(layoutInflater)
    setContentView(vb.root)

    PolicyStore.init(applicationContext)
    setupSoundSpinner()
    setupAdvancedSettings()
    showRules()

    vb.btnAddRule.setOnClickListener { RuleEditor.show(this, null) { showRules() } }
    vb.btnCopyPrompt.setOnClickListener {
      clipboard().setPrimaryClip(ClipData.newPlainText("DingDong prompt", AiPrompt.build(PolicyStore.policy)))
      Toast.makeText(this, "Prompt copied. Paste it into an AI chat app.", Toast.LENGTH_LONG).show()
    }
    vb.btnPastePolicy.setOnClickListener {
      val text = clipboard().primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
      if (text.isNullOrBlank()) {
        Toast.makeText(this, "The clipboard is empty", Toast.LENGTH_SHORT).show()
      } else {
        startActivity(PolicyReviewActivity.intent(this, text))
      }
    }
    vb.btnReviewClipboard.setOnClickListener {
      vb.btnReviewClipboard.visibility = android.view.View.GONE
      pendingProposal?.let { startActivity(PolicyReviewActivity.intent(this, it)) }
    }

    vb.btnPreview.setOnClickListener {
      val selectedPosition = vb.spinnerSound.selectedItemPosition
      if (selectedPosition >= 0 && selectedPosition < SoundManager.builtInSounds.size) {
        val sound = SoundManager.builtInSounds[selectedPosition]
        SoundManager.previewSound(this, sound)
      } else if (customSoundUri != null) {
        val customSound = AlertSound("custom", "Custom Sound", customUri = customSoundUri.toString(), isCustom = true)
        SoundManager.previewSound(this, customSound)
      }
    }

    vb.btnCustomSound.setOnClickListener {
      pickAudioFile.launch("audio/*")
    }

    vb.spinnerSound.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
      override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
        if (position < SoundManager.builtInSounds.size) {
          val sound = SoundManager.builtInSounds[position]
          SoundManager.setSelectedSound(this@MainActivity, sound)
        }
      }

      override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
    })

    vb.btnNotificationAccess.setOnClickListener {
      startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    vb.btnCallScreeningRole.setOnClickListener {
      when {
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
          != PackageManager.PERMISSION_GRANTED -> {
          requestPhoneState.launch(Manifest.permission.READ_PHONE_STATE)
        }
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
          != PackageManager.PERMISSION_GRANTED -> {
          requestCallLog.launch(Manifest.permission.READ_CALL_LOG)
        }
        else -> {
          Toast.makeText(this, "Phone permissions already granted!", Toast.LENGTH_SHORT).show()
        }
      }
    }

    vb.btnPostNotifications.setOnClickListener {
      if (Build.VERSION.SDK_INT >= 33) {
        requestNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
      }
    }

    vb.btnViewLog.setOnClickListener {
      startActivity(Intent(this, EventLogActivity::class.java))
    }

    vb.btnClearLogs.setOnClickListener {
      EventLogger.clearEvents(this)
      Toast.makeText(this, "Event logs cleared", Toast.LENGTH_SHORT).show()
    }
  }

  private fun setupSoundSpinner() {
    val selectedSound = SoundManager.getSelectedSound(this)
    val soundNames = if (selectedSound.isCustom) {
      SoundManager.builtInSounds.map { it.name } + "Custom Sound"
    } else {
      SoundManager.builtInSounds.map { it.name }
    }

    val adapter = ArrayAdapter(this, R.layout.spinner_item, soundNames)
    adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
    vb.spinnerSound.adapter = adapter

    val selectedIndex = if (selectedSound.isCustom) {
      soundNames.size - 1
    } else {
      SoundManager.builtInSounds.indexOfFirst { it.id == selectedSound.id }.coerceAtLeast(0)
    }
    vb.spinnerSound.setSelection(selectedIndex)
  }

  override fun onResume() {
    super.onResume()
    // Settings may have changed in the review screen.
    showRules()
    setupSoundSpinner()
    setupAdvancedSettings()
  }

  /**
   * Android only lets the focused app read the clipboard. The clip's timestamp
   * is readable without reading the clip itself, so it's only read when new.
   */
  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (!hasFocus) return
    val cm = clipboard()
    val stamp = cm.primaryClipDescription?.timestamp ?: return
    val prefs = getSharedPreferences("ui", MODE_PRIVATE)
    if (stamp == prefs.getLong("clip_checked", 0)) return
    prefs.edit().putLong("clip_checked", stamp).apply()
    val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString() ?: return
    val proposed = Policy.find(text) ?: return
    if (proposed == PolicyStore.policy) return
    pendingProposal = text
    vb.btnReviewClipboard.visibility = android.view.View.VISIBLE
  }

  private var pendingProposal: String? = null

  private fun clipboard() = getSystemService(ClipboardManager::class.java)

  /** One row per rule: on/off switch, name and summary; tap to edit. */
  private fun showRules() {
    val box = vb.rulesContainer
    box.removeAllViews()
    val rules = PolicyStore.policy.rules
    if (rules.isEmpty()) {
      box.addView(TextView(this).apply { text = "No rules yet, so nothing alerts."; setTextColor(0xFF333333.toInt()) })
    }
    rules.forEach { rule ->
      val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(0xFFFFFFFF.toInt())
        setPadding(dp(12), dp(8), dp(4), dp(8))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) }
      }
      val words = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
      words.addView(TextView(this).apply { text = rule.name; textSize = 16f; setTextColor(0xFF000000.toInt()) })
      words.addView(TextView(this).apply {
        text = rule.describe().replaceFirstChar { it.uppercase() }; textSize = 13f; setTextColor(0xFF555555.toInt())
      })
      row.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
      row.addView(SwitchCompat(this).apply {
        isChecked = rule.enabled
        setOnCheckedChangeListener { _, on ->
          PolicyStore.update { p -> p.copy(rules = p.rules.map { if (it.name == rule.name) it.copy(enabled = on) else it }) }
        }
      })
      row.setOnClickListener { RuleEditor.show(this, rule) { showRules() } }
      box.addView(row)
    }
  }

  private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

  /** Each control writes straight into the policy; there's no separate Save. */
  private fun setupAdvancedSettings() {
    // Load saved values
    val p = PolicyStore.policy
    vb.seekMinVolume.progress = p.minVolumePercent
    vb.textMinVolume.text = "${p.minVolumePercent}%"

    vb.seekVolumeDelay.progress = p.restoreDelay
    vb.textVolumeDelay.text = "${p.restoreDelay}s"

    vb.seekRepeatCount.progress = p.repeat
    vb.textRepeatCount.text = "${p.repeat}x"

    vb.seekWakeDuration.progress = p.wakeDuration
    vb.textWakeDuration.text = "${p.wakeDuration}s"

    vb.checkVibrate.setOnCheckedChangeListener(null)
    vb.checkVibrate.isChecked = p.vibrate
    vb.checkVibrate.setOnCheckedChangeListener { _, on -> PolicyStore.update { it.copy(vibrate = on) } }
    vb.checkRestoreVolume.setOnCheckedChangeListener(null)
    vb.checkRestoreVolume.isChecked = p.restoreVolume
    vb.checkRestoreVolume.setOnCheckedChangeListener { _, on -> PolicyStore.update { it.copy(restoreVolume = on) } }

    // Logging settings
    vb.checkEnableLogging.setOnCheckedChangeListener(null)
    vb.checkEnableLogging.isChecked = p.logging
    vb.checkEnableLogging.setOnCheckedChangeListener { _, on -> PolicyStore.update { it.copy(logging = on) } }
    vb.seekLogRetention.progress = p.logRetentionHours
    updateLogRetentionText(p.logRetentionHours)

    // Set up seekbar listeners
    vb.seekMinVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        vb.textMinVolume.text = "$progress%"
      }
      override fun onStartTrackingTouch(seekBar: SeekBar?) {}
      override fun onStopTrackingTouch(seekBar: SeekBar?) {
        PolicyStore.update { it.copy(minVolumePercent = seekBar!!.progress) }
      }
    })

    vb.seekVolumeDelay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        vb.textVolumeDelay.text = "${progress}s"
      }
      override fun onStartTrackingTouch(seekBar: SeekBar?) {}
      override fun onStopTrackingTouch(seekBar: SeekBar?) {
        PolicyStore.update { it.copy(restoreDelay = seekBar!!.progress) }
      }
    })

    vb.seekRepeatCount.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        vb.textRepeatCount.text = "${progress}x"
      }
      override fun onStartTrackingTouch(seekBar: SeekBar?) {}
      override fun onStopTrackingTouch(seekBar: SeekBar?) {
        PolicyStore.update { it.copy(repeat = seekBar!!.progress) }
      }
    })

    vb.seekWakeDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        vb.textWakeDuration.text = "${progress}s"
      }
      override fun onStartTrackingTouch(seekBar: SeekBar?) {}
      override fun onStopTrackingTouch(seekBar: SeekBar?) {
        PolicyStore.update { it.copy(wakeDuration = seekBar!!.progress) }
      }
    })

    vb.seekLogRetention.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        updateLogRetentionText(progress)
      }
      override fun onStartTrackingTouch(seekBar: SeekBar?) {}
      override fun onStopTrackingTouch(seekBar: SeekBar?) {
        PolicyStore.update { it.copy(logRetentionHours = seekBar!!.progress) }
      }
    })
  }

  private fun updateLogRetentionText(hours: Int) {
    vb.textLogRetention.text = when {
      hours == 0 -> "Off"
      hours <= 24 -> "${hours}h"
      hours <= 168 -> "${hours / 24}d"
      else -> "7d"
    }
  }
}