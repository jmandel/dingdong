package com.jmandel.nightstand;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The whole Nightstand policy as one JSON object, stored in SharedPreferences.
 * The settings screen edits it field by field, and it can also be copied out
 * and pasted back whole (e.g. after a language model proposes changes).
 * {@link #FIELDS} is the single description of every field: defaults,
 * validation, the AI prompt and the change review all come from it.
 */
class Settings {
    static final String KIND = "nightstand-policy";

    /** One policy field: a whole number in a range, a yes/no, a text, or one of a few choices. */
    static final class Field {
        final String key, label, help;
        final Object def;
        final int min, max;          // numbers only
        final String[] choices;      // choices only

        private Field(String key, String label, Object def, int min, int max, String[] choices, String help) {
            this.key = key;
            this.label = label;
            this.def = def;
            this.min = min;
            this.max = max;
            this.choices = choices;
            this.help = help;
        }

        static Field bool(String key, String label, boolean def, String help) {
            return new Field(key, label, def, 0, 0, null, help);
        }

        static Field num(String key, String label, int def, int min, int max, String help) {
            return new Field(key, label, def, min, max, null, help);
        }

        static Field text(String key, String label, String def, String help) {
            return new Field(key, label, def, 0, 0, null, help);
        }

        static Field choice(String key, String label, String def, String[] choices, String help) {
            return new Field(key, label, def, 0, 0, choices, help);
        }

        /** The value as stored, or an {@link Error}. */
        Object check(Object v) {
            if (def instanceof Boolean) {
                return v instanceof Boolean ? v : error("should be true or false");
            }
            if (def instanceof Integer) {
                if (!(v instanceof Number) || ((Number) v).doubleValue() != ((Number) v).intValue()) {
                    return error("should be a whole number");
                }
                int n = ((Number) v).intValue();
                return n < min || n > max ? error("should be between " + min + " and " + max) : n;
            }
            if (!(v instanceof String)) return error("should be text");
            String s = (String) v;
            if (choices != null) {
                for (String c : choices) if (c.equals(s)) return s;
                return error("should be one of " + String.join(", ", choices));
            }
            if (key.equals("clockColor") && !s.matches("#[0-9a-fA-F]{6}")) return error("should look like #8B0000");
            if (s.trim().isEmpty()) return error("shouldn't be empty");
            return s;
        }

        private Error error(String why) {
            return new Error("\"" + key + "\" " + why);
        }

        /** One line of the AI prompt. */
        String describe() {
            String kind = def instanceof Boolean ? "true/false"
                    : def instanceof Integer ? "whole number " + min + "–" + max
                    : choices != null ? "one of \"" + String.join("\", \"", choices) + "\""
                    : "text";
            return "- \"" + key + "\" (" + kind + ", default " + JSONObject.wrap(def) + "): " + help;
        }
    }

    /** A validation failure, kept distinct from valid string values. */
    static final class Error {
        final String message;

        Error(String message) {
            this.message = message;
        }
    }

    static final Field[] FIELDS = {
            Field.bool("autoStart", "Auto-play", true,
                    "while the screensaver shows, start the podcast when the headphones connect"),
            Field.text("headphones", "Headphones", "Bose QC35 II",
                    "Bluetooth name of the headphones; only plays when these are the media output"),
            Field.text("podcast", "Podcast", "Spilled Milk",
                    "exact AntennaPod subscription title to play from"),
            Field.num("startVolume", "Starting volume", 2, 1, 10,
                    "media volume step to start at (the phone has 25 steps)"),
            Field.num("maxVolumePct", "Never louder than", 20, 5, 50,
                    "volume cap in percent; dragging never goes above it"),
            Field.num("minMinutesLeft", "Skip episodes with less left than", 15, 0, 60,
                    "minutes; episodes with less remaining are passed over when picking one (0 = never)"),
            Field.num("skipSeconds", "Double/triple-tap skips", 30, 10, 120,
                    "seconds forward (double-tap) or back (triple-tap)"),
            Field.text("clockColor", "Clock color", "#8B0000",
                    "clock color as #RRGGBB at full brightness; dark reds are easiest on night vision"),
            Field.num("clockBrightness", "Clock brightness", 80, 5, 100,
                    "percent of the color's full brightness"),
            Field.num("clockWeight", "Clock thickness", 100, 100, 900,
                    "font weight of the digits in steps of 100: 100 hairline, 400 regular, 900 heavy"),
            Field.bool("lowestBacklight", "Lowest backlight", true,
                    "hold the screen at its lowest backlight while Nightstand shows, so it looks the same in "
                            + "any room; clock brightness then sets how bright the digits are"),
            Field.num("clockSize", "Clock size", 28, 10, 45,
                    "digit height as a percent of the screen's shorter side"),
            Field.choice("clockFormat", "Clock format", "12h", new String[] {"12h", "24h"},
                    "12-hour (7:06) or 24-hour (19:06)"),
            Field.bool("showEpisode", "Show episode", true,
                    "show the playing/paused episode under the clock"),
            Field.bool("drift", "Drift", true,
                    "move the clock a little each minute so nothing burns into the screen"),
    };

    private final SharedPreferences p;
    private JSONObject policy;

    Settings(Context ctx) {
        p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
        policy = load();
    }

    /** The stored policy with every field present; fills in defaults and earlier per-key settings. */
    private JSONObject load() {
        JSONObject stored;
        try {
            stored = new JSONObject(p.getString("policy", "{}"));
        } catch (JSONException e) {
            stored = new JSONObject();
        }
        JSONObject out = new JSONObject();
        for (Field f : FIELDS) {
            Object v = stored.opt(f.key);
            if (v == null) v = legacy(f);
            Object ok = v == null ? f.def : f.check(v);
            put(out, f.key, ok instanceof Error ? f.def : ok);
        }
        return out;
    }

    /** A value saved by versions that kept each setting under its own snake_case key. */
    private Object legacy(Field f) {
        return p.getAll().get(f.key.replaceAll("([A-Z])", "_$1").toLowerCase());
    }

    boolean autoStart() { return policy.optBoolean("autoStart"); }
    /** Bluetooth device name; matches outputs whose name contains it. */
    String headphones() { return policy.optString("headphones"); }
    String podcast() { return policy.optString("podcast"); }
    /** Volume steps (of the stream's max, 25 on this phone). */
    int startVolume() { return policy.optInt("startVolume"); }
    int maxVolumePct() { return policy.optInt("maxVolumePct"); }
    int minMinutesLeft() { return policy.optInt("minMinutesLeft"); }
    int skipSeconds() { return policy.optInt("skipSeconds"); }
    int clockColor() { return android.graphics.Color.parseColor(policy.optString("clockColor")); }
    int clockBrightness() { return policy.optInt("clockBrightness"); }
    int clockWeight() { return policy.optInt("clockWeight"); }
    int clockSize() { return policy.optInt("clockSize"); }
    boolean clock24h() { return "24h".equals(policy.optString("clockFormat")); }
    boolean showEpisode() { return policy.optBoolean("showEpisode"); }
    boolean drift() { return policy.optBoolean("drift"); }
    boolean lowestBacklight() { return policy.optBoolean("lowestBacklight"); }

    /** Sets one field (already valid, from the settings screen) and saves. */
    void set(String key, Object v) {
        put(policy, key, v);
        save();
    }

    /** Replaces the whole policy with one returned by {@link #parse}. */
    void replace(JSONObject validated) {
        policy = validated;
        save();
    }

    private void save() {
        p.edit().putString("policy", policy.toString()).apply();
    }

    /** The policy as pretty JSON, tagged so a pasted copy can be recognized. */
    String toJson() {
        JSONObject out = new JSONObject();
        put(out, "kind", KIND);
        put(out, "version", 1);
        for (Field f : FIELDS) put(out, f.key, policy.opt(f.key));
        try {
            return out.toString(2);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A full policy from pasted JSON, or null with the problems added to
     * errors. Fields left out keep their current values; unknown fields are
     * errors so a typo can't silently do nothing.
     */
    JSONObject parse(JSONObject in, List<String> errors) {
        if (!KIND.equals(in.optString("kind"))) errors.add("\"kind\" should be \"" + KIND + "\"");
        JSONObject out = new JSONObject();
        for (Field f : FIELDS) {
            Object v = in.opt(f.key);
            Object ok = v == null ? policy.opt(f.key) : f.check(v);
            if (ok instanceof Error) errors.add(((Error) ok).message);
            else put(out, f.key, ok);
        }
        for (Iterator<String> it = in.keys(); it.hasNext(); ) {
            String k = it.next();
            if (!k.equals("kind") && !k.equals("version") && field(k) == null) {
                errors.add("unknown field \"" + k + "\"");
            }
        }
        return errors.isEmpty() ? out : null;
    }

    /** Plain-English differences between the current policy and a proposed one. */
    List<String> changes(JSONObject proposed) {
        List<String> out = new ArrayList<>();
        for (Field f : FIELDS) {
            Object a = policy.opt(f.key), b = proposed.opt(f.key);
            if (!String.valueOf(a).equals(String.valueOf(b))) out.add(f.label + ": " + show(a) + " → " + show(b));
        }
        return out;
    }

    private static String show(Object v) {
        return v instanceof Boolean ? ((Boolean) v ? "on" : "off") : String.valueOf(v);
    }

    static Field field(String key) {
        for (Field f : FIELDS) if (f.key.equals(key)) return f;
        return null;
    }

    /** The screensaver that was set before Nightstand took over (device state, not policy). */
    String previousScreensaver(String fallback) { return p.getString("previous_screensaver", fallback); }
    void setPreviousScreensaver(String v) { p.edit().putString("previous_screensaver", v).apply(); }

    private static void put(JSONObject o, String k, Object v) {
        try {
            o.put(k, v);
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
