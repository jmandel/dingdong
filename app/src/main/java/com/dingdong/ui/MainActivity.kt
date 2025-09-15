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
import com.dingdong.data.RegexStore
import com.dingdong.databinding.ActivityMainBinding
import com.dingdong.media.AlertSound
import com.dingdong.media.SoundManager
import android.widget.SeekBar

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

    RegexStore.init(applicationContext)
    setupSoundSpinner()
    setupAdvancedSettings()

    vb.editPhone.setText(RegexStore.getPhonePatternsText())
    vb.editAppNames.setText(RegexStore.getAppPatternsText())
    vb.editNotificationContent.setText(RegexStore.getContentPatternsText())
    vb.checkRequireBoth.isChecked = RegexStore.getRequireBoth()

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

    vb.btnSave.setOnClickListener {
      RegexStore.updatePhoneRegexes(vb.editPhone.text.toString())
      RegexStore.updateAppRegexes(vb.editAppNames.text.toString())
      RegexStore.updateContentRegexes(vb.editNotificationContent.text.toString())
      RegexStore.updateRequireBoth(vb.checkRequireBoth.isChecked)

      // Save advanced settings
      RegexStore.setMinVolumePercent(vb.seekMinVolume.progress)
      RegexStore.setVolumeRestoreDelay(vb.seekVolumeDelay.progress)
      RegexStore.setRepeatCount(vb.seekRepeatCount.progress)
      RegexStore.setWakeDuration(vb.seekWakeDuration.progress)
      RegexStore.setVibrateEnabled(vb.checkVibrate.isChecked)
      RegexStore.setRestoreVolume(vb.checkRestoreVolume.isChecked)

      Toast.makeText(this, "All settings saved!", Toast.LENGTH_SHORT).show()
    }

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

  private fun setupAdvancedSettings() {
    // Load saved values
    vb.seekMinVolume.progress = RegexStore.getMinVolumePercent()
    vb.textMinVolume.text = "${RegexStore.getMinVolumePercent()}%"

    vb.seekVolumeDelay.progress = RegexStore.getVolumeRestoreDelay()
    vb.textVolumeDelay.text = "${RegexStore.getVolumeRestoreDelay()}s"

    vb.seekRepeatCount.progress = RegexStore.getRepeatCount()
    vb.textRepeatCount.text = "${RegexStore.getRepeatCount()}x"

    vb.seekWakeDuration.progress = RegexStore.getWakeDuration()
    vb.textWakeDuration.text = "${RegexStore.getWakeDuration()}s"

    vb.checkVibrate.isChecked = RegexStore.getVibrateEnabled()
    vb.checkRestoreVolume.isChecked = RegexStore.getRestoreVolume()

    // Set up seekbar listeners
    vb.seekMinVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        vb.textMinVolume.text = "$progress%"
      }
      override fun onStartTrackingTouch(seekBar: SeekBar?) {}
      override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    })

    vb.seekVolumeDelay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        vb.textVolumeDelay.text = "${progress}s"
      }
      override fun onStartTrackingTouch(seekBar: SeekBar?) {}
      override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    })

    vb.seekRepeatCount.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        vb.textRepeatCount.text = "${progress}x"
      }
      override fun onStartTrackingTouch(seekBar: SeekBar?) {}
      override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    })

    vb.seekWakeDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        vb.textWakeDuration.text = "${progress}s"
      }
      override fun onStartTrackingTouch(seekBar: SeekBar?) {}
      override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    })
  }
}