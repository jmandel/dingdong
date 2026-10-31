package com.dingdong.data

import android.content.Context
import android.content.SharedPreferences

object RegexStore {
  private const val PREFS_NAME = "regex_alerts_prefs"
  private const val KEY_PHONE_PATTERNS = "phone_patterns"
  private const val KEY_APP_PATTERNS = "app_patterns"
  private const val KEY_CONTENT_PATTERNS = "content_patterns"
  private const val KEY_REQUIRE_BOTH = "require_both"
  private const val KEY_MIN_VOLUME = "min_volume_percent"
  private const val KEY_VOLUME_DELAY = "volume_restore_delay"
  private const val KEY_REPEAT_COUNT = "repeat_count"
  private const val KEY_WAKE_DURATION = "wake_duration"
  private const val KEY_VIBRATE = "vibrate_enabled"
  private const val KEY_RESTORE_VOLUME = "restore_volume"
  private const val KEY_LOGGING_ENABLED = "logging_enabled"
  private const val KEY_LOG_RETENTION_HOURS = "log_retention_hours"

  private lateinit var prefs: SharedPreferences

  @Volatile var phoneNumberPatterns: List<String> = listOf()
  @Volatile var appNamePatterns: List<String> = listOf()
  /**
   * Each line is a list of parts joined by "&"; a line matches when every part
   * appears. A part starting "from:" must appear in the notification title,
   * which messaging apps set to the sender (or "Group: Sender").
   */
  @Volatile var contentPatterns: List<List<ContentPart>> = listOf()

  data class ContentPart(val text: String, val titleOnly: Boolean)
  @Volatile var requireBothAppAndContent: Boolean = false

  fun init(context: Context) {
    prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    loadPatterns()
  }

  private fun loadPatterns() {
    val phonePatterns = prefs.getString(KEY_PHONE_PATTERNS, "555") ?: ""
    phoneNumberPatterns = phonePatterns.split('\n')
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .map { normalizeText(it) }

    val appPatterns = prefs.getString(KEY_APP_PATTERNS, "slack") ?: ""
    appNamePatterns = appPatterns.split('\n')
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .map { normalizeText(it) }

    val contentPatternsStr = prefs.getString(KEY_CONTENT_PATTERNS, "urgent\nproduction down") ?: ""
    contentPatterns = parseContentPatterns(contentPatternsStr)

    requireBothAppAndContent = prefs.getBoolean(KEY_REQUIRE_BOTH, false)
  }

  private fun normalizeText(text: String): String {
    return text.lowercase()
      .filter { it.isLetterOrDigit() }
  }

  fun phoneMatches(phoneNumber: String?): Boolean {
    if (phoneNumber == null || phoneNumberPatterns.isEmpty()) return false
    val normalizedPhone = normalizeText(phoneNumber)
    return phoneNumberPatterns.any { pattern ->
      normalizedPhone.contains(pattern)
    }
  }

  fun notificationMatches(packageName: String, appDisplayName: String?, content: String, title: String = ""): Boolean {
    val normalizedPackage = normalizeText(packageName)
    val normalizedAppName = appDisplayName?.let { normalizeText(it) } ?: ""
    val normalizedContent = normalizeText(content)
    val normalizedTitle = normalizeText(title)

    val appMatches = if (appNamePatterns.isEmpty()) {
      true
    } else {
      appNamePatterns.any { pattern ->
        normalizedPackage.contains(pattern) || normalizedAppName.contains(pattern)
      }
    }

    val contentMatches = if (contentPatterns.isEmpty()) {
      true
    } else {
      contentPatterns.any { parts ->
        parts.all { (if (it.titleOnly) normalizedTitle else normalizedContent).contains(it.text) }
      }
    }

    return if (requireBothAppAndContent) {
      appMatches && contentMatches
    } else {
      (appNamePatterns.isNotEmpty() && appMatches) || (contentPatterns.isNotEmpty() && contentMatches)
    }
  }

  fun updatePhoneRegexes(lines: String) {
    prefs.edit().putString(KEY_PHONE_PATTERNS, lines).apply()
    phoneNumberPatterns = lines.split('\n')
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .map { normalizeText(it) }
  }

  fun updateAppRegexes(lines: String) {
    prefs.edit().putString(KEY_APP_PATTERNS, lines).apply()
    appNamePatterns = lines.split('\n')
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .map { normalizeText(it) }
  }

  fun updateContentRegexes(lines: String) {
    prefs.edit().putString(KEY_CONTENT_PATTERNS, lines).apply()
    contentPatterns = parseContentPatterns(lines)
  }

  private fun parseContentPatterns(lines: String): List<List<ContentPart>> =
    lines.split('\n')
      .map { line ->
        line.split('&').map { raw ->
          val part = raw.trim()
          val titleOnly = part.startsWith("from:", ignoreCase = true)
          ContentPart(normalizeText(if (titleOnly) part.substring(5) else part), titleOnly)
        }.filter { it.text.isNotEmpty() }
      }
      .filter { it.isNotEmpty() }

  fun updateRequireBoth(require: Boolean) {
    prefs.edit().putBoolean(KEY_REQUIRE_BOTH, require).apply()
    requireBothAppAndContent = require
  }

  fun getPhonePatternsText(): String =
    prefs.getString(KEY_PHONE_PATTERNS, "555") ?: ""

  fun getAppPatternsText(): String =
    prefs.getString(KEY_APP_PATTERNS, "slack") ?: ""

  fun getContentPatternsText(): String =
    prefs.getString(KEY_CONTENT_PATTERNS, "urgent\nproduction down") ?: ""

  fun getRequireBoth(): Boolean =
    prefs.getBoolean(KEY_REQUIRE_BOTH, false)

  // Advanced configuration settings
  fun getMinVolumePercent(): Int = prefs.getInt(KEY_MIN_VOLUME, 10)
  fun setMinVolumePercent(value: Int) = prefs.edit().putInt(KEY_MIN_VOLUME, value).apply()

  fun getVolumeRestoreDelay(): Int = prefs.getInt(KEY_VOLUME_DELAY, 3)
  fun setVolumeRestoreDelay(value: Int) = prefs.edit().putInt(KEY_VOLUME_DELAY, value).apply()

  fun getRepeatCount(): Int = prefs.getInt(KEY_REPEAT_COUNT, 1)
  fun setRepeatCount(value: Int) = prefs.edit().putInt(KEY_REPEAT_COUNT, value).apply()

  fun getWakeDuration(): Int = prefs.getInt(KEY_WAKE_DURATION, 7)
  fun setWakeDuration(value: Int) = prefs.edit().putInt(KEY_WAKE_DURATION, value).apply()

  fun getVibrateEnabled(): Boolean = prefs.getBoolean(KEY_VIBRATE, true)
  fun setVibrateEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_VIBRATE, value).apply()

  fun getRestoreVolume(): Boolean = prefs.getBoolean(KEY_RESTORE_VOLUME, true)
  fun setRestoreVolume(value: Boolean) = prefs.edit().putBoolean(KEY_RESTORE_VOLUME, value).apply()

  fun getLoggingEnabled(): Boolean = prefs.getBoolean(KEY_LOGGING_ENABLED, true)
  fun setLoggingEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_LOGGING_ENABLED, value).apply()

  fun getLogRetentionHours(): Int = prefs.getInt(KEY_LOG_RETENTION_HOURS, 24)
  fun setLogRetentionHours(value: Int) = prefs.edit().putInt(KEY_LOG_RETENTION_HOURS, value).apply()
}