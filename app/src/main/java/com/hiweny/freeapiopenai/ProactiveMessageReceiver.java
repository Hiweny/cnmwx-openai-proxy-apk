package com.hiweny.freeapiopenai;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProactiveMessageReceiver extends BroadcastReceiver {
    static final String ACTION_HEARTBEAT = "com.hiweny.freeapiopenai.PROACTIVE_HEARTBEAT";
    static final String CHANNEL_ID = "proactive_messages";
    private static final int NOTIFY_ID = 4601;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            ensureChannel(context.getApplicationContext());
            schedule(context.getApplicationContext());
            return;
        }
        if (!ACTION_HEARTBEAT.equals(action)) return;
        PendingResult result = goAsync();
        new Thread(() -> {
            try {
                handleHeartbeat(context.getApplicationContext());
            } finally {
                result.finish();
            }
        }, "proactive-heartbeat").start();
    }

    static void schedule(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("wechat_native_state", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("proactiveEnabled", false)) return;
        int minutes = Math.max(1, prefs.getInt("proactiveIntervalMinutes", 30));
        int checkMinutes = Math.min(minutes, 1);
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        PendingIntent pi = pendingIntent(context);
        alarm.cancel(pi);
        long triggerAt = System.currentTimeMillis() + checkMinutes * 60_000L;
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                alarm.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        } catch (SecurityException ignored) {
            alarm.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    static void cancel(Context context) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null) alarm.cancel(pendingIntent(context));
    }

    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "主动消息", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("不同人设根据上下文主动发来的消息");
        channel.enableLights(true);
        channel.setLightColor(Color.rgb(7, 193, 96));
        nm.createNotificationChannel(channel);
    }

    private static PendingIntent pendingIntent(Context context) {
        Intent i = new Intent(context, ProactiveMessageReceiver.class);
        i.setAction(ACTION_HEARTBEAT);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, 4601, i, flags);
    }

    private void handleHeartbeat(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("wechat_native_state", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("proactiveEnabled", false)) return;
        try {
            List<Persona> personas = loadPersonas(prefs);
            if (personas.isEmpty()) return;
            int minutes = Math.max(1, prefs.getInt("proactiveIntervalMinutes", 30));
            String activePersonaId = prefs.getString("activePersonaId", "");
            long now = System.currentTimeMillis();
            Persona target = chooseTarget(personas, now, minutes, activePersonaId);
            if (target == null) return;

            String message = generateProactiveMessage(prefs, target, now, minutes);
            if (message == null || message.trim().isEmpty()) return;
            MessageParser.Parsed parsed = MessageParser.parse(message);
            String finalText = parsed.parts.isEmpty() ? message.trim() : parsed.parts.get(0);
            if (parsed.parts.isEmpty()) {
                target.messages.add(new ChatMessage(false, finalText));
            } else {
                for (String part : parsed.parts) {
                    if (part != null && !part.trim().isEmpty()) {
                        target.messages.add(new ChatMessage(false, part.trim()));
                    }
                }
            }
            target.lastMessageTime = now;
            target.lastProactiveTime = now;
            savePersonas(prefs, personas);
            notifyNow(context, target, finalText);
        } catch (Exception ignored) {
        } finally {
            schedule(context);
        }
    }

    private List<Persona> loadPersonas(SharedPreferences prefs) throws Exception {
        List<Persona> list = new ArrayList<>();
        String raw = prefs.getString("personasJson", "");
        if (raw.isEmpty()) return list;
        JSONArray arr = new JSONArray(raw);
        for (int i = 0; i < arr.length(); i++) list.add(Persona.fromJson(arr.optJSONObject(i)));
        return list;
    }

    private void savePersonas(SharedPreferences prefs, List<Persona> personas) throws Exception {
        JSONArray arr = new JSONArray();
        for (Persona p : personas) arr.put(p.toJson());
        prefs.edit().putString("personasJson", arr.toString()).apply();
    }

    private Persona chooseTarget(List<Persona> personas, long now, int minutes, String activePersonaId) {
        Persona best = null;
        long bestIdle = 0;
        boolean bestActive = false;
        long activeIdleGap = 60_000L;
        long otherIdleGap = Math.max(2, Math.min(minutes, 3)) * 60_000L;
        long activeRepeatGap = Math.max(2, Math.min(minutes, 5)) * 60_000L;
        long otherRepeatGap = Math.max(3, minutes) * 60_000L;
        for (Persona p : personas) {
            long lastUser = p.lastUserMessageTime > 0 ? p.lastUserMessageTime : p.lastMessageTime;
            long idle = now - lastUser;
            boolean active = p.id != null && p.id.equals(activePersonaId);
            long requiredIdle = active ? activeIdleGap : otherIdleGap;
            long repeatGap = active ? activeRepeatGap : otherRepeatGap;
            if (idle < requiredIdle) continue;
            if (p.lastProactiveTime > 0 && now - p.lastProactiveTime < repeatGap) continue;
            if (active) return p;
            if (best == null || (active && !bestActive) || (!bestActive && idle > bestIdle) || (p.pinned && !best.pinned)) {
                best = p;
                bestIdle = idle;
                bestActive = active;
            }
        }
        return best;
    }

    private String generateProactiveMessage(SharedPreferences prefs, Persona p, long now, int heartbeatMinutes) throws Exception {
        String userName = prefs.getString("userName", "我");
        long lastUser = p.lastUserMessageTime > 0 ? p.lastUserMessageTime : p.lastMessageTime;
        long idleMinutes = Math.max(0L, (now - lastUser) / 60_000L);
        StringBuilder sb = new StringBuilder();
        sb.append("# 身份边界\n")
                .append("你现在扮演「").append(p.name).append("」。用户是「").append(userName).append("」。\n")
                .append("你只能输出「").append(p.name).append("」主动发给用户的消息正文。不要替用户说话，不要输出任何说话人前缀，不要写旁白或规则解释。\n\n")
                .append(p.prompt == null ? "" : p.prompt.trim()).append("\n\n");
        sb.append("当前时间：")
                .append(new SimpleDateFormat("yyyy年M月d日 HH:mm EEEE", Locale.CHINA).format(new Date(now)))
                .append("\n");
        sb.append("后台心跳说明：应用大约每 ").append(heartbeatMinutes)
                .append(" 分钟醒来检查一次，但这不是让你每次都发消息。你必须像真实的人一样，自主判断此刻要不要打扰用户。\n")
                .append("用户距离上次发消息约 ").append(idleMinutes).append(" 分钟；如果刚聊完但用户短时间没有回复，也可以像真人一样判断是否补一句、撒娇一句、提醒一句或安静跳过。\n");
        if (p.hiddenMemory != null && !p.hiddenMemory.trim().isEmpty()) {
            sb.append("长期背景，只用于保持一致，不要主动暴露：\n").append(p.hiddenMemory.trim()).append("\n");
        }
        sb.append("最近聊天记录：\n");
        int start = Math.max(0, p.messages.size() - 10);
        for (int i = start; i < p.messages.size(); i++) {
            ChatMessage m = p.messages.get(i);
            if (m.loading || m.error || m.tickle || m.memoryDivider || m.recalled) continue;
            sb.append(m.user ? userName : p.name).append("：").append(m.text).append("\n");
        }
        sb.append("\n# 主动消息判断\n")
                .append("请根据当前时间、最近聊天氛围、关系亲密度、用户是否刚忙完、是否太晚/太早、上次是谁先结束对话、用户短时间没回是否需要补一句，自主判断是否应该主动发消息。\n")
                .append("不自然、太频繁、像机器人定时任务、可能打扰用户时，只回复 [skip]。\n")
                .append("只有你觉得此刻真实地想找用户说一句，才发一条很短的微信式消息，1-2句即可，必要时用单个反斜线 \\ 拆分气泡。\n")
                .append("不要解释判断过程，不要输出名字前缀。")
                .append("\n直接从消息正文开始输出：");
        String result = new UpstreamClient().complete(sb.toString()).trim();
        if (result.contains("[skip]")) return "";
        return result;
    }

    static void notifyNow(Context context, Persona p, String text) {
        if (context == null || p == null || text == null || text.trim().isEmpty()) return;
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ensureChannel(context);
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentIntent = PendingIntent.getActivity(context, 4602, open, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_stat_proxy)
                .setContentTitle(p.name)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setPriority(Notification.PRIORITY_HIGH);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            if (nm != null) nm.notify(NOTIFY_ID, builder.build());
        } catch (SecurityException ignored) {
        }
    }
}
