package com.jmandel.nightstand;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.dreams.DreamService;
import android.util.Log;
import android.view.MotionEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Nightstand screensaver for sleepless nights.
 *
 * While it's showing, turning on the chosen headphones starts the newest
 * unfinished episode of the chosen podcast (with enough left) through them at
 * a very low volume. Tap to pause or resume, double-tap to skip ahead,
 * triple-tap to go back, drag right/left to change volume (capped), long-press
 * to leave. A new episode only ever starts in the headphones, and headphone
 * playback pauses as soon as they stop being the output. Something already
 * playing on the speaker keeps going and the gestures control it too.
 * Options live in Settings / SettingsActivity.
 */
public class NightDream extends DreamService {
    static final String TAG = Podcasts.TAG;
    static final float PX_PER_STEP = 60f;
    static final long TAP_WINDOW_MS = 350;
    static final long LONG_PRESS_MS = 800;
    static final long SPEAKER_UNDO_MS = 60_000;

    private Settings settings;
    private AudioManager audio;
    private Podcasts podcasts;
    private MediaController controller;
    private NightView view;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean devicesSeen;            // first device callback is just the current list
    private float dragAccum, downX, downY, lastX;
    private boolean dragging;
    private int taps;
    private float touchSlop;
    private boolean onHeadphones;           // playback belongs in the headphones; pause if they go
    private long pausedOnSpeakerAt;         // when we paused speaker playback (0 = we didn't)

    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
            boolean initial = !devicesSeen;
            devicesSeen = true;
            if (initial) return;
            for (AudioDeviceInfo d : added) {
                if (isHeadphones(d)) {
                    Log.i(TAG, "headphones connected: " + d.getProductName());
                    if (isPlaying()) {
                        // Already playing (e.g. on the speaker): carry on, now in the headphones.
                        onHeadphones = true;
                        return;
                    }
                    if (!settings.autoStart()) return;
                    // Let the route settle before starting.
                    handler.postDelayed(() -> startListening(), 1500);
                    return;
                }
            }
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            for (AudioDeviceInfo d : removed) {
                if (isHeadphones(d)) {
                    Log.i(TAG, "headphones disconnected");
                    pause();
                }
            }
        }
    };

    // Belt and braces: while playing, re-check the route every couple of seconds.
    private final Runnable guard = new Runnable() {
        @Override
        public void run() {
            if (isPlaying() && onHeadphones && !headphonesAreOutput()) {
                Log.w(TAG, "headphones playback lost its route; pausing");
                pause();
            }
            if (isPlaying()) trackProgress();
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(true);
        setFullscreen(true);
        setScreenBright(false);
        settings = new Settings(this);
        audio = getSystemService(AudioManager.class);
        podcasts = new Podcasts(this);
        view = new NightView(this);
        touchSlop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();
        view.setOnTouchListener((v, ev) -> onTouch(ev));
        setContentView(view);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        settings = new Settings(this);  // re-read each time, in case they changed
        view.apply(settings);
        holdBacklight(getWindow(), settings);
        devicesSeen = false;
        onHeadphones = false;
        pausedOnSpeakerAt = 0;
        audio.registerAudioDeviceCallback(deviceCallback, handler);
        podcasts.connect(c -> {
            controller = c;
            onHeadphones = isPlaying() && headphonesAreOutput();
            controller.registerCallback(new MediaController.Callback() {
                @Override
                public void onPlaybackStateChanged(PlaybackState state) { trackProgress(); refresh(); }

                @Override
                public void onMetadataChanged(MediaMetadata metadata) { trackProgress(); refresh(); }
            }, handler);
            refresh();
        });
        handler.post(guard);
        refresh();
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacksAndMessages(null);
        audio.unregisterAudioDeviceCallback(deviceCallback);
        podcasts.disconnect();
        controller = null;
        super.onDreamingStopped();
    }

    // --- Audio routing ---------------------------------------------------

    private static boolean isBluetooth(AudioDeviceInfo d) {
        switch (d.getType()) {
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
            case AudioDeviceInfo.TYPE_BLE_SPEAKER:
            case AudioDeviceInfo.TYPE_BLE_BROADCAST:
                return true;
            default:
                return false;
        }
    }

    private boolean isHeadphones(AudioDeviceInfo d) {
        return isBluetooth(d) && String.valueOf(d.getProductName()).contains(settings.headphones());
    }

    /** True only when the chosen headphones are connected for media and no other Bluetooth output is. */
    private boolean headphonesAreOutput() {
        boolean found = false;
        for (AudioDeviceInfo d : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (!isBluetooth(d)) continue;
            if (isHeadphones(d)) found = true;
            else return false;
        }
        return found;
    }

    // --- Playback --------------------------------------------------------

    private boolean isPlaying() {
        PlaybackState s = controller == null ? null : controller.getPlaybackState();
        return s != null && s.getState() == PlaybackState.STATE_PLAYING;
    }

    private boolean currentIsPodcast() {
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        if (m == null) return false;
        String podcast = settings.podcast();
        return podcast.equals(m.getString(MediaMetadata.METADATA_KEY_ALBUM))
                || podcast.equals(m.getString(MediaMetadata.METADATA_KEY_ARTIST))
                || String.valueOf(m.getDescription().getSubtitle()).contains(podcast);
    }

    /**
     * Pauses whatever's playing. Resuming or starting needs the headphones,
     * except that something we paused on the speaker may resume there within a
     * minute, as a quick undo. After that a stray tap must not fill the room.
     */
    private void togglePlay() {
        if (isPlaying()) {
            pausedOnSpeakerAt = headphonesAreOutput() ? 0 : SystemClock.elapsedRealtime();
            pause();
        } else if (headphonesAreOutput()) {
            if (controller != null && currentIsPodcast()) {
                clampVolume();
                onHeadphones = true;
                controller.getTransportControls().play();
            } else {
                startListening();
            }
        } else if (pausedOnSpeakerAt != 0 && controller != null
                && SystemClock.elapsedRealtime() - pausedOnSpeakerAt <= SPEAKER_UNDO_MS) {
            pausedOnSpeakerAt = 0;
            controller.getTransportControls().play();
        } else {
            refuse();
        }
    }

    private void pause() {
        if (controller != null && isPlaying()) {
            controller.getTransportControls().pause();
        }
    }

    private void skip(long deltaMs) {
        PlaybackState s = controller == null ? null : controller.getPlaybackState();
        if (s == null || s.getState() != PlaybackState.STATE_PLAYING) return;
        controller.getTransportControls().seekTo(Math.max(0, position(s) + deltaMs));
    }

    // --- Touch -----------------------------------------------------------
    // Taps are counted rather than using GestureDetector, which has no triple tap:
    // 1 = play/pause, 2 = forward 30 s, 3 = back 30 s. Dragging sideways changes volume;
    // a long press leaves the screensaver.

    private final Runnable tapsDone = () -> {
        int n = taps;
        taps = 0;
        Log.i(TAG, n + " tap(s)");
        if (n == 1) togglePlay();
        else if (n == 2) skip(settings.skipSeconds() * 1000L);
        else if (n >= 3) skip(-settings.skipSeconds() * 1000L);
    };
    private final Runnable longPress = this::finish;

    private boolean onTouch(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = ev.getY();
                downX = lastX = ev.getX();
                dragging = false;
                dragAccum = 0;
                handler.removeCallbacks(tapsDone);
                handler.postDelayed(longPress, LONG_PRESS_MS);
                break;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && Math.hypot(ev.getX() - downX, ev.getY() - downY) > touchSlop) {
                    dragging = true;
                    taps = 0;
                    handler.removeCallbacks(longPress);
                    Log.i(TAG, "drag started");
                }
                if (dragging) {
                    // Sideways: the system takes vertical swipes over a screensaver.
                    dragAccum += ev.getX() - lastX;  // positive when moving right
                    while (Math.abs(dragAccum) >= PX_PER_STEP) {
                        int dir = dragAccum > 0 ? 1 : -1;
                        dragAccum -= dir * PX_PER_STEP;
                        stepVolume(dir);
                    }
                }
                lastX = ev.getX();
                break;
            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPress);
                if (!dragging) {
                    taps++;
                    handler.postDelayed(tapsDone, TAP_WINDOW_MS);
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                Log.i(TAG, "touch cancelled (taken by the system?)");
                handler.removeCallbacks(longPress);
                break;
        }
        return true;
    }

    /**
     * Plays the newest unfinished episode with at least 15 minutes left; an
     * in-progress one resumes where it left off.
     */
    private void startListening() {
        if (!headphonesAreOutput()) { refuse(); return; }
        podcasts.connect(c -> {
            controller = c;
            podcasts.children("subscriptions", feeds -> {
                for (MediaBrowser.MediaItem f : feeds) {
                    if (settings.podcast().contentEquals(String.valueOf(f.getDescription().getTitle()))) {
                        podcasts.children(f.getMediaId(), this::playNewestUnfinished);
                        return;
                    }
                }
                Log.w(TAG, "no subscription named " + settings.podcast());
                refuse();
            });
        });
    }

    private void playNewestUnfinished(java.util.List<MediaBrowser.MediaItem> episodes) {
        // AntennaPod's state comes from its database export (slow-ish to read);
        // Nightstand's own record covers listening since that export.
        new Thread(() -> {
            Map<String, AntennaPodExport.Episode> exported = AntennaPodExport.episodes(settings.podcast());
            handler.post(() -> {
                for (MediaBrowser.MediaItem it : episodes) {  // newest first
                    if (it.isPlayable() && worthPlaying(it.getMediaId(), exported.get(it.getMediaId()))) {
                        play(it);
                        return;
                    }
                }
                Log.w(TAG, "no unfinished episode with enough left");
                refuse();
            });
        }).start();
    }

    /** Unfinished, with at least the configured minutes to go from the furthest known position. */
    private boolean worthPlaying(String id, AntennaPodExport.Episode e) {
        SharedPreferences h = history();
        if (h.getStringSet("finished", new HashSet<>()).contains(id)) return false;
        if (e != null && e.played) return false;
        long duration = Math.max(e == null ? 0 : e.duration, h.getLong("dur:" + id, 0));
        long position = Math.max(e == null ? 0 : e.position, h.getLong("pos:" + id, 0));
        return duration <= 0 || duration - position >= settings.minMinutesLeft() * 60_000L;
    }

    private void play(MediaBrowser.MediaItem it) {
        if (!headphonesAreOutput()) { refuse(); return; }  // route may have changed while browsing
        Log.i(TAG, "playing " + it.getMediaId() + " " + it.getDescription().getTitle());
        onHeadphones = true;
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.min(settings.startVolume(), maxVolume()), 0);
        controller.getTransportControls().playFromMediaId(it.getMediaId(), null);
    }

    /**
     * Records the current podcast episode's position, and marks it finished
     * once it gets within 90 s of the end. Runs on every playback change and
     * from the 2 s guard, because AntennaPod sends nothing useful when an
     * episode simply runs out: the session just stops or goes away.
     */
    private void trackProgress() {
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        PlaybackState s = controller == null ? null : controller.getPlaybackState();
        String id = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
        if (id == null || s == null || !currentIsPodcast()) return;
        long pos = position(s);
        long dur = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (dur <= 0) dur = history().getLong("dur:" + id, 0);
        if (pos <= 0 && s.getState() != PlaybackState.STATE_PLAYING) return;  // reset after the end
        SharedPreferences.Editor e = history().edit().putLong("pos:" + id, pos).putLong("dur:" + id, dur);
        Set<String> finished = history().getStringSet("finished", new HashSet<>());
        if (dur > 0 && pos >= dur - 90_000 && !finished.contains(id)) {
            finished = new HashSet<>(finished);
            finished.add(id);
            e.putStringSet("finished", finished);
            Log.i(TAG, "finished " + id);
        }
        e.apply();
    }

    /** The current position, extrapolated from the last update while playing. */
    private static long position(PlaybackState s) {
        if (s.getState() != PlaybackState.STATE_PLAYING) return s.getPosition();
        long elapsed = SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime();
        return s.getPosition() + (long) (elapsed * s.getPlaybackSpeed());
    }

    private SharedPreferences history() {
        return getSharedPreferences("history", Context.MODE_PRIVATE);
    }

    /**
     * At the lowest backlight the screen looks the same in a dark room and a
     * lit one (auto-brightness otherwise varies it), which also makes the
     * settings preview faithful.
     */
    static void holdBacklight(android.view.Window w, Settings s) {
        android.view.WindowManager.LayoutParams lp = w.getAttributes();
        lp.screenBrightness = s.lowestBacklight() ? 0f
                : android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        w.setAttributes(lp);
    }

    // --- Volume ----------------------------------------------------------

    private int maxVolume() {
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return Math.max(1, Math.round(max * settings.maxVolumePct() / 100f));
    }

    private void clampVolume() {
        int v = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (v > maxVolume()) audio.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume(), 0);
        if (v == 0) audio.setStreamVolume(AudioManager.STREAM_MUSIC, 1, 0);
    }

    /** Down always works; up never passes the cap (and does nothing if already above it). */
    private void stepVolume(int dir) {
        int v = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        int next = dir > 0 ? Math.min(v + 1, Math.max(v, maxVolume())) : Math.max(1, v - 1);
        Log.i(TAG, "volume " + v + " -> " + next);
        if (next != v) {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0);
        }
        view.showVolume(next, maxVolume());
    }

    // --- Feedback --------------------------------------------------------

    private void refresh() {
        if (view == null) return;
        String line = null;
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        if (m != null && m.getDescription().getTitle() != null) {
            line = (isPlaying() ? "" : "paused · ") + m.getDescription().getTitle();
        }
        view.setStatus(line);
    }

    /** Refused, e.g. the headphones aren't connected. No vibration, ever: it should stay silent. */
    private void refuse() {
        Log.i(TAG, "refused");
    }
}
