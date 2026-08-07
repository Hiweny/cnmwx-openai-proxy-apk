package com.hiweny.freeapiopenai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class ProxyService extends Service {
    private static final String CHANNEL_ID = "proxy_service";
    private static volatile boolean running = false;
    private LocalOpenAiServer server;

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int port = intent != null ? intent.getIntExtra("port", 8787) : 8787;
        startForeground(7, buildNotification(port));
        if (server == null) {
            try {
                server = new LocalOpenAiServer(port);
                server.start();
                running = true;
            } catch (Exception e) {
                running = false;
                stopSelf();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (server != null) {
            server.stop();
            server = null;
        }
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "OpenAI 代理服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(int port) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setContentTitle("OpenAI 兼容代理运行中")
                .setContentText("http://127.0.0.1:" + port + "/v1/chat/completions")
                .setSmallIcon(com.hiweny.freeapiopenai.R.drawable.ic_stat_proxy)
                .setOngoing(true)
                .build();
    }
}
