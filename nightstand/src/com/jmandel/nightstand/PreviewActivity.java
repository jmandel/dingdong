package com.jmandel.nightstand;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/** The clock full screen, as the screensaver would show it; tap to close. */
public class PreviewActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        NightView view = new NightView(this);
        view.setStatus("playing · Episode 771: Naan");
        view.setOnClickListener(v -> finish());
        setContentView(view);
        // Match the screensaver's dimmed screen.
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 0.01f;
        getWindow().setAttributes(lp);
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            c.hide(WindowInsets.Type.systemBars());
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }
}
