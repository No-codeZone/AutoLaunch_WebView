package com.example.autolaunchtechstern.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.example.autolaunchtechstern.ui.helper.MyDeviceAdminReceiver;

public class PermissionManager {
    private static final String TAG = "PermissionManager";
    private final Activity activity;
    private final PermissionCallback callback;
    private int currentStep = 0;

    // Permission request codes
    private static final int REQUEST_DEVICE_ADMIN = 1001;
    private static final int REQUEST_BATTERY_OPTIMIZATION = 1002;
    private static final int REQUEST_OVERLAY_PERMISSION = 1003;

    public interface PermissionCallback {
        void onAllPermissionsGranted();
        void onPermissionDenied(String permission);
        void onPermissionStepProgress(int current, int total, String permissionName);
    }

    public PermissionManager(Activity activity, PermissionCallback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    public void requestAllPermissions() {
        currentStep = 0;
        checkNextPermission();
    }

    private void checkNextPermission() {
        switch (currentStep) {
            case 0:
                checkDeviceAdmin();
                break;
            case 1:
                checkBatteryOptimization();
                break;
            case 2:
                checkOverlayPermission();
                break;
            default:
                // All permissions checked
                callback.onAllPermissionsGranted();
                break;
        }
    }

    private void checkDeviceAdmin() {
        callback.onPermissionStepProgress(1, 3, "Device Administrator");

        DevicePolicyManager dpm = (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(activity, MyDeviceAdminReceiver.class);

        if (dpm != null && dpm.isAdminActive(adminComponent)) {
            Log.d(TAG, "Device admin already granted");
            currentStep++;
            checkNextPermission();
        } else {
            showPermissionDialog(
                    "Device Administrator Access",
                    "This app needs Device Administrator privileges to enable kiosk mode and prevent unauthorized access.",
                    "Grant Permission",
                    () -> requestDeviceAdmin()
            );
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private void checkBatteryOptimization() {
        callback.onPermissionStepProgress(2, 3, "Battery Optimization");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
            if (pm != null && pm.isIgnoringBatteryOptimizations(activity.getPackageName())) {
                Log.d(TAG, "Battery optimization already disabled");
                currentStep++;
                checkNextPermission();
            } else {
                showPermissionDialog(
                        "Battery Optimization",
                        "To ensure the kiosk runs continuously, please disable battery optimization for this app.",
                        "Disable Optimization",
                        () -> requestBatteryOptimization()
                );
            }
        } else {
            currentStep++;
            checkNextPermission();
        }
    }

    private void checkOverlayPermission() {
        callback.onPermissionStepProgress(3, 3, "Overlay Permission");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(activity)) {
                Log.d(TAG, "Overlay permission already granted");
                currentStep++;
                checkNextPermission();
            } else {
                showPermissionDialog(
                        "Display Over Other Apps",
                        "This permission allows the admin unlock dialog to appear over the kiosk screen.",
                        "Grant Permission",
                        () -> requestOverlayPermission()
                );
            }
        } else {
            currentStep++;
            checkNextPermission();
        }
    }

    private void showPermissionDialog(String title, String message, String buttonText, Runnable onAccept) {
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(buttonText, (dialog, which) -> onAccept.run())
                .setNegativeButton("Skip", (dialog, which) -> {
                    callback.onPermissionDenied(title);
                    currentStep++;
                    checkNextPermission();
                })
                .setCancelable(false)
                .show();
    }

    private void requestDeviceAdmin() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponent = new ComponentName(activity, MyDeviceAdminReceiver.class);

            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "This app requires Device Administrator privileges to function as a secure kiosk. " +
                            "This will allow the app to prevent unauthorized access and maintain kiosk mode.");

            activity.startActivityForResult(intent, REQUEST_DEVICE_ADMIN);
        } catch (Exception e) {
            Log.e(TAG, "Error requesting device admin: " + e.getMessage());
            callback.onPermissionDenied("Device Administrator");
            currentStep++;
            checkNextPermission();
        }
    }

    @SuppressLint("BatteryLife")
    private void requestBatteryOptimization() {
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivityForResult(intent, REQUEST_BATTERY_OPTIMIZATION);
        } catch (Exception e) {
            Log.e(TAG, "Error requesting battery optimization: " + e.getMessage());
            callback.onPermissionDenied("Battery Optimization");
            currentStep++;
            checkNextPermission();
        }
    }

    private void requestOverlayPermission() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
        } catch (Exception e) {
            Log.e(TAG, "Error requesting overlay permission: " + e.getMessage());
            callback.onPermissionDenied("Overlay Permission");
            currentStep++;
            checkNextPermission();
        }
    }

    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_DEVICE_ADMIN:
                if (resultCode == Activity.RESULT_OK) {
                    Log.d(TAG, "Device admin granted");
                    Toast.makeText(activity, "Device Administrator activated", Toast.LENGTH_SHORT).show();
                } else {
                    Log.w(TAG, "Device admin denied");
                    Toast.makeText(activity, "Device Administrator required for full functionality", Toast.LENGTH_LONG).show();
                }
                currentStep++;
                // Add small delay to let the system settle
                new Handler(Looper.getMainLooper()).postDelayed(this::checkNextPermission, 1000);
                break;

            case REQUEST_BATTERY_OPTIMIZATION:
                PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
                if (pm != null && pm.isIgnoringBatteryOptimizations(activity.getPackageName())) {
                    Log.d(TAG, "Battery optimization disabled");
                    Toast.makeText(activity, "Battery optimization disabled", Toast.LENGTH_SHORT).show();
                } else {
                    Log.w(TAG, "Battery optimization still enabled");
                    Toast.makeText(activity, "Battery optimization recommended for best performance", Toast.LENGTH_LONG).show();
                }
                currentStep++;
                new Handler(Looper.getMainLooper()).postDelayed(this::checkNextPermission, 1000);
                break;

            case REQUEST_OVERLAY_PERMISSION:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(activity)) {
                    Log.d(TAG, "Overlay permission granted");
                    Toast.makeText(activity, "Overlay permission granted", Toast.LENGTH_SHORT).show();
                } else {
                    Log.w(TAG, "Overlay permission denied");
                    Toast.makeText(activity, "Overlay permission recommended for admin access", Toast.LENGTH_LONG).show();
                }
                currentStep++;
                new Handler(Looper.getMainLooper()).postDelayed(this::checkNextPermission, 1000);
                break;
        }
    }
}
