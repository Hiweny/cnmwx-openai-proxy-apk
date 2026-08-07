package com.hiweny.freeapiopenai;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int PORT = 8787;
    private TextView statusText;
    private Button startButton;
    private Button stopButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
        buildUi();
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(28));
        root.setBackgroundColor(Color.rgb(248, 250, 252));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("公益 API OpenAI 代理");
        title.setTextColor(Color.rgb(15, 23, 42));
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("在手机本机启动 OpenAI 兼容接口，自动隐藏上游广告，适合给支持 OpenAI Base URL 的客户端使用。");
        subtitle.setTextColor(Color.rgb(71, 85, 105));
        subtitle.setTextSize(15);
        subtitle.setPadding(0, dp(10), 0, dp(18));
        root.addView(subtitle);

        statusText = cardText("状态检查中...");
        root.addView(statusText);

        startButton = primaryButton("启动代理");
        startButton.setOnClickListener(v -> startProxy());
        root.addView(startButton);

        stopButton = secondaryButton("停止代理");
        stopButton.setOnClickListener(v -> stopProxy());
        root.addView(stopButton);

        String url = "http://127.0.0.1:" + PORT + "/v1/chat/completions";
        TextView endpoint = cardText("接口地址\n" + url + "\n\n模型名\nfree-api\n\n鉴权\n无需 API Key");
        endpoint.setTextIsSelectable(true);
        root.addView(endpoint);

        Button copy = secondaryButton("复制接口地址");
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("OpenAI Base URL", url));
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
        });
        root.addView(copy);

        Button health = secondaryButton("打开健康检查");
        health.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:" + PORT + "/health"))));
        root.addView(health);

        TextView note = new TextView(this);
        note.setText("提示：如需让其他设备访问，请把 127.0.0.1 换成手机在同一局域网内的 IP 地址，并保持本应用服务运行。");
        note.setTextColor(Color.rgb(100, 116, 139));
        note.setTextSize(13);
        note.setPadding(0, dp(18), 0, 0);
        root.addView(note);

        setContentView(scroll);
    }

    private TextView cardText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.rgb(30, 41, 59));
        tv.setTextSize(15);
        tv.setPadding(dp(16), dp(14), dp(16), dp(14));
        tv.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(14));
        tv.setLayoutParams(lp);
        return tv;
    }

    private Button primaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.rgb(37, 99, 235));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.setMargins(0, 0, 0, dp(12));
        b.setLayoutParams(lp);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private Button secondaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(Color.rgb(15, 23, 42));
        b.setBackgroundColor(Color.rgb(226, 232, 240));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.setMargins(0, 0, 0, dp(12));
        b.setLayoutParams(lp);
        return b;
    }

    private void startProxy() {
        Intent intent = new Intent(this, ProxyService.class);
        intent.putExtra("port", PORT);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        statusText.postDelayed(this::refreshStatus, 800);
    }

    private void stopProxy() {
        stopService(new Intent(this, ProxyService.class));
        statusText.postDelayed(this::refreshStatus, 400);
    }

    private void refreshStatus() {
        boolean running = ProxyService.isRunning();
        statusText.setText(running
                ? "运行中\n本机端口：" + PORT + "\n上游：free-api.cnmwx.com\n广告过滤：已开启"
                : "未运行\n点击“启动代理”后开始监听本机端口 " + PORT);
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
