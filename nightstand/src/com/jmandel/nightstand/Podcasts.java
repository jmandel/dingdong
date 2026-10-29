package com.jmandel.nightstand;

import android.content.ComponentName;
import android.content.Context;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.os.Bundle;
import android.util.Log;

import java.util.List;
import java.util.function.Consumer;

/** Connects to AntennaPod's media browser to find and play episodes. */
class Podcasts {
    static final String TAG = "Nightstand";
    static final String PKG = "de.danoeh.antennapod";
    static ComponentName SERVICE =
            new ComponentName(PKG, PKG + ".playback.service.Media3PlaybackService");

    private final Context ctx;
    private MediaBrowser browser;

    Podcasts(Context ctx) {
        this.ctx = ctx;
    }

    /** Connects (once) and hands back a controller for AntennaPod's session. */
    void connect(Consumer<MediaController> onReady) {
        if (browser != null && browser.isConnected()) {
            onReady.accept(new MediaController(ctx, browser.getSessionToken()));
            return;
        }
        browser = new MediaBrowser(ctx, SERVICE, new MediaBrowser.ConnectionCallback() {
            @Override
            public void onConnected() {
                Log.i(TAG, "browser connected, root=" + browser.getRoot());
                onReady.accept(new MediaController(ctx, browser.getSessionToken()));
            }

            @Override
            public void onConnectionFailed() {
                Log.w(TAG, "browser connection refused by " + SERVICE.getClassName());
            }

            @Override
            public void onConnectionSuspended() {
                Log.w(TAG, "browser connection suspended");
            }
        }, null);
        browser.connect();
    }

    MediaBrowser browser() {
        return browser;
    }

    void children(String id, Consumer<List<MediaBrowser.MediaItem>> out) {
        browser.subscribe(id, new MediaBrowser.SubscriptionCallback() {
            @Override
            public void onChildrenLoaded(String parentId, List<MediaBrowser.MediaItem> items) {
                browser.unsubscribe(parentId);
                out.accept(items);
            }

            @Override
            public void onError(String parentId) {
                Log.w(TAG, "browse failed: " + parentId);
            }
        });
    }

    static String describe(MediaBrowser.MediaItem it) {
        Bundle ex = it.getDescription().getExtras();
        StringBuilder sb = new StringBuilder();
        sb.append(it.isBrowsable() ? "[dir] " : "[play] ")
                .append(it.getMediaId()).append(" | ")
                .append(it.getDescription().getTitle()).append(" | ")
                .append(it.getDescription().getSubtitle());
        if (ex != null) {
            for (String k : ex.keySet()) sb.append(" ").append(k).append("=").append(ex.get(k));
        }
        return sb.toString();
    }

    void disconnect() {
        if (browser != null) browser.disconnect();
        browser = null;
    }
}
