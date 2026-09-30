package com.localsms.util;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String NAME = "localsms_prefs";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_AUTO_BOOT = "auto_boot";
    private static final String KEY_RUNNING = "running";

    private Prefs() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String getToken(Context c) {
        String token = p(c).getString(KEY_TOKEN, null);
        if (token == null || token.length() != 6) {
            token = String.format(java.util.Locale.US, "%06d", new java.security.SecureRandom().nextInt(1_000_000));
            p(c).edit().putString(KEY_TOKEN, token).apply();
        }
        return token;
    }

    public static void regenerateToken(Context c) {
        String token = String.format(java.util.Locale.US, "%06d", new java.security.SecureRandom().nextInt(1_000_000));
        p(c).edit().putString(KEY_TOKEN, token).apply();
    }

    public static boolean autoBoot(Context c) {
        return p(c).getBoolean(KEY_AUTO_BOOT, true);
    }

    public static void setAutoBoot(Context c, boolean value) {
        p(c).edit().putBoolean(KEY_AUTO_BOOT, value).apply();
    }

    public static boolean running(Context c) {
        return p(c).getBoolean(KEY_RUNNING, false);
    }

    public static void setRunning(Context c, boolean value) {
        p(c).edit().putBoolean(KEY_RUNNING, value).apply();
    }
}
