package com.jmandel.volpause;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * On the charger with audio playing, a quick double-press of volume down
 * pauses playback, but only when the screen is asleep or showing the
 * screensaver. In normal use the volume keys are left alone and never delayed.
 *
 * Screensaver: the presses reach onKeyEvent; the first may still lower the
 * volume a step, so it is restored shortly after pausing. Truly asleep: Android doesn't deliver keys to
 * accessibility services, but still adjusts the music volume, so two quick
 * volume-change broadcasts trigger the pause and the volume is restored.
 */
public class VolPauseService extends AccessibilityService {
    private static final String TAG = "VolPause";
    private static final long DOUBLE_MS = 500;
    private static final String VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";
    private static final String EXTRA_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE";
    private static final String EXTRA_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE";
    private static final String EXTRA_PREV = "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE";

    private static final long WAKE_GRACE_MS = 3000;

    private AudioManager audio;
    private PowerManager power;
    private boolean screenOff;      // per the last screen on/off broadcast
    private boolean dreaming;       // screensaver running
    private long dreamStoppedAt;    // when the screensaver last ended
    private long keyDownAt;         // first volume-down press during the screensaver
    private int volumeAtFirstKey;   // music volume before that press
    private final Handler handler = new Handler(Looper.getMainLooper());

    private long firstDropAt;       // time of the first volume-down step, 0 if none pending
    private int volumeBefore;       // music volume before that step
    private int lastSeen = -1;      // last music volume we saw broadcast

    private final BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent i) {
            if (i.getIntExtra(EXTRA_STREAM, -1) != AudioManager.STREAM_MUSIC) return;
            int value = i.getIntExtra(EXTRA_VALUE, -1);
            int prev = i.getIntExtra(EXTRA_PREV, -1);
            if (value < 0 || prev < 0 || value == prev) return;
            if (lastSeen < 0) lastSeen = prev;
            onMusicVolume(prev, value);
            lastSeen = value;
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent i) {
            String a = i.getAction();
            Log.i(TAG, "broadcast " + a);
            if (Intent.ACTION_DREAMING_STARTED.equals(a)) { dreaming = true; return; }
            if (Intent.ACTION_DREAMING_STOPPED.equals(a)) {
                dreaming = false;
                dreamStoppedAt = SystemClock.uptimeMillis();
                return;
            }
            screenOff = Intent.ACTION_SCREEN_OFF.equals(a);
        }
    };

    @Override
    protected void onServiceConnected() {
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        power = (PowerManager) getSystemService(Context.POWER_SERVICE);
        screenOff = !power.isInteractive();
        registerReceiver(volumeReceiver, new IntentFilter(VOLUME_CHANGED), Context.RECEIVER_NOT_EXPORTED);
        IntentFilter screen = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        screen.addAction(Intent.ACTION_SCREEN_ON);
        screen.addAction(Intent.ACTION_DREAMING_STARTED);
        screen.addAction(Intent.ACTION_DREAMING_STOPPED);
        registerReceiver(screenReceiver, screen, Context.RECEIVER_NOT_EXPORTED);
        Log.i(TAG, "connected");
    }

    private void onMusicVolume(int prev, int value) {
        long now = SystemClock.uptimeMillis();
        // The press itself wakes the screen, so count "just woke" as off.
        boolean wasOff = screenOff || !power.isInteractive();
        boolean plugged = isPlugged();
        boolean playing = isPlaying();
        Log.i(TAG, "music volume " + prev + "->" + value + " wasOff=" + wasOff
                + " plugged=" + plugged + " playing=" + playing);
        if (!(wasOff && plugged && playing) || value > prev) {
            firstDropAt = 0;
            return;
        }
        // Close-together broadcasts get merged, so a double press can arrive as
        // one event whose value is two steps below the volume we last saw.
        boolean pending = firstDropAt != 0 && now - firstDropAt <= DOUBLE_MS;
        if (pending || lastSeen - value >= 2) {
            int restore = pending ? volumeBefore : lastSeen;
            firstDropAt = 0;
            lastSeen = restore;
            pause(restore);
            return;
        }
        firstDropAt = now;
        volumeBefore = prev;
        lastSeen = value;
    }

    private boolean isPlaying() {
        if (audio.isMusicActive()) return true;
        for (AudioPlaybackConfiguration c : audio.getActivePlaybackConfigurations()) {
            if (c.getAudioAttributes().getUsage() == AudioAttributes.USAGE_MEDIA) return true;
        }
        return false;
    }

    private boolean isPlugged() {
        Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return b != null && b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
    }

    private void pause(int restoreVolume) {
        Log.i(TAG, "double tap: pausing, restoring volume " + restoreVolume);
        long now = SystemClock.uptimeMillis();
        audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
        audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
        if (restoreVolume >= 0) audio.setStreamVolume(AudioManager.STREAM_MUSIC, restoreVolume, 0);
        Vibrator v = getSystemService(Vibrator.class);
        if (v != null) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(volumeReceiver);
        unregisterReceiver(screenReceiver);
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN || e.getRepeatCount() != 0) return false;
        long now = SystemClock.uptimeMillis();
        boolean inDream = dreaming || now - dreamStoppedAt <= WAKE_GRACE_MS;
        boolean armed = inDream && isPlugged() && isPlaying();
        Log.i(TAG, "volume-down inDream=" + inDream + " armed=" + armed);
        if (!armed) {
            keyDownAt = 0;
            return false;
        }
        if (keyDownAt != 0 && now - keyDownAt <= DOUBLE_MS) {
            keyDownAt = 0;
            pause(-1);
            // The first press's volume step lands a moment later; undo it then.
            int restore = volumeAtFirstKey;
            handler.postDelayed(() ->
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, restore, 0), 800);
            return true;
        }
        keyDownAt = now;
        volumeAtFirstKey = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
