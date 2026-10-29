package com.jmandel.nightstand;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.browse.MediaBrowser;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings.Secure;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** Everything configurable about Nightstand, plus a setup checklist. Built in code, no layouts. */
public class SettingsActivity extends Activity {
    private static final String DREAM =
            new ComponentName("com.jmandel.nightstand", NightDream.class.getName()).flattenToShortString();
    /** Pixel's stock clock screensaver, put back when Nightstand is switched off if nothing else was saved. */
    private static final String PIXEL_CLOCK =
            "com.google.assistant.hubui/com.google.assistant.hubui.dream.EverydayClockDreamService";

    private Settings settings;
    private LinearLayout root, checklist;
    private Switch useDream, autoPlay;
    private int pad;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new Settings(this);
        pad = dp(16);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad * 2, pad, pad * 2);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);

        text("Nightstand", 26, true);
        text("A screensaver for sleepless nights: turn on your headphones and a podcast starts "
                + "quietly through them. Tap to pause, double-tap to skip ahead, triple-tap to go "
                + "back, drag sideways for volume, long-press to leave. Never plays out loud, never vibrates.", 14, false);

        heading("Behavior");
        useDream = toggle("Nightstand screensaver",
                "When off, the phone goes back to Android's usual clock.",
                false, on -> { if (on != isScreensaver()) setScreensaver(on); });
        autoPlay = toggle("Auto-play",
                "While the screensaver is showing, start the podcast when your headphones connect.",
                settings.autoStart(), on -> settings.put("auto_start", on));

        heading("Headphones");
        headphonePicker();

        heading("Podcast");
        podcastPicker();

        int maxSteps = getSystemService(AudioManager.class).getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        heading("Volume");
        slider("Starting volume", 1, 10, 1, settings.startVolume(),
                v -> v + " of " + maxSteps + " (" + Math.round(100f * v / maxSteps) + "%)",
                v -> settings.put("start_volume", v));
        slider("Never louder than", 5, 50, 5, settings.maxVolumePct(),
                v -> v + "%", v -> settings.put("max_volume_pct", v));

        heading("Episodes and skipping");
        slider("Skip episodes with less left than", 0, 60, 5, settings.minMinutesLeft(),
                v -> v == 0 ? "never skip" : v + " min", v -> settings.put("min_minutes_left", v));
        slider("Double/triple-tap skips", 10, 120, 5, settings.skipSeconds(),
                v -> v + " s", v -> settings.put("skip_seconds", v));

        heading("Setup");
        checklist = new LinearLayout(this);
        checklist.setOrientation(LinearLayout.VERTICAL);
        root.addView(checklist);

        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.BLUETOOTH_CONNECT}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshChecklist();
    }

    /** Auto-play only happens inside the screensaver, so it's greyed out without it. */
    private void refreshSwitches() {
        boolean on = isScreensaver();
        useDream.setChecked(on);
        autoPlay.setEnabled(on);
        ((View) autoPlay.getParent()).setEnabled(on);
        ((View) autoPlay.getParent()).setAlpha(on ? 1f : 0.4f);
    }

    private boolean canWriteSecure() {
        return checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isScreensaver() {
        String dream = readSecure("screensaver_components");
        return dream != null && dream.contains(DREAM) && "1".equals(readSecure("screensaver_enabled"));
    }

    /**
     * Switches between Nightstand and whatever screensaver was set before it
     * (Pixel's clock if unknown), along with the dark-room clock that Pixel
     * would otherwise swap in over Nightstand. Without permission to change
     * settings directly, opens the system screensaver settings instead.
     */
    private void setScreensaver(boolean on) {
        if (!canWriteSecure()) {
            startActivity(new Intent(android.provider.Settings.ACTION_DREAM_SETTINGS));
            return;
        }
        android.content.ContentResolver cr = getContentResolver();
        if (on) {
            String current = readSecure("screensaver_components");
            if (current != null && !current.isEmpty() && !current.contains(DREAM)) {
                settings.put("previous_screensaver", current);
            }
            Secure.putString(cr, "screensaver_components", DREAM);
            Secure.putInt(cr, "screensaver_enabled", 1);
            Secure.putInt(cr, "low_light_display_behavior_enabled", 0);
        } else {
            Secure.putString(cr, "screensaver_components", settings.previousScreensaver(PIXEL_CLOCK));
            Secure.putInt(cr, "low_light_display_behavior_enabled", 1);
        }
        refreshChecklist();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        recreate();  // fill the headphone list now that we can see paired devices
    }

    // --- Pickers -----------------------------------------------------------

    private void headphonePicker() {
        List<String> names = new ArrayList<>();
        try {
            for (BluetoothDevice d : getSystemService(BluetoothManager.class).getAdapter().getBondedDevices()) {
                if (d.getName() != null) names.add(d.getName());
            }
        } catch (SecurityException e) {
            // No BLUETOOTH_CONNECT yet; the current choice still shows.
        }
        picker(names, settings.headphones(), v -> settings.put("headphones", v));
        text("Only plays when these are the Bluetooth media output.", 13, false);
    }

    private void podcastPicker() {
        List<String> names = new ArrayList<>();
        Spinner spinner = picker(names, settings.podcast(), v -> settings.put("podcast", v));
        TextView note = text("Loading your AntennaPod subscriptions…", 13, false);
        Podcasts podcasts = new Podcasts(this);
        podcasts.connect(c -> podcasts.children("subscriptions", feeds -> {
            List<String> titles = new ArrayList<>();
            for (MediaBrowser.MediaItem f : feeds) titles.add(String.valueOf(f.getDescription().getTitle()));
            titles.sort(String.CASE_INSENSITIVE_ORDER);
            fill(spinner, titles, settings.podcast());
            note.setText("Plays the newest unfinished episode with enough time left.");
            podcasts.disconnect();
        }));
    }

    private Spinner picker(List<String> options, String current, java.util.function.Consumer<String> onPick) {
        Spinner s = new Spinner(this);
        fill(s, options, current);
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                onPick.accept((String) parent.getItemAtPosition(pos));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        root.addView(s);
        return s;
    }

    private void fill(Spinner s, List<String> options, String current) {
        List<String> all = new ArrayList<>(options);
        if (!all.contains(current)) all.add(0, current);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, all);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        s.setSelection(all.indexOf(current));
    }

    private void slider(String label, int min, int max, int step, int value,
                        IntFunction<String> show, IntConsumer onChange) {
        TextView t = text("", 15, false);
        SeekBar bar = new SeekBar(this);
        bar.setMax((max - min) / step);
        bar.setProgress((Math.max(min, Math.min(max, value)) - min) / step);
        t.setText(label + ": " + show.apply(min + bar.getProgress() * step));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar b, int p, boolean user) {
                int v = min + p * step;
                t.setText(label + ": " + show.apply(v));
                if (user) onChange.accept(v);
            }

            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        root.addView(bar);
    }

    // --- Setup checklist ---------------------------------------------------

    private void refreshChecklist() {
        refreshSwitches();
        checklist.removeAllViews();
        boolean canWrite = canWriteSecure();

        if (isScreensaver()) {
            check(true, "Nightstand is your screensaver", null, null, null, null);
            // Android hides this setting from apps, so it can't be checked; with
            // permission to write it, just make sure it's off every time.
            String lowLight = readSecure("low_light_display_behavior_enabled");
            if (!"0".equals(lowLight) && canWrite) {
                Secure.putInt(getContentResolver(), "low_light_display_behavior_enabled", 0);
                lowLight = "0";
            }
            check("0".equals(lowLight), "Nightstand shows in the dark too",
                    lowLight == null ? "Pixel's dark-room clock may replace Nightstand"
                            : "In a dark room, Pixel's own clock replaces Nightstand",
                    "Then Nightstand never starts at night. Fix it with the adb grant below, "
                            + "or turn off the low-light clock in screen saver settings.",
                    null, null);
        } else {
            check(false, null, "Nightstand isn't your screensaver",
                    "Turn on \"Use Nightstand as the screensaver\" above.", null, null);
        }

        boolean files = Environment.isExternalStorageManager();
        check(files, "Can read AntennaPod's export", "Can't read AntennaPod's export",
                "Needed to know which episodes you've finished.",
                "Allow file access", () -> startActivity(new Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()))));

        File export = AntennaPodExport.latestExport();
        if (export == null) {
            check(false, null, "No AntennaPod export in Documents",
                    "Set AntennaPod → Settings → Import/Export → automatic database export to Documents.",
                    null, null);
        } else {
            long days = (System.currentTimeMillis() - export.lastModified()) / 86_400_000L;
            String age = days == 0 ? "from today" : days + " day" + (days == 1 ? "" : "s") + " old";
            check(days <= 4, "Latest AntennaPod export is " + age,
                    "Latest AntennaPod export is " + age + " (should refresh every 3 days)",
                    export.getName(), null, null);
        }

        if (!canWrite) {
            text("Tip: the buttons above can fix settings directly after a one-time "
                    + "`adb shell pm grant com.jmandel.nightstand android.permission.WRITE_SECURE_SETTINGS`.",
                    12, false, checklist);
        }
    }

    /** A secure setting's value, or null when it's unset or hidden from apps. */
    private String readSecure(String key) {
        try {
            String v = Secure.getString(getContentResolver(), key);
            android.util.Log.i(Podcasts.TAG, "setting " + key + " = " + v);
            return v;
        } catch (SecurityException e) {
            android.util.Log.i(Podcasts.TAG, "setting " + key + " is hidden from apps");
            return null;
        }
    }

    /** One checklist row: what's true now, and when it needs fixing, why and a button named for the result. */
    private void check(boolean ok, String okTitle, String badTitle, String badDetail,
                       String action, Runnable fix) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, pad / 2, 0, pad / 2);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView t = text((ok ? "✓  " : "✗  ") + (ok ? okTitle : badTitle), 15, false, words);
        t.setTextColor(ok ? Color.rgb(120, 200, 120) : Color.rgb(230, 140, 120));
        if (!ok && badDetail != null) text(badDetail, 13, false, words);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        if (!ok && action != null && fix != null) {
            Button b = new Button(this);
            b.setText(action);
            b.setOnClickListener(v -> fix.run());
            row.addView(b);
        }
        checklist.addView(row);
    }

    // --- Little view helpers -----------------------------------------------

    /** A setting row: title over a dimmer one-line explanation, switch on the right. */
    private Switch toggle(String title, String summary, boolean checked,
                          java.util.function.Consumer<Boolean> onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, pad / 2, 0, pad / 2);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        text(title, 16, false, words);
        text(summary, 13, false, words).setAlpha(0.6f);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((b, on) -> onChange.accept(on));
        row.addView(sw);
        row.setOnClickListener(v -> sw.toggle());
        root.addView(row);
        return sw;
    }

    private void heading(String s) {
        TextView t = text(s, 18, true);
        t.setPadding(0, pad * 2, 0, pad / 2);
    }

    private TextView text(String s, int sp, boolean bold) {
        return text(s, sp, bold, root);
    }

    private TextView text(String s, int sp, boolean bold, LinearLayout parent) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setPadding(0, pad / 4, 0, pad / 4);
        parent.addView(t);
        return t;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
