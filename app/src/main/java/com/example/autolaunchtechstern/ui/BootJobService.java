package com.example.autolaunchtechstern.ui;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

@SuppressLint("ObsoleteSdkInt")
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class BootJobService extends JobService {
    private static final String TAG = "BootJobService";

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "BootJobService started");

        // Execute in background thread
        new Thread(() -> {
            try {
                // Wait a bit more for system to fully boot
                Thread.sleep(5000);

                // Try to start the main activity
                Intent launchIntent = new Intent(this, MainActivity.class);
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK |
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                launchIntent.putExtra("launched_from_job", true);

                startActivity(launchIntent);
                Log.d(TAG, "MainActivity started from JobService");

            } catch (Exception e) {
                Log.e(TAG, "Failed to start MainActivity from JobService: " + e.getMessage());
            } finally {
                // Job is finished
                jobFinished(params, false);
            }
        }).start();

        return true; // Job is running asynchronously
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "BootJobService stopped");
        return false; // Don't reschedule
    }
}
