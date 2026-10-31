package com.jmandel.nightstand;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Moves the policy through text: a prompt to hand a language model, and
 * finding a proposed policy in whatever text comes back (a bare JSON object,
 * a ```json block inside a reply, or a link).
 *
 * Links look like https://config.joshuamandel.com/nightstand#<url-encoded JSON>.
 * The app claims that host without domain verification (approved once with
 * {@code pm set-app-links-user-selection}); the JSON rides in the fragment,
 * which browsers never send to the server.
 */
final class PolicyTransfer {
    static final String LINK = "https://config.joshuamandel.com/nightstand";

    private PolicyTransfer() {}

    static String prompt(Settings s) {
        StringBuilder fields = new StringBuilder();
        for (Settings.Field f : Settings.FIELDS) fields.append(f.describe()).append('\n');
        return "You're helping me configure Nightstand, an Android screensaver app for sleepless nights. "
                + "When I turn on my headphones while it's showing, it plays the newest unfinished episode of "
                + "one podcast through them at a very low volume. Tap pauses/resumes, double-tap skips forward, "
                + "triple-tap skips back, a sideways drag changes volume (never above the cap), long-press leaves. "
                + "It shows a dim clock that drifts to avoid burn-in.\n\n"
                + "Its whole configuration is one JSON object. Fields:\n"
                + "- \"kind\": always \"" + Settings.KIND + "\"\n"
                + "- \"version\": always 1\n"
                + fields
                + "\nExample: a dim amber 24-hour clock that's a little bolder:\n"
                + "{\"kind\": \"" + Settings.KIND + "\", \"version\": 1, \"clockColor\": \"#B36B00\", "
                + "\"clockBrightness\": 50, \"clockWeight\": 300, \"clockFormat\": \"24h\"}\n"
                + "(Fields you leave out keep their current values, but please send them all.)\n\n"
                + "My current configuration:\n```json\n" + s.toJson() + "\n```\n\n"
                + "Ask me what I'd like to change. Then reply with:\n"
                + "1. a one-line summary of what changed;\n"
                + "2. a link I can tap: " + LINK + "# followed by the complete new configuration as compact "
                + "JSON, URL-encoded (encodeURIComponent);\n"
                + "3. the same configuration as a single ```json code block, in case the link gets garbled "
                + "(then I'll copy your reply instead).\n"
                + "Either way Nightstand shows me the changes before anything is applied.";
    }

    private static final Pattern FENCED = Pattern.compile("```(?:json)?\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL);

    /** The first policy-shaped JSON object in the text, or null. */
    static JSONObject find(CharSequence text) {
        if (text == null) return null;
        String s = text.toString().trim();
        Matcher link = Pattern.compile("(?:" + Pattern.quote(LINK) + "|nightstand://policy)\\S+").matcher(s);
        if (link.find()) {
            JSONObject o = fromLink(android.net.Uri.parse(link.group()));
            if (o != null) return o;
        }
        Matcher m = FENCED.matcher(s);
        while (m.find()) {
            JSONObject o = object(m.group(1));
            if (o != null) return o;
        }
        int start = s.indexOf('{'), end = s.lastIndexOf('}');
        return start >= 0 && end > start ? object(s.substring(start, end + 1)) : null;
    }

    /** The policy carried by a link's fragment (or ?json=), or null. */
    static JSONObject fromLink(android.net.Uri uri) {
        String json = uri.getFragment();
        if (json == null || json.isEmpty()) json = uri.getQueryParameter("json");
        return json == null ? null : object(json);
    }

    private static JSONObject object(String json) {
        try {
            JSONObject o = new JSONObject(json);
            return Settings.KIND.equals(o.optString("kind")) ? o : null;
        } catch (JSONException e) {
            return null;
        }
    }
}
