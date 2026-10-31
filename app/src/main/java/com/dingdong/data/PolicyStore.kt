package com.dingdong.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Holds the current [Policy], saved as JSON in SharedPreferences. On first
 * run it builds the policy from the old free-text pattern settings.
 */
object PolicyStore {
  private const val PREFS_NAME = "regex_alerts_prefs"
  private const val KEY_POLICY = "policy_json"

  private lateinit var prefs: SharedPreferences

  @Volatile var policy: Policy = Policy(emptyList())
    private set

  fun init(context: Context) {
    prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    Policy.soundIds = com.dingdong.media.SoundManager.builtInSounds.map { it.id }.toSet()
    val saved = prefs.getString(KEY_POLICY, null)
    policy = saved?.let { runCatching { Policy.parse(it) }.getOrNull() } ?: migrate().also { save(it) }
  }

  fun save(p: Policy) {
    policy = p
    prefs.edit().putString(KEY_POLICY, p.toJsonString()).apply()
  }

  fun update(change: (Policy) -> Policy) = save(change(policy))

  fun phoneMatches(number: String?): Boolean =
    number != null && policy.rules.any { it.matchesCall(number) }

  /** The first enabled rule this notification satisfies, if any. */
  fun matchingRule(pkg: String, appName: String?, title: String, content: String): Policy.Rule? =
    policy.rules.firstOrNull { it.matchesNotification(pkg, appName.orEmpty(), title, content) }

  // Settings read by the alert pipeline.
  fun getMinVolumePercent() = policy.minVolumePercent
  fun getVolumeRestoreDelay() = policy.restoreDelay
  fun getRepeatCount() = policy.repeat
  fun getWakeDuration() = policy.wakeDuration
  fun getVibrateEnabled() = policy.vibrate
  fun getRestoreVolume() = policy.restoreVolume
  fun getLoggingEnabled() = policy.logging
  fun getLogRetentionHours() = policy.logRetentionHours

  /**
   * Converts the old settings: one-per-line phone patterns, app patterns and
   * content patterns (parts joined by "&", "from:" for the title), combined
   * by the "require both app and content" switch.
   */
  private fun migrate(): Policy {
    fun lines(key: String, default: String) =
      (prefs.getString(key, default) ?: "").split('\n').map { it.trim() }.filter { Policy.normalize(it).isNotEmpty() }

    val phones = lines("phone_patterns", "555")
    val apps = lines("app_patterns", "slack")
    val contents = lines("content_patterns", "urgent\nproduction down").map { line ->
      line.split('&').map { it.trim() }.filter { Policy.normalize(it).isNotEmpty() }.partition {
        it.startsWith("from:", ignoreCase = true)
      }.let { (from, text) -> from.map { it.substring(5).trim() } to text }
    }
    val requireBoth = prefs.getBoolean("require_both", false)

    // Lines that are a single "from:" collapse into one rule listing every sender.
    val (senderOnly, others) = contents.partition { (from, text) -> from.size == 1 && text.isEmpty() }
    val senders = senderOnly.map { it.first.single() }
    fun contentRules(app: String?): List<Policy.Rule> {
      val out = mutableListOf<Policy.Rule>()
      if (senders.isNotEmpty()) out += Policy.Rule(
        name = senders.first().replaceFirstChar { it.uppercase() } + (app?.let { " on ${it.replaceFirstChar { c -> c.uppercase() }}" } ?: ""),
        app = app, from = senders)
      others.forEach { (from, text) ->
        out += Policy.Rule(name = ((from + text).joinToString(" & ")) + (app?.let { " on $it" } ?: ""),
          app = app, from = from, textAll = text)
      }
      return out
    }

    val rules = mutableListOf<Policy.Rule>()
    if (requireBoth && apps.isNotEmpty() && contents.isNotEmpty()) {
      apps.forEach { rules += contentRules(it) }
    } else {
      apps.forEach { rules += Policy.Rule(name = "All ${it.replaceFirstChar { c -> c.uppercase() }} notifications", app = it) }
      rules += contentRules(null)
    }
    phones.forEach { rules += Policy.Rule(name = "Calls from $it", call = it) }

    return Policy(
      rules = rules.distinctBy { it.name },
      sound = prefs.getString("selected_sound_id", "alarmed") ?: "alarmed",
      repeat = prefs.getInt("repeat_count", 1).coerceIn(1, 5),
      minVolumePercent = prefs.getInt("min_volume_percent", 10),
      restoreVolume = prefs.getBoolean("restore_volume", true),
      restoreDelay = prefs.getInt("volume_restore_delay", 3),
      wakeDuration = prefs.getInt("wake_duration", 7),
      vibrate = prefs.getBoolean("vibrate_enabled", true),
      logging = prefs.getBoolean("logging_enabled", true),
      logRetentionHours = prefs.getInt("log_retention_hours", 24),
    )
  }
}
