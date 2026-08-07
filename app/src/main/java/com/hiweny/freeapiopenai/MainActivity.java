package com.hiweny.freeapiopenai;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
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

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int REQ_WALLPAPER = 3001;
    private static final int REQ_USER_AVATAR = 3002;
    private static final int REQ_PERSONA_AVATAR = 3003;
    private static final int REQ_NOTIFICATIONS = 3004;
    private static final int MAX_TEMP_LOGS = 60;
    private static final int MAX_CORE_MEMORIES = 50;

    private static final int C_GREEN = Color.parseColor("#07C160");
    private static final int C_RED = Color.parseColor("#EF4444");
    private static final String SPLIT_RULE = "\n# 气泡分割\n如果回复包含多个短句，可以用单个反斜线 \\ 分隔多个气泡；分隔符只是给程序看的，最终界面不会显示。每个气泡尽量控制在1-2个短句，不要每次都写很长一整段。禁止输出说话人名字、角色名前缀、旁白或规则说明。";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final UpstreamClient api = new UpstreamClient();
    private final List<Persona> personas = new ArrayList<>();

    private SharedPreferences prefs;
    private FrameLayout root;
    private ImageView wallpaperView;
    private LinearLayout messageList;
    private ScrollView scrollView;
    private EditText input;
    private ImageButton sendButton;
    private TextView titleView;
    private TextView subTitleView;
    private FrameLayout sideOverlay;
    private LinearLayout sideContent;
    private TextView currentAssistantTextView;
    private long lastStreamRenderAt = 0L;
    private boolean streamRenderPending = false;
    private StringBuilder currentAssistantBuffer;

    private String activePersonaId = "xiaomei";
    private String userName = "我";
    private String userAvatarUri = "";
    private String wallpaperUri = "";
    private int talkCount = 10;
    private boolean autoMemory = true;
    private int memoryThreshold = 30;
    private boolean timeInject = true;
    private boolean proactiveEnabled = false;
    private int proactiveIntervalMinutes = 30;
    private String themeId = "wechat_dark";
    private ChatTheme theme = ChatTheme.WECHAT_DARK;
    private boolean generating = false;
    private ChatMessage currentAssistant;
    private long lastOrganizedCount = 0;
    private int pendingPickPersonaIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("wechat_native_state", MODE_PRIVATE);
        loadState();
        setupWindow();
        ProactiveMessageReceiver.ensureChannel(this);
        buildUi();
        renderAll();
        ProactiveMessageReceiver.schedule(this);
    }

    private void setupWindow() {
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(theme.headerBg);
            getWindow().setNavigationBarColor(theme.headerBg);
        }
        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(theme.dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private void loadState() {
        userName = prefs.getString("userName", "我");
        userAvatarUri = prefs.getString("userAvatarUri", "");
        wallpaperUri = prefs.getString("wallpaperUri", "");
        activePersonaId = prefs.getString("activePersonaId", "xiaomei");
        talkCount = prefs.getInt("talkCount", 10);
        autoMemory = prefs.getBoolean("autoMemory", true);
        memoryThreshold = prefs.getInt("memoryThreshold", 30);
        timeInject = prefs.getBoolean("timeInject", true);
        proactiveEnabled = prefs.getBoolean("proactiveEnabled", false);
        proactiveIntervalMinutes = prefs.getInt("proactiveIntervalMinutes", 30);
        themeId = prefs.getString("themeId", "wechat_dark");
        theme = ChatTheme.byId(themeId);
        lastOrganizedCount = prefs.getLong("lastOrganizedCount", 0);

        personas.clear();
        String raw = prefs.getString("personasJson", "");
        if (!raw.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    personas.add(Persona.fromJson(arr.optJSONObject(i)));
                }
            } catch (Exception ignored) {
            }
        }
        if (personas.isEmpty()) {
            personas.add(Persona.defaults("xiaomei", "小美",
                    "# 任务\n你只扮演聊天对象「小美」，根据角色经历与关系，模拟微信里的日常聊天。\n# 角色\n你是19岁的女生，大一，文学院学生，刚与对方开始交往。\n# 性格\n热情多话，调皮活泼，爱开玩笑，也很体贴。\n# 对话边界\n用户说的话只作为上下文，你不能替用户说话，也不能编造用户的新发言。\n# 回复风格\n回复尽量短，像真实微信聊天，直接给出小美要发送的内容。" + SPLIT_RULE));
            personas.add(Persona.defaults("xiaoshuai", "小帅",
                    "# 任务\n你只扮演聊天对象「小帅」，根据角色经历与关系，模拟微信里的日常聊天。\n# 角色\n你是23岁的男生，大三，计算机学院学生。\n# 性格\n温和沉稳，话不多但很贴心，会照顾对方情绪。\n# 对话边界\n用户说的话只作为上下文，你不能替用户说话，也不能编造用户的新发言。\n# 回复风格\n回复尽量短，像真实微信聊天，直接给出小帅要发送的内容。" + SPLIT_RULE));
            activePersonaId = "xiaomei";
            saveState();
        }
        for (Persona p : personas) {
            p.prompt = ensureSplitRule(p.prompt);
        }
        if (findActivePersona() == null && !personas.isEmpty()) {
            activePersonaId = personas.get(0).id;
        }
    }

    private void saveState() {
        try {
            JSONArray arr = new JSONArray();
            for (Persona p : personas) arr.put(p.toJson());
            prefs.edit()
                    .putString("userName", userName)
                    .putString("userAvatarUri", userAvatarUri)
                    .putString("wallpaperUri", wallpaperUri)
                    .putString("activePersonaId", activePersonaId)
                    .putInt("talkCount", talkCount)
                    .putBoolean("autoMemory", autoMemory)
                    .putInt("memoryThreshold", memoryThreshold)
                    .putBoolean("timeInject", timeInject)
                    .putBoolean("proactiveEnabled", proactiveEnabled)
                    .putInt("proactiveIntervalMinutes", proactiveIntervalMinutes)
                    .putString("themeId", themeId)
                    .putLong("lastOrganizedCount", lastOrganizedCount)
                    .putString("personasJson", arr.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private Persona findActivePersona() {
        for (Persona p : personas) {
            if (p.id.equals(activePersonaId)) return p;
        }
        return personas.isEmpty() ? null : personas.get(0);
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(theme.chatBg);
        setContentView(root);

        MeshGradientView mesh = new MeshGradientView(this);
        mesh.setAlpha(theme.dark ? 0.18f : 0.06f);
        root.addView(mesh, new FrameLayout.LayoutParams(-1, -1));

        wallpaperView = new ImageView(this);
        wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaperView.setAlpha(theme.dark ? 0.24f : 0.32f);
        if (!wallpaperUri.isEmpty()) wallpaperView.setImageURI(Uri.parse(wallpaperUri));
        root.addView(wallpaperView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setBackgroundColor(theme.overlay);
        main.setFitsSystemWindows(true);
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(-1, -2);
        headerLp.setMargins(dp(10), dp(8), dp(10), 0);
        main.addView(headerView(), headerLp);

        scrollView = new ScrollView(this);
        messageList = new LinearLayout(this);
        messageList.setOrientation(LinearLayout.VERTICAL);
        messageList.setPadding(dp(10), dp(12), dp(10), dp(16));
        scrollView.addView(messageList, new ScrollView.LayoutParams(-1, -2));
        main.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, -2);
        inputLp.setMargins(dp(8), 0, dp(8), dp(8));
        main.addView(inputBar(), inputLp);

        buildSidePanel();
    }

    private View headerView() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(5), dp(8), dp(5));
        header.setBackground(roundStroke(theme.headerBg, dp(15), dp(1), subtleBorder()));

        ImageButton list = iconButton("☰", theme.textPrimary);
        list.setOnClickListener(v -> showSidebar());
        header.addView(list);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setGravity(Gravity.CENTER);
        titleBox.setOnClickListener(v -> {
            Persona p = findActivePersona();
            if (p != null) showSidebar();
        });
        header.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1f));

        titleView = new TextView(this);
        titleView.setTextColor(theme.textPrimary);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleBox.addView(titleView);

        subTitleView = new TextView(this);
        subTitleView.setTextColor(theme.textSecondary);
        subTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        subTitleView.setGravity(Gravity.CENTER);
        titleBox.addView(subTitleView);

        ImageButton settings = iconButton("⋯", theme.textPrimary);
        settings.setOnClickListener(v -> showSidebar());
        header.addView(settings);

        return header;
    }

    private View inputBar() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.BOTTOM);
        wrap.setPadding(dp(9), dp(9), dp(9), dp(9));
        wrap.setBackground(roundStroke(theme.headerBg, dp(20), dp(1), subtleBorder()));

        input = new EditText(this);
        input.setMinLines(1);
        input.setMaxLines(5);
        input.setHint("发消息");
        input.setHintTextColor(theme.textMuted);
        input.setTextColor(theme.textPrimary);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        input.setPadding(dp(13), dp(8), dp(13), dp(8));
        input.setBackground(roundStroke(theme.inputBg, dp(theme.inputRadius), dp(1), theme.inputBorder));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setSingleLine(false);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean enterSend = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_UP
                    && !event.isShiftPressed();
            if (actionId == EditorInfo.IME_ACTION_SEND || enterSend) {
                if (generating) stopGeneration();
                else sendMessage();
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0, -2, 1f);
        inputLp.setMargins(dp(8), 0, dp(8), 0);
        wrap.addView(input, inputLp);

        sendButton = iconButton("➤", theme.sendButtonText);
        sendButton.setBackground(round(theme.sendButton, dp(21)));
        sendButton.setOnClickListener(v -> {
            if (generating) stopGeneration();
            else sendMessage();
        });
        wrap.addView(sendButton);
        return wrap;
    }

    private void buildSidePanel() {
        sideOverlay = new FrameLayout(this);
        sideOverlay.setVisibility(View.GONE);
        sideOverlay.setBackgroundColor(Color.argb(theme.dark ? 155 : 105, 0, 0, 0));
        sideOverlay.setOnClickListener(v -> hideSidebar());

        ScrollView drawer = new ScrollView(this);
        drawer.setFillViewport(false);
        drawer.setBackground(roundStroke(theme.headerBg, dp(22), dp(1), subtleBorder()));
        drawer.setOnClickListener(v -> {});

        sideContent = new LinearLayout(this);
        sideContent.setOrientation(LinearLayout.VERTICAL);
        sideContent.setPadding(dp(16), dp(18), dp(16), dp(28));
        sideContent.setOnClickListener(v -> {});
        drawer.addView(sideContent, new ScrollView.LayoutParams(-1, -2));

        int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.86f);
        FrameLayout.LayoutParams drawerLp = new FrameLayout.LayoutParams(width, -1, Gravity.LEFT);
        drawerLp.setMargins(dp(8), dp(8), dp(20), dp(8));
        sideOverlay.addView(drawer, drawerLp);
        root.addView(sideOverlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private void showSidebar() {
        if (sideOverlay == null || sideContent == null) return;
        Persona active = findActivePersona();
        sideContent.removeAllViews();
        addSidebarHeader(active);
        addPersonaSection();
        if (active != null) {
            addPersonaEditorSection(active);
            addMemorySection(active);
        }
        addSettingsSection();
        sideOverlay.setVisibility(View.VISIBLE);
    }

    private void hideSidebar() {
        if (sideOverlay != null) sideOverlay.setVisibility(View.GONE);
    }

    private void addSidebarHeader(Persona p) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(14));
        if (p != null) {
            row.addView(avatarView(p.name, p.avatarUri));
            TextView name = new TextView(this);
            name.setText(p.name + "\n" + p.messages.size() + " 条消息 · " + p.memories.size() + " 条记忆");
            name.setTextColor(theme.textPrimary);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setPadding(dp(12), 0, 0, 0);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        Button close = miniButton("关闭");
        close.setOnClickListener(v -> hideSidebar());
        row.addView(close, new LinearLayout.LayoutParams(dp(64), dp(36)));
        sideContent.addView(row);
    }

    private void addPersonaSection() {
        sideContent.addView(sectionTitle("聊天对象"));
        List<Persona> sorted = new ArrayList<>(personas);
        Collections.sort(sorted, (a, b) -> {
            if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
            return Long.compare(b.lastMessageTime, a.lastMessageTime);
        });
        for (Persona p : sorted) {
            Button b = listButton((p.id.equals(activePersonaId) ? "● " : "") + (p.pinned ? "★ " : "") + p.name + "  ·  " + p.messages.size() + "条");
            b.setOnClickListener(v -> {
                activePersonaId = p.id;
                saveState();
                renderAll();
                showSidebar();
            });
            sideContent.addView(b);
        }
        Button add = listButton("＋ 新建人设");
        add.setOnClickListener(v -> {
            Persona p = Persona.defaults(UUID.randomUUID().toString(), "新朋友",
                    "# 任务\n你只扮演这个聊天对象本人，像微信聊天一样自然回复。\n# 对话边界\n用户说的话只作为上下文，你不能替用户说话，也不能输出任何说话人前缀。\n# 回复风格\n回复简短，直接输出这个聊天对象要发送的内容。" + SPLIT_RULE);
            personas.add(p);
            activePersonaId = p.id;
            saveState();
            renderAll();
            showSidebar();
        });
        sideContent.addView(add);
    }

    private void addPersonaEditorSection(Persona p) {
        sideContent.addView(sectionTitle("人设编辑"));
        pendingPickPersonaIndex = personas.indexOf(p);
        EditText name = field("名称", p.name, 1);
        EditText prompt = field("人格设定", p.prompt, 6);
        EditText hidden = field("隐藏记忆（不会显示在聊天里）", p.hiddenMemory, 4);
        Button avatar = listButton(TextUtils.isEmpty(p.avatarUri) ? "设置 AI 头像" : "更换 AI 头像");
        avatar.setOnClickListener(v -> {
            pendingPickPersonaIndex = personas.indexOf(p);
            pickImage(REQ_PERSONA_AVATAR);
        });
        Button pin = listButton(p.pinned ? "取消置顶" : "置顶这个人设");
        pin.setOnClickListener(v -> {
            p.pinned = !p.pinned;
            saveState();
            showSidebar();
        });
        Button save = listButton("保存人设");
        save.setOnClickListener(v -> {
            p.name = safe(name.getText().toString(), "新朋友");
            p.prompt = ensureSplitRule(safe(prompt.getText().toString(), p.prompt));
            p.hiddenMemory = hidden.getText().toString().trim();
            saveState();
            renderAll();
            showSidebar();
            Toast.makeText(this, "人设已保存", Toast.LENGTH_SHORT).show();
        });
        Button proactiveNow = listButton("让 TA 现在主动发一条");
        proactiveNow.setOnClickListener(v -> {
            hideSidebar();
            triggerManualProactive(p);
        });
        sideContent.addView(name);
        sideContent.addView(prompt);
        sideContent.addView(hidden);
        sideContent.addView(avatar);
        sideContent.addView(pin);
        sideContent.addView(save);
        sideContent.addView(proactiveNow);
        if (personas.size() > 1) {
            Button delete = listButton("删除这个人设");
            delete.setTextColor(C_RED);
            delete.setOnClickListener(v -> {
                personas.remove(p);
                activePersonaId = personas.get(0).id;
                saveState();
                renderAll();
                showSidebar();
            });
            sideContent.addView(delete);
        }
    }

    private void addMemorySection(Persona p) {
        sideContent.addView(sectionTitle("记忆"));
        TextView info = sidebarText("长期记忆：" + p.memories.size() + " 条\n临时记录：" + p.tempLogs.size() + " 条");
        sideContent.addView(info);
        int count = 0;
        for (CoreMemory m : topMemories(p)) {
            sideContent.addView(sidebarText("• " + m.content));
            if (++count >= 5) break;
        }
        Button organize = listButton("立即整理记忆");
        organize.setOnClickListener(v -> organizeMemory(p, true));
        Button clear = listButton("清空长期记忆");
        clear.setOnClickListener(v -> {
            p.memories.clear();
            saveState();
            renderAll();
            showSidebar();
        });
        sideContent.addView(organize);
        sideContent.addView(clear);
    }

    private void addSettingsSection() {
        sideContent.addView(sectionTitle("设置"));
        EditText name = field("我的昵称", userName, 1);
        EditText context = field("上下文轮数", String.valueOf(talkCount), 1);
        context.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText threshold = field("自动整理阈值（消息条数）", String.valueOf(memoryThreshold), 1);
        threshold.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText proactiveInterval = field("主动消息心跳间隔（分钟，建议 15-60）", String.valueOf(proactiveIntervalMinutes), 1);
        proactiveInterval.setInputType(InputType.TYPE_CLASS_NUMBER);
        Button auto = listButton(autoMemory ? "自动整理：开" : "自动整理：关");
        final boolean[] autoValue = {autoMemory};
        auto.setOnClickListener(v -> { autoValue[0] = !autoValue[0]; auto.setText(autoValue[0] ? "自动整理：开" : "自动整理：关"); });
        Button time = listButton(timeInject ? "时间注入：开" : "时间注入：关");
        final boolean[] timeValue = {timeInject};
        time.setOnClickListener(v -> { timeValue[0] = !timeValue[0]; time.setText(timeValue[0] ? "时间注入：开" : "时间注入：关"); });
        Button proactive = listButton(proactiveEnabled ? "主动消息：开" : "主动消息：关");
        final boolean[] proactiveValue = {proactiveEnabled};
        proactive.setOnClickListener(v -> {
            proactiveValue[0] = !proactiveValue[0];
            proactive.setText(proactiveValue[0] ? "主动消息：开" : "主动消息：关");
        });
        Button themeBtn = listButton("聊天主题：" + theme.name);
        themeBtn.setOnClickListener(v -> showThemePicker());
        Button notify = listButton("申请通知权限 / 后台保活");
        notify.setOnClickListener(v -> {
            requestNotificationPermission();
            requestBatteryOptimizationIgnore();
            ProactiveMessageReceiver.ensureChannel(MainActivity.this);
        });
        Button testNotify = listButton("立即测试主动消息通知");
        testNotify.setOnClickListener(v -> {
            Persona p = findActivePersona();
            requestNotificationPermission();
            ProactiveMessageReceiver.ensureChannel(this);
            if (!hasNotificationPermission()) {
                Toast.makeText(this, "请先允许通知权限，再点一次测试", Toast.LENGTH_LONG).show();
                return;
            }
            if (p != null) ProactiveMessageReceiver.notifyNow(MainActivity.this, p, "这是一条主动消息通知测试。看到这条弹窗，就说明主动消息通知通道正常。");
            Toast.makeText(this, "测试通知已发送", Toast.LENGTH_SHORT).show();
        });
        Button myAvatar = listButton("设置我的头像");
        myAvatar.setOnClickListener(v -> pickImage(REQ_USER_AVATAR));
        Button wall = listButton("设置聊天背景");
        wall.setOnClickListener(v -> pickImage(REQ_WALLPAPER));
        Button save = listButton("保存设置");
        save.setOnClickListener(v -> {
            userName = safe(name.getText().toString(), "我");
            talkCount = clamp(parseInt(context.getText().toString(), 10), 1, 30);
            memoryThreshold = clamp(parseInt(threshold.getText().toString(), 30), 8, 200);
            proactiveIntervalMinutes = clamp(parseInt(proactiveInterval.getText().toString(), 30), 1, 180);
            autoMemory = autoValue[0];
            timeInject = timeValue[0];
            proactiveEnabled = proactiveValue[0];
            saveState();
            if (proactiveEnabled) {
                requestNotificationPermission();
                ProactiveMessageReceiver.schedule(this);
            } else {
                ProactiveMessageReceiver.cancel(this);
            }
            renderAll();
            showSidebar();
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        });
        sideContent.addView(name);
        sideContent.addView(sidebarText("我的昵称：用于写入 Prompt，让 AI 明确“用户是谁”，避免把双方说话搞混。"));
        sideContent.addView(context);
        sideContent.addView(sidebarText("上下文轮数：每次回复最多带入最近多少轮对话。越大越懂上下文，但回复会更慢。建议 8-12。"));
        sideContent.addView(threshold);
        sideContent.addView(sidebarText("自动整理阈值：累计多少条临时聊天后整理为长期记忆。越小越频繁，建议 30。"));
        sideContent.addView(proactiveInterval);
        sideContent.addView(sidebarText("主动消息心跳：开启后按这个间隔检查一次。到了时间后，应用会在后台挑一个最久没聊的人设，让 TA 生成一条短消息，写入聊天记录，并通过系统通知弹窗提醒你。安卓会受通知权限、电池优化和厂商后台限制影响；点“申请通知权限 / 后台保活”后，再点“立即测试主动消息通知”可以马上确认弹窗是否正常。"));
        sideContent.addView(auto);
        sideContent.addView(time);
        sideContent.addView(proactive);
        sideContent.addView(themeBtn);
        sideContent.addView(notify);
        sideContent.addView(testNotify);
        sideContent.addView(myAvatar);
        sideContent.addView(wall);
        sideContent.addView(save);
    }

    private void renderAll() {
        Persona p = findActivePersona();
        if (p == null) return;
        titleView.setText(p.name);
        subTitleView.setText(proactiveEnabled ? "主动消息已开启" : "轻触打开资料侧栏");
        renderMessages(true);
    }

    private void renderMessages(boolean allowAutoScroll) {
        Persona p = findActivePersona();
        if (p == null) return;
        boolean shouldScroll = allowAutoScroll && isNearBottom();
        currentAssistantTextView = null;
        messageList.removeAllViews();
        if (p.messages.isEmpty()) {
            ChatMessage tip = new ChatMessage(false, "你们现在可以开始聊天了");
            tip.tickle = true;
            messageList.addView(systemBubble(tip));
        } else {
            for (ChatMessage m : p.messages) {
                if (m.memoryDivider || m.tickle || m.recalled) messageList.addView(systemBubble(m));
                else messageList.addView(chatBubble(p, m));
            }
        }
        if (shouldScroll) scrollBottom();
    }

    private View chatBubble(Persona persona, ChatMessage msg) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(msg.user ? Gravity.RIGHT : Gravity.LEFT);
        row.setPadding(0, dp(5), 0, dp(5));

        View avatar = avatarView(msg.user ? userName : persona.name, msg.user ? userAvatarUri : persona.avatarUri);
        TextView bubble = new TextView(this);
        bubble.setText(msg == currentAssistant ? MessageParser.preview(msg.text) : msg.text);
        bubble.setTextColor(msg.user ? theme.bubbleUserText : theme.bubbleAiText);
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        bubble.setLineSpacing(dp(2), 1.18f);
        bubble.setPadding(dp(12), dp(9), dp(12), dp(9));
        bubble.setTextIsSelectable(true);
        bubble.setBackground(roundStroke(msg.user ? theme.bubbleUser : theme.bubbleAi, dp(theme.bubbleRadius), dp(1), msg.user ? transparentBorder() : subtleBorder()));
        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.70f));
        bubble.setMinWidth(0);
        bubble.setOnLongClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("message", bubble.getText()));
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
            return true;
        });
        if (msg == currentAssistant) currentAssistantTextView = bubble;
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(-2, -2);
        bLp.setMargins(dp(8), 0, dp(8), 0);

        if (msg.user) {
            row.addView(bubble, bLp);
            row.addView(avatar);
        } else {
            row.addView(avatar);
            row.addView(bubble, bLp);
        }
        return row;
    }

    private View systemBubble(ChatMessage msg) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setGravity(Gravity.CENTER);
        wrap.setPadding(0, dp(8), 0, dp(8));
        TextView tv = new TextView(this);
        if (msg.recalled) tv.setText("对方撤回了一条消息");
        else tv.setText(msg.text);
        tv.setTextColor(theme.dark ? Color.rgb(230, 230, 230) : Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setPadding(dp(10), dp(4), dp(10), dp(4));
        tv.setBackground(round(Color.argb(100, 80, 80, 80), dp(10)));
        wrap.addView(tv);
        return wrap;
    }

    private View avatarView(String name, String uri) {
        FrameLayout box = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
        box.setLayoutParams(lp);
        boolean isUserAvatar = name != null && name.equals(userName);
        box.setBackground(round(uri != null && !uri.isEmpty() ? Color.TRANSPARENT : (isUserAvatar ? theme.avatarUser : theme.avatarAi), dp(theme.avatarRadius)));
        if (Build.VERSION.SDK_INT >= 21) box.setClipToOutline(true);
        if (uri != null && !uri.isEmpty()) {
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageURI(Uri.parse(uri));
            img.setBackground(round(Color.TRANSPARENT, dp(theme.avatarRadius)));
            if (Build.VERSION.SDK_INT >= 21) img.setClipToOutline(true);
            box.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            TextView tv = new TextView(this);
            tv.setText(name == null || name.isEmpty() ? "AI" : name.substring(0, 1));
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setGravity(Gravity.CENTER);
            tv.setBackground(round(isUserAvatar ? theme.avatarUser : theme.avatarAi, dp(theme.avatarRadius)));
            box.addView(tv, new FrameLayout.LayoutParams(-1, -1));
        }
        box.setOnLongClickListener(v -> {
            Persona p = findActivePersona();
            if (p != null && !isUserAvatar) showPersonaProfile(p);
            return !isUserAvatar;
        });
        return box;
    }

    private void sendMessage() {
        Persona p = findActivePersona();
        if (p == null) return;
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        input.setText("");

        ChatMessage user = new ChatMessage(true, text);
        p.messages.add(user);
        addTempLog(p, "user", text);
        p.lastUserMessageTime = System.currentTimeMillis();

        currentAssistant = new ChatMessage(false, "正在连接...");
        currentAssistant.loading = true;
        currentAssistantBuffer = new StringBuilder();
        p.messages.add(currentAssistant);
        p.lastMessageTime = System.currentTimeMillis();
        generating = true;
        updateSendButton();
        renderOutgoingMessages(user, currentAssistant);

        new Thread(() -> {
            String prompt = buildPrompt(p, text);
            api.streamAsync(prompt, new UpstreamClient.StreamCallback() {
                @Override
                public void onDelta(String delta) {
                    ui.post(() -> {
                        if (currentAssistant != null) {
                            appendAssistantDelta(delta);
                            scheduleStreamRender();
                        }
                    });
                }

                @Override
                public void onDone() {
                    ui.post(() -> finishAssistantResponse(p));
                }

                @Override
                public void onError(Exception error) {
                    ui.post(() -> {
                        if (currentAssistant != null) {
                            currentAssistant.loading = false;
                            currentAssistant.error = true;
                            currentAssistant.text = "消息发送失败，稍后再试";
                        }
                        currentAssistant = null;
                        currentAssistantBuffer = null;
                        generating = false;
                        updateSendButton();
                        saveState();
                        renderAll();
                    });
                }
            });
        }, "prompt-build-start").start();
    }

    private void renderOutgoingMessages(ChatMessage user, ChatMessage assistant) {
        if (messageList == null) {
            renderAll();
            return;
        }
        if (messageList.getChildCount() == 1 && findActivePersona() != null && findActivePersona().messages.size() <= 2) {
            messageList.removeAllViews();
        }
        messageList.addView(chatBubble(findActivePersona(), user));
        messageList.addView(chatBubble(findActivePersona(), assistant));
        scrollBottom();
    }

    private String buildPrompt(Persona p, String currentUserText) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 身份边界\n")
                .append("你现在扮演「").append(p.name).append("」。\n")
                .append("用户名字是「").append(userName).append("」。\n")
                .append("你只能输出「").append(p.name).append("」要发给用户的消息内容，绝对不要代替用户说话，绝对不要输出“")
                .append(userName).append("：”或“").append(p.name).append("：”这种说话人前缀。\n")
                .append("用户发言只代表用户已经说过的话，不是让你续写用户台词。不要解释规则，不要写旁白，不要写内心分析。\n\n")
                .append(ensureSplitRule(p.prompt)).append("\n\n");
        if (timeInject) {
            sb.append("当前时间：")
                    .append(new SimpleDateFormat("yyyy年M月d日 HH:mm EEEE", Locale.CHINA).format(new Date()))
                    .append("\n");
        }
        if (p.hiddenMemory != null && !p.hiddenMemory.trim().isEmpty()) {
            sb.append("\n以下内容是长期背景，只用于保持一致，不要主动说出：\n")
                    .append(p.hiddenMemory.trim()).append("\n");
        }
        List<CoreMemory> top = topMemories(p);
        if (!top.isEmpty()) {
            sb.append("\n以下是长期记忆，只用于理解关系和偏好，不要主动暴露：\n");
            for (CoreMemory m : top) {
                sb.append("## 记忆片段 [")
                        .append(formatDate(m.createdAt, "yyyy-MM-dd HH:mm"))
                        .append("]\n重要度: ")
                        .append(m.importance)
                        .append("\n摘要: ")
                        .append(m.content)
                        .append("\n");
            }
        }
        sb.append("\n# 最近聊天记录\n");
        List<ChatMessage> history = selectContext(p);
        ChatMessage currentUserMessage = p.messages.size() >= 2 ? p.messages.get(p.messages.size() - 2) : null;
        for (ChatMessage m : history) {
            if (m == currentUserMessage) continue;
            sb.append(m.user ? userName : p.name).append("：").append(m.text).append("\n");
        }
        sb.append("\n# 当前用户刚刚发送\n")
                .append(userName).append("：").append(currentUserText).append("\n\n")
                .append("# 输出要求\n")
                .append("只回复").append(p.name).append("接下来要发的一条或多条消息。")
                .append("如果要拆成多个气泡，用单个反斜线 \\ 分隔，但分隔符不要当成正文。不要输出任何说话人名字，不要续写用户的话。\n")
                .append("直接从消息正文开始输出：");
        return sb.toString();
    }

    private List<ChatMessage> selectContext(Persona p) {
        List<ChatMessage> valid = new ArrayList<>();
        for (ChatMessage m : p.messages) {
            if (m == currentAssistant || m.loading || m.error || m.tickle || m.memoryDivider || m.recalled) continue;
            if (m.text == null || m.text.trim().isEmpty()) continue;
            valid.add(m);
        }
        int max = Math.max(2, talkCount * 2);
        int start = Math.max(0, valid.size() - max);
        return valid.subList(start, valid.size());
    }

    private void finishAssistantResponse(Persona p) {
        if (currentAssistant == null) return;
        if (currentAssistantBuffer != null) currentAssistant.text = currentAssistantBuffer.toString();
        String raw = currentAssistant.text == null ? "" : currentAssistant.text.trim();
        if ("正在连接...".equals(raw)) raw = "";
        MessageParser.Parsed parsed = MessageParser.parse(raw);
        currentAssistant.loading = false;

        if (parsed.recall) recallPreviousAi(p);
        if (parsed.parts.isEmpty()) {
            currentAssistant.text = "我刚刚走神了，再发一次试试";
        } else {
            currentAssistant.text = parsed.parts.get(0);
            if (parsed.parts.size() > 1) {
                for (int i = 1; i < parsed.parts.size(); i++) {
                    final String part = parsed.parts.get(i);
                    ui.postDelayed(() -> {
                        p.messages.add(new ChatMessage(false, part));
                        saveState();
                        renderAll();
                    }, 350L * i + (long) (Math.random() * 250));
                }
            }
            addTempLog(p, "ai", joinParts(parsed.parts));
        }
        currentAssistant = null;
        currentAssistantBuffer = null;
        generating = false;
        updateSendButton();
        saveState();
        renderAll();
        maybeAutoOrganize(p);
        ProactiveMessageReceiver.schedule(this);
    }

    private void stopGeneration() {
        api.cancel();
        if (currentAssistant != null) {
            currentAssistant.loading = false;
            if (currentAssistantBuffer != null) currentAssistant.text = currentAssistantBuffer.toString();
            if (currentAssistant.text.trim().isEmpty()) currentAssistant.text = "已停止";
        }
        currentAssistant = null;
        currentAssistantBuffer = null;
        generating = false;
        updateSendButton();
        saveState();
        renderAll();
    }

    private void addTempLog(Persona p, String role, String content) {
        p.tempLogs.add(new TempLog(role, content));
        while (p.tempLogs.size() > MAX_TEMP_LOGS) p.tempLogs.remove(0);
    }

    private void maybeAutoOrganize(Persona p) {
        if (!autoMemory || p.messages.size() < 5) return;
        if (p.messages.size() / memoryThreshold > lastOrganizedCount / memoryThreshold) {
            lastOrganizedCount = p.messages.size();
            organizeMemory(p, false);
        }
    }

    private void organizeMemory(Persona p, boolean manual) {
        if (p.tempLogs.size() < 5) {
            if (manual) Toast.makeText(this, "消息太少，暂时不用整理", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "正在整理记忆...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String dialogue = buildTempDialogue(p);
                String date = new SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINA).format(new Date());
                String summaryPrompt = "当前日期：" + date + "\n请以" + p.name + "的视角，用中文总结以下对话，提取重要信息总结为一段话作为记忆片段。必须使用具体日期，禁止使用今天、昨天等相对时间。直接回复一段话：\n" + dialogue;
                String summary = api.complete(summaryPrompt).replace("\n", " ").trim();
                if (summary.length() > 260) summary = summary.substring(0, 260);

                int importance = 3;
                try {
                    String score = api.complete("为以下记忆的重要性评分（1-5，直接回复数字）：\n" + summary);
                    for (char c : score.toCharArray()) {
                        if (c >= '1' && c <= '5') {
                            importance = c - '0';
                            break;
                        }
                    }
                } catch (Exception ignored) {
                }
                String category = "other";
                try {
                    String cat = api.complete("将以下记忆分类，直接回复一个分类名：user_info、preference、event、other。\n记忆内容：" + summary).trim();
                    if (cat.contains("user_info")) category = "user_info";
                    else if (cat.contains("preference")) category = "preference";
                    else if (cat.contains("event")) category = "event";
                } catch (Exception ignored) {
                }

                CoreMemory memory = new CoreMemory();
                memory.content = summary;
                memory.importance = importance;
                memory.category = category;

                ui.post(() -> {
                    p.memories.add(memory);
                    while (p.memories.size() > MAX_CORE_MEMORIES) p.memories.remove(0);
                    p.tempLogs.clear();
                    ChatMessage divider = new ChatMessage(false, "聊天记录已整理");
                    divider.memoryDivider = true;
                    p.messages.add(divider);
                    saveState();
                    renderAll();
                    Toast.makeText(this, "记忆已更新", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                ui.post(() -> Toast.makeText(this, "整理失败，稍后再试", Toast.LENGTH_SHORT).show());
            }
        }, "memory-organize").start();
    }

    private String buildTempDialogue(Persona p) {
        StringBuilder sb = new StringBuilder();
        for (TempLog l : p.tempLogs) {
            sb.append(formatDate(l.time, "yyyy-MM-dd HH:mm"))
                    .append(" | [")
                    .append("user".equals(l.role) ? userName : p.name)
                    .append("] ")
                    .append(l.content)
                    .append("\n");
        }
        return sb.toString();
    }

    private List<CoreMemory> topMemories(Persona p) {
        List<CoreMemory> list = new ArrayList<>(p.memories);
        Collections.sort(list, (a, b) -> Double.compare(memoryScore(b), memoryScore(a)));
        if (list.size() > 12) return list.subList(0, 12);
        return list;
    }

    private double memoryScore(CoreMemory m) {
        double hours = (System.currentTimeMillis() - m.createdAt) / 3600000.0;
        return 0.6 * m.importance - 0.02 * hours;
    }

    private void recallPreviousAi(Persona p) {
        for (int i = p.messages.size() - 2; i >= 0; i--) {
            ChatMessage m = p.messages.get(i);
            if (!m.user && !m.tickle && !m.memoryDivider && !m.recalled) {
                m.recalled = true;
                m.text = "消息已撤回";
                return;
            }
        }
    }

    private void showPersonaList() {
        if (sideOverlay != null) {
            showSidebar();
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), dp(8));

        TextView hint = new TextView(this);
        hint.setText("点按切换，长按编辑；也可以点顶部 ✎ 编辑当前人设。");
        hint.setTextColor(theme.textSecondary);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setPadding(dp(2), 0, dp(2), dp(8));
        box.addView(hint);

        List<Persona> sorted = new ArrayList<>(personas);
        Collections.sort(sorted, (a, b) -> {
            if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
            return Long.compare(b.lastMessageTime, a.lastMessageTime);
        });
        for (Persona p : sorted) {
            Button b = listButton((p.pinned ? "★ " : "") + p.name + "  ·  " + p.messages.size() + "条");
            b.setOnClickListener(v -> {
                activePersonaId = p.id;
                saveState();
                renderAll();
            });
            b.setOnLongClickListener(v -> {
                showPersonaEditor(p);
                return true;
            });
            box.addView(b);
        }
        Button add = listButton("＋ 新建人设");
        add.setOnClickListener(v -> {
            Persona p = Persona.defaults(UUID.randomUUID().toString(), "新朋友",
                    "# 任务\n你需要扮演一个真实聊天对象，像微信聊天一样自然回复。\n# 规则\n回复简短，必要时用反斜线 \\ 分隔多条气泡。" + SPLIT_RULE);
            personas.add(p);
            activePersonaId = p.id;
            saveState();
            showPersonaEditor(p);
        });
        box.addView(add);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("聊天对象")
                .setView(box)
                .setPositiveButton("关闭", null)
                .create();
        dlg.setOnDismissListener(d -> renderAll());
        dlg.show();
    }

    private void showPersonaEditor(Persona p) {
        if (sideOverlay != null) {
            if (p != null) activePersonaId = p.id;
            pendingPickPersonaIndex = personas.indexOf(p);
            saveState();
            renderAll();
            showSidebar();
            return;
        }
        int index = personas.indexOf(p);
        pendingPickPersonaIndex = index;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(8), dp(14), 0);
        EditText name = field("名称", p.name, 1);
        EditText prompt = field("人格设定", p.prompt, 7);
        EditText hidden = field("隐藏记忆（不会显示在聊天里）", p.hiddenMemory, 5);
        Button avatar = listButton(TextUtils.isEmpty(p.avatarUri) ? "设置 AI 头像" : "更换 AI 头像");
        avatar.setOnClickListener(v -> pickImage(REQ_PERSONA_AVATAR));
        Button pin = listButton(p.pinned ? "取消置顶" : "置顶");
        pin.setOnClickListener(v -> {
            p.pinned = !p.pinned;
            saveState();
            Toast.makeText(this, p.pinned ? "已置顶" : "已取消置顶", Toast.LENGTH_SHORT).show();
        });
        box.addView(name);
        box.addView(prompt);
        box.addView(hidden);
        box.addView(avatar);
        box.addView(pin);
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("编辑人设")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    p.name = safe(name.getText().toString(), "新朋友");
                    p.prompt = ensureSplitRule(safe(prompt.getText().toString(), p.prompt));
                    p.hiddenMemory = hidden.getText().toString().trim();
                    saveState();
                    renderAll();
                })
                .setNegativeButton("删除", (d, w) -> {
                    if (personas.size() <= 1) return;
                    personas.remove(p);
                    activePersonaId = personas.get(0).id;
                    saveState();
                    renderAll();
                })
                .setNeutralButton("取消", null)
                .create();
        dlg.show();
    }

    private void showSettings() {
        if (sideOverlay != null) {
            showSidebar();
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(8), dp(14), 0);
        EditText name = field("我的昵称", userName, 1);
        EditText context = field("上下文轮数", String.valueOf(talkCount), 1);
        context.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText threshold = field("自动整理阈值（消息条数）", String.valueOf(memoryThreshold), 1);
        threshold.setInputType(InputType.TYPE_CLASS_NUMBER);
        Button auto = listButton(autoMemory ? "自动整理：开" : "自动整理：关");
        final boolean[] autoValue = {autoMemory};
        auto.setOnClickListener(v -> { autoValue[0] = !autoValue[0]; auto.setText(autoValue[0] ? "自动整理：开" : "自动整理：关"); });
        Button time = listButton(timeInject ? "时间注入：开" : "时间注入：关");
        final boolean[] timeValue = {timeInject};
        time.setOnClickListener(v -> { timeValue[0] = !timeValue[0]; time.setText(timeValue[0] ? "时间注入：开" : "时间注入：关"); });
        Button proactive = listButton(proactiveEnabled ? "主动消息：开" : "主动消息：关");
        final boolean[] proactiveValue = {proactiveEnabled};
        proactive.setOnClickListener(v -> {
            proactiveValue[0] = !proactiveValue[0];
            proactive.setText(proactiveValue[0] ? "主动消息：开" : "主动消息：关");
        });
        EditText proactiveInterval = field("主动消息心跳间隔（分钟，建议 15-60）", String.valueOf(proactiveIntervalMinutes), 1);
        proactiveInterval.setInputType(InputType.TYPE_CLASS_NUMBER);
        Button notify = listButton("申请通知权限 / 后台保活");
        notify.setOnClickListener(v -> {
            requestNotificationPermission();
            requestBatteryOptimizationIgnore();
            ProactiveMessageReceiver.ensureChannel(this);
        });
        Button themeBtn = listButton("聊天主题：" + theme.name);
        themeBtn.setOnClickListener(v -> showThemePicker());
        Button avatar = listButton("设置我的头像");
        avatar.setOnClickListener(v -> pickImage(REQ_USER_AVATAR));
        Button wall = listButton("设置聊天背景");
        wall.setOnClickListener(v -> pickImage(REQ_WALLPAPER));
        box.addView(name);
        box.addView(context);
        box.addView(threshold);
        box.addView(auto);
        box.addView(time);
        box.addView(proactive);
        box.addView(proactiveInterval);
        box.addView(notify);
        box.addView(themeBtn);
        box.addView(avatar);
        box.addView(wall);
        new AlertDialog.Builder(this)
                .setTitle("设置")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    userName = safe(name.getText().toString(), "我");
                    talkCount = clamp(parseInt(context.getText().toString(), 10), 1, 30);
                    memoryThreshold = clamp(parseInt(threshold.getText().toString(), 30), 8, 200);
                    autoMemory = autoValue[0];
                    timeInject = timeValue[0];
                    proactiveEnabled = proactiveValue[0];
                    proactiveIntervalMinutes = clamp(parseInt(proactiveInterval.getText().toString(), 30), 1, 180);
                    saveState();
                    if (proactiveEnabled) {
                        requestNotificationPermission();
                        ProactiveMessageReceiver.schedule(this);
                    } else {
                        ProactiveMessageReceiver.cancel(this);
                    }
                    renderAll();
                })
                .setNegativeButton("清空背景", (d, w) -> {
                    wallpaperUri = "";
                    wallpaperView.setImageDrawable(null);
                    saveState();
                })
                .setNeutralButton("取消", null)
                .show();
    }

    private void showMemoryPanel() {
        Persona p = findActivePersona();
        if (p == null) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(8), dp(14), 0);
        TextView info = new TextView(this);
        info.setText("长期记忆：" + p.memories.size() + " 条\n临时记录：" + p.tempLogs.size() + " 条");
        info.setTextColor(theme.textPrimary);
        info.setPadding(0, 0, 0, dp(8));
        box.addView(info);
        int count = 0;
        for (CoreMemory m : topMemories(p)) {
            TextView tv = new TextView(this);
            tv.setText("• " + m.content);
            tv.setTextColor(theme.textSecondary);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tv.setPadding(0, dp(4), 0, dp(4));
            box.addView(tv);
            if (++count >= 8) break;
        }
        Button now = listButton("立即整理");
        now.setOnClickListener(v -> organizeMemory(p, true));
        Button clear = listButton("清空长期记忆");
        clear.setOnClickListener(v -> {
            p.memories.clear();
            saveState();
            renderAll();
            Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show();
        });
        box.addView(now);
        box.addView(clear);
        new AlertDialog.Builder(this)
                .setTitle("记忆")
                .setView(box)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void triggerManualProactive(Persona p) {
        if (p == null || generating) return;
        ChatMessage ai = new ChatMessage(false, "正在连接...");
        ai.loading = true;
        p.messages.add(ai);
        currentAssistant = ai;
        currentAssistantBuffer = new StringBuilder();
        generating = true;
        updateSendButton();
        renderAll();
        String prompt = buildProactivePrompt(p, true);
        api.streamAsync(prompt, new UpstreamClient.StreamCallback() {
            @Override public void onDelta(String delta) {
                ui.post(() -> {
                    appendAssistantDelta(delta);
                    scheduleStreamRender();
                });
            }
            @Override public void onDone() {
                ui.post(() -> {
                    if (currentAssistantBuffer != null) ai.text = currentAssistantBuffer.toString();
                    String notifyText = MessageParser.preview(ai.text);
                    p.lastProactiveTime = System.currentTimeMillis();
                    finishAssistantResponse(p);
                    ProactiveMessageReceiver.notifyNow(MainActivity.this, p, notifyText);
                });
            }
            @Override public void onError(Exception error) {
                ui.post(() -> {
                    ai.loading = false;
                    ai.text = "我本来想主动找你来着，结果卡住了";
                    currentAssistant = null;
                    currentAssistantBuffer = null;
                    generating = false;
                    updateSendButton();
                    saveState();
                    renderAll();
                });
            }
        });
    }

    private String buildProactivePrompt(Persona p, boolean forceSend) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 身份边界\n")
                .append("你现在扮演「").append(p.name).append("」。用户是「").append(userName).append("」。\n")
                .append("你只能输出").append(p.name).append("主动发给用户的消息，不要输出用户的话，不要输出说话人前缀，不要写旁白。\n\n")
                .append(ensureSplitRule(p.prompt)).append("\n\n");
        if (timeInject) {
            sb.append("当前时间：")
                    .append(new SimpleDateFormat("yyyy年M月d日 HH:mm EEEE", Locale.CHINA).format(new Date()))
                    .append("\n");
        }
        if (p.hiddenMemory != null && !p.hiddenMemory.trim().isEmpty()) {
            sb.append("长期背景，只用于保持一致，不要主动说出：\n")
                    .append(p.hiddenMemory.trim()).append("\n");
        }
        sb.append("最近聊天记录：\n");
        List<ChatMessage> history = selectContext(p);
        for (ChatMessage m : history) {
            sb.append(m.user ? userName : p.name).append("：").append(m.text).append("\n");
        }
        if (forceSend) {
            sb.append("\n现在请你根据人设和上下文主动给用户发一条自然消息。像真实聊天一样短，1-2句即可，必要时用单个反斜线 \\ 分隔多个气泡。不要解释。");
        } else {
            sb.append("\n用户有一段时间没说话。请判断是否适合主动发消息；如果不适合只回复 [skip]，如果适合就发一条短消息。");
        }
        sb.append("\n直接从消息正文开始输出：");
        return sb.toString();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    private boolean hasNotificationPermission() {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATIONS) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            Toast.makeText(this, granted ? "通知权限已允许，可以收到主动消息弹窗" : "通知权限未允许，主动消息只能写入聊天记录，可能不会弹窗", Toast.LENGTH_LONG).show();
        }
    }

    private void requestBatteryOptimizationIgnore() {
        if (Build.VERSION.SDK_INT < 23) return;
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
                Toast.makeText(this, "请在系统设置里允许后台运行", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void pickImage(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, requestCode);
    }

    private void showPersonaProfile(Persona p) {
        if (sideOverlay != null) {
            if (p != null) activePersonaId = p.id;
            pendingPickPersonaIndex = personas.indexOf(p);
            saveState();
            renderAll();
            showSidebar();
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(10), dp(14), dp(2));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        View avatar = avatarView(p.name, p.avatarUri);
        TextView info = new TextView(this);
        info.setText(p.name + "\n" + p.messages.size() + " 条消息 · " + p.memories.size() + " 条记忆");
        info.setTextColor(theme.textPrimary);
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        info.setPadding(dp(12), 0, 0, 0);
        top.addView(avatar);
        top.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(top);

        Button avatarBtn = listButton(TextUtils.isEmpty(p.avatarUri) ? "设置 AI 头像" : "更换 AI 头像");
        avatarBtn.setOnClickListener(v -> {
            pendingPickPersonaIndex = personas.indexOf(p);
            pickImage(REQ_PERSONA_AVATAR);
        });
        Button editBtn = listButton("编辑人格和隐藏记忆");
        editBtn.setOnClickListener(v -> showPersonaEditor(p));
        Button proactiveNow = listButton("让 TA 现在主动发一条");
        proactiveNow.setOnClickListener(v -> triggerManualProactive(p));
        box.addView(avatarBtn);
        box.addView(editBtn);
        box.addView(proactiveNow);

        new AlertDialog.Builder(this)
                .setTitle("聊天资料")
                .setView(box)
                .setPositiveButton("关闭", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        if (requestCode == REQ_WALLPAPER) {
            wallpaperUri = uri.toString();
            wallpaperView.setImageURI(uri);
        } else if (requestCode == REQ_USER_AVATAR) {
            userAvatarUri = uri.toString();
        } else if (requestCode == REQ_PERSONA_AVATAR && pendingPickPersonaIndex >= 0 && pendingPickPersonaIndex < personas.size()) {
            personas.get(pendingPickPersonaIndex).avatarUri = uri.toString();
        }
        saveState();
        renderAll();
    }

    private ImageButton iconButton(String text, int color) {
        ImageButton b = new ImageButton(this);
        b.setBackground(round(Color.TRANSPARENT, dp(18)));
        b.setImageBitmap(TextBitmap.create(text, color, dp(20), dp(42), dp(42)));
        b.setPadding(0, 0, 0, 0);
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        return b;
    }

    private Button listButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        b.setTextColor(theme.textPrimary);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setBackground(roundStroke(theme.inputBg, dp(12), dp(1), theme.inputBorder));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
        lp.setMargins(0, dp(4), 0, dp(4));
        b.setLayoutParams(lp);
        return b;
    }

    private Button miniButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(theme.textPrimary);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setBackground(roundStroke(theme.inputBg, dp(14), dp(1), theme.inputBorder));
        return b;
    }

    private TextView sectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(theme.textPrimary);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(2), dp(18), dp(2), dp(8));
        return tv;
    }

    private TextView sidebarText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(theme.textSecondary);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setLineSpacing(dp(2), 1.12f);
        tv.setPadding(dp(2), dp(4), dp(2), dp(4));
        return tv;
    }

    private EditText field(String hint, String value, int lines) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(theme.textPrimary);
        e.setHintTextColor(theme.textMuted);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        e.setMinLines(lines);
        e.setMaxLines(Math.max(lines, 10));
        e.setPadding(dp(10), dp(8), dp(10), dp(8));
        e.setBackground(roundStroke(theme.inputBg, dp(10), dp(1), theme.inputBorder));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(5), 0, dp(5));
        e.setLayoutParams(lp);
        return e;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private GradientDrawable roundStroke(int color, int radius, int strokeWidth, int strokeColor) {
        GradientDrawable d = round(color, radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    private void updateSendButton() {
        sendButton.setBackground(round(generating ? C_RED : theme.sendButton, dp(21)));
        sendButton.setImageBitmap(TextBitmap.create(generating ? "■" : "➤", theme.sendButtonText, dp(19), dp(42), dp(42)));
    }

    private void scheduleStreamRender() {
        if (streamRenderPending) return;
        streamRenderPending = true;
        long now = System.currentTimeMillis();
        long delay = Math.max(0L, 90L - (now - lastStreamRenderAt));
        ui.postDelayed(() -> {
            streamRenderPending = false;
            lastStreamRenderAt = System.currentTimeMillis();
            if (currentAssistant != null && currentAssistantBuffer != null) {
                currentAssistant.text = currentAssistantBuffer.toString();
            }
            if (currentAssistantTextView != null && currentAssistant != null) {
                currentAssistantTextView.setText(MessageParser.preview(currentAssistant.text));
                if (isNearBottom()) scrollBottom();
                return;
            }
            renderAll();
        }, delay);
    }

    private void appendAssistantDelta(String delta) {
        if (currentAssistant == null || delta == null || delta.isEmpty()) return;
        if (currentAssistantBuffer == null) {
            currentAssistantBuffer = new StringBuilder();
            if (currentAssistant.text != null && !"正在连接...".equals(currentAssistant.text)) {
                currentAssistantBuffer.append(currentAssistant.text);
            }
        }
        currentAssistantBuffer.append(delta);
        if ("正在连接...".equals(currentAssistant.text)) {
            currentAssistant.text = "";
        }
    }

    private void showThemePicker() {
        ChatTheme[] themes = ChatTheme.all();
        String[] names = new String[themes.length];
        int checked = 0;
        for (int i = 0; i < themes.length; i++) {
            names[i] = themes[i].name;
            if (themes[i].id.equals(themeId)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("选择主题")
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    themeId = themes[which].id;
                    theme = themes[which];
                    saveState();
                    setupWindow();
                    buildUi();
                    renderAll();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void scrollBottom() {
        scrollView.postDelayed(() -> scrollView.fullScroll(View.FOCUS_DOWN), 50);
    }

    private boolean isNearBottom() {
        if (scrollView == null || messageList == null || messageList.getChildCount() == 0) return true;
        int diff = messageList.getBottom() - (scrollView.getHeight() + scrollView.getScrollY());
        return diff < dp(96);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    private String joinParts(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(p);
        }
        return sb.toString();
    }

    private String formatDate(long time, String pattern) {
        return new SimpleDateFormat(pattern, Locale.CHINA).format(new Date(time));
    }

    private String safe(String s, String fallback) {
        if (s == null || s.trim().isEmpty()) return fallback;
        return s.trim();
    }

    private String ensureSplitRule(String prompt) {
        String base = prompt == null ? "" : prompt.trim();
        if (base.contains("# 气泡分割")) return base;
        return base + SPLIT_RULE;
    }

    private int subtleBorder() {
        return theme.dark ? Color.argb(75, 255, 255, 255) : Color.argb(55, 0, 0, 0);
    }

    private int transparentBorder() {
        return Color.argb(0, 0, 0, 0);
    }

    private int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
