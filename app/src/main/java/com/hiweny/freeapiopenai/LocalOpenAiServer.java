package com.hiweny.freeapiopenai;

import org.json.JSONObject;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class LocalOpenAiServer extends NanoHTTPD {
    private final UpstreamClient upstream = new UpstreamClient();

    public LocalOpenAiServer(int port) {
        super("0.0.0.0", port);
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (Method.OPTIONS.equals(session.getMethod())) {
            return withCors(newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", ""));
        }

        String path = session.getUri();
        try {
            if ("/".equals(path) || "/health".equals(path)) {
                return json(200, "{\"status\":\"ok\",\"service\":\"free-api-openai-proxy\"}");
            }
            if ("/v1/models".equals(path)) {
                return json(200, OpenAiMapper.models());
            }
            if (Method.POST.equals(session.getMethod()) && "/v1/chat/completions".equals(path)) {
                return handleChat(session);
            }
            if (Method.POST.equals(session.getMethod()) && "/v1/completions".equals(path)) {
                return handleCompletions(session);
            }
            return json(404, "{\"error\":{\"message\":\"not found\",\"type\":\"invalid_request_error\"}}");
        } catch (Exception e) {
            return json(500, OpenAiMapper.error("proxy_error", e.getMessage()));
        }
    }

    private Response handleChat(IHTTPSession session) throws Exception {
        JSONObject req = parseJson(session);
        String model = req.optString("model", "free-api");
        boolean stream = req.optBoolean("stream", false);
        String prompt = OpenAiMapper.promptFromChat(req);
        if (stream) {
            return streamResponse(model, prompt, true);
        }
        String text = upstream.complete(prompt);
        return json(200, OpenAiMapper.chatCompletion(model, text));
    }

    private Response handleCompletions(IHTTPSession session) throws Exception {
        JSONObject req = parseJson(session);
        String model = req.optString("model", "free-api");
        boolean stream = req.optBoolean("stream", false);
        String prompt = OpenAiMapper.promptFromCompletion(req);
        if (stream) {
            return streamResponse(model, prompt, false);
        }
        String text = upstream.complete(prompt);
        return json(200, OpenAiMapper.textCompletion(model, text));
    }

    private Response streamResponse(String model, String prompt, boolean chatMode) throws Exception {
        PipedInputStream input = new PipedInputStream(65536);
        PipedOutputStream output = new PipedOutputStream(input);
        new Thread(() -> {
            try (PipedOutputStream out = output) {
                if (chatMode) {
                    out.write(OpenAiMapper.sse(OpenAiMapper.roleChunk(model)).getBytes(StandardCharsets.UTF_8));
                }
                upstream.stream(prompt, delta -> {
                    String payload = chatMode
                            ? OpenAiMapper.chatDelta(model, delta)
                            : OpenAiMapper.textDelta(model, delta);
                    out.write(OpenAiMapper.sse(payload).getBytes(StandardCharsets.UTF_8));
                    out.flush();
                });
                out.write(OpenAiMapper.sse(chatMode ? OpenAiMapper.chatDone(model) : OpenAiMapper.textDone(model)).getBytes(StandardCharsets.UTF_8));
                out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (Exception ignored) {
            }
        }, "openai-proxy-stream").start();

        Response response = newChunkedResponse(Response.Status.OK, "text/event-stream; charset=utf-8", input);
        response.addHeader("Cache-Control", "no-cache");
        response.addHeader("Connection", "keep-alive");
        return withCors(response);
    }

    private JSONObject parseJson(IHTTPSession session) throws Exception {
        Map<String, String> body = new HashMap<>();
        session.parseBody(body);
        String raw = body.get("postData");
        if (raw == null || raw.trim().isEmpty()) {
            return new JSONObject();
        }
        return new JSONObject(raw);
    }

    private Response json(int statusCode, String body) {
        Response.Status status = Response.Status.lookup(statusCode);
        if (status == null) status = Response.Status.INTERNAL_ERROR;
        return withCors(newFixedLengthResponse(status, "application/json; charset=utf-8", body));
    }

    private Response withCors(Response response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        return response;
    }
}
