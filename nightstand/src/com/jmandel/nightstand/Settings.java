package com.jmandel.nightstand;

import android.content.Context;
import android.content.SharedPreferences;

/** User settings, edited in SettingsActivity and read by NightDream each time it starts. */
class Settings {
    private final SharedPreferences p;

    Settings(Context ctx) {
        p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    boolean autoStart() { return p.getBoolean("auto_start", true); }
    /** Bluetooth device name; matches outputs whose name contains it. */
    String headphones() { return p.getString("headphones", "Bose QC35 II"); }
    String podcast() { return p.getString("podcast", "Spilled Milk"); }
    /** Volume steps (of the stream's max, 25 on this phone). */
    int startVolume() { return p.getInt("start_volume", 2); }
    int maxVolumePct() { return p.getInt("max_volume_pct", 20); }
    int minMinutesLeft() { return p.getInt("min_minutes_left", 15); }
    int skipSeconds() { return p.getInt("skip_seconds", 30); }

    void put(String key, boolean v) { p.edit().putBoolean(key, v).apply(); }
    void put(String key, int v) { p.edit().putInt(key, v).apply(); }
    void put(String key, String v) { p.edit().putString(key, v).apply(); }
}
