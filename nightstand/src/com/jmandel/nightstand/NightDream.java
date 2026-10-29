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
 * While it's showing, turning on the Bose headphones starts the newest
 * unfinished Spilled Milk episode with at least 15 minutes left, through
 * them at a very low volume. Tap to pause or resume, double-tap to skip
 * ahead 30 s, triple-tap to go back 30 s, drag right/left to change volume
 * (capped at 20%), long-press to leave. Audio only ever starts when the Bose is the output, and pauses as
 * soon as it isn't.
 */
public class NightDream extends DreamService {
    static final String TAG = Podcasts.TAG;
    static final String HEADPHONES = "Bose";
    static final String PODCAST = "Spilled Milk";
    static final int START_VOLUME = 2;      // of 25 steps
    static final int MAX_VOLUME_PCT = 20;
    static final float PX_PER_STEP = 60f;
    static final long SKIP_MS = 30_000;
    static final long MIN_LEFT_MS = 15 * 60_000;
    static final long TAP_WINDOW_MS = 350;
    static final long LONG_PRESS_MS = 800;

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
    private String lastMediaId;             // podcast episode last seen playing
    private long lastPosition, lastDuration;

    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
            boolean initial = !devicesSeen;
            devicesSeen = true;
            if (initial) return;
            for (AudioDeviceInfo d : added) {
                if (isBose(d)) {
                    Log.i(TAG, "Bose connected: " + d.getProductName());
                    // Let the route settle before starting.
                    handler.postDelayed(() -> startListening(), 1500);
                    return;
                }
            }
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            for (AudioDeviceInfo d : removed) {
                if (isBose(d)) {
                    Log.i(TAG, "Bose disconnected");
                    pause();
                }
            }
        }
    };

    // Belt and braces: while playing, re-check the route every couple of seconds.
    private final Runnable guard = new Runnable() {
        @Override
        public void run() {
            if (isPlaying() && !boseIsOutput()) {
                Log.w(TAG, "playing but Bose isn't the output; pausing");
                pause();
            }
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(true);
        setFullscreen(true);
        setScreenBright(false);
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
        devicesSeen = false;
        audio.registerAudioDeviceCallback(deviceCallback, handler);
        podcasts.connect(c -> {
            controller = c;
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

    private static boolean isBose(AudioDeviceInfo d) {
        return isBluetooth(d) && String.valueOf(d.getProductName()).contains(HEADPHONES);
    }

    /** True only when the Bose is connected for media and no other Bluetooth output is. */
    private boolean boseIsOutput() {
        boolean bose = false;
        for (AudioDeviceInfo d : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (!isBluetooth(d)) continue;
            if (isBose(d)) bose = true;
            else return false;
        }
        return bose;
    }

    // --- Playback --------------------------------------------------------

    private boolean isPlaying() {
        PlaybackState s = controller == null ? null : controller.getPlaybackState();
        return s != null && s.getState() == PlaybackState.STATE_PLAYING;
    }

    private boolean currentIsPodcast() {
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        if (m == null) return false;
        return PODCAST.equals(m.getString(MediaMetadata.METADATA_KEY_ALBUM))
                || PODCAST.equals(m.getString(MediaMetadata.METADATA_KEY_ARTIST))
                || String.valueOf(m.getDescription().getSubtitle()).contains(PODCAST);
    }

    private void togglePlay() {
        if (isPlaying()) {
            pause();
        } else if (controller != null && currentIsPodcast()) {
            if (!boseIsOutput()) { refuse(); return; }
            clampVolume();
            controller.getTransportControls().play();
        } else {
            startListening();
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
        long elapsed = SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime();
        long pos = s.getPosition() + (long) (elapsed * s.getPlaybackSpeed());
        controller.getTransportControls().seekTo(Math.max(0, pos + deltaMs));
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
        else if (n == 2) skip(SKIP_MS);
        else if (n >= 3) skip(-SKIP_MS);
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
        if (!boseIsOutput()) { refuse(); return; }
        podcasts.connect(c -> {
            controller = c;
            podcasts.children("subscriptions", feeds -> {
                for (MediaBrowser.MediaItem f : feeds) {
                    if (PODCAST.contentEquals(String.valueOf(f.getDescription().getTitle()))) {
                        podcasts.children(f.getMediaId(), this::playNewestUnfinished);
                        return;
                    }
                }
                Log.w(TAG, "no subscription named " + PODCAST);
                refuse();
            });
        });
    }

    private void playNewestUnfinished(java.util.List<MediaBrowser.MediaItem> episodes) {
        // AntennaPod's state comes from its database export (slow-ish to read);
        // Nightstand's own record covers listening since that export.
        new Thread(() -> {
            Map<String, AntennaPodExport.Episode> exported = AntennaPodExport.episodes(PODCAST);
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

    /** Unfinished, with at least MIN_LEFT_MS to go from the furthest known position. */
    private boolean worthPlaying(String id, AntennaPodExport.Episode e) {
        SharedPreferences h = history();
        if (h.getStringSet("finished", new HashSet<>()).contains(id)) return false;
        if (e != null && e.played) return false;
        long duration = Math.max(e == null ? 0 : e.duration, h.getLong("dur:" + id, 0));
        long position = Math.max(e == null ? 0 : e.position, h.getLong("pos:" + id, 0));
        return duration <= 0 || duration - position >= MIN_LEFT_MS;
    }

    private void play(MediaBrowser.MediaItem it) {
        if (!boseIsOutput()) { refuse(); return; }  // route may have changed while browsing
        Log.i(TAG, "playing " + it.getMediaId() + " " + it.getDescription().getTitle());
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, START_VOLUME, 0);
        controller.getTransportControls().playFromMediaId(it.getMediaId(), null);
    }

    /** Remembers an episode of ours that reached (nearly) its end when playback moves on. */
    private void trackProgress() {
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        PlaybackState s = controller == null ? null : controller.getPlaybackState();
        String id = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
        if (lastMediaId != null && !lastMediaId.equals(id)
                && lastDuration > 0 && lastPosition >= lastDuration - 90_000) {
            Set<String> finished = new HashSet<>(history().getStringSet("finished", new HashSet<>()));
            finished.add(lastMediaId);
            history().edit().putStringSet("finished", finished).apply();
            Log.i(TAG, "finished " + lastMediaId);
        }
        if (id != null && currentIsPodcast()) {
            lastMediaId = id;
            lastDuration = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
            if (s != null) lastPosition = s.getPosition();
            history().edit().putLong("pos:" + id, lastPosition).putLong("dur:" + id, lastDuration).apply();
        } else {
            lastMediaId = null;
        }
    }

    private SharedPreferences history() {
        return getSharedPreferences("history", Context.MODE_PRIVATE);
    }

    // --- Volume ----------------------------------------------------------

    private int maxVolume() {
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return Math.max(1, Math.round(max * MAX_VOLUME_PCT / 100f));
    }

    private void clampVolume() {
        int v = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (v > maxVolume()) audio.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume(), 0);
        if (v == 0) audio.setStreamVolume(AudioManager.STREAM_MUSIC, 1, 0);
    }

    private void stepVolume(int dir) {
        if (!boseIsOutput()) {  // never adjust (or unmute) the speaker from here
            Log.i(TAG, "volume step ignored: Bose isn't the output");
            return;
        }
        int v = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        int next = Math.max(1, Math.min(maxVolume(), v + dir));
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

    /** Refused, e.g. the Bose isn't connected. No vibration, ever: it should stay silent. */
    private void refuse() {
        Log.i(TAG, "refused");
    }
}
