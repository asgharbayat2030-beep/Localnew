package com.localsms;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.localsms.server.LocalServer;
import com.localsms.service.LocalSmsService;
import com.localsms.util.DeviceInfo;
import com.localsms.util.NetworkUtils;
import com.localsms.util.Prefs;

public class MainActivity extends Activity {
    private static final int REQ_SMS = 100;
    private static final int REQ_NOTIFICATIONS = 101;
    private TextView status;
    private TextView device;
    private TextView endpoints;

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        requestRequiredPermissions();
    }

    private TextView text(String value, float size, int color) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setPadding(dp(2), dp(4), dp(2), dp(4));
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(14), dp(18), dp(14)); c.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(8), 0, dp(8));
        c.setLayoutParams(lp); return c;
    }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(15); b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(-1, -2)); return b;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(Color.rgb(244,247,251));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18), dp(24), dp(18), dp(24));

        TextView title = text("📱  Local Device Manager", 27, Color.rgb(20,31,49)); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);
        TextView sub = text("کنترل و پایش امن دستگاه در شبکه محلی", 14, Color.DKGRAY); root.addView(sub);

        LinearLayout overview = card();
        status = text("در حال بررسی وضعیت…", 18, Color.rgb(25,35,50)); status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        device = text("", 14, Color.DKGRAY); endpoints = text("", 13, Color.DKGRAY); endpoints.setTextIsSelectable(true);
        overview.addView(status); overview.addView(device); overview.addView(endpoints); root.addView(overview);

        LinearLayout actions = card(); actions.addView(text("کنترل سرویس", 18, Color.rgb(25,35,50)));
        actions.addView(button("▶  فعال کردن سرویس و داشبورد", v -> startServer()));
        actions.addView(button("■  توقف کامل سرویس", v -> stopServer()));
        actions.addView(button("🔑  تولید Token جدید", v -> { Prefs.regenerateToken(this); refreshUi(); Toast.makeText(this, "Token جدید ساخته شد", Toast.LENGTH_SHORT).show(); }));
        actions.addView(button("📋  کپی آدرس Remote", v -> copy("http://" + NetworkUtils.getLocalIpAddress() + ":8081/?token=" + Prefs.getToken(this))));
        root.addView(actions);

        LinearLayout settings = card(); settings.addView(text("تنظیمات", 18, Color.rgb(25,35,50)));
        CheckBox auto = new CheckBox(this); auto.setText("اجرای خودکار بعد از روشن شدن گوشی"); auto.setChecked(Prefs.autoBoot(this));
        auto.setOnCheckedChangeListener((b, checked) -> Prefs.setAutoBoot(this, checked)); settings.addView(auto);
        settings.addView(text("نسخه 4.0 • Remote LAN • Dashboard • SMS • Device status", 13, Color.GRAY)); root.addView(settings);

        LinearLayout info = card(); info.addView(text("امنیت", 18, Color.rgb(25,35,50)));
        info.addView(text("این نسخه برای شبکه محلی طراحی شده است. دسترسی‌های مدیریتی فقط از طریق APIهای مشخص برنامه انجام می‌شوند و اجرای آزاد دستورات سیستم در Remote وجود ندارد.", 13, Color.DKGRAY)); root.addView(info);

        scroll.addView(root); setContentView(scroll); refreshUi();
    }

    private void requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS); return;
        }
        requestSmsIfNeeded();
    }
    private void requestSmsIfNeeded() { if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.READ_SMS}, REQ_SMS); }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == REQ_NOTIFICATIONS) requestSmsIfNeeded();
        if (requestCode == REQ_SMS && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) startServer();
    }

    private void startServer() {
        if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) { requestSmsIfNeeded(); return; }
        Intent i = new Intent(this, LocalSmsService.class).setAction(LocalSmsService.ACTION_START);
        try { if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i); Prefs.setAutoBoot(this, true); refreshUi(); }
        catch (Exception e) { status.setText("❌ خطا: " + e.getMessage()); }
    }
    private void stopServer() { startService(new Intent(this, LocalSmsService.class).setAction(LocalSmsService.ACTION_STOP)); Prefs.setRunning(this, false); refreshUi(); }

    private void refreshUi() {
        boolean running = Prefs.running(this);
        status.setText(running ? "🟢 سرویس آنلاین است" : "⚪ سرویس متوقف است");
        device.setText(DeviceInfo.model() + "  •  " + DeviceInfo.androidVersion() + "  •  🔋 " + DeviceInfo.battery(this) + "%");
        String ip = NetworkUtils.getLocalIpAddress();
        endpoints.setText("Dashboard: http://" + ip + ":8080\nRemote: http://" + ip + ":8081/?token=" + Prefs.getToken(this));
    }

    private void copy(String value) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Local Device Manager", value));
        Toast.makeText(this, "کپی شد", Toast.LENGTH_SHORT).show();
    }
    @Override protected void onResume() { super.onResume(); refreshUi(); }
}
