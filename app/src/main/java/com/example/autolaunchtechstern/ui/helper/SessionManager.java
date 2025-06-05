package com.example.autolaunchtechstern.ui.helper;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "AppPrefs";
    private static final String KEY_URL = "server_url";
    private static final String KEY_HEARTBEAT = "heartbeat_interval";
    private SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void setServerUrl(String url) {
        prefs.edit().putString(KEY_URL, url).apply();
    }

    public String getServerUrl() {
        return prefs.getString(KEY_URL, "https://www.techstern.com/");
    }

    public void setHeartbeat(int seconds) {
        prefs.edit().putInt(KEY_HEARTBEAT, seconds).apply();
    }

    public int getHeartbeat() {
        return prefs.getInt(KEY_HEARTBEAT, 30);
    }
}

