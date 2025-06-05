package com.example.autolaunchtechstern.ui;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.example.autolaunchtechstern.ui.helper.InactivityMonitorService;
import com.example.autolaunchtechstern.ui.helper.MyDeviceAdminReceiver;
import com.example.autolaunchtechstern.ui.helper.SessionManager;
import android.app.Activity;
import android.view.MotionEvent;

public class MainActivity extends Activity implements PermissionManager.PermissionCallback {
    private SessionManager session;
    private boolean dialogShown = false;
    private long lastBackPressTime = 0;
    private int backPressCount = 0;
    private static final int REQUIRED_BACK_PRESSES = 5;
    private static final long BACK_PRESS_TIMEOUT = 2000;
    private static final long INACTIVITY_DELAY_MS = 30_000;
    private final Handler inactivityHandler = new Handler(Looper.getMainLooper());
    private Handler handler;
    private final Runnable enterKioskRunnable = this::ensureKioskMode;
    private final Handler heartbeatHandler = new Handler(Looper.getMainLooper());
    private boolean isKioskModeActive = false;
    private DevicePolicyManager dpm;
    private ComponentName adminComponent;
    private PermissionManager permissionManager;
    private boolean allPermissionsGranted = false;
    private ProgressDialog permissionProgressDialog;
    private final Runnable heartbeatTask = new Runnable() {
        @Override
        public void run() {
            int interval = session.getHeartbeat() * 1000;
            Log.d("Heartbeat", "Ping at " + System.currentTimeMillis());
            heartbeatHandler.postDelayed(this, interval);
        }
    };
    @SuppressLint({"ClickableViewAccessibility", "SetJavaScriptEnabled", "ObsoleteSdkInt"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new Handler(Looper.getMainLooper());
        session = new SessionManager(this);
        // Initialize device admin components
        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, MyDeviceAdminReceiver.class);
        // Initialize permission manager
        permissionManager = new PermissionManager(this, this);
        // Check if launched from boot
        boolean launchedFromBoot = getIntent().getBooleanExtra("launched_from_boot", false) ||
                getIntent().getBooleanExtra("launched_from_job", false);
        if (launchedFromBoot) {
            Log.d("MainActivity", "App launched from boot - checking permissions");
            // If launched from boot, be more lenient with permission checking
            if (hasEssentialPermissions()) {
                allPermissionsGranted = true;
                initializeApp();
            } else {
                // Show simplified setup for boot launch
                showBootPermissionDialog();
            }
        } else {
            // Normal app launch - full permission check
            if (needsPermissionSetup()) {
                showPermissionSetupDialog();
            } else {
                allPermissionsGranted = true;
                initializeApp();
            }
        }
    }
    private boolean hasEssentialPermissions() {
        // Check only the most critical permissions for boot launch
        boolean hasDeviceAdmin = dpm != null && dpm.isAdminActive(adminComponent);
        return hasDeviceAdmin; // Only require device admin for boot launch
    }
    private void showBootPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Kiosk Started from Boot")
                .setMessage("The kiosk app has started automatically. Some permissions may need to be configured for full functionality.")
                .setPositiveButton("Configure Now", (dialog, which) -> startPermissionSetup())
                .setNegativeButton("Continue", (dialog, which) -> {
                    allPermissionsGranted = false;
                    initializeApp();
                })
                .setCancelable(false)
                .show();
    }
    private boolean needsPermissionSetup() {
        // Check if essential permissions are missing
        boolean hasDeviceAdmin = dpm != null && dpm.isAdminActive(adminComponent);
        boolean hasBatteryOptimization = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            hasBatteryOptimization = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        }

        boolean hasOverlayPermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hasOverlayPermission = Settings.canDrawOverlays(this);
        }
        return !hasDeviceAdmin || !hasBatteryOptimization || !hasOverlayPermission;
    }
    private void showPermissionSetupDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Kiosk Setup Required")
                .setMessage("This app requires several permissions to function as a secure kiosk. " +
                        "You'll be guided through each permission step by step.")
                .setPositiveButton("Continue Setup", (dialog, which) -> startPermissionSetup())
                .setNegativeButton("Skip (Limited Mode)", (dialog, which) -> {
                    allPermissionsGranted = false;
                    initializeApp();
                })
                .setCancelable(false)
                .show();
    }
    private void startPermissionSetup() {
        // Show progress dialog
        permissionProgressDialog = new ProgressDialog(this);
        permissionProgressDialog.setTitle("Setting up Kiosk");
        permissionProgressDialog.setMessage("Preparing permissions...");
        permissionProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        permissionProgressDialog.setMax(3);
        permissionProgressDialog.setCancelable(false);
        permissionProgressDialog.show();
        // Start permission flow
        permissionManager.requestAllPermissions();
    }
    // PermissionManager.PermissionCallback implementation
    @Override
    public void onAllPermissionsGranted() {
        if (permissionProgressDialog != null && permissionProgressDialog.isShowing()) {
            permissionProgressDialog.dismiss();
        }
        allPermissionsGranted = true;
        new AlertDialog.Builder(this)
                .setTitle("Setup Complete")
                .setMessage("All permissions have been configured. The kiosk is now ready to use.")
                .setPositiveButton("Start Kiosk", (dialog, which) -> initializeApp())
                .setCancelable(false)
                .show();
    }
    @Override
    public void onPermissionDenied(String permission) {
        Log.w("MainActivity", "Permission denied: " + permission);
        Toast.makeText(this, "Permission denied: " + permission + " (functionality may be limited)",
                Toast.LENGTH_LONG).show();
    }
    @Override
    public void onPermissionStepProgress(int current, int total, String permissionName) {
        if (permissionProgressDialog != null) {
            permissionProgressDialog.setProgress(current);
            permissionProgressDialog.setMessage("Requesting: " + permissionName);
        }
    }
    private void initializeApp() {
        // Start the kiosk service
        Intent serviceIntent = new Intent(this, KioskService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        // Setup device admin if available
        if (allPermissionsGranted) {
            setupKioskPermissions();
        }
        // Setup UI
        setupWebView();
    }
    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        FrameLayout layout = new FrameLayout(this);
        WebView webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Ensure kiosk mode after page loads
                if (allPermissionsGranted) {
                    ensureKioskMode();
                }
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                Log.e("MainActivity", "WebView error: " + error.getDescription());
            }
        });
        webView.loadUrl(session.getServerUrl());
        layout.addView(webView);

        // Setup input handlers
        setupInputHandlers(webView);
        setContentView(layout);
    }
    @SuppressLint("ClickableViewAccessibility")
    private void setupInputHandlers(WebView webView) {
        // Handle various input methods for unlock
        webView.setOnTouchListener((v, event) -> {
            // Method 1: Long press detection
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.performLongClick();
            }
            // Method 2: Right-click detection (for devices with mouse support)
            if (event.getAction() == MotionEvent.ACTION_DOWN &&
                    (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0) {
                showUnlockDialog();
                return true;
            }
            return false;
        });
        // Method 3: Long press listener
        webView.setOnLongClickListener(v -> {
            showUnlockDialog();
            return true;
        });
    }

    // Method 4: Multiple back button presses (Android TV remote friendly)
    @Override
    public void onBackPressed() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBackPressTime > BACK_PRESS_TIMEOUT) {
            backPressCount = 1;
        } else {
            backPressCount++;
        }
        lastBackPressTime = currentTime;

        if (backPressCount >= REQUIRED_BACK_PRESSES) {
            backPressCount = 0;
            showUnlockDialog();
        } else {
            Toast.makeText(this, "Press back " + (REQUIRED_BACK_PRESSES - backPressCount) + " more times",
                    Toast.LENGTH_SHORT).show();
        }
        // Don't call super.onBackPressed() to prevent exiting the app
    }
    // Method 5: Key combination detection (for Android TV)
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Detect specific key combinations
        if (keyCode == KeyEvent.KEYCODE_MENU ||
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.isShiftPressed()) ||
                (keyCode == KeyEvent.KEYCODE_ENTER && event.isAltPressed())) {
            showUnlockDialog();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Right-click detection at activity level
        if (event.getAction() == MotionEvent.ACTION_DOWN &&
                (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0) {
            showUnlockDialog();
            return true;
        }
        return super.dispatchTouchEvent(event);
    }
    private void showUnlockDialog() {
        if (dialogShown) {
            return;
        }
        dialogShown = true;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Admin PIN");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("Enter 4-digit PIN");
        builder.setView(input);
        builder.setPositiveButton("OK", (dialog, which) -> {
            String enteredPin = input.getText().toString();
            if (enteredPin.equals("1234")) {
                stopKioskAndGoToSettings();
            } else {
                Toast.makeText(MainActivity.this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
            }
            dialogShown = false;
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.cancel();
            dialogShown = false;
        });
        builder.setOnDismissListener(dialog -> dialogShown = false);
        AlertDialog dialog = builder.create();
        // Ensure dialog can be shown over kiosk mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Settings.canDrawOverlays(this)) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        }
        dialog.show();
        // Auto-focus and show keyboard
        input.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        if (allPermissionsGranted) {
            resetInactivityTimer();
            heartbeatHandler.post(heartbeatTask);
            // Delay kiosk mode to avoid interference with other activities
            handler.postDelayed(this::ensureKioskMode, 1000);
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
        cancelInactivityTimer();
        heartbeatHandler.removeCallbacks(heartbeatTask);
        stopInactivityMonitoring();
    }
    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (allPermissionsGranted) {
            resetInactivityTimer();
        }
        Intent serviceIntent = new Intent(this, InactivityMonitorService.class);
        serviceIntent.putExtra("action", "reset_timer");
        startService(serviceIntent);
    }
    private void stopInactivityMonitoring() {
        Intent serviceIntent = new Intent(this, InactivityMonitorService.class);
        serviceIntent.putExtra("action", "stop_monitoring");
        startService(serviceIntent);
    }
    private void resetInactivityTimer() {
        cancelInactivityTimer();
        inactivityHandler.postDelayed(enterKioskRunnable, INACTIVITY_DELAY_MS);
    }
    private void cancelInactivityTimer() {
        inactivityHandler.removeCallbacks(enterKioskRunnable);
    }
    @SuppressLint("ObsoleteSdkInt")
    private void stopKioskAndGoToSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && isKioskModeActive) {
                stopLockTask();
                isKioskModeActive = false;
                Log.d("MainActivity", "Exited kiosk mode successfully");
            }

            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            Log.e("MainActivity", "Error stopping kiosk mode: " + e.getMessage());
            // Still try to go to settings even if kiosk exit fails
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        }
    }
    @SuppressLint("ObsoleteSdkInt")
    private void ensureKioskMode() {
        if (!allPermissionsGranted) {
            Log.w("MainActivity", "Skipping kiosk mode - permissions not granted");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null && am.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_LOCKED) {
                    if (dpm != null && dpm.isLockTaskPermitted(getPackageName())) {
                        Log.d("MainActivity", "Entering kiosk mode via startLockTask()");
                        startLockTask();
                        isKioskModeActive = true;
                    } else {
                        Log.w("MainActivity", "Lock task not permitted");
                    }
                } else {
                    Log.d("MainActivity", "Already in kiosk mode");
                    isKioskModeActive = true;
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Log.d("MainActivity", "Starting kiosk mode for older Android version");
                startLockTask();
                isKioskModeActive = true;
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error starting kiosk mode: " + e.getMessage());
        }
    }
    private void setupKioskPermissions() {
        if (dpm != null && adminComponent != null && dpm.isAdminActive(adminComponent)) {
            try {
                // Set lock task packages
                dpm.setLockTaskPackages(adminComponent, new String[]{getPackageName()});
                Log.d("MainActivity", "Kiosk permissions configured");
            } catch (Exception e) {
                Log.e("MainActivity", "Error setting up kiosk permissions: " + e.getMessage());
            }
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Let PermissionManager handle the result
        if (permissionManager != null) {
            permissionManager.handleActivityResult(requestCode, resultCode, data);
        }
    }
}