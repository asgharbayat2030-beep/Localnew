package com.localsms.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.localsms.MainActivity;
import com.localsms.R;
import com.localsms.server.LocalServer;
import com.localsms.server.RemoteControlServer;
import com.localsms.util.NetworkUtils;
import com.localsms.util.Prefs;

public class LocalSmsService extends Service {
    public static final String ACTION_START = "com.localsms.action.START";
    public static final String ACTION_STOP = "com.localsms.action.STOP";
    public static final String ACTION_REMOTE_START = "com.localsms.action.REMOTE_START";
    public static final String ACTION_REMOTE_STOP = "com.localsms.action.REMOTE_STOP";
    private static final String CHANNEL_ID = "localsms_server";
    private static final int NOTIFICATION_ID = 8080;
    private LocalServer server;
    private RemoteControlServer remote;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        remote = new RemoteControlServer(this);
        remote.start();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopServerAndSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_REMOTE_STOP.equals(action)) {
            stopMainServerOnly();
            return START_STICKY;
        }
        if (ACTION_REMOTE_START.equals(action)) {
            startMainServer();
            return START_STICKY;
        }
        startMainServer();
        return START_STICKY;
    }


    private void startMainServer() {
        if (checkSelfPermission(android.Manifest.permission.READ_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Prefs.setRunning(this, false);
            return;
        }
        if (server == null || !server.isRunning()) {
            server = new LocalServer(this);
            if (!server.start()) {
                Prefs.setRunning(this, false);
                updateNotification();
                return;
            }
        }
        Prefs.setRunning(this, true);
        updateNotification();
    }

    private void stopMainServerOnly() {
        if (server != null) server.stop();
        server = null;
        Prefs.setRunning(this, false);
        updateNotification();
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, LocalSmsService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 2, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String ip = NetworkUtils.getLocalIpAddress();
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText("http://" + ip + ":" + LocalServer.PORT)
                .setContentIntent(openPi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null, getString(R.string.notification_stop), stopPi).build())
                .build();
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification());
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("وضعیت سرور محلی Local Device Manager");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private void stopServerAndSelf() {
        if (server != null) server.stop();
        server = null;
        if (remote != null) remote.stop();
        remote = null;
        Prefs.setRunning(this, false);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onDestroy() {
        if (server != null) server.stop();
        if (remote != null) remote.stop();
        Prefs.setRunning(this, false);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
