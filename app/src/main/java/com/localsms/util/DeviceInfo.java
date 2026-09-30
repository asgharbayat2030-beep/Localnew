package com.localsms.util;

import android.app.ActivityManager;
import android.content.Context;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import java.util.Locale;

public final class DeviceInfo {
    private DeviceInfo() {}

    public static int battery(Context c) {
        BatteryManager bm = (BatteryManager) c.getSystemService(Context.BATTERY_SERVICE);
        if (bm == null) return -1;
        int v = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return v >= 0 ? v : -1;
    }

    public static String model() {
        return Build.MANUFACTURER + " " + Build.MODEL;
    }

    public static String androidVersion() {
        return "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
    }

    public static String storage(Context c) {
        StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
        long total = stat.getTotalBytes();
        long free = stat.getAvailableBytes();
        long used = Math.max(0, total - free);
        return "{\"total\":" + total + ",\"free\":" + free + ",\"used\":" + used + "}";
    }

    public static String memory(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return "{\"total\":0,\"available\":0}";
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return "{\"total\":" + mi.totalMem + ",\"available\":" + mi.availMem + "}";
    }

    public static String humanBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double v = bytes / 1024.0;
        String[] units = {"KB", "MB", "GB", "TB"};
        int i = 0;
        while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
        return String.format(Locale.US, "%.1f %s", v, units[i]);
    }
}
