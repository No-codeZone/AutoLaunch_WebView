package com.example.autolaunchtechstern.ui.helper;

import android.annotation.SuppressLint;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.UserManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

public class MyDeviceAdminReceiver extends DeviceAdminReceiver {

    private static final String TAG = "MyDeviceAdminReceiver";

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.d(TAG, "Device Admin enabled");
        Toast.makeText(context, "Device Admin enabled", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Log.d(TAG, "Device Admin disabled");
        Toast.makeText(context, "Device Admin disabled", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPasswordChanged(Context context, Intent intent) {
        super.onPasswordChanged(context, intent);
        Log.d(TAG, "Password changed");
    }

    @Override
    public void onPasswordFailed(Context context, Intent intent) {
        super.onPasswordFailed(context, intent);
        Log.d(TAG, "Password failed");
    }

    @Override
    public void onPasswordSucceeded(Context context, Intent intent) {
        super.onPasswordSucceeded(context, intent);
        Log.d(TAG, "Password succeeded");
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        Log.d(TAG, "Disable requested");
        return "This will disable the kiosk mode. Are you sure?";
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);

        if (DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED.equals(action)) {
            // Device admin was enabled
            setupDeviceOwnerPolicies(context);
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private void setupDeviceOwnerPolicies(Context context) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(context, MyDeviceAdminReceiver.class);

        if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
            try {
                // Set lock task packages for kiosk mode
                dpm.setLockTaskPackages(adminComponent, new String[]{context.getPackageName()});

                // Disable keyguard
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    dpm.setKeyguardDisabled(adminComponent, true);
                }

                // Disable status bar
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    dpm.setStatusBarDisabled(adminComponent, true);
                }

                // Set user restrictions
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT);
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET);
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER);
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA);

                Log.d(TAG, "Device owner policies configured successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error setting up device owner policies: " + e.getMessage());
            }
        } else {
            Log.w(TAG, "App is not device owner, limited functionality available");
        }
    }
}