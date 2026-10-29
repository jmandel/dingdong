package com.jmandel.nightstand;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.browse.MediaBrowser;
import android.util.Log;

/** adb shell am broadcast -n com.jmandel.nightstand/.DebugReceiver --es id <mediaId>  (logs a browse node) */
public class DebugReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult pending = goAsync();
        Podcasts p = new Podcasts(context.getApplicationContext());
        String id = intent.getStringExtra("id");
        String svc = intent.getStringExtra("svc");
        if (svc != null) Podcasts.SERVICE = new android.content.ComponentName(Podcasts.PKG, svc);
        Log.i(Podcasts.TAG, "connecting to " + Podcasts.SERVICE.getClassName());
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable timeout = () -> {
            Log.w(Podcasts.TAG, "timed out");
            p.disconnect();
            pending.finish();
        };
        h.postDelayed(timeout, 8000);
        p.connect(controller -> {
            String target = id != null ? id : p.browser().getRoot();
            Log.i(Podcasts.TAG, "dump " + target + " state=" + controller.getPlaybackState());
            p.children(target, items -> {
                for (MediaBrowser.MediaItem it : items) Log.i(Podcasts.TAG, "  " + Podcasts.describe(it));
                Log.i(Podcasts.TAG, "dump done (" + items.size() + ")");
                h.removeCallbacks(timeout);
                p.disconnect();
                pending.finish();
            });
        });
    }
}
