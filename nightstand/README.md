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

The screen shows a dim red clock that drifts slowly to avoid burn-in, plus the
current episode.

Everything is configurable in the **Nightstand** app (also the gear next to it
in Screen saver settings): auto-start on/off, which headphones, which podcast,
starting and maximum volume, minimum time left, skip length, and a setup
checklist that can fix the screensaver and low-light settings itself.

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
