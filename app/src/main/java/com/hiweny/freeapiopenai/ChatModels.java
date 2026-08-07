package com.hiweny.freeapiopenai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class Persona {
    String id = UUID.randomUUID().toString();
    String name;
    String avatarUri = "";
    String prompt;
    String hiddenMemory = "";
    boolean pinned = false;
    long lastMessageTime = System.currentTimeMillis();
    final List<ChatMessage> messages = new ArrayList<>();
    final List<CoreMemory> memories = new ArrayList<>();
    final List<TempLog> tempLogs = new ArrayList<>();

    static Persona defaults(String id, String name, String prompt) {
        Persona p = new Persona();
        p.id = id;
        p.name = name;
        p.prompt = prompt;
        return p;
    }

    JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("avatarUri", avatarUri);
        o.put("prompt", prompt);
        o.put("hiddenMemory", hiddenMemory);
        o.put("pinned", pinned);
        o.put("lastMessageTime", lastMessageTime);
        JSONArray ms = new JSONArray();
        for (ChatMessage m : messages) ms.put(m.toJson());
        JSONArray cms = new JSONArray();
        for (CoreMemory m : memories) cms.put(m.toJson());
        JSONArray logs = new JSONArray();
        for (TempLog l : tempLogs) logs.put(l.toJson());
        o.put("messages", ms);
        o.put("memories", cms);
        o.put("tempLogs", logs);
        return o;
    }

    static Persona fromJson(JSONObject o) {
        Persona p = new Persona();
        p.id = o.optString("id", UUID.randomUUID().toString());
        p.name = o.optString("name", "小美");
        p.avatarUri = o.optString("avatarUri", "");
        p.prompt = o.optString("prompt", "");
        p.hiddenMemory = o.optString("hiddenMemory", "");
        p.pinned = o.optBoolean("pinned", false);
        p.lastMessageTime = o.optLong("lastMessageTime", System.currentTimeMillis());
        JSONArray ms = o.optJSONArray("messages");
        if (ms != null) {
            for (int i = 0; i < ms.length(); i++) {
                p.messages.add(ChatMessage.fromJson(ms.optJSONObject(i)));
            }
        }
        JSONArray cms = o.optJSONArray("memories");
        if (cms != null) {
            for (int i = 0; i < cms.length(); i++) {
                p.memories.add(CoreMemory.fromJson(cms.optJSONObject(i)));
            }
        }
        JSONArray logs = o.optJSONArray("tempLogs");
        if (logs != null) {
            for (int i = 0; i < logs.length(); i++) {
                p.tempLogs.add(TempLog.fromJson(logs.optJSONObject(i)));
            }
        }
        return p;
    }
}

class ChatMessage {
    String id = UUID.randomUUID().toString();
    String text = "";
    boolean user;
    boolean loading;
    boolean error;
    boolean recalled;
    boolean tickle;
    boolean memoryDivider;
    String imageUri = "";
    String replyToText = "";
    boolean replyToUser;
    long time = System.currentTimeMillis();

    ChatMessage(boolean user, String text) {
        this.user = user;
        this.text = text;
    }

    JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("text", text);
        o.put("user", user);
        o.put("loading", loading);
        o.put("error", error);
        o.put("recalled", recalled);
        o.put("tickle", tickle);
        o.put("memoryDivider", memoryDivider);
        o.put("imageUri", imageUri);
        o.put("replyToText", replyToText);
        o.put("replyToUser", replyToUser);
        o.put("time", time);
        return o;
    }

    static ChatMessage fromJson(JSONObject o) {
        if (o == null) return new ChatMessage(false, "");
        ChatMessage m = new ChatMessage(o.optBoolean("user"), o.optString("text", ""));
        m.id = o.optString("id", UUID.randomUUID().toString());
        m.loading = o.optBoolean("loading");
        m.error = o.optBoolean("error");
        m.recalled = o.optBoolean("recalled");
        m.tickle = o.optBoolean("tickle");
        m.memoryDivider = o.optBoolean("memoryDivider");
        m.imageUri = o.optString("imageUri", "");
        m.replyToText = o.optString("replyToText", "");
        m.replyToUser = o.optBoolean("replyToUser");
        m.time = o.optLong("time", System.currentTimeMillis());
        return m;
    }
}

class CoreMemory {
    String id = UUID.randomUUID().toString();
    String content = "";
    int importance = 3;
    String category = "other";
    long createdAt = System.currentTimeMillis();

    JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("content", content);
        o.put("importance", importance);
        o.put("category", category);
        o.put("createdAt", createdAt);
        return o;
    }

    static CoreMemory fromJson(JSONObject o) {
        CoreMemory m = new CoreMemory();
        if (o == null) return m;
        m.id = o.optString("id", UUID.randomUUID().toString());
        m.content = o.optString("content", "");
        m.importance = o.optInt("importance", 3);
        m.category = o.optString("category", "other");
        m.createdAt = o.optLong("createdAt", System.currentTimeMillis());
        return m;
    }
}

class TempLog {
    String role;
    String content;
    long time = System.currentTimeMillis();

    TempLog(String role, String content) {
        this.role = role;
        this.content = content;
    }

    JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("role", role);
        o.put("content", content);
        o.put("time", time);
        return o;
    }

    static TempLog fromJson(JSONObject o) {
        if (o == null) return new TempLog("user", "");
        TempLog l = new TempLog(o.optString("role", "user"), o.optString("content", ""));
        l.time = o.optLong("time", System.currentTimeMillis());
        return l;
    }
}
