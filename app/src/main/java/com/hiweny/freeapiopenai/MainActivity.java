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
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Main activity with dark mode Material Design UI.
 * Provides controls for the proxy service and displays configuration info.
 */
public class MainActivity extends Activity {

    private static final int PORT = 8787;
    private static final String BASE_URL = "http://127.0.0.1:" + PORT + "/v1";
    private static final String ENDPOINT = BASE_URL + "/chat/completions";
    private static final String API_KEY = "sk-free-api";
    private static final String MODEL_LIST = "free-api, gemini-pro, gemini-1.5-pro, gemini-1.5-flash, gemini-flash, gpt-4o, gpt-4o-mini, deepseek-chat";

    // Dark theme colors
    private static final int C_BG = Color.parseColor("#0F172A");
    private static final int C_BG_CARD = Color.parseColor("#1E293B");
    private static final int C_BG_INPUT = Color.parseColor("#334155");
    private static final int C_TEXT = Color.parseColor("#F1F5F9");
    private static final int C_TEXT_SEC = Color.parseColor("#94A3B8");
    private static final int C_TEXT_MUTE = Color.parseColor("#64748B");
    private static final int C_BLUE = Color.parseColor("#3B82F6");
    private static final int C_GREEN = Color.parseColor("#22C55E");
    private static final int C_RED = Color.parseColor("#EF4444");
    private static final int C_BORDER = Color.parseColor("#334155");

    private TextView statusText;
    private TextView statusDot;
    private TextView testResult;
    private Button startButton;
    private Button stopButton;
    private Button testButton;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set dark window background
        getWindow().setBackgroundDrawable(new ColorDrawable(C_BG));
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(C_BG);
            getWindow().setNavigationBarColor(C_BG);
        }

        // Request notification permission
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
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
        scroll.setBackgroundColor(C_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(32), dp(22), dp(32));
        root.setBackgroundColor(C_BG);
        scroll.addView(root);

        // === Title ===
        TextView title = new TextView(this);
        title.setText("OpenAI 兼容代理");
        title.setTextColor(C_TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("在手机本机启动 OpenAI 兼容接口，自动隐藏广告，支持工具调用、流式响应和多模型别名。");
        subtitle.setTextColor(C_TEXT_SEC);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subtitle.setLineSpacing(dp(2), 1.2f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(8), 0, dp(24));
        subtitle.setLayoutParams(subLp);
        root.addView(subtitle);

        // === Status Card ===
        LinearLayout statusCard = card();
        LinearLayout statusHeader = new LinearLayout(this);
        statusHeader.setOrientation(LinearLayout.HORIZONTAL);
        statusHeader.setGravity(Gravity.CENTER_VERTICAL);

        statusDot = new TextView(this);
        statusDot.setText("\u25CF");
        statusDot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        statusDot.setTextColor(C_TEXT_MUTE);
        statusHeader.addView(statusDot);

        TextView statusLabel = new TextView(this);
        statusLabel.setText("  服务状态");
        statusLabel.setTextColor(C_TEXT);
        statusLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        statusLabel.setTypeface(Typeface.DEFAULT_BOLD);
        statusHeader.addView(statusLabel);
        statusCard.addView(statusHeader);

        statusText = new TextView(this);
        statusText.setTextColor(C_TEXT_SEC);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        statusText.setLineSpacing(dp(2), 1.2f);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(-1, -2);
        stLp.setMargins(0, dp(10), 0, 0);
        statusText.setLayoutParams(stLp);
        statusCard.addView(statusText);
        root.addView(statusCard);

        // === Buttons ===
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setWeightSum(2f);
        LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(-1, -2);
        btnRowLp.setMargins(0, dp(16), 0, dp(16));
        btnRow.setLayoutParams(btnRowLp);

        startButton = primaryButton("启动代理");
        startButton.setOnClickListener(v -> startProxy());
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        startLp.setMargins(0, 0, dp(6), 0);
        startButton.setLayoutParams(startLp);
        btnRow.addView(startButton);

        stopButton = secondaryButton("停止代理");
        stopButton.setOnClickListener(v -> stopProxy());
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        stopLp.setMargins(dp(6), 0, 0, 0);
        stopButton.setLayoutParams(stopLp);
        btnRow.addView(stopButton);
        root.addView(btnRow);

        // === Endpoint Card ===
        root.addView(infoCard("接口地址 (Base URL)", BASE_URL, "复制 Base URL"));

        // === Endpoint Full ===
        root.addView(infoCard("完整接口", ENDPOINT, "复制完整接口"));

        // === API Key Card ===
        root.addView(infoCard("API Key", API_KEY + "\n(任意 Key 均可，留空也行)", "复制 API Key"));

        // === Models Card ===
        root.addView(infoCard("可用模型", MODEL_LIST, "复制模型列表"));

        // === Test Button ===
        testButton = outlineButton("测试连接");
        testButton.setOnClickListener(v -> testConnection());
        root.addView(testButton);

        // === Test Result ===
        LinearLayout testCard = card();
        TextView testLabel = new TextView(this);
        testLabel.setText("测试结果");
        testLabel.setTextColor(C_TEXT);
        testLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        testLabel.setTypeface(Typeface.DEFAULT_BOLD);
        testCard.addView(testLabel);

        testResult = new TextView(this);
        testResult.setText("点击上方按钮测试连接...");
        testResult.setTextColor(C_TEXT_SEC);
        testResult.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        testResult.setMovementMethod(new ScrollingMovementMethod());
        testResult.setMinHeight(dp(80));
        testResult.setMaxHeight(dp(200));
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(-1, -2);
        trLp.setMargins(0, dp(8), 0, 0);
        testResult.setLayoutParams(trLp);
        testCard.addView(testResult);
        root.addView(testCard);

        // === Python SDK Config ===
        String pyConfig = "from openai import OpenAI\n\n"
                + "client = OpenAI(\n"
                + "    base_url=\"" + BASE_URL + "\",\n"
                + "    api_key=\"" + API_KEY + "\"\n"
                + ")\n\n"
                + "response = client.chat.completions.create(\n"
                + "    model=\"free-api\",\n"
                + "    messages=[{\"role\": \"user\", \"content\": \"你好\"}]\n"
                + ")";
        root.addView(infoCard("Python SDK 示例", pyConfig, "复制代码"));

        // === Copy All ===
        Button copyAll = outlineButton("复制全部配置");
        copyAll.setOnClickListener(v -> {
            String all = "OpenAI 兼容代理配置\n"
                    + "Base URL: " + BASE_URL + "\n"
                    + "Endpoint: " + ENDPOINT + "\n"
                    + "API Key: " + API_KEY + " (任意 Key 均可)\n"
                    + "Models: " + MODEL_LIST + "\n"
                    + "工具调用: 支持\n"
                    + "流式响应: 支持\n"
                    + "广告过滤: 已开启";
            copyToClipboard("配置", all);
        });
        root.addView(copyAll);

        // === Health Check Button ===
        Button health = outlineButton("打开健康检查");
        health.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:" + PORT + "/health"))));
        root.addView(health);

        // === Notes ===
        TextView note = new TextView(this);
        note.setText("提示：\n"
                + "- 如需让其他设备访问，将 127.0.0.1 替换为手机的局域网 IP\n"
                + "- 支持流式 (stream) 和非流式两种模式\n"
                + "- 工具调用 (function calling) 通过提示词注入实现\n"
                + "- 后端模型为 Google Gemini 系列\n"
                + "- 上游广告已自动过滤");
        note.setTextColor(C_TEXT_MUTE);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        note.setLineSpacing(dp(2), 1.3f);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.setMargins(0, dp(20), 0, 0);
        note.setLayoutParams(noteLp);
        root.addView(note);

        setContentView(scroll);
    }

    // === UI Helpers ===

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(C_BG_CARD);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(lp);
        return card;
    }

    private LinearLayout infoCard(String label, String content, String copyLabel) {
        LinearLayout card = card();

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(C_TEXT);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(labelView);

        TextView contentView = new TextView(this);
        contentView.setText(content);
        contentView.setTextColor(C_TEXT_SEC);
        contentView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        contentView.setTextIsSelectable(true);
        contentView.setLineSpacing(dp(1), 1.2f);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(-1, -2);
        contentLp.setMargins(0, dp(6), 0, dp(8));
        contentView.setLayoutParams(contentLp);
        card.addView(contentView);

        Button copyBtn = smallButton(copyLabel);
        copyBtn.setOnClickListener(v -> {
            copyToClipboard(label, content);
        });
        card.addView(copyBtn);

        return card;
    }

    private Button primaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(C_BLUE);
        b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.setMargins(0, 0, 0, dp(12));
        b.setLayoutParams(lp);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private Button secondaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTextColor(C_TEXT);
        b.setBackgroundColor(C_BG_INPUT);
        b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.setMargins(0, 0, 0, dp(12));
        b.setLayoutParams(lp);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private Button outlineButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setTextColor(C_BLUE);
        b.setBackgroundColor(C_BG_CARD);
        b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
        lp.setMargins(0, 0, 0, dp(12));
        b.setLayoutParams(lp);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setTextColor(C_TEXT_SEC);
        b.setBackgroundColor(C_BG_INPUT);
        b.setStateListAnimator(null);
        b.setPadding(dp(16), dp(6), dp(16), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        b.setLayoutParams(lp);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    // === Actions ===

    private void startProxy() {
        Intent intent = new Intent(this, ProxyService.class);
        intent.putExtra("port", PORT);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        handler.postDelayed(this::refreshStatus, 1000);
    }

    private void stopProxy() {
        stopService(new Intent(this, ProxyService.class));
        handler.postDelayed(this::refreshStatus, 500);
    }

    private void testConnection() {
        testButton.setEnabled(false);
        testButton.setText("测试中...");
        testResult.setText("正在发送测试请求...");
        testResult.setTextColor(C_TEXT_SEC);

        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .build();

                JSONObject payload = new JSONObject();
                payload.put("model", "free-api");
                payload.put("stream", false);
                org.json.JSONArray messages = new org.json.JSONArray();
                JSONObject msg = new JSONObject();
                msg.put("role", "user");
                msg.put("content", "你好，请用一句话介绍你自己");
                messages.put(msg);
                payload.put("messages", messages);

                Request request = new Request.Builder()
                        .url(ENDPOINT)
                        .post(RequestBody.create(payload.toString(), MediaType.get("application/json")))
                        .header("Authorization", "Bearer " + API_KEY)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String body = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        JSONObject json = new JSONObject(body);
                        org.json.JSONArray choices = json.optJSONArray("choices");
                        if (choices != null && choices.length() > 0) {
                            JSONObject choice = choices.optJSONObject(0);
                            JSONObject message = choice.optJSONObject("message");
                            String content = message != null ? message.optString("content", "") : "";
                            handler.post(() -> {
                                testResult.setText("连接成功!\n\n模型回复:\n" + content);
                                testResult.setTextColor(C_GREEN);
                            });
                        } else {
                            handler.post(() -> {
                                testResult.setText("连接成功，但响应格式异常:\n" + body.substring(0, Math.min(body.length(), 500)));
                                testResult.setTextColor(C_TEXT_SEC);
                            });
                        }
                    } else {
                        handler.post(() -> {
                            testResult.setText("连接失败 (" + response.code() + "):\n" + body.substring(0, Math.min(body.length(), 500)));
                            testResult.setTextColor(C_RED);
                        });
                    }
                }
            } catch (Exception e) {
                handler.post(() -> {
                    testResult.setText("连接失败:\n" + e.getMessage());
                    testResult.setTextColor(C_RED);
                });
            } finally {
                handler.post(() -> {
                    testButton.setEnabled(true);
                    testButton.setText("测试连接");
                });
            }
        }, "test-connection").start();
    }

    private void refreshStatus() {
        boolean running = ProxyService.isRunning();
        String error = ProxyService.getLastError();

        if (running) {
            statusDot.setTextColor(C_GREEN);
            statusText.setText("运行中\n"
                    + "本机端口: " + PORT + "\n"
                    + "上游: free-api.cnmwx.com\n"
                    + "广告过滤: 已开启\n"
                    + "工具调用: 已支持\n"
                    + "流式响应: 已支持");
            startButton.setEnabled(false);
            startButton.setAlpha(0.5f);
            stopButton.setEnabled(true);
            stopButton.setAlpha(1f);
        } else {
            statusDot.setTextColor(C_RED);
            if (error != null && !error.isEmpty()) {
                statusText.setText("未运行 (上次错误: " + error + ")\n点击\"启动代理\"重试");
            } else {
                statusText.setText("未运行\n点击\"启动代理\"开始监听本机端口 " + PORT);
            }
            startButton.setEnabled(true);
            startButton.setAlpha(1f);
            stopButton.setEnabled(false);
            stopButton.setAlpha(0.5f);
        }
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
