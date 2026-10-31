# DingDong - Android Notification Breakthrough

Break through silent mode for important calls and notifications on Android.

## Features

- **Breakthrough Silent Mode**: Plays custom alert sounds even when phone is on silent/vibrate
- **Alert rules**: Match notifications by app, sender and words, and calls by number
- **Policy as data**: All settings are one JSON policy, edited in the app or by any AI chat app
- **36 Built-in Sounds**: High-quality notification sounds from the akx/Notifications collection
- **Custom Sounds**: Add your own MP3 files
- **Advanced Configuration**:
  - Minimum volume level (1-100%)
  - Volume restore delay (0-10 seconds)
  - Sound repeat count (1-5 times)
  - Wake lock duration (1-20 seconds)
  - Vibration toggle
  - Volume restoration toggle
- **Event Logging**:
  - Track all calls and notifications
  - See what matched and what triggered alerts
  - Configurable retention (1 hour to 7 days)
  - Enable/disable logging for privacy

## Alert Rules

DingDong's whole configuration is one JSON **policy**: a list of rules plus
the alert settings (sound, repeat, minimum volume, and so on). The settings
screen edits the same policy, so anything set by hand can be set by JSON and
the other way round.

A rule alerts when every field it sets matches; DingDong alerts when any rule
that's on matches.

| Field | Matches when |
|---|---|
| `app` | the app's name or package contains it |
| `from` | the title contains any of these; messaging apps put the sender there ("Carrie", or "Group: Carrie") |
| `textAny` | the title or text contains at least one of these |
| `textAll` | the title or text contains all of these |
| `call` | an incoming caller's number contains these digits (call rules have no other match fields) |

Matching ignores case, spaces and punctuation, and finds words inside longer
text: "prod" matches "production", "(555) 123-4567" contains "5551234".

```json
{
  "version": 1,
  "rules": [
    {"name": "Carrie on Signal", "app": "signal", "from": ["carrie", "caroline"]},
    {"name": "Prod outages", "app": "slack", "textAll": ["prod", "down"]},
    {"name": "Calls from 47822", "call": "47822"}
  ],
  "sound": "koto", "repeat": 1, "minVolumePercent": 24,
  "restoreVolume": true, "restoreDelay": 5, "wakeDuration": 10,
  "vibrate": false, "logging": true, "logRetentionHours": 12
}
```

Ongoing notifications, media players and group summaries (Signal's "Most
recent from: Carrie") are never matched.

Upgrading from the old text-box filters converts them to rules automatically.

### Editing with an AI

1. **Copy prompt for AI** copies a prompt describing DingDong, this format,
   and your current policy.
2. Paste it into any AI chat app and say what you want changed.
3. The AI replies with a link and a ```json block. Tap the link, or copy the
   reply and return to DingDong: it notices a policy on the clipboard and
   offers to review it (**Paste settings** does the same by hand).
4. The review screen lists every change in plain English. Nothing changes
   until you tap **Accept**; invalid policies are rejected with the reason.

Links look like `https://dingdong.config.joshuamandel.com/dingdong#<URL-encoded JSON>`
(`?json=` also works, as does `dingdong://policy?json=...`). The domain isn't
verified, so approve it once on the phone:

```bash
adb shell pm set-app-links-user-selection --user 0 --package com.dingdong true dingdong.config.joshuamandel.com
```

Android lets only one app claim an unverified domain, so each app has its own
host (Nightstand uses another), and the approval can reset when the app is updated.


## Installation

1. Build the APK:
   ```bash
   ./gradlew assembleDebug
   ```

2. Install on device:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## Required Permissions

- **Notification Access**: To monitor incoming notifications
- **Phone State & Call Log**: To detect incoming calls
- **Post Notifications**: To show service notifications (Android 13+)

## Setup

1. Grant all required permissions through the app
2. Add alert rules (by hand, or with an AI as above)
3. Choose your alert sound
4. Adjust advanced settings as needed; changes apply immediately

## Privacy

- All filtering happens locally on device
- No data is sent to external servers
- Event logs are stored locally and auto-deleted based on retention settings
- Logs can be disabled entirely for privacy

## Development

Built with:
- Kotlin
- Android SDK 35
- Material Design 3
- 36 open-source notification sounds from akx/Notifications

## Companion: Nightstand

[`nightstand/`](nightstand/) is a screensaver for sleepless nights: turn on the
Bose headphones and the newest unfinished Spilled Milk episode starts quietly
through them; tap to pause, double/triple-tap to skip ±30 s, drag sideways for
volume (capped at 20%). See its README.

## License

Open source - includes notification sounds from the akx/Notifications repository.

---

🤖 Generated with [Claude Code](https://claude.ai/code)

Co-Authored-By: Claude <noreply@anthropic.com>
