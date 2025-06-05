package com.example.autolaunchtechstern.ui;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class AutoLaunchAccessibilityService extends AccessibilityService {
    private static final String TAG = "AutoLaunchA11y";
    private static final String TARGET_PACKAGE = "com.example.autolaunchtechstern";
    private static final long DELAY_MS = 30_000; // 30 seconds

    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isAppInBackground = false;

    private final Runnable relaunchTask = () -> {
        if (isAppInBackground) {
            Log.d(TAG, "App still in background after 30s → relaunching MainActivity");
            Intent launch = new Intent(this, MainActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(launch);
        }
    };

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "AccessibilityService connected.");
        // At this point, the service is running. We don’t start MainActivity here—
        // BootReceiver already did that. This service’s job is only to detect “background” events.
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) {
            return;
        }

        // We only care about WINDOW_STATE_CHANGED events:
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        String currentPackage = event.getPackageName().toString();
        Log.v(TAG, "Window changed: " + currentPackage);

        if (!currentPackage.equals(TARGET_PACKAGE)) {
            // If the package is not our own, it means *something else* (home screen, other app, etc.) is now front.
            if (!isAppInBackground) {
                isAppInBackground = true;
                Log.d(TAG, "Our app moved to background → scheduling relaunch in 30s");
                handler.postDelayed(relaunchTask, DELAY_MS);
            }
        } else {
            // currentPackage == TARGET_PACKAGE → our app is back in front.
            if (isAppInBackground) {
                isAppInBackground = false;
                Log.d(TAG, "Our app returned to foreground → cancel relaunch");
                handler.removeCallbacks(relaunchTask);
            }
        }
    }

    @Override
    public void onInterrupt() {
        // no-op
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(relaunchTask);
    }
}


