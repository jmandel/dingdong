package com.dingdong.ui

import com.dingdong.data.Policy
import com.dingdong.media.SoundManager

/** The self-contained prompt copied for an AI chat app: what DingDong is, the format, the current policy. */
object AiPrompt {
  const val LINK_BASE = "https://dingdong.config.joshuamandel.com/dingdong"

  fun build(current: Policy): String = """
You're helping me configure DingDong, an Android app that plays a loud alert sound for the notifications and phone calls that matter to me, even when my phone is quiet. Its whole configuration is the JSON "policy" below.

First ask me what I want to change. Then reply with the complete new policy, in two forms:
1. A link: $LINK_BASE#<the policy JSON, minified and URL-encoded with encodeURIComponent>. Tapping it opens DingDong's review screen.
2. The same policy as one ```json code block. If the link is garbled or doesn't open, I'll copy this block and DingDong will pick it up from the clipboard.
DingDong shows me every change in plain English and changes nothing until I accept.

## Format

{
  "version": 1,
  "rules": [ ...rules, see below... ],
  "sound": one of ${SoundManager.builtInSounds.joinToString(", ") { "\"${it.id}\"" }}, or "custom" (a sound file chosen on the phone),
  "repeat": times to play the sound, 1-5,
  "minVolumePercent": the media volume is raised to at least this for the alert, 0-100,
  "restoreVolume": true to put the volume back afterwards,
  "restoreDelay": seconds to wait before restoring the volume, 0-10,
  "wakeDuration": seconds to keep the phone awake while alerting, 0-20,
  "vibrate": true to also vibrate,
  "logging": true to keep a log of notifications and calls seen,
  "logRetentionHours": how long to keep the log, 0-168
}
Every key is required; unknown keys are rejected.

A rule alerts when every field it sets matches; DingDong alerts when any rule that's on matches. Matching ignores case, spaces and punctuation, and a word matches anywhere inside longer text ("prod" matches "production").
  "name": required, unique, shown to me
  "enabled": true or false (default true)
  "app": the app's name or package must contain this, e.g. "signal", "slack"
  "from": list; the notification title must contain one of these. Messaging apps put the sender in the title ("Carrie", or "Group: Carrie" in a group chat)
  "textAny": list; the title or text must contain at least one
  "textAll": list; the title or text must contain all of them
  "call": digits; instead of notifications, the rule matches incoming calls whose number contains these. A call rule has no other match fields.
A notification rule needs at least one of app, from, textAny or textAll.

Examples:
  {"name": "Carrie on Signal", "app": "signal", "from": ["carrie", "caroline"]}
  {"name": "Prod outages", "app": "slack", "textAll": ["prod", "down"]}
  {"name": "Anything urgent", "textAny": ["urgent", "emergency"]}
  {"name": "Calls from the school", "call": "6085551234"}

## My current policy

```json
${current.toJsonString()}
```
""".trimStart()
}
