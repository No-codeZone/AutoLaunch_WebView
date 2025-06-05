package com.example.autolaunchtechstern.ui;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import java.util.List;
import com.example.autolaunchtechstern.R;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    private static final int BOOT_NOTIFICATION_ID = 1001;
    private static final String BOOT_CHANNEL_ID = "KIOSK_BOOT_CHANNEL";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received broadcast: " + action);

        if (isBootAction(action)) {
            Log.d(TAG, "Boot completed - initializing kiosk startup sequence");

            // Create notification channel first
            createBootNotificationChannel(context);

            // Start the startup sequence
            initiateStartupSequence(context);
        }
    }

    private boolean isBootAction(String action) {
        return Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action) ||
                "com.htc.intent.action.QUICKBOOT_POWERON".equals(action) ||
                Intent.ACTION_REBOOT.equals(action);
    }

    private void initiateStartupSequence(Context context) {
        // Step 1: Start the kiosk service immediately
        startKioskService(context);

        // Step 2: Create persistent notification for manual launch
        createPersistentBootNotification(context);

        // Step 3: Try multiple launch strategies
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ - Use careful approach
            startWithModernAndroid(context);
        } else {
            // Older Android - More permissive
            startWithLegacyAndroid(context);
        }

        // Step 4: Schedule job service as backup
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            scheduleBootJob(context);
        }

        // Step 5: Setup continuous monitoring
        setupContinuousMonitoring(context);
    }

    private void startKioskService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, KioskService.class);
            serviceIntent.putExtra("started_from_boot", true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            Log.d(TAG, "KioskService started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start KioskService: " + e.getMessage());
        }
    }

    private void startWithModernAndroid(Context context) {
        Handler handler = new Handler(Looper.getMainLooper());

        // Multiple attempts with increasing delays
        int[] delays = {3000, 8000, 15000, 30000, 60000}; // 3s, 8s, 15s, 30s, 60s

        for (int i = 0; i < delays.length; i++) {
            final int attempt = i + 1;
            handler.postDelayed(() -> {
                Log.d(TAG, "Launch attempt " + attempt + " for modern Android");

                if (!isMainActivityRunning(context)) {
                    boolean launched = attemptDirectLaunch(context);

                    if (!launched && attempt == delays.length) {
                        // Last attempt failed, try AutoLaunchService
                        Log.d(TAG, "Direct launch failed, trying AutoLaunchService");
                        startAutoLaunchService(context);
                    }
                } else {
                    Log.d(TAG, "MainActivity already running, stopping launch attempts");
                }
            }, delays[i]);
        }
    }

    private void startWithLegacyAndroid(Context context) {
        Handler handler = new Handler(Looper.getMainLooper());

        // Single attempt with reasonable delay for system to settle
        handler.postDelayed(() -> {
            Log.d(TAG, "Launching app for legacy Android");

            if (!attemptDirectLaunch(context)) {
                // Fallback to AutoLaunchService
                startAutoLaunchService(context);
            }
        }, 8000);
    }

    private boolean attemptDirectLaunch(Context context) {
        try {
            Intent launchIntent = new Intent(context, MainActivity.class);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK |
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            launchIntent.putExtra("launched_from_boot", true);

            context.startActivity(launchIntent);
            Log.d(TAG, "Direct launch successful");
            return true;

        } catch (Exception e) {
            Log.w(TAG, "Direct launch failed: " + e.getMessage());
            return false;
        }
    }

    private void startAutoLaunchService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, AutoLaunchService.class);
            serviceIntent.putExtra("boot_launch", true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            Log.d(TAG, "AutoLaunchService started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start AutoLaunchService: " + e.getMessage());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void scheduleBootJob(Context context) {
        try {
            JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (jobScheduler == null) return;

            // Cancel any existing jobs
            jobScheduler.cancel(2001);

            JobInfo jobInfo = new JobInfo.Builder(2001, new ComponentName(context, BootJobService.class))
                    .setMinimumLatency(20000) // Wait at least 20 seconds
                    .setOverrideDeadline(45000) // Execute within 45 seconds
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                    .setPersisted(false)
                    .build();

            int result = jobScheduler.schedule(jobInfo);
            Log.d(TAG, "Boot job scheduled: " + (result == JobScheduler.RESULT_SUCCESS ? "SUCCESS" : "FAILED"));

        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule boot job: " + e.getMessage());
        }
    }

    private void createBootNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager != null) {
                NotificationChannel channel = new NotificationChannel(
                        BOOT_CHANNEL_ID,
                        "Kiosk Boot Notifications",
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Notifications for kiosk app boot and launch");
                channel.enableLights(true);
                channel.enableVibration(true);
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void createPersistentBootNotification(Context context) {
        try {
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager == null) return;

            Intent launchIntent = new Intent(context, MainActivity.class);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            launchIntent.putExtra("launched_from_notification", true);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context, 0, launchIntent,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT :
                            PendingIntent.FLAG_UPDATE_CURRENT
            );

            Notification notification = new NotificationCompat.Builder(context, BOOT_CHANNEL_ID)
                    .setContentTitle("Kiosk App Ready")
                    .setContentText("System started. Tap to open kiosk app.")
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(false) // Keep notification persistent
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .build();

            notificationManager.notify(BOOT_NOTIFICATION_ID, notification);
            Log.d(TAG, "Persistent boot notification created");

        } catch (Exception e) {
            Log.e(TAG, "Failed to create boot notification: " + e.getMessage());
        }
    }

    private void setupContinuousMonitoring(Context context) {
        Handler monitoringHandler = new Handler(Looper.getMainLooper());

        Runnable monitoringRunnable = new Runnable() {
            private int checkCount = 0;
            private final int maxChecks = 20; // Check for 10 minutes (30s intervals)

            @Override
            public void run() {
                checkCount++;

                if (checkCount <= maxChecks) {
                    Log.d(TAG, "Monitoring check " + checkCount + "/" + maxChecks);

                    if (!isMainActivityRunning(context)) {
                        Log.d(TAG, "MainActivity not running, attempting restart");

                        // Try direct launch first
                        if (!attemptDirectLaunch(context)) {
                            // Fallback to service
                            startAutoLaunchService(context);
                        }

                        // Continue monitoring
                        monitoringHandler.postDelayed(this, 30000); // Check every 30 seconds
                    } else {
                        Log.d(TAG, "MainActivity is running, stopping monitoring");
                        // Remove the persistent notification since app is running
                        clearBootNotification(context);
                    }
                } else {
                    Log.d(TAG, "Maximum monitoring checks reached");
                }
            }
        };

        // Start monitoring after 2 minutes
        monitoringHandler.postDelayed(monitoringRunnable, 120000);
    }

    private boolean isMainActivityRunning(Context context) {
        try {
            ActivityManager activityManager =
                    (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

            if (activityManager == null) return false;

            List<ActivityManager.RunningTaskInfo> runningTasks = activityManager.getRunningTasks(5);
            String packageName = context.getPackageName();

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

    private void clearBootNotification(Context context) {
        try {
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager != null) {
                notificationManager.cancel(BOOT_NOTIFICATION_ID);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error clearing boot notification: " + e.getMessage());
        }
    }
}



