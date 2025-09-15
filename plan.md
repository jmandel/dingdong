Got it — you want a **drop‑in, self‑contained guide** (with paste‑ready code) for an Android app that:

* **Plays a short sound** whenever (1) an **incoming call** number matches your regex or (2) any **notification text/title** matches your regex.
* **Keeps the phone’s ringer off** (silent/vibrate is fine). We **do not** toggle ringer mode; we only ensure **media volume** is audible for the brief playback and then restore it.
* Uses only the **permissions actually required**.

Below is the complete setup + code. I’ll also explain **why media volume is all you need** (and what permission it takes).

---

## TL;DR on *ringer vs media* (your clarification)

* Android tracks **separate volume streams** (Media, Ring, Notification, Alarm, Call, …). The **ringer/silent switch only affects ring/notification** sounds; **media (music) is separate**. So you can **leave ringer off** and still play a loud media sound as long as **`STREAM_MUSIC` > 0**. ([Google Help][1])
* To adjust media volume programmatically, you need **`MODIFY_AUDIO_SETTINGS`**. That’s a **normal install‑time** permission (no runtime prompt). The AOSP manifest explicitly lists `android.permission.MODIFY_AUDIO_SETTINGS` with **protectionLevel="normal"**. ([Android Git Repositories][2])

---

## What you’ll build (architecture)

```
app/
 ├─ ui/MainActivity                // Settings UI to enter regexes & open system permission screens
 ├─ notifications/RegexNotificationListener : NotificationListenerService
 ├─ calls/RegexCallScreeningService : CallScreeningService
 ├─ media/AudioControllerMediaOnly // Save/restore STREAM_MUSIC only (no ringer changes)
 ├─ media/SoundPlayer              // Plays a bundled raw/ MP3 as USAGE_MEDIA
 ├─ media/AlertOrchestrator        // Serializes: ensure media audible → play → restore
 └─ data/RegexStore                // In-memory regex lists (swap for DataStore/Room later)
```

**Key flows**

* **Notification posted** → read `Notification.extras` (`EXTRA_TITLE`, `EXTRA_TEXT`, etc.) → regex match → `AlertOrchestrator.trigger(...)`. ([Android Developers][3])
* **Incoming call** → `CallScreeningService.onScreenCall()` → normalize to **E.164** (libphonenumber‑android) → regex match → (optionally) `setSilenceCall(true)` so the **system ringtone remains quiet** while we play our media tone → `AlertOrchestrator.trigger(...)`. ([Android Developers][4])

> **Why we can skip ringer toggles:** You’re playing through **media**. As long as media volume isn’t zero, the tone will be heard even if ringer is silent and DND is off. ([Google Help][1])

---

## 1) Create a fresh project (current tools)

1. **Install Android Studio** (Narwhal 3 Feature Drop | 2025.1.3 or newer). ([Android Developers][5])
2. **New Project** → *Empty Views Activity* (or *Empty Compose Activity* if you prefer Compose UI).
3. Choose **Kotlin**, **minSdk 26+**, **compileSdk 36** (Android 16), **targetSdk 36**. (You can target lower; 36 is current stable.) ([Android Developers][6])
4. In **Gradle**, use a recent AGP (8.13 is latest stable as of Sept 2025). ([Maven Repository][7])

**`settings.gradle.kts`** – keep default.

**Project `build.gradle.kts`** – keep default.

**Module `app/build.gradle.kts`** (Kotlin DSL):

```kotlin
plugins {
  id("com.android.application")
  kotlin("android")
}

android {
  namespace = "com.example.regexalerts"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.example.regexalerts"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }

  buildFeatures { viewBinding = true } // or Compose, if you prefer
  kotlinOptions { jvmTarget = "17" }
}

dependencies {
  implementation("io.michaelrocks:libphonenumber-android:9.0.12")  // E.164 normalization
}
```

(`libphonenumber-android` latest series & coordinates shown here.) ([Maven Central][8])

---

## 2) Add the required permissions, roles, and services

**`AndroidManifest.xml`** (put inside `<manifest>`/`<application>`)

```xml
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.VIBRATE" />

<!-- Optional: only if you post notifications (e.g., a tiny FGS notification) on Android 13+ -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- For optional Foreground Service fallback on some OEMs -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

<application
    android:allowBackup="true"
    android:label="@string/app_name"
    android:supportsRtl="true"
    android:theme="@style/Theme.Material3.DayNight.NoActionBar">

  <!-- 1) Notification listener (user must enable in Settings) -->
  <service
      android:name=".notifications.RegexNotificationListener"
      android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
      android:exported="true">
    <intent-filter>
      <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
  </service>

  <!-- 2) Call screening (user must grant CALL_SCREENING role) -->
  <service
      android:name=".calls.RegexCallScreeningService"
      android:permission="android.permission.BIND_SCREENING_SERVICE"
      android:exported="true">
    <intent-filter>
      <action android:name="android.telecom.CallScreeningService" />
    </intent-filter>
  </service>

  <!-- 3) (Optional) Tiny Foreground Service for playback robustness -->
  <service
      android:name=".media.AlertPlayerService"
      android:exported="false"
      android:foregroundServiceType="mediaPlayback|shortService" />
</application>
```

* **Notification listener access** is enabled by the user in system Settings (you’ll deep‑link there from the app). ([Microsoft Learn][9])
* **Call screening** uses the **CALL\_SCREENING role** (again granted by user) via `RoleManager`. ([Android Developers][10])
* **POST\_NOTIFICATIONS** is a **runtime** permission on Android 13+ if you show your own notifications (e.g., an FGS notification). ([Android Developers][11])
* If you later decide to start a short **Foreground Service** to play your sound, Android 14+ requires the **service types** on the manifest and the corresponding permissions, as declared above. ([Android Developers][12])

---

## 3) Paste these Kotlin files

### 3.1 `data/RegexStore.kt`

```kotlin
package com.example.regexalerts.data

object RegexStore {
  @Volatile var phoneNumberPatterns: List<Regex> = listOf(
    // Example: E.164 U.S. numbers starting with +1 555...
    Regex("""^\+1555\d{7}$""")
  )
  @Volatile var notificationPatterns: List<Regex> = listOf(
    Regex("""(?i)\bprod(uction)?\s+down\b""")
  )

  fun phoneMatches(e164: String?): Boolean =
    e164 != null && phoneNumberPatterns.any { it.containsMatchIn(e164) }

  fun textMatches(s: String): Boolean =
    notificationPatterns.any { it.containsMatchIn(s) }

  fun updatePhoneRegexes(lines: String) {
    phoneNumberPatterns = lines.split('\n')
      .mapNotNull { it.trim().takeIf { it.isNotEmpty() } }
      .mapNotNull { runCatching { Regex(it) }.getOrNull() }
  }

  fun updateNotificationRegexes(lines: String) {
    notificationPatterns = lines.split('\n')
      .mapNotNull { it.trim().takeIf { it.isNotEmpty() } }
      .mapNotNull { runCatching { Regex(it) }.getOrNull() }
  }
}
```

> This keeps it simple (in‑memory). Swap for **Jetpack DataStore** later if you want persistence.

---

### 3.2 `notifications/RegexNotificationListener.kt`

```kotlin
package com.example.regexalerts.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.regexalerts.data.RegexStore
import com.example.regexalerts.media.AlertOrchestrator

class RegexNotificationListener : NotificationListenerService() {

  override fun onNotificationPosted(sbn: StatusBarNotification) {
    val n: Notification = sbn.notification ?: return
    val extras = n.extras
    val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
    val text  = extras?.getCharSequence(Notification.EXTRA_TEXT )?.toString().orEmpty()
    val big   = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
    val content = sequenceOf(title, text, big).filter { it.isNotBlank() }.joinToString(" • ")

    if (content.isNotBlank() && RegexStore.textMatches(content)) {
      AlertOrchestrator.trigger(applicationContext, reason = "NOTIFICATION",
        label = "${sbn.packageName}: $title")
    }
  }
}
```

> `NotificationListenerService` lets you read other apps’ notifications once the user grants access; `EXTRA_TITLE` and `EXTRA_TEXT` are the standard keys in `Notification.extras`. ([Android Developers][3])

---

### 3.3 `calls/RegexCallScreeningService.kt`

```kotlin
package com.example.regexalerts.calls

import android.net.Uri
import android.telecom.Call
import android.telecom.CallScreeningService
import com.example.regexalerts.data.RegexStore
import com.example.regexalerts.media.AlertOrchestrator
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil

class RegexCallScreeningService : CallScreeningService() {

  override fun onScreenCall(details: Call.Details) {
    if (details.callDirection != Call.Details.DIRECTION_INCOMING) return

    val handle: Uri? = details.handle          // e.g., tel:+12133734253
    val raw = handle?.schemeSpecificPart ?: return

    val e164 = try {
      val util = PhoneNumberUtil.createInstance(this)
      val parsed = util.parse(raw, null)
      util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164) // +12133734253
    } catch (_: Throwable) { raw }

    if (RegexStore.phoneMatches(e164)) {
      // Keep OS ring suppressed to avoid overlap with your media tone
      val response = CallResponse.Builder()
          .setSilenceCall(true)
          .setRejectCall(false)
          .setDisallowCall(false)
          .setSkipCallLog(false)
          .setSkipNotification(false)
          .build()
      respondToCall(details, response)

      AlertOrchestrator.trigger(applicationContext, reason = "CALL", label = e164)
    }
  }
}
```

> `CallScreeningService` + **CALL\_SCREENING role** delivers incoming call details and lets you **silence** the ring (`setSilenceCall(true)`) while still showing the incoming call UI. ([Android Developers][4])

---

### 3.4 `media/AudioControllerMediaOnly.kt` (no ringer changes)

```kotlin
package com.example.regexalerts.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.PowerManager
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Ensures MEDIA is audible for a one-shot tone, without touching ringer mode.
 * Saves & restores STREAM_MUSIC volume only.
 */
class AudioControllerMediaOnly(private val ctx: Context) {
  private val am = ctx.getSystemService(AudioManager::class.java)

  private var savedMusicVol: Int = am.getStreamVolume(AudioManager.STREAM_MUSIC)
  private var focus: AudioFocusRequest? = null
  private var wake: PowerManager.WakeLock? = null

  /** Raise STREAM_MUSIC to a minimal audible step if needed, then grab transient audio focus. */
  fun prepare(targetMinPercent: Int = 10) {
    // Light PARTIAL_WAKE_LOCK so the CPU doesn't sleep mid-tone.
    val pm = ctx.getSystemService(PowerManager::class.java)
    wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "regexalerts:play").apply {
      setReferenceCounted(false); acquire(7_000)
    }

    // If media volume is 0, lift to a small percent of max (or at least 1 step).
    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val minTarget = max(1, (maxVol * (targetMinPercent / 100.0)).roundToInt())
    if (am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
      am.setStreamVolume(AudioManager.STREAM_MUSIC, minTarget, 0)
    }

    // Grab transient audio focus so others duck/pause briefly.
    val attrs = AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_MEDIA) // media usage (you keep ringer off)
      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
      .build()
    val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
      .setAudioAttributes(attrs)
      .setOnAudioFocusChangeListener { /* one-shot: ignore */ }
      .build()
    if (am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      focus = req
    }
  }

  /** Put the stream back exactly where it was; drop focus & wakelock. */
  fun restore() {
    am.setStreamVolume(AudioManager.STREAM_MUSIC, savedMusicVol, 0)
    focus?.let { am.abandonAudioFocusRequest(it) }
    wake?.let { if (it.isHeld) it.release() }
  }
}
```

> Adjusting media volume uses `AudioManager.setStreamVolume(STREAM_MUSIC, …)` and transient **audio focus** is the recommended etiquette. No ringer mode changes are needed. ([Android Developers][13])

---

### 3.5 `media/SoundPlayer.kt`

```kotlin
package com.example.regexalerts.media

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.example.regexalerts.R

object SoundPlayer {
  /** Blocks until completion; keep your tone short (1–3 s). */
  fun playOnce(ctx: Context) {
    val mp = MediaPlayer()
    mp.setAudioAttributes(
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA) // play as media (not ring)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    )
    val afd = ctx.resources.openRawResourceFd(R.raw.alert_tone)
    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
    afd.close()

    mp.setOnCompletionListener { it.release() }
    mp.prepare()
    mp.start()

    while (mp.isPlaying) Thread.sleep(30)
  }
}
```

---

### 3.6 `media/AlertOrchestrator.kt` (serialize events)

```kotlin
package com.example.regexalerts.media

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AlertOrchestrator {
  private val scope = CoroutineScope(Dispatchers.Default)
  private val gate = Mutex()

  fun trigger(context: Context, reason: String, label: String) {
    scope.launch {
      gate.withLock {
        val audio = AudioControllerMediaOnly(context)
        try {
          audio.prepare()
          SoundPlayer.playOnce(context)
        } finally {
          audio.restore()
        }
      }
    }
  }
}
```

---

### 3.7 `media/AlertPlayerService.kt` (optional fallback; can omit)

For some OEMs, a **very short Foreground Service** makes playback more robust. If you see playback being killed in the background, drop this in and call it instead of playing directly.

```kotlin
package com.example.regexalerts.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.regexalerts.R

class AlertPlayerService : Service() {
  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    startForeground(42, buildNotification())
    Thread {
      val audio = AudioControllerMediaOnly(this)
      try {
        audio.prepare()
        SoundPlayer.playOnce(this)
      } finally {
        audio.restore()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
      }
    }.start()
    return START_NOT_STICKY
  }

  private fun buildNotification(): Notification {
    val nm = getSystemService(NotificationManager::class.java)
    val channelId = "alert_playback"
    if (Build.VERSION.SDK_INT >= 26) {
      nm.createNotificationChannel(
        NotificationChannel(channelId, "Alert playback", NotificationManager.IMPORTANCE_LOW)
      )
    }
    return NotificationCompat.Builder(this, channelId)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(getString(R.string.app_name))
      .setContentText("Playing alert sound…")
      .setOngoing(true)
      .build()
  }
}
```

> On Android 14+, **declare foreground service types** (we did: `mediaPlayback|shortService`) and hold the corresponding permissions. Android 12+ restricts starting FGS from background; for a quick tone it’s often fine to **play directly** from the system‑bound services, but this service is here if you need it. ([Android Developers][12])

---

### 3.8 `ui/MainActivity.kt` (simple UI to input regexes & open settings)

```kotlin
package com.example.regexalerts.ui

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.regexalerts.data.RegexStore
import com.example.regexalerts.databinding.ActivityMainBinding

class MainActivity : ComponentActivity() {
  private lateinit var vb: ActivityMainBinding

  private val requestNotifications =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* ignore */ }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    vb = ActivityMainBinding.inflate(layoutInflater)
    setContentView(vb.root)

    // Preload current patterns in text boxes
    vb.editPhone.setText(RegexStore.phoneNumberPatterns.joinToString("\n") { it.pattern })
    vb.editNotification.setText(RegexStore.notificationPatterns.joinToString("\n") { it.pattern })

    vb.btnSave.setOnClickListener {
      RegexStore.updatePhoneRegexes(vb.editPhone.text.toString())
      RegexStore.updateNotificationRegexes(vb.editNotification.text.toString())
    }

    vb.btnNotificationAccess.setOnClickListener {
      // System screen to grant Notification Listener access
      startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    vb.btnCallScreeningRole.setOnClickListener {
      val rm = getSystemService(RoleManager::class.java)
      val intent = rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
      startActivity(intent)
    }

    vb.btnPostNotifications.setOnClickListener {
      if (Build.VERSION.SDK_INT >= 33) {
        requestNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }
}
```

**`activity_main.xml`** (very small; replace with Compose if you prefer)

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
  android:layout_width="match_parent"
  android:layout_height="match_parent">

  <LinearLayout
      android:orientation="vertical"
      android:padding="16dp"
      android:layout_width="match_parent"
      android:layout_height="wrap_content">

    <TextView
        android:text="Phone number regexes (one per line, match E.164 like +12135551212)"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />

    <EditText
        android:id="@+id/editPhone"
        android:inputType="textMultiLine"
        android:minLines="3"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />

    <TextView
        android:text="Notification regexes (applied to title • text • bigText)"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"/>

    <EditText
        android:id="@+id/editNotification"
        android:inputType="textMultiLine"
        android:minLines="3"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />

    <Button
        android:id="@+id/btnSave"
        android:text="Save regexes"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"/>

    <Button
        android:id="@+id/btnNotificationAccess"
        android:text="Grant Notification Access"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="24dp"/>

    <Button
        android:id="@+id/btnCallScreeningRole"
        android:text="Grant Call Screening Role"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"/>

    <Button
        android:id="@+id/btnPostNotifications"
        android:text="Request POST_NOTIFICATIONS (Android 13+)"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"/>

  </LinearLayout>
</ScrollView>
```

> System Settings intents:
> • **Notification listener access**: `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`
> • **Call screening role**: `RoleManager.ROLE_CALL_SCREENING` via `createRequestRoleIntent()`
> • **POST\_NOTIFICATIONS**: runtime permission on Android 13+
> Sources: Notification listener API & settings action, RoleManager API, and POST\_NOTIFICATIONS docs. ([Android Developers][3])

---

## 4) Put your sound in the app

Add your MP3/WAV to **`app/src/main/res/raw/alert_tone.mp3`**.
We play it as **USAGE\_MEDIA**, so it’s independent from the ringer. (You can also use `USAGE_ALARM` if you ever decide to bypass more scenarios, but for your requirement we keep it as media.) ([Android Developers][14])

---

## 5) Runtime checklist (first launch)

1. **Grant Notification Access** (the app deep‑links to the system screen). ([Microsoft Learn][9])
2. **Grant Call Screening Role** (tap the button → accept). ([Android Developers][10])
3. (Android 13+) **Grant POST\_NOTIFICATIONS** if you plan to show your own notifications or use the optional FGS. ([Android Developers][11])
4. Enter your **regexes** on the main screen and **Save**.

Now put the phone **on silent/vibrate** (DND *off*). Trigger a test notification matching your regex → you should hear your tone even though the ringer is off. Same for an incoming call from a matching number.

---

## 6) Why we don’t touch ringer mode (deeper)

* **Media** volume is separate from **Ring/Notification** volume; **silent ringer** doesn’t inhibit **media** as long as `STREAM_MUSIC` > 0. (Android’s user help and media output docs reflect the separate streams and how volume keys map to them.) ([Google Help][1])
* Programmatic media volume changes are allowed with **`MODIFY_AUDIO_SETTINGS`**, which is **install‑time “normal”** (auto‑granted). The platform’s permissions manifest shows this explicitly. ([Android Git Repositories][2])

If a device’s **media volume** is also zero, we **temporarily raise it**, play the short tone, and **restore** it.

---

## 7) API references to keep handy

* **NotificationListenerService** (listen to notifications; read `StatusBarNotification.notification.extras`). ([Android Developers][3])
* **Notification extras** keys `EXTRA_TITLE` / `EXTRA_TEXT` (used above). ([Microsoft Learn][15])
* **Screen calls** guide (`respondToCall(...)`, `setSilenceCall(true)`). ([Android Developers][4])
* **ROLE\_CALL\_SCREENING** via `RoleManager`. ([Android Developers][10])
* **Audio focus** best practices (`AudioFocusRequest`, transient focus). ([Android Developers][13])
* **POST\_NOTIFICATIONS** runtime permission (Android 13+). ([Android Developers][11])
* **Foreground Service types required** on Android 14+. ([Android Developers][12])
* **Restrictions on starting FGS from background** (Android 12+). ([Android Developers][16])

---

## 8) Testing tips

* **Notifications:** send a test notification from adb:

  ```
  adb shell cmd notification post -S bigtext com.example.regexalerts/0 "Test Title" "prod down in us-east"
  ```

  (Any content matching your regex will trigger the tone.)
* **Calls:** call your device from another phone using a number that matches your E.164 regex (e.g., `+1555xxxxxxx`).

---

## 9) Troubleshooting & notes

* If you ever enable **DND** in the future, media playback typically still works, but some policies may affect it. For guaranteed behavior across modes, you’d add **DND policy access** and temporarily set interruption filter — **not needed** for your current requirement. ([Android Developers][17])
* If playback is **intermittently killed** in the background on certain OEMs, use the provided **`AlertPlayerService`** Foreground Service with `mediaPlayback|shortService` and stop it immediately after playback. Mind Android 12+ background FGS restrictions. ([Android Developers][12])
* **libphonenumber-android** is used solely to **normalize numbers to E.164** for clean regex matching; the maven coordinate/version above is current. ([Maven Central][8])

---

## 10) Answers to your specific concerns

> **“I don’t understand why ringer volume matters. I just need media volume high enough; ringer can stay off.”**

Correct — for your use case, **ringer can stay off**. We **do not** touch ringer mode at all. The code only ensures **media volume** isn’t zero while your tone plays, then restores it. This uses `AudioManager.setStreamVolume(STREAM_MUSIC, …)` and a transient `AudioFocusRequest`. No special runtime permission is required beyond **`MODIFY_AUDIO_SETTINGS`** (normal). ([Android Developers][13])

---

### What to paste where (recap)

* **Gradle:** add the dependency and compile/target Sdks (Section 1).
* **Manifest:** permissions + 3 services (Section 2).
* **Code:** Sections 3.1–3.8 into corresponding packages.
* **Resource:** `res/raw/alert_tone.mp3`.
* **Run:** build → install → open app → grant **Notification access**, **Call Screening role**, (Android 13+) **POST\_NOTIFICATIONS** → save regexes → test.

If you’d like, I can adapt the UI to **Jetpack Compose** and add **DataStore** so your regexes persist across app restarts.

[1]: https://support.google.com/android/answer/9082609?hl=en&utm_source=chatgpt.com "Change volume, sound, & vibrate settings - Android Help"
[2]: https://android.googlesource.com/platform/frameworks/base/%2B/master/core/res/AndroidManifest.xml?utm_source=chatgpt.com "core/res/AndroidManifest.xml - platform/frameworks/base"
[3]: https://developer.android.com/reference/android/service/notification/NotificationListenerService?utm_source=chatgpt.com "NotificationListenerService | API reference"
[4]: https://developer.android.com/develop/connectivity/telecom/dialer-app/screen-calls?utm_source=chatgpt.com "Screen calls | Android Developers"
[5]: https://developer.android.com/studio?utm_source=chatgpt.com "Download Android Studio & App Tools"
[6]: https://developer.android.com/latest-updates?utm_source=chatgpt.com "Latest updates"
[7]: https://mvnrepository.com/artifact/com.android.tools.build/gradle?utm_source=chatgpt.com "Gradle - com.android.tools.build"
[8]: https://central.sonatype.com/artifact/io.michaelrocks/libphonenumber-android?utm_source=chatgpt.com "libphonenumber-android - io.michaelrocks - Maven Central"
[9]: https://learn.microsoft.com/en-us/dotnet/api/android.provider.settings.actionnotificationlistenersettings?view=net-android-35.0&utm_source=chatgpt.com "Settings.ActionNotificationListenerSettings Field"
[10]: https://developer.android.com/reference/android/app/role/RoleManager?utm_source=chatgpt.com "RoleManager | API reference"
[11]: https://developer.android.com/develop/ui/views/notifications/notification-permission?utm_source=chatgpt.com "Notification runtime permission | Views"
[12]: https://developer.android.com/about/versions/14/changes/fgs-types-required?utm_source=chatgpt.com "Foreground service types are required - Android Developers"
[13]: https://developer.android.com/media/optimize/audio-focus?utm_source=chatgpt.com "Manage audio focus | Android media"
[14]: https://developer.android.com/reference/android/media/AudioAttributes?utm_source=chatgpt.com "AudioAttributes | API reference"
[15]: https://learn.microsoft.com/en-us/dotnet/api/android.app.notification.extratitle?view=net-android-35.0&utm_source=chatgpt.com "Notification.ExtraTitle Field (Android.App)"
[16]: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start?utm_source=chatgpt.com "Restrictions on starting a foreground service from the ..."
[17]: https://developer.android.com/guide/topics/permissions/overview "Permissions on Android  |  Privacy  |  Android Developers"

