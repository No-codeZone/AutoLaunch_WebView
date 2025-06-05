package com.example.autolaunchtechstern.ui;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import com.example.autolaunchtechstern.R;

import java.util.List;
import java.util.Objects;

public class KioskService extends Service {

    private static final int NOTIFICATION_ID = 1001;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable kioskChecker;

    @SuppressLint("ForegroundServiceType")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        startKioskChecker();

        return START_STICKY; // Restart if killed
    }

    private void startKioskChecker() {
        kioskChecker = new Runnable() {
            @Override
            public void run() {
                ensureMainActivityRunning();
                handler.postDelayed(this, 10000); // Check every 10 seconds
            }
        };
        handler.post(kioskChecker);
    }

    private void ensureMainActivityRunning() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);

        if (tasks.isEmpty() ||
                !Objects.requireNonNull(tasks.get(0).topActivity).getPackageName().equals(getPackageName())) {
            // MainActivity is not running, restart it
            Intent launchIntent = new Intent(this, MainActivity.class);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(launchIntent);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "KIOSK_CHANNEL",
                    "Kiosk Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, "KIOSK_CHANNEL")
                .setContentTitle("Kiosk Mode Active")
                .setContentText("App is running in kiosk mode")
                .setSmallIcon(R.drawable.ic_notification)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && kioskChecker != null) {
            handler.removeCallbacks(kioskChecker);
        }
        // Restart the service
        Intent restartIntent = new Intent(this, KioskService.class);
        startService(restartIntent);
    }
}

