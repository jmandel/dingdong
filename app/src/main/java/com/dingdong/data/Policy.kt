package com.dingdong.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * DingDong's whole configuration as data: which notifications and calls alert,
 * and how the alert sounds. The settings screen edits this object, and it
 * round-trips through JSON so a language model can propose a new one.
 */
data class Policy(
  val rules: List<Rule>,
  val sound: String = "alarmed",
  val repeat: Int = 1,
  val minVolumePercent: Int = 10,
  val restoreVolume: Boolean = true,
  val restoreDelay: Int = 3,
  val wakeDuration: Int = 7,
  val vibrate: Boolean = true,
  val logging: Boolean = true,
  val logRetentionHours: Int = 24,
) {
  /**
   * One alert rule. A notification rule fires when every field it sets
   * matches; a call rule (only `call` set) fires for incoming numbers.
   * All matching is lowercase, letters and digits only, substring.
   */
  data class Rule(
    val name: String,
    val enabled: Boolean = true,
    /** Matches the app's name or package. */
    val app: String? = null,
    /** Any of these must appear in the title, which messaging apps set to the sender. */
    val from: List<String> = emptyList(),
    /** Any of these must appear in the title or text. */
    val textAny: List<String> = emptyList(),
    /** All of these must appear in the title or text. */
    val textAll: List<String> = emptyList(),
    /** Digits that must appear in an incoming caller's number. */
    val call: String? = null,
  ) {
    val isCall get() = call != null

    fun matchesNotification(pkg: String, appName: String, title: String, content: String): Boolean {
      if (!enabled || isCall) return false
      val t = normalize(title)
      val c = normalize(content)
      if (app != null && !(normalize(pkg).contains(normalize(app)) || normalize(appName).contains(normalize(app)))) return false
      if (from.isNotEmpty() && from.none { t.contains(normalize(it)) }) return false
      if (textAny.isNotEmpty() && textAny.none { c.contains(normalize(it)) }) return false
      if (textAll.any { !c.contains(normalize(it)) }) return false
      return true
    }

    fun matchesCall(number: String): Boolean =
      enabled && call != null && normalize(number).contains(normalize(call))

    /** Plain-English summary, e.g. "Signal notifications from carrie or caroline". */
    fun describe(): String {
      if (call != null) return "calls from numbers containing $call"
      val parts = mutableListOf((app?.let { "$it notifications" } ?: "notifications from any app"))
      if (from.isNotEmpty()) parts += "from ${from.joinToString(" or ")}"
      if (textAny.isNotEmpty()) parts += "mentioning ${textAny.joinToString(" or ")}"
      if (textAll.isNotEmpty()) parts += "mentioning all of ${textAll.joinToString(", ")}"
      return parts.joinToString(" ")
    }

    fun toJson(): JSONObject = JSONObject().apply {
      put("name", name)
      put("enabled", enabled)
      app?.let { put("app", it) }
      if (from.isNotEmpty()) put("from", JSONArray(from))
      if (textAny.isNotEmpty()) put("textAny", JSONArray(textAny))
      if (textAll.isNotEmpty()) put("textAll", JSONArray(textAll))
      call?.let { put("call", it) }
    }
  }

  fun toJson(): JSONObject = JSONObject().apply {
    put("version", 1)
    put("rules", JSONArray(rules.map { it.toJson() }))
    put("sound", sound)
    put("repeat", repeat)
    put("minVolumePercent", minVolumePercent)
    put("restoreVolume", restoreVolume)
    put("restoreDelay", restoreDelay)
    put("wakeDuration", wakeDuration)
    put("vibrate", vibrate)
    put("logging", logging)
    put("logRetentionHours", logRetentionHours)
  }

  fun toJsonString(): String = toJson().toString(2)

  /** Plain-English list of what changes going from this policy to [next]. */
  fun changesTo(next: Policy): List<String> {
    val out = mutableListOf<String>()
    val before = rules.associateBy { it.name }
    val after = next.rules.associateBy { it.name }
    for (r in next.rules) {
      val old = before[r.name]
      when {
        old == null -> out += "Adds rule “${r.name}”: ${r.describe()}${if (r.enabled) "" else " (off)"}"
        old.copy(enabled = r.enabled) != r -> out += "Changes “${r.name}”: was ${old.describe()}, now ${r.describe()}"
      }
      if (old != null && old.enabled != r.enabled) out += "Turns “${r.name}” ${if (r.enabled) "on" else "off"}"
    }
    for (r in rules) if (r.name !in after) out += "Removes rule “${r.name}”: ${r.describe()}"
    fun <T> setting(label: String, a: T, b: T, fmt: (T) -> String = { it.toString() }) {
      if (a != b) out += "$label: ${fmt(a)} → ${fmt(b)}"
    }
    val onOff = { v: Boolean -> if (v) "on" else "off" }
    setting("Sound", sound, next.sound)
    setting("Plays", repeat, next.repeat) { "${it}×" }
    setting("Minimum volume", minVolumePercent, next.minVolumePercent) { "$it%" }
    setting("Restore volume afterwards", restoreVolume, next.restoreVolume, onOff)
    setting("Restore delay", restoreDelay, next.restoreDelay) { "${it}s" }
    setting("Keep awake for", wakeDuration, next.wakeDuration) { "${it}s" }
    setting("Vibrate", vibrate, next.vibrate, onOff)
    setting("Event log", logging, next.logging, onOff)
    setting("Keep log for", logRetentionHours, next.logRetentionHours) { "${it}h" }
    return out
  }

  companion object {
    fun normalize(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    private val TOP_KEYS = setOf("version", "rules", "sound", "repeat", "minVolumePercent", "restoreVolume",
      "restoreDelay", "wakeDuration", "vibrate", "logging", "logRetentionHours")
    private val RULE_KEYS = setOf("name", "enabled", "app", "from", "textAny", "textAll", "call")

    /** Parses and strictly validates a policy; throws [IllegalArgumentException] saying what's wrong. */
    fun parse(json: String): Policy {
      val o = try { JSONObject(json) } catch (e: JSONException) { throw IllegalArgumentException("Not valid JSON: ${e.message}") }
      (o.keys().asSequence().toSet() - TOP_KEYS).firstOrNull()?.let { bad("Unknown setting \"$it\"") }
      (TOP_KEYS - o.keys().asSequence().toSet()).firstOrNull()?.let { bad("Missing \"$it\"") }
      if (o.opt("version") != 1) bad("\"version\" must be 1")
      val arr = o.opt("rules") as? JSONArray ?: bad("\"rules\" must be a list")
      val rules = (0 until arr.length()).map { i ->
        val r = arr.opt(i) as? JSONObject ?: bad("Rule ${i + 1} isn't an object")
        parseRule(r, i + 1)
      }
      rules.groupBy { it.name }.filter { it.value.size > 1 }.keys.firstOrNull()?.let { bad("Two rules are named \"$it\"") }
      return Policy(
        rules = rules,
        sound = str(o, "sound").also { if (it != "custom" && soundIds.isNotEmpty() && it !in soundIds) bad("Unknown sound \"$it\"") },
        repeat = int(o, "repeat", 1..5),
        minVolumePercent = int(o, "minVolumePercent", 0..100),
        restoreVolume = bool(o, "restoreVolume"),
        restoreDelay = int(o, "restoreDelay", 0..10),
        wakeDuration = int(o, "wakeDuration", 0..20),
        vibrate = bool(o, "vibrate"),
        logging = bool(o, "logging"),
        logRetentionHours = int(o, "logRetentionHours", 0..168),
      )
    }

    /** Finds a policy in pasted text: the whole text, or a ```json block inside it. Null if none. */
    fun find(text: String): Policy? {
      val candidates = Regex("```(?:json)?\\s*([\\s\\S]*?)```").findAll(text).map { it.groupValues[1] } +
        sequenceOf(text.trim())
      return candidates.firstNotNullOfOrNull { runCatching { parse(it) }.getOrNull() }
    }

    /** Built-in sound ids; set by SoundManager so this file stays free of Android resources. */
    @Volatile var soundIds: Set<String> = emptySet()

    private fun parseRule(r: JSONObject, n: Int): Rule {
      (r.keys().asSequence().toSet() - RULE_KEYS).firstOrNull()?.let { bad("Rule $n: unknown field \"$it\"") }
      val name = str(r, "name").trim().ifEmpty { bad("Rule $n has an empty name") }
      val rule = Rule(
        name = name,
        enabled = if (r.has("enabled")) bool(r, "enabled") else true,
        app = if (r.has("app")) str(r, "app") else null,
        from = list(r, "from"),
        textAny = list(r, "textAny"),
        textAll = list(r, "textAll"),
        call = if (r.has("call")) str(r, "call") else null,
      )
      if (rule.call != null) {
        if (normalize(rule.call).isEmpty()) bad("“$name”: \"call\" needs digits")
        if (rule.app != null || rule.from.isNotEmpty() || rule.textAny.isNotEmpty() || rule.textAll.isNotEmpty())
          bad("“$name”: a call rule can't also have app/from/textAny/textAll")
      } else if (rule.app == null && rule.from.isEmpty() && rule.textAny.isEmpty() && rule.textAll.isEmpty()) {
        bad("“$name” matches nothing; set app, from, textAny, textAll or call")
      }
      listOfNotNull(rule.app).plus(rule.from).plus(rule.textAny).plus(rule.textAll).forEach {
        if (normalize(it).isEmpty()) bad("“$name”: \"$it\" has no letters or digits to match")
      }
      return rule
    }

    private fun bad(msg: String): Nothing = throw IllegalArgumentException(msg)
    private fun str(o: JSONObject, k: String) = o.opt(k) as? String ?: bad("\"$k\" must be text")
    private fun bool(o: JSONObject, k: String) = o.opt(k) as? Boolean ?: bad("\"$k\" must be true or false")
    private fun int(o: JSONObject, k: String, range: IntRange): Int {
      val v = o.opt(k)
      if (v !is Int) bad("\"$k\" must be a whole number")
      if (v !in range) bad("\"$k\" must be between ${range.first} and ${range.last}")
      return v
    }
    private fun list(o: JSONObject, k: String): List<String> {
      if (!o.has(k)) return emptyList()
      val a = o.opt(k) as? JSONArray ?: bad("\"$k\" must be a list of text")
      return (0 until a.length()).map { a.opt(it) as? String ?: bad("\"$k\" must be a list of text") }
    }
  }
}
