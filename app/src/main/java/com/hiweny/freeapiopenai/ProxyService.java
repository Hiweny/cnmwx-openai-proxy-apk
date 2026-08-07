package com.hiweny.freeapiopenai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/**
 * Foreground service that runs the local OpenAI-compatible proxy server.
 * Keeps the server alive in the background and shows a persistent notification.
 */
public class ProxyService extends Service {
    private static final String TAG = "ProxyService";
    private static final String CHANNEL_ID = "proxy_service";

    private static volatile boolean running = false;
    private static volatile String lastError = null;

    private LocalOpenAiServer server;

    public static boolean isRunning() {
        return running;
    }

    public static String getLastError() {
        return lastError;
    }

    public static void clearError() {
        lastError = null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int port = intent != null ? intent.getIntExtra("port", 8787) : 8787;
        startForeground(7, buildNotification(port, "服务启动中..."));

        if (server == null) {
            try {
                server = new LocalOpenAiServer(port);
                // Use long timeout for streaming responses (5 minutes)
                server.start(300000, true);
                running = true;
                lastError = null;
                updateNotification(port, "运行中 - http://127.0.0.1:" + port);
                Log.i(TAG, "Server started on port " + port);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start server: " + e.getMessage(), e);
                running = false;
                lastError = e.getMessage();
                updateNotification(port, "启动失败: " + e.getMessage());
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping server: " + e.getMessage());
            }
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
            channel.setDescription("保持 OpenAI 兼容代理在后台运行");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(int port, String content) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setContentTitle("OpenAI 兼容代理")
                .setContentText(content)
                .setSmallIcon(com.hiweny.freeapiopenai.R.drawable.ic_stat_proxy)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(int port, String content) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(7, buildNotification(port, content));
        }
    }
}
