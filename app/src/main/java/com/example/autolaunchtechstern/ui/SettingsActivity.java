package com.example.autolaunchtechstern.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.example.autolaunchtechstern.R;
import com.example.autolaunchtechstern.ui.helper.InactivityMonitorService;
import com.example.autolaunchtechstern.ui.helper.SessionManager;
import android.app.ActivityManager;
import android.content.Context;
import android.text.InputType;
import android.util.Log;

public class SettingsActivity extends Activity {

    private static final String TAG = "SettingsActivity";
    private EditText editServerUrl, editHeartbeat;
    private SessionManager session;

    @SuppressLint("ObsoleteSdkInt")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        session = new SessionManager(this);

        editServerUrl = findViewById(R.id.editServerUrl);
        editHeartbeat = findViewById(R.id.editHeartbeat);

        ensureKioskModeIfNotLocked();  // <- avoid multiple kiosk logs

        editServerUrl.setText(session.getServerUrl());
        editHeartbeat.setText(String.valueOf(session.getHeartbeat()));

        Button btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> {
            String url = editServerUrl.getText().toString().trim();
            String hb = editHeartbeat.getText().toString().trim();

            if (!url.startsWith("http")) {
                Toast.makeText(this, "Enter a valid URL", Toast.LENGTH_SHORT).show();
                return;
            }

            int heartbeat = hb.isEmpty() ? 30 : Integer.parseInt(hb);
            session.setServerUrl(url);
            session.setHeartbeat(heartbeat);

            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();

            Intent i = new Intent(SettingsActivity.this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });

        Button btnExit = findViewById(R.id.btnExitKiosk);
        btnExit.setOnClickListener(v -> showExitPinDialog());
    }

    @SuppressLint("ObsoleteSdkInt")
    private void showExitPinDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Admin PIN Required");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        builder.setView(input);

        builder.setPositiveButton("Unlock", (dialog, which) -> {
            String enteredPin = input.getText().toString();
            if (enteredPin.equals("1234")) {
                stopKioskMode();
                startInactivityMonitoring(); // Start monitoring after kiosk exit
                Toast.makeText(this, "Kiosk mode exited", Toast.LENGTH_SHORT).show();
                finishAffinity(); // Exit app
            } else {
                Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP &&
                (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0) {
            unlockAndExit();
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private void unlockAndExit() {
        stopKioskMode();
        startInactivityMonitoring(); // Start monitoring after kiosk exit
        finishAffinity(); // Close entire app
    }

    // Start the inactivity monitoring service
    private void startInactivityMonitoring() {
        Log.d(TAG, "Starting inactivity monitoring service");
        Intent serviceIntent = new Intent(this, InactivityMonitorService.class);
        serviceIntent.putExtra("action", "start_monitoring");
        startService(serviceIntent);
    }

    // ---- Android 13+ friendly kiosk lock ----
    @SuppressLint("ObsoleteSdkInt")
    private void ensureKioskModeIfNotLocked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null && am.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_LOCKED) {
                try {
                    startLockTask();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                startLockTask();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private void stopKioskMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                stopLockTask();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reset inactivity timer when user interacts with settings
        Intent serviceIntent = new Intent(this, InactivityMonitorService.class);
        serviceIntent.putExtra("action", "reset_timer");
        startService(serviceIntent);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        // Reset inactivity timer on any user interaction
        Intent serviceIntent = new Intent(this, InactivityMonitorService.class);
        serviceIntent.putExtra("action", "reset_timer");
        startService(serviceIntent);
    }
}