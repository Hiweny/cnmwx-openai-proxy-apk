package com.hiweny.freeapiopenai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

public class OpenAiMapper {
    private static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    public static String models() {
        JSONObject root = new JSONObject();
        JSONArray data = new JSONArray();
        JSONObject model = new JSONObject();
        model.put("id", "free-api");
        model.put("object", "model");
        model.put("created", now());
        model.put("owned_by", "free-api.cnmwx.com");
        data.put(model);
        root.put("object", "list");
        root.put("data", data);
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
        msg.put("role", "assistant");
        msg.put("content", text);
        choice.put("index", 0);
        choice.put("message", msg);
        choice.put("finish_reason", "stop");
        choices.put(choice);
        root.put("choices", choices);
        root.put("usage", usage(0, text.length()));
        return root.toString();
    }

    public static String textCompletion(String model, String text) {
        JSONObject root = base("text_completion", model);
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        choice.put("index", 0);
        choice.put("text", text);
        choice.put("finish_reason", "stop");
        choices.put(choice);
        root.put("choices", choices);
        root.put("usage", usage(0, text.length()));
        return root.toString();
    }

    public static String roleChunk(String model) {
        JSONObject root = base("chat.completion.chunk", model);
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        JSONObject delta = new JSONObject();
        delta.put("role", "assistant");
        choice.put("index", 0);
        choice.put("delta", delta);
        choice.put("finish_reason", JSONObject.NULL);
        choices.put(choice);
        root.put("choices", choices);
        return root.toString();
    }

    public static String chatDelta(String model, String deltaText) {
        JSONObject root = base("chat.completion.chunk", model);
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        JSONObject delta = new JSONObject();
        delta.put("content", deltaText);
        choice.put("index", 0);
        choice.put("delta", delta);
        choice.put("finish_reason", JSONObject.NULL);
        choices.put(choice);
        root.put("choices", choices);
        return root.toString();
    }

    public static String chatDone(String model) {
        JSONObject root = base("chat.completion.chunk", model);
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        choice.put("index", 0);
        choice.put("delta", new JSONObject());
        choice.put("finish_reason", "stop");
        choices.put(choice);
        root.put("choices", choices);
        return root.toString();
    }

    public static String textDelta(String model, String deltaText) {
        JSONObject root = base("text_completion.chunk", model);
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        choice.put("index", 0);
        choice.put("text", deltaText);
        choice.put("finish_reason", JSONObject.NULL);
        choices.put(choice);
        root.put("choices", choices);
        return root.toString();
    }

    public static String textDone(String model) {
        JSONObject root = base("text_completion.chunk", model);
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        choice.put("index", 0);
        choice.put("text", "");
        choice.put("finish_reason", "stop");
        choices.put(choice);
        root.put("choices", choices);
        return root.toString();
    }

    public static String sse(String json) {
        return "data: " + json + "\n\n";
    }

    public static String error(String type, String message) {
        JSONObject root = new JSONObject();
        JSONObject error = new JSONObject();
        error.put("type", type);
        error.put("message", message == null ? "unknown error" : message);
        root.put("error", error);
        return root.toString();
    }

    private static JSONObject base(String object, String model) {
        JSONObject root = new JSONObject();
        root.put("id", "chatcmpl-" + UUID.randomUUID().toString().replace("-", ""));
        root.put("object", object);
        root.put("created", now());
        root.put("model", model == null || model.isEmpty() ? "free-api" : model);
        return root;
    }

    private static JSONObject usage(int promptTokens, int completionTokens) {
        JSONObject usage = new JSONObject();
        usage.put("prompt_tokens", promptTokens);
        usage.put("completion_tokens", completionTokens);
        usage.put("total_tokens", promptTokens + completionTokens);
        return usage;
    }
}
