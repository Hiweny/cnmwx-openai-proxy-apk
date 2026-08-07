package com.hiweny.freeapiopenai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class OpenAiMapper {
    private static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    private static void put(JSONObject object, String key, Object value) {
        try {
            object.put(key, value);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String models() {
        JSONObject root = new JSONObject();
        JSONArray data = new JSONArray();
        JSONObject model = new JSONObject();
        put(model, "id", "free-api");
        put(model, "object", "model");
        put(model, "created", now());
        put(model, "owned_by", "free-api.cnmwx.com");
        data.put(model);
        put(root, "object", "list");
        put(root, "data", data);
        return root.toString();
    }

    public static String promptFromChat(JSONObject req) {
        JSONArray messages = req.optJSONArray("messages");
        if (messages == null || messages.length() == 0) {
            return req.optString("prompt", "");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject msg = messages.optJSONObject(i);
            if (msg == null) continue;
            String role = msg.optString("role", "user");
            String content = contentToText(msg.opt("content"));
            if (content.trim().isEmpty()) continue;
            sb.append(role).append(": ").append(content.trim()).append("\n");
        }
        sb.append("assistant:");
        return sb.toString();
    }

    public static String promptFromCompletion(JSONObject req) {
        Object prompt = req.opt("prompt");
        if (prompt instanceof JSONArray) {
            JSONArray array = (JSONArray) prompt;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                if (i > 0) sb.append("\n");
                sb.append(array.optString(i));
            }
            return sb.toString();
        }
        return req.optString("prompt", "");
    }

    private static String contentToText(Object content) {
        if (content == null || JSONObject.NULL.equals(content)) return "";
        if (content instanceof String) return (String) content;
        if (content instanceof JSONArray) {
            JSONArray parts = (JSONArray) content;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part != null && "text".equals(part.optString("type"))) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(part.optString("text"));
                }
            }
            return sb.toString();
        }
        return String.valueOf(content);
    }

    public static String chatCompletion(String model, String text) {
        JSONObject root = base("chat.completion", model);
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        JSONObject msg = new JSONObject();
        put(msg, "role", "assistant");
        put(msg, "content", text);
        put(choice, "index", 0);
        put(choice, "message", msg);
        put(choice, "finish_reason", "stop");
        choices.put(choice);
        put(root, "choices", choices);
        put(root, "usage", usage(0, text.length()));
        return root.toString();
    }

    public static String textCompletion(String model, String text) {
        JSONObject root = base("text_completion", model);
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        put(choice, "index", 0);
        put(choice, "text", text);
        put(choice, "finish_reason", "stop");
        choices.put(choice);
        put(root, "choices", choices);
        put(root, "usage", usage(0, text.length()));
        return root.toString();
    }

    public static String roleChunk(String model) {
        JSONObject root = base("chat.completion.chunk", model);
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        JSONObject delta = new JSONObject();
        put(delta, "role", "assistant");
        put(choice, "index", 0);
        put(choice, "delta", delta);
        put(choice, "finish_reason", JSONObject.NULL);
        choices.put(choice);
        put(root, "choices", choices);
        return root.toString();
    }

    public static String chatDelta(String model, String deltaText) {
        JSONObject root = base("chat.completion.chunk", model);
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        JSONObject delta = new JSONObject();
        put(delta, "content", deltaText);
        put(choice, "index", 0);
        put(choice, "delta", delta);
        put(choice, "finish_reason", JSONObject.NULL);
        choices.put(choice);
        put(root, "choices", choices);
        return root.toString();
    }

    public static String chatDone(String model) {
        JSONObject root = base("chat.completion.chunk", model);
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        put(choice, "index", 0);
        put(choice, "delta", new JSONObject());
        put(choice, "finish_reason", "stop");
        choices.put(choice);
        put(root, "choices", choices);
        return root.toString();
    }

    public static String textDelta(String model, String deltaText) {
        JSONObject root = base("text_completion.chunk", model);
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        put(choice, "index", 0);
        put(choice, "text", deltaText);
        put(choice, "finish_reason", JSONObject.NULL);
        choices.put(choice);
        put(root, "choices", choices);
        return root.toString();
    }

    public static String textDone(String model) {
        JSONObject root = base("text_completion.chunk", model);
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        put(choice, "index", 0);
        put(choice, "text", "");
        put(choice, "finish_reason", "stop");
        choices.put(choice);
        put(root, "choices", choices);
        return root.toString();
    }

    public static String sse(String json) {
        return "data: " + json + "\n\n";
    }

    public static String error(String type, String message) {
        JSONObject root = new JSONObject();
        JSONObject error = new JSONObject();
        put(error, "type", type);
        put(error, "message", message == null ? "unknown error" : message);
        put(root, "error", error);
        return root.toString();
    }

    private static JSONObject base(String object, String model) {
        JSONObject root = new JSONObject();
        put(root, "id", "chatcmpl-" + UUID.randomUUID().toString().replace("-", ""));
        put(root, "object", object);
        put(root, "created", now());
        put(root, "model", model == null || model.isEmpty() ? "free-api" : model);
        return root;
    }

    private static JSONObject usage(int promptTokens, int completionTokens) {
        JSONObject usage = new JSONObject();
        put(usage, "prompt_tokens", promptTokens);
        put(usage, "completion_tokens", completionTokens);
        put(usage, "total_tokens", promptTokens + completionTokens);
        return usage;
    }
}
