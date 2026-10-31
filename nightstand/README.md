# Nightstand

A screensaver for sleepless nights. With the phone on its stand showing
Nightstand, turning on the Bose headphones starts a podcast through them at a
very low volume. No buttons to press, nothing out loud, no vibration.

- **Turn on the Bose:** plays the newest unfinished *Spilled Milk* episode
  with at least 15 minutes left (an in-progress one resumes), at volume 2/25.
- **Tap:** pause / resume.
- **Double-tap:** forward 30 s. **Triple-tap:** back 30 s.
- **Drag sideways:** volume, right = louder. Capped at 20%.
- **Long-press:** leave the screensaver.
- **Turn off the Bose:** pauses.

If a podcast is already playing on the speaker when the screensaver starts,
it keeps playing and the gestures control it too. A tap pauses it; tapping
again resumes it on the speaker only within a minute (a quick undo), so a
stray tap later in the night can't fill the room.

The screen shows a dim clock that drifts slowly to avoid burn-in, plus the
current episode. Color, brightness, thickness, size, 12/24-hour, the episode
line and drift are all adjustable, with a live preview and a full-screen one.
By default the screen is held at its lowest backlight while Nightstand shows,
so it looks the same in any room and the full-screen preview is exact. (Apps
can't start the real screensaver while the phone is unplugged.)

Everything is configurable in the **Nightstand** app (also the gear next to it
in Screen saver settings), including a switch between Nightstand and Android's
usual clock screensaver (switching off puts back the previous screensaver and
Pixel's low-light clock) and a setup checklist.

## Settings as data

All settings are one JSON object (`Settings.FIELDS` describes every field:
default, range, help text). The settings screen edits it field by field, or
you can work on it as text:

- **Copy prompt for AI** copies a self-contained prompt: what Nightstand does,
  every field, an example, and your current settings. Paste it into any AI chat
  app and say what you want.
- The AI replies with a link,
  `https://nightstand.config.joshuamandel.com/nightstand#<url-encoded JSON>`, and the
  same JSON as a code block. Tapping the link opens Nightstand. Copying the
  reply and returning to Nightstand also works.
- Either way Nightstand lists the changes in plain English and applies them
  only on **Apply**. Invalid values and unknown fields are rejected with
  reasons.

The link host isn't domain-verified (nothing is hosted there; the JSON stays in
the URL fragment, which browsers don't send). Approve it once per install:

```bash
adb shell pm set-app-links-user-selection --user 0 --package com.jmandel.nightstand true nightstand.config.joshuamandel.com
```

Android lets only one app claim an unverified domain, so each app has its own
host (DingDong uses another), and the approval can reset when the app is updated.


## Safety

A new episode only starts, and headphone playback only resumes, when the
chosen headphones are the Bluetooth media output and no other Bluetooth
output is connected. If headphone playback loses that route (headphones off,
route change) it pauses at once; a guard re-checks every 2 s as well.
Dragging up never passes the volume cap, on any output. Nothing vibrates.

## How it works

- `NightDream` is a `DreamService` (screensaver). An `AudioDeviceCallback`
  notices the Bose connecting and disconnecting.
- Playback goes through AntennaPod's media browser
  (`Media3PlaybackService`; the older `PlaybackService` refuses outside apps).
  Browser media IDs are AntennaPod `FeedMedia` ids.
- The browser doesn't expose played state or positions, so `AntennaPodExport`
  reads the newest `AntennaPodBackup-*.db` in Documents (AntennaPod's automatic
  database export, set to every 3 days). Nightstand also records positions and
  finished episodes from its own playback to cover the time since the export.
- Taps are counted by hand (GestureDetector has no triple tap). Volume is a
  sideways drag because the system takes vertical swipes over a screensaver
  (swipe up to unlock, down for the shade).

Settings live in `Settings` (SharedPreferences) and are re-read each time the
screensaver starts.

## Build and set up

No Gradle; `build.sh` uses the SDK's build-tools 36 / android-36 and the debug
keystore.

```bash
./build.sh
adb install -r build/nightstand.apk
# Read AntennaPod's export in Documents:
adb shell appops set com.jmandel.nightstand MANAGE_EXTERNAL_STORAGE allow
# Make it the screensaver:
adb shell settings put secure screensaver_components com.jmandel.nightstand/.NightDream
# Pixels swap in a "low-light clock" instead of the screensaver in a dark room; turn that off:
adb shell settings put secure low_light_display_behavior_enabled 0
# Let the settings screen list paired headphones and fix the two settings above itself:
adb shell pm grant com.jmandel.nightstand android.permission.BLUETOOTH_CONNECT
adb shell pm grant com.jmandel.nightstand android.permission.WRITE_SECURE_SETTINGS
```

Logs go to logcat under the `Nightstand` tag. To inspect what AntennaPod
exposes: `adb shell am broadcast -n com.jmandel.nightstand/.DebugReceiver --es id <mediaId>`
(omit `id` for the root; try `subscriptions`, then a `FeedId:N`).
