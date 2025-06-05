package com.example.autolaunchtechstern.ui.helper;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.util.Log;
import android.app.ActivityManager;
import android.os.Build;
import com.example.autolaunchtechstern.ui.MainActivity;
import java.util.List;

public class InactivityMonitorService extends Service {
    private static final String TAG = "InactivityMonitor";
    private static final long INACTIVITY_TIMEOUT = 30000; // 30 seconds

    private Handler handler;
    private Runnable inactivityRunnable;
    private ScreenReceiver screenReceiver;
    private boolean isMonitoring = false;
    private boolean screenOn = true;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());

        // Register screen state receiver
        screenReceiver = new ScreenReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenReceiver, filter);

        setupInactivityRunnable();
        Log.d(TAG, "InactivityMonitorService created");
    }

    private void setupInactivityRunnable() {
        inactivityRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Inactivity timeout reached");
                if (shouldRelaunchApp()) {
                    relaunchMainApp();
                }
                stopMonitoring();
            }
        };
    }

    public void startMonitoring() {
        if (!isMonitoring && screenOn) {
            isMonitoring = true;
            handler.postDelayed(inactivityRunnable, INACTIVITY_TIMEOUT);
            Log.d(TAG, "Started monitoring for inactivity");
        }
    }

    public void stopMonitoring() {
        if (isMonitoring) {
            isMonitoring = false;
            handler.removeCallbacks(inactivityRunnable);
            Log.d(TAG, "Stopped monitoring for inactivity");
        }
    }

    private void resetInactivityTimer() {
        if (isMonitoring) {
            handler.removeCallbacks(inactivityRunnable);
            handler.postDelayed(inactivityRunnable, INACTIVITY_TIMEOUT);
            Log.d(TAG, "Reset inactivity timer");
        }
    }

    private boolean shouldRelaunchApp() {
        // Check if our app is not in foreground
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
                for (ActivityManager.RunningAppProcessInfo process : processes) {
                    if (process.processName.equals(getPackageName()) &&
                            process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                        return false; // Our app is already in foreground
                    }
                }
            }
        }
        return true;
    }

    private void relaunchMainApp() {
        Log.d(TAG, "Relaunching main app due to inactivity");
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getStringExtra("action");
            if ("start_monitoring".equals(action)) {
                startMonitoring();
            } else if ("stop_monitoring".equals(action)) {
                stopMonitoring();
            } else if ("reset_timer".equals(action)) {
                resetInactivityTimer();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopMonitoring();
        if (screenReceiver != null) {
            unregisterReceiver(screenReceiver);
        }
        Log.d(TAG, "InactivityMonitorService destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class ScreenReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Screen state changed: " + action);

            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                screenOn = false;
                stopMonitoring(); // Stop monitoring when screen is off
            } else if (Intent.ACTION_SCREEN_ON.equals(action) ||
                    Intent.ACTION_USER_PRESENT.equals(action)) {
                screenOn = true;
                if (!isAppInForeground()) {
                    startMonitoring(); // Start monitoring when screen comes on and app is not in foreground
                }
            }
        }

        private boolean isAppInForeground() {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
                for (ActivityManager.RunningAppProcessInfo process : processes) {
                    if (process.processName.equals(getPackageName()) &&
                            process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}