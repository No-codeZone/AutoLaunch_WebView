package com.example.autolaunchtechstern.ui;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import com.example.autolaunchtechstern.R;
import android.util.Log;
import java.util.List;

public class AutoLaunchService extends Service {
    private static final String TAG = "AutoLaunchService";
    private static final String CHANNEL_ID = "AutoLaunchChannel";
    private static final int NOTIFICATION_ID = 2001;
    private static final int LAUNCH_NOTIFICATION_ID = 2002;
    private Handler handler;
    private boolean isBootLaunch = false;
    @SuppressLint("ForegroundServiceType")
    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        Log.d(TAG, "AutoLaunchService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "AutoLaunchService started");

        // Check if this is a boot launch
        isBootLaunch = intent != null && intent.getBooleanExtra("boot_launch", false);

        // Start as foreground service
        startForeground(NOTIFICATION_ID, createServiceNotification());

        // Determine launch strategy based on Android version and launch type
        if (isBootLaunch && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For boot launch on Android 10+, use more aggressive approach
            startBootLaunchSequence();
        } else {
            // Regular launch or older Android
            startRegularLaunchSequence();
        }

        return START_NOT_STICKY; // We don't want this service to restart
    }

    private void startBootLaunchSequence() {
        Log.d(TAG, "Starting boot launch sequence for Android 10+");

        // Multiple attempts with different strategies

        // Attempt 1: Immediate try (might fail)
        handler.postDelayed(this::attemptLaunch, 1000);

        // Attempt 2: After system settles
        handler.postDelayed(this::attemptLaunch, 5000);

        // Attempt 3: Try notification approach
        handler.postDelayed(this::launchViaNotification, 8000);

        // Attempt 4: Final attempt with full flags
        handler.postDelayed(this::attemptAggressiveLaunch, 12000);

        // Cleanup: Stop service after all attempts
        handler.postDelayed(this::stopSelf, 15000);
    }

    private void startRegularLaunchSequence() {
        Log.d(TAG, "Starting regular launch sequence");

        // Simple approach for regular launches
        handler.postDelayed(() -> {
            if (attemptLaunch()) {
                stopSelf();
            } else {
                // Fallback to notification
                launchViaNotification();
                handler.postDelayed(this::stopSelf, 5000);
            }
        }, 2000);
    }

    private boolean attemptLaunch() {
        try {
            // Check if MainActivity is already running
            if (isMainActivityRunning()) {
                Log.d(TAG, "MainActivity already running, no launch needed");
                return true;
            }

            Intent launchIntent = createLaunchIntent();

            // Try direct launch
            startActivity(launchIntent);
            Log.d(TAG, "Direct launch attempted");
            return true;

        } catch (Exception e) {
            Log.w(TAG, "Direct launch failed: " + e.getMessage());
            return false;
        }
    }

    private void attemptAggressiveLaunch() {
        try {
            if (isMainActivityRunning()) {
                Log.d(TAG, "MainActivity already running");
                return;
            }

            Intent launchIntent = createLaunchIntent();

            // Add more aggressive flags for boot launch
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK |
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS |
                    Intent.FLAG_ACTIVITY_NO_HISTORY |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP |
                    Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);

            startActivity(launchIntent);
            Log.d(TAG, "Aggressive launch attempted");

        } catch (Exception e) {
            Log.e(TAG, "Aggressive launch failed: " + e.getMessage());
            // Final fallback to notification
            launchViaNotification();
        }
    }

    private Intent createLaunchIntent() {
        Intent launchIntent = new Intent(this, MainActivity.class);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        if (isBootLaunch) {
            launchIntent.putExtra("launched_from_boot", true);
        } else {
            launchIntent.putExtra("launched_from_service", true);
        }

        return launchIntent;
    }

    private void launchViaNotification() {
        try {
            Log.d(TAG, "Creating launch notification");

            Intent launchIntent = createLaunchIntent();

            @SuppressLint("ObsoleteSdkInt") PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, launchIntent,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT :
                            PendingIntent.FLAG_UPDATE_CURRENT
            );

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Kiosk App Ready")
                    .setContentText("Tap to start kiosk mode")
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setOngoing(false)
                    .build();

            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager != null) {
                notificationManager.notify(LAUNCH_NOTIFICATION_ID, notification);
                Log.d(TAG, "Launch notification created");
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to create launch notification: " + e.getMessage());
        }
    }

    private boolean isMainActivityRunning() {
        try {
            ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager == null) return false;

            List<ActivityManager.RunningTaskInfo> runningTasks = activityManager.getRunningTasks(3);
            String packageName = getPackageName();

            for (ActivityManager.RunningTaskInfo taskInfo : runningTasks) {
                if (taskInfo.topActivity != null &&
                        packageName.equals(taskInfo.topActivity.getPackageName())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error checking if MainActivity is running: " + e.getMessage());
            return false;
        }
    }

    private Notification createServiceNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Auto Launch Service")
                .setContentText(isBootLaunch ? "Starting kiosk from boot..." : "Launching kiosk app...")
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Auto Launch Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Service for launching the kiosk app");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "AutoLaunchService destroyed");

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
}

