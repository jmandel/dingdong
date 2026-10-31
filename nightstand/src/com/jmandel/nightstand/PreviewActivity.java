package com.jmandel.nightstand;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

/** The clock full screen, at the screensaver's backlight; tap to close. */
public class PreviewActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        NightView view = new NightView(this);
        view.setStatus("playing · Episode 771: Naan");
        view.setOnClickListener(v -> finish());
        setContentView(view);
        // Same backlight as the screensaver itself.
        NightDream.holdBacklight(getWindow(), new Settings(this));
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            c.hide(WindowInsets.Type.systemBars());
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }
}
