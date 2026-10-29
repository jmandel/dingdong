# Volume Double-Tap Pause

A companion to DingDong for the nightstand: with the phone on its charger and
audio playing, a quick double-press of volume down pauses playback (with a
short buzz). Useful when a podcast is playing at night and the phone is on a
stand you don't want to pick up.

It only arms when all of these hold, so everyday volume presses are untouched
and never delayed:

- the phone is charging,
- audio is playing, and
- the screen is asleep or showing the screensaver (e.g. the clock on a stand).

## How it works

It's a single accessibility service (`VolPauseService`), no UI.

- **Screensaver:** volume presses reach the service's `onKeyEvent`. Two
  volume-down presses within 500 ms send a media pause. The first press may
  still lower the volume a step, so the volume is restored shortly after.
- **Screen truly asleep:** Android doesn't deliver keys to accessibility
  services, but still adjusts the music volume. Two quick volume-down steps
  seen via the volume-changed broadcast trigger the pause, and the volume is
  restored.

## Build and install

No Gradle; `build.sh` drives aapt2/javac/d8/apksigner from the Android SDK
(build-tools 36, platform android-36) and signs with the debug keystore.

```bash
./build.sh
adb install -r build/vol-pause.apk
# Sideloaded apps can't be enabled from Settings (restricted setting), so via adb:
adb shell settings put secure enabled_accessibility_services com.jmandel.volpause/.VolPauseService
adb shell settings put secure accessibility_enabled 1
```

Logs go to logcat under the `VolPause` tag.
