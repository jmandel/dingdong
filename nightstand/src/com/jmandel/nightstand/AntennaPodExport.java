package com.jmandel.nightstand;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads episode state from AntennaPod's automatic database export
 * (AntennaPodBackup-*.db in Documents). AntennaPod's media browser doesn't
 * expose played state or positions, so this is the only view of listening
 * done outside Nightstand. Browser media IDs are FeedMedia ids.
 */
class AntennaPodExport {
    /** Played flag, saved position and duration (ms) of one episode. */
    static class Episode {
        final boolean played;
        final long position, duration;

        Episode(boolean played, long position, long duration) {
            this.played = played;
            this.position = position;
            this.duration = duration;
        }
    }

    private static File cachedFile;
    private static long cachedModified;
    private static Map<String, Episode> cached = new HashMap<>();

    /** Episodes of the named podcast by media ID; empty if no export is readable. */
    static synchronized Map<String, Episode> episodes(String podcast) {
        File latest = latestExport();
        if (latest == null) {
            Log.w(Podcasts.TAG, "no AntennaPod export found in Documents");
            return new HashMap<>();
        }
        if (latest.equals(cachedFile) && latest.lastModified() == cachedModified) return cached;

        Map<String, Episode> out = new HashMap<>();
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(
                latest.getPath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor c = db.rawQuery(
                     "SELECT m.id, i.read, m.position, m.duration FROM FeedMedia m "
                             + "JOIN FeedItems i ON m.feeditem = i.id JOIN Feeds f ON i.feed = f.id "
                             + "WHERE f.title = ?",
                     new String[] {podcast})) {
            while (c.moveToNext()) {
                out.put(c.getString(0), new Episode(c.getInt(1) == 1, c.getLong(2), c.getLong(3)));
            }
        } catch (Exception e) {
            Log.w(Podcasts.TAG, "couldn't read " + latest, e);
            return cached;
        }
        Log.i(Podcasts.TAG, latest.getName() + ": " + out.size() + " episodes of " + podcast);
        cachedFile = latest;
        cachedModified = latest.lastModified();
        cached = out;
        return out;
    }

    private static File latestExport() {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File[] files = dir.listFiles((d, name) -> name.startsWith("AntennaPodBackup") && name.endsWith(".db"));
        File best = null;
        if (files != null) {
            for (File f : files) if (best == null || f.lastModified() > best.lastModified()) best = f;
        }
        return best;
    }
}
