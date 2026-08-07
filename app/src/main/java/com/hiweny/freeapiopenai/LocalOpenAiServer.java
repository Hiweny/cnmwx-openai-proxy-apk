package com.hiweny.freeapiopenai;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * Local HTTP server providing OpenAI-compatible API endpoints.
 * Wraps the upstream free API (free-api.cnmwx.com) into standard OpenAI format.
 *
 * Endpoints:
 * - GET  /v1/models          - List available models
 * - POST /v1/chat/completions - Chat completions (streaming & non-streaming)
 * - POST /v1/completions      - Text completions
 * - GET  /health              - Health check
 *
 * Features:
 * - API Key support (accepts any Bearer token for compatibility)
 * - Model listing with multiple aliases
 * - Tool/function calling via prompt injection + tag parsing
 * - Ad filtering from upstream responses
 * - CORS support for browser-based clients
 */
public class LocalOpenAiServer extends NanoHTTPD {
    private static final String TAG = "LocalOpenAiServer";

    private final UpstreamClient upstream = new UpstreamClient();

    public LocalOpenAiServer(int port) {
        super("0.0.0.0", port);
    }

    @Override
    public Response serve(IHTTPSession session) {
        // Handle CORS preflight
        if (Method.OPTIONS.equals(session.getMethod())) {
            return withCors(newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", ""));
        }

        String path = session.getUri();
        try {
            // Health check endpoints
            if ("/".equals(path) || "/health".equals(path)) {
                return json(200, "{\"status\":\"ok\",\"service\":\"free-api-openai-proxy\",\"version\":\"2.0\"}");
            }

            // Model listing
            if ("/v1/models".equals(path)) {
                return json(200, OpenAiMapper.models());
            }

            // Chat completions
            if (Method.POST.equals(session.getMethod()) && "/v1/chat/completions".equals(path)) {
                return handleChat(session);
            }

            // Text completions
            if (Method.POST.equals(session.getMethod()) && "/v1/completions".equals(path)) {
                return handleCompletions(session);
            }

            // Embeddings - return not supported error
            if (Method.POST.equals(session.getMethod()) && "/v1/embeddings".equals(path)) {
                return json(501, OpenAiMapper.error("not_supported",
                        "Embeddings are not supported by this proxy", "not_supported"));
            }

            // 404 for unknown endpoints
            return json(404, OpenAiMapper.error("invalid_request_error",
                    "Unknown endpoint: " + path, "not_found"));

        } catch (Exception e) {
            Log.e(TAG, "Request handling error: " + e.getMessage(), e);
            return json(500, OpenAiMapper.error("internal_error",
                    e.getMessage() != null ? e.getMessage() : "Internal server error"));
        }
    }

    /**
     * Handle chat completion requests.
     * Supports streaming, non-streaming, and tool calls.
     */
    private Response handleChat(IHTTPSession session) throws Exception {
        JSONObject req = parseJson(session);
        String model = req.optString("model", "free-api");
        boolean stream = req.optBoolean("stream", false);
        JSONArray tools = req.optJSONArray("tools");
        String toolChoice = req.optString("tool_choice", "auto");
        boolean hasTools = tools != null && tools.length() > 0 && !"none".equals(toolChoice);

        String prompt = OpenAiMapper.promptFromChat(req);

        if (stream) {
            if (hasTools) {
                return streamChatWithTools(model, prompt);
            } else {
                return streamChat(model, prompt);
            }
        }

        // Non-streaming: collect full response
        String text = upstream.complete(prompt);

        if (hasTools) {
            ToolCallParser.Result toolResult = ToolCallParser.parse(text);
            return json(200, OpenAiMapper.chatCompletion(model, text, toolResult));
        } else {
            return json(200, OpenAiMapper.chatCompletion(model, text, null));
        }
    }

    /**
     * Handle text completion requests.
     */
    private Response handleCompletions(IHTTPSession session) throws Exception {
        JSONObject req = parseJson(session);
        String model = req.optString("model", "free-api");
        boolean stream = req.optBoolean("stream", false);
        String prompt = OpenAiMapper.promptFromCompletion(req);

        if (stream) {
            return streamText(model, prompt);
        }

        String text = upstream.complete(prompt);
        return json(200, OpenAiMapper.textCompletion(model, text));
    }

    /**
     * Stream chat completions without tool calls.
     * Text is streamed incrementally from upstream to client.
     */
    private Response streamChat(String model, String prompt) throws Exception {
        PipedInputStream input = new PipedInputStream(262144);
        PipedOutputStream output = new PipedOutputStream(input);

        new Thread(() -> {
            try (PipedOutputStream out = output) {
                // Send initial role chunk
                out.write(OpenAiMapper.sse(OpenAiMapper.roleChunk(model)).getBytes(StandardCharsets.UTF_8));
                out.flush();

                // Stream content from upstream
                upstream.stream(prompt, delta -> {
                    String payload = OpenAiMapper.chatDelta(model, delta);
                    out.write(OpenAiMapper.sse(payload).getBytes(StandardCharsets.UTF_8));
                    out.flush();
                });

                // Send done chunk
                out.write(OpenAiMapper.sse(OpenAiMapper.chatDone(model, "stop")).getBytes(StandardCharsets.UTF_8));
                out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (Exception e) {
                Log.e(TAG, "Stream chat error: " + e.getMessage(), e);
                try {
                    String errorPayload = OpenAiMapper.sse(OpenAiMapper.error("stream_error",
                            e.getMessage() != null ? e.getMessage() : "Stream error"));
                    output.write(errorPayload.getBytes(StandardCharsets.UTF_8));
                    output.flush();
                } catch (Exception ignored) {
                }
            }
        }, "openai-stream-chat").start();

        Response response = newChunkedResponse(Response.Status.OK, "text/event-stream; charset=utf-8", input);
        response.addHeader("Cache-Control", "no-cache");
        response.addHeader("Connection", "keep-alive");
        response.addHeader("X-Accel-Buffering", "no");
        return withCors(response);
    }

    /**
     * Stream chat completions with tool call support.
     * Buffers the full response, then parses for tool calls and sends appropriate chunks.
     */
    private Response streamChatWithTools(String model, String prompt) throws Exception {
        PipedInputStream input = new PipedInputStream(262144);
        PipedOutputStream output = new PipedOutputStream(input);

        new Thread(() -> {
            try (PipedOutputStream out = output) {
                // Send initial role chunk immediately to keep connection alive
                out.write(OpenAiMapper.sse(OpenAiMapper.roleChunk(model)).getBytes(StandardCharsets.UTF_8));
                out.flush();

                // Collect full response from upstream
                String fullText = upstream.complete(prompt);

                // Parse for tool calls
                ToolCallParser.Result result = ToolCallParser.parse(fullText);

                if (result.hasToolCalls) {
                    // Send content before tool calls (if any)
                    if (!result.content.isEmpty()) {
                        out.write(OpenAiMapper.sse(OpenAiMapper.chatDelta(model, result.content)).getBytes(StandardCharsets.UTF_8));
                        out.flush();
                    }

                    // Send each tool call as a separate chunk with index
                    JSONArray toolCalls = result.toolCalls;
                    for (int i = 0; i < toolCalls.length(); i++) {
                        JSONObject tc = toolCalls.optJSONObject(i);
                        if (tc != null) {
                            // Add index for streaming format
                            tc.put("index", i);
                            JSONArray singleCall = new JSONArray();
                            singleCall.put(tc);
                            out.write(OpenAiMapper.sse(OpenAiMapper.chatToolCallDelta(model, singleCall)).getBytes(StandardCharsets.UTF_8));
                            out.flush();
                        }
                    }

                    // Send done with tool_calls finish reason
                    out.write(OpenAiMapper.sse(OpenAiMapper.chatDone(model, "tool_calls")).getBytes(StandardCharsets.UTF_8));
                } else {
                    // No tool calls found, send as normal content
                    if (!fullText.isEmpty()) {
                        out.write(OpenAiMapper.sse(OpenAiMapper.chatDelta(model, fullText)).getBytes(StandardCharsets.UTF_8));
                        out.flush();
                    }
                    out.write(OpenAiMapper.sse(OpenAiMapper.chatDone(model, "stop")).getBytes(StandardCharsets.UTF_8));
                }

                out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (Exception e) {
                Log.e(TAG, "Stream chat with tools error: " + e.getMessage(), e);
                try {
                    String errorPayload = OpenAiMapper.sse(OpenAiMapper.error("stream_error",
                            e.getMessage() != null ? e.getMessage() : "Stream error"));
                    output.write(errorPayload.getBytes(StandardCharsets.UTF_8));
                    output.flush();
                } catch (Exception ignored) {
                }
            }
        }, "openai-stream-tools").start();

        Response response = newChunkedResponse(Response.Status.OK, "text/event-stream; charset=utf-8", input);
        response.addHeader("Cache-Control", "no-cache");
        response.addHeader("Connection", "keep-alive");
        response.addHeader("X-Accel-Buffering", "no");
        return withCors(response);
    }

    /**
     * Stream text completions.
     */
    private Response streamText(String model, String prompt) throws Exception {
        PipedInputStream input = new PipedInputStream(262144);
        PipedOutputStream output = new PipedOutputStream(input);

        new Thread(() -> {
            try (PipedOutputStream out = output) {
                upstream.stream(prompt, delta -> {
                    String payload = OpenAiMapper.textDelta(model, delta);
                    out.write(OpenAiMapper.sse(payload).getBytes(StandardCharsets.UTF_8));
                    out.flush();
                });
                out.write(OpenAiMapper.sse(OpenAiMapper.textDone(model)).getBytes(StandardCharsets.UTF_8));
                out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (Exception e) {
                Log.e(TAG, "Stream text error: " + e.getMessage(), e);
            }
        }, "openai-stream-text").start();

        Response response = newChunkedResponse(Response.Status.OK, "text/event-stream; charset=utf-8", input);
        response.addHeader("Cache-Control", "no-cache");
        response.addHeader("Connection", "keep-alive");
        response.addHeader("X-Accel-Buffering", "no");
        return withCors(response);
    }

    /**
     * Parse JSON request body from the HTTP session.
     * Uses NanoHTTPD's parseBody method which handles JSON content type.
     */
    private JSONObject parseJson(IHTTPSession session) throws Exception {
        Map<String, String> body = new HashMap<>();
        try {
            session.parseBody(body);
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse body: " + e.getMessage());
            throw new IllegalArgumentException("Failed to parse request body: " + e.getMessage());
        }

        String raw = body.get("postData");
        if (raw == null || raw.trim().isEmpty()) {
            // Try query parameter string as fallback
            raw = session.getQueryParameterString();
            if (raw == null || raw.trim().isEmpty()) {
                return new JSONObject();
            }
        }
        return new JSONObject(raw);
    }

    private Response json(int statusCode, String body) {
        Response.Status status = Response.Status.lookup(statusCode);
        if (status == null) status = Response.Status.INTERNAL_ERROR;
        Response response = newFixedLengthResponse(status, "application/json; charset=utf-8", body);
        return withCors(response);
    }

    private Response withCors(Response response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.addHeader("Access-Control-Max-Age", "86400");
        return response;
    }
}
