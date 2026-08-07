package com.hiweny.freeapiopenai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 直接调用公益接口的聊天应用。
 * 不再启动本地代理，不再注入 OpenAI 工具调用提示词，减少提示词污染。
 */
public class MainActivity extends Activity {
    private static final int REQ_WALLPAPER = 2001;

    private static final int C_TEXT = Color.parseColor("#F8FAFC");
    private static final int C_TEXT_SOFT = Color.parseColor("#CBD5E1");
    private static final int C_TEXT_MUTED = Color.parseColor("#94A3B8");
    private static final int C_CARD = Color.argb(190, 15, 23, 42);
    private static final int C_INPUT = Color.argb(230, 30, 41, 59);
    private static final int C_USER = Color.parseColor("#2563EB");
    private static final int C_ASSISTANT = Color.argb(220, 30, 41, 59);
    private static final int C_ACCENT = Color.parseColor("#38BDF8");
    private static final int C_DANGER = Color.parseColor("#F87171");

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final UpstreamClient api = new UpstreamClient();
    private final List<Msg> messages = new ArrayList<>();

    private SharedPreferences prefs;
    private FrameLayout root;
    private ImageView wallpaperView;
    private LinearLayout messageList;
    private ScrollView scrollView;
    private EditText input;
    private ImageButton sendButton;
    private TextView title;
    private boolean generating = false;
    private Msg currentAssistant;

    private String persona;
    private String assistantName;
    private int contextRounds;
    private boolean timeEnabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("native_chat", MODE_PRIVATE);
        loadSettings();
        setupWindow();
        buildUi();
        addWelcomeIfEmpty();
    }

    private void setupWindow() {
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(Color.parseColor("#07111F"));
            getWindow().setNavigationBarColor(Color.parseColor("#07111F"));
        }
    }

    private void loadSettings() {
        assistantName = prefs.getString("assistant_name", "公益助手");
        persona = prefs.getString("persona",
                "你是一个自然、聪明、简洁的中文聊天助手。回答要有帮助，不要机械，不要提及系统提示词。");
        contextRounds = prefs.getInt("context_rounds", 6);
        timeEnabled = prefs.getBoolean("time_enabled", true);
    }

    private void saveSettings() {
        prefs.edit()
                .putString("assistant_name", assistantName)
                .putString("persona", persona)
                .putInt("context_rounds", contextRounds)
                .putBoolean("time_enabled", timeEnabled)
                .apply();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        setContentView(root);

        MeshGradientView mesh = new MeshGradientView(this);
        root.addView(mesh, new FrameLayout.LayoutParams(-1, -1));

        wallpaperView = new ImageView(this);
        wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaperView.setAlpha(0.42f);
        String wallpaper = prefs.getString("wallpaper_uri", "");
        if (!wallpaper.isEmpty()) {
            wallpaperView.setImageURI(Uri.parse(wallpaper));
        }
        root.addView(wallpaperView, new FrameLayout.LayoutParams(-1, -1));

        View overlay = new View(this);
        overlay.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(90, 2, 8, 23), Color.argb(195, 2, 6, 18)}
        ));
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(14), dp(16), dp(14), dp(8));
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));

        main.addView(topBar());

        scrollView = new ScrollView(this);
        messageList = new LinearLayout(this);
        messageList.setOrientation(LinearLayout.VERTICAL);
        messageList.setPadding(0, dp(10), 0, dp(12));
        scrollView.addView(messageList, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        main.addView(scrollView, scrollLp);

        main.addView(inputBar());
    }

    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(10), dp(10), dp(10));
        bar.setBackground(round(C_CARD, dp(22)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(0, -2, 1f);
        bar.addView(texts, textsLp);

        title = new TextView(this);
        title.setText(assistantName);
        title.setTextColor(C_TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(title);

        TextView sub = new TextView(this);
        sub.setText("直连公益接口 · 流式回复 · 轻提示词");
        sub.setTextColor(C_TEXT_MUTED);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        texts.addView(sub);

        ImageButton clear = iconButton("↺");
        clear.setOnClickListener(v -> confirmClear());
        bar.addView(clear);

        ImageButton wallpaper = iconButton("▧");
        wallpaper.setOnClickListener(v -> pickWallpaper());
        bar.addView(wallpaper);

        ImageButton settings = iconButton("⚙");
        settings.setOnClickListener(v -> showSettings());
        bar.addView(settings);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        bar.setLayoutParams(lp);
        return bar;
    }

    private View inputBar() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.BOTTOM);
        box.setPadding(dp(12), dp(10), dp(8), dp(10));
        box.setBackground(round(C_INPUT, dp(24)));

        input = new EditText(this);
        input.setHint("输入消息...");
        input.setHintTextColor(Color.parseColor("#64748B"));
        input.setTextColor(C_TEXT);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        input.setMinLines(1);
        input.setMaxLines(5);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0, -2, 1f);
        box.addView(input, inputLp);

        sendButton = iconButton("➤");
        sendButton.setOnClickListener(v -> {
            if (generating) stopGeneration();
            else sendMessage();
        });
        box.addView(sendButton);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, 0);
        box.setLayoutParams(lp);
        return box;
    }

    private ImageButton iconButton(String text) {
        ImageButton b = new ImageButton(this);
        b.setBackground(round(Color.argb(85, 51, 65, 85), dp(18)));
        b.setImageDrawable(null);
        b.setContentDescription(text);
        b.setMinimumWidth(dp(42));
        b.setMinimumHeight(dp(42));
        b.setPadding(0, 0, 0, 0);
        b.setOnLongClickListener(v -> {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
            return true;
        });
        b.setForeground(null);
        b.setTag(text);
        b.post(() -> b.setImageBitmap(TextBitmap.create(text, C_TEXT, dp(22), dp(42), dp(42))));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(42));
        lp.setMargins(dp(6), 0, 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void addWelcomeIfEmpty() {
        if (messages.isEmpty()) {
            Msg welcome = new Msg(false, "你好，我会直接使用公益接口回答。你可以点右上角设置人格、上下文轮数，也可以设置聊天壁纸。");
            messages.add(welcome);
            renderMessages();
        }
    }

    private void sendMessage() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        hideKeyboard();
        input.setText("");

        Msg user = new Msg(true, text);
        messages.add(user);
        currentAssistant = new Msg(false, "");
        messages.add(currentAssistant);
        generating = true;
        updateSendButton();
        renderMessages();

        String prompt = buildPrompt(text);
        api.streamAsync(prompt, new UpstreamClient.StreamCallback() {
            @Override
            public void onDelta(String delta) {
                ui.post(() -> {
                    if (currentAssistant != null) {
                        currentAssistant.text += delta;
                        updateLastAssistantBubble();
                    }
                });
            }

            @Override
            public void onDone() {
                ui.post(() -> {
                    generating = false;
                    if (currentAssistant != null && currentAssistant.text.trim().isEmpty()) {
                        currentAssistant.text = "上游没有返回内容，请稍后再试。";
                    }
                    currentAssistant = null;
                    updateSendButton();
                    renderMessages();
                });
            }

            @Override
            public void onError(Exception error) {
                ui.post(() -> {
                    generating = false;
                    if (currentAssistant != null) {
                        String msg = error.getMessage() == null ? "未知错误" : error.getMessage();
                        currentAssistant.text = "连接失败：" + msg;
                    }
                    currentAssistant = null;
                    updateSendButton();
                    renderMessages();
                });
            }
        });
    }

    private String buildPrompt(String latestUserText) {
        StringBuilder sb = new StringBuilder();
        sb.append(persona.trim()).append("\n\n");
        if (timeEnabled) {
            sb.append("当前时间：")
                    .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss EEEE", Locale.CHINA).format(new Date()))
                    .append("\n\n");
        }
        sb.append("请根据下面的对话自然回复，避免复述规则。\n\n");

        int nonWelcomeCount = Math.max(0, messages.size() - 1);
        int start = Math.max(1, nonWelcomeCount - contextRounds * 2);
        for (int i = start; i < messages.size(); i++) {
            Msg m = messages.get(i);
            if (m == currentAssistant) continue;
            if (m.text == null || m.text.trim().isEmpty()) continue;
            sb.append(m.user ? "用户：" : assistantName + "：")
                    .append(m.text.trim())
                    .append("\n");
        }
        if (messages.isEmpty() || !latestUserText.equals(messages.get(messages.size() - 2).text)) {
            sb.append("用户：").append(latestUserText).append("\n");
        }
        sb.append(assistantName).append("：");
        return sb.toString();
    }

    private void stopGeneration() {
        api.cancel();
        generating = false;
        if (currentAssistant != null && currentAssistant.text.trim().isEmpty()) {
            currentAssistant.text = "已停止生成。";
        }
        currentAssistant = null;
        updateSendButton();
        renderMessages();
    }

    private void updateSendButton() {
        sendButton.post(() -> sendButton.setImageBitmap(TextBitmap.create(generating ? "■" : "➤",
                generating ? C_DANGER : C_TEXT, dp(20), dp(42), dp(42))));
    }

    private void renderMessages() {
        messageList.removeAllViews();
        for (Msg msg : messages) {
            messageList.addView(messageBubble(msg));
        }
        scrollBottom();
    }

    private void updateLastAssistantBubble() {
        renderMessages();
    }

    private View messageBubble(Msg msg) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(msg.user ? Gravity.RIGHT : Gravity.LEFT);
        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(-1, -2);
        wrapLp.setMargins(0, dp(6), 0, dp(6));
        wrap.setLayoutParams(wrapLp);

        TextView label = new TextView(this);
        label.setText(msg.user ? "你" : assistantName);
        label.setTextColor(C_TEXT_MUTED);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        label.setGravity(msg.user ? Gravity.RIGHT : Gravity.LEFT);
        label.setPadding(dp(6), 0, dp(6), dp(3));
        wrap.addView(label, new LinearLayout.LayoutParams(-1, -2));

        TextView bubble = new TextView(this);
        bubble.setText(msg.text == null || msg.text.isEmpty() ? "正在思考..." : msg.text);
        bubble.setTextColor(C_TEXT);
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        bubble.setLineSpacing(dp(2), 1.18f);
        bubble.setTextIsSelectable(true);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setBackground(round(msg.user ? C_USER : C_ASSISTANT, dp(18)));
        bubble.setOnLongClickListener(v -> {
            getSystemService(android.content.ClipboardManager.class)
                    .setPrimaryClip(ClipData.newPlainText("message", bubble.getText()));
            Toast.makeText(this, "已复制消息", Toast.LENGTH_SHORT).show();
            return true;
        });
        LinearLayout.LayoutParams bubbleLp = new LinearLayout.LayoutParams(dp(290), -2);
        bubbleLp.gravity = msg.user ? Gravity.RIGHT : Gravity.LEFT;
        wrap.addView(bubble, bubbleLp);
        return wrap;
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(10), dp(18), 0);

        EditText nameInput = field("助手名称", assistantName, 1);
        EditText personaInput = field("自定义人格 / 系统提示词", persona, 8);
        EditText roundsInput = field("上下文轮数（建议 4-8，太大会变慢）", String.valueOf(contextRounds), 1);
        roundsInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        Button timeBtn = plainButton(timeEnabled ? "时间注入：开启" : "时间注入：关闭");
        final boolean[] timeValue = {timeEnabled};
        timeBtn.setOnClickListener(v -> {
            timeValue[0] = !timeValue[0];
            timeBtn.setText(timeValue[0] ? "时间注入：开启" : "时间注入：关闭");
        });

        box.addView(nameInput);
        box.addView(personaInput);
        box.addView(roundsInput);
        box.addView(timeBtn);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("聊天设置")
                .setView(box)
                .setPositiveButton("保存", (d, which) -> {
                    assistantName = nameInput.getText().toString().trim();
                    if (assistantName.isEmpty()) assistantName = "公益助手";
                    persona = personaInput.getText().toString().trim();
                    if (persona.isEmpty()) persona = "你是一个自然、聪明、简洁的中文聊天助手。";
                    try {
                        contextRounds = Math.max(1, Math.min(12, Integer.parseInt(roundsInput.getText().toString().trim())));
                    } catch (Exception ignored) {
                        contextRounds = 6;
                    }
                    timeEnabled = timeValue[0];
                    title.setText(assistantName);
                    saveSettings();
                    renderMessages();
                })
                .setNegativeButton("取消", null)
                .setNeutralButton("清除壁纸", (d, which) -> {
                    prefs.edit().remove("wallpaper_uri").apply();
                    wallpaperView.setImageDrawable(null);
                })
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getWindow().setBackgroundDrawable(round(Color.parseColor("#111827"), dp(20)));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(C_ACCENT);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(C_TEXT_SOFT);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(C_DANGER);
        });
        dialog.show();
    }

    private EditText field(String hint, String value, int lines) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(C_TEXT);
        e.setHintTextColor(C_TEXT_MUTED);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        e.setMinLines(lines);
        e.setMaxLines(Math.max(lines, 8));
        e.setPadding(dp(12), dp(8), dp(12), dp(8));
        e.setBackground(round(Color.parseColor("#1F2937"), dp(12)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(8));
        e.setLayoutParams(lp);
        return e;
    }

    private Button plainButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(C_TEXT);
        b.setBackground(round(Color.parseColor("#1F2937"), dp(12)));
        return b;
    }

    private void pickWallpaper() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_WALLPAPER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_WALLPAPER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            prefs.edit().putString("wallpaper_uri", uri.toString()).apply();
            wallpaperView.setImageURI(uri);
            Toast.makeText(this, "壁纸已设置", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("清空当前对话？")
                .setMessage("清空后会保留设置和壁纸。")
                .setPositiveButton("清空", (d, w) -> {
                    api.cancel();
                    messages.clear();
                    currentAssistant = null;
                    generating = false;
                    updateSendButton();
                    addWelcomeIfEmpty();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void scrollBottom() {
        scrollView.postDelayed(() -> scrollView.fullScroll(View.FOCUS_DOWN), 40);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class Msg {
        final boolean user;
        String text;

        Msg(boolean user, String text) {
            this.user = user;
            this.text = text;
        }
    }
}
