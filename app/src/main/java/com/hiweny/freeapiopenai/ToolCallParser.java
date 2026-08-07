package com.hiweny.freeapiopenai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse tool call tags from model output and build tool definition prompts.
 * Inspired by ds-free-api's prompt injection + tag parsing approach.
 */
public class ToolCallParser {

    public static final String TAG_START = "<tool_call>";
    public static final String TAG_END = "</tool_call>";

    private static final Pattern TOOL_CALL_PATTERN =
            Pattern.compile("<tool_call>\\s*([\\s\\S]*?)\\s*</tool_call>", Pattern.MULTILINE);

    public static class Result {
        public final String content;
        public final JSONArray toolCalls;
        public final boolean hasToolCalls;

        public Result(String content, JSONArray toolCalls) {
            this.content = content;
            this.toolCalls = toolCalls;
            this.hasToolCalls = toolCalls != null && toolCalls.length() > 0;
        }
    }

    /**
     * Parse text for tool call tags. Returns content outside tags and extracted tool calls.
     */
    public static Result parse(String text) {
        if (text == null || text.isEmpty()) {
            return new Result("", null);
        }

        JSONArray toolCalls = null;
        StringBuilder content = new StringBuilder();
        Matcher matcher = TOOL_CALL_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            // Append text before the match
            content.append(text, lastEnd, matcher.start());

            String jsonStr = matcher.group(1).trim();
            JSONArray calls = parseToolCallJson(jsonStr);
            if (calls != null && calls.length() > 0) {
                if (toolCalls == null) {
                    toolCalls = new JSONArray();
                }
                for (int i = 0; i < calls.length(); i++) {
                    toolCalls.put(calls.opt(i));
                }
            }
            lastEnd = matcher.end();
        }

        // Append remaining text after last match
        content.append(text, lastEnd, text.length());

        String cleanContent = content.toString().trim();
        return new Result(cleanContent, toolCalls);
    }

    /**
     * Parse JSON from tool call content. Supports both array and single object format.
     */
    private static JSONArray parseToolCallJson(String jsonStr) {
        JSONArray result = new JSONArray();
        try {
            String trimmed = jsonStr.trim();
            if (trimmed.startsWith("[")) {
                JSONArray arr = new JSONArray(trimmed);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject call = normalizeToolCall(arr.optJSONObject(i), i);
                    if (call != null) {
                        result.put(call);
                    }
                }
            } else if (trimmed.startsWith("{")) {
                JSONObject call = normalizeToolCall(new JSONObject(trimmed), 0);
                if (call != null) {
                    result.put(call);
                }
            }
        } catch (Exception e) {
            // Try to salvage partial JSON
            JSONObject salvaged = salvageToolCall(jsonStr);
            if (salvaged != null) {
                result.put(salvaged);
            }
        }
        return result;
    }

    /**
     * Normalize a tool call JSON object to OpenAI format.
     */
    private static JSONObject normalizeToolCall(JSONObject obj, int index) {
        if (obj == null) return null;
        try {
            JSONObject call = new JSONObject();
            call.put("id", "call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
            call.put("type", "function");

            JSONObject function = new JSONObject();
            String name = obj.optString("name", obj.optString("function", ""));
            if (name.isEmpty()) {
                JSONObject fnObj = obj.optJSONObject("function");
                if (fnObj != null) {
                    name = fnObj.optString("name", "");
                    Object args = fnObj.opt("arguments");
                    if (args == null) args = fnObj.opt("parameters");
                    if (args instanceof JSONObject) {
                        function.put("arguments", args.toString());
                    } else if (args instanceof String) {
                        function.put("arguments", args);
                    } else {
                        function.put("arguments", "{}");
                    }
                } else {
                    function.put("arguments", "{}");
                }
            } else {
                function.put("name", name);
                Object args = obj.opt("arguments");
                if (args == null) args = obj.opt("parameters");
                if (args == null) args = obj.opt("args");
                if (args instanceof JSONObject) {
                    function.put("arguments", args.toString());
                } else if (args instanceof String) {
                    function.put("arguments", args);
                } else {
                    function.put("arguments", "{}");
                }
            }
            function.put("name", name);
            call.put("function", function);
            return call;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Attempt to salvage a malformed tool call JSON.
     */
    private static JSONObject salvageToolCall(String jsonStr) {
        try {
            // Try to extract name and arguments with regex
            Pattern namePattern = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
            Pattern argsPattern = Pattern.compile("\"arguments\"\\s*:\\s*(\\{[^}]*\\})");

            Matcher nameMatcher = namePattern.matcher(jsonStr);
            Matcher argsMatcher = argsPattern.matcher(jsonStr);

            JSONObject call = new JSONObject();
            call.put("id", "call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
            call.put("type", "function");

            JSONObject function = new JSONObject();
            function.put("name", nameMatcher.find() ? nameMatcher.group(1) : "unknown");
            function.put("arguments", argsMatcher.find() ? argsMatcher.group(1) : "{}");
            call.put("function", function);
            return call;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Build a system prompt section that defines available tools and instructs the model
     * to use <tool_call> tags when it wants to call a function.
     */
    public static String buildToolPrompt(JSONArray tools) {
        if (tools == null || tools.length() == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n=== 工具调用说明 ===\n");
        sb.append("你可以使用以下工具来帮助用户完成任务。当需要调用工具时，请严格按照以下格式输出：\n\n");
        sb.append(TAG_START).append("\n");
        sb.append("{\"name\": \"工具名称\", \"arguments\": {\"参数名\": \"参数值\"}}\n");
        sb.append(TAG_END).append("\n\n");
        sb.append("注意事项：\n");
        sb.append("- 每个工具调用必须用 <tool_call> 标签单独包裹\n");
        sb.append("- arguments 必须是合法的 JSON 对象\n");
        sb.append("- 可以在一次回复中调用多个工具\n");
        sb.append("- 如果不需要调用工具，直接正常回复即可\n\n");
        sb.append("可用工具列表：\n\n");

        for (int i = 0; i < tools.length(); i++) {
            JSONObject tool = tools.optJSONObject(i);
            if (tool == null) continue;

            JSONObject function = tool.optJSONObject("function");
            if (function != null) {
                sb.append("---\n");
                sb.append("工具名: ").append(function.optString("name", "unknown")).append("\n");
                sb.append("描述: ").append(function.optString("description", "")).append("\n");

                JSONObject params = function.optJSONObject("parameters");
                if (params != null) {
                    sb.append("参数:\n");
                    JSONObject properties = params.optJSONObject("properties");
                    JSONArray required = params.optJSONArray("required");
                    if (properties != null) {
                        JSONArray names = properties.names();
                        if (names != null) {
                            for (int j = 0; j < names.length(); j++) {
                                String paramName = names.optString(j);
                                JSONObject paramDef = properties.optJSONObject(paramName);
                                if (paramDef != null) {
                                    String type = paramDef.optString("type", "any");
                                    String desc = paramDef.optString("description", "");
                                    boolean isRequired = false;
                                    if (required != null) {
                                        for (int k = 0; k < required.length(); k++) {
                                            if (paramName.equals(required.optString(k))) {
                                                isRequired = true;
                                                break;
                                            }
                                        }
                                    }
                                    sb.append("- ").append(paramName)
                                            .append(" (").append(type)
                                            .append(isRequired ? ", 必填" : ", 可选")
                                            .append("): ").append(desc).append("\n");
                                }
                            }
                        }
                    }
                }
                sb.append("---\n\n");
            } else {
                // Simple format: tool is directly a function definition
                sb.append("---\n");
                sb.append("工具名: ").append(tool.optString("name", "unknown")).append("\n");
                sb.append("描述: ").append(tool.optString("description", "")).append("\n");
                sb.append("---\n\n");
            }
        }

        return sb.toString();
    }

    /**
     * Convert tool role messages (function results) to text for prompt context.
     */
    public static String formatToolResult(JSONObject msg) {
        try {
            String toolCallId = msg.optString("tool_call_id", "");
            String content = msg.optString("content", "");
            return "[工具返回结果 " + toolCallId + "]: " + content + "\n";
        } catch (Exception e) {
            return "";
        }
    }
}
