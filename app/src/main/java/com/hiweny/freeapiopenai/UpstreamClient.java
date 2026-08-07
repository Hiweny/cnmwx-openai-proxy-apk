package com.hiweny.freeapiopenai;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Client for the upstream free API at https://free-api.cnmwx.com/v1/completions.
 * Handles both streaming (SSE) and non-streaming responses.
 *
 * Key findings from API testing:
 * - SSE format uses "data:" (no space after colon), non-standard
 * - No [DONE] terminator, stream just ends
 * - Ad content injected as first SSE frame
 * - Requires Accept: text/event-stream header for SSE mode
 * - Backend is Google Gemini via Tencent EdgeOne CDN
 */
public class UpstreamClient {

    private static final String ENDPOINT = "https://free-api.cnmwx.com/v1/completions";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final OkHttpClient SHARED_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(600, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    private final OkHttpClient client = SHARED_CLIENT;
    private volatile okhttp3.Call currentCall;

    public interface DeltaCallback {
        void onDelta(String text) throws Exception;
    }

    public interface StreamCallback {
        void onDelta(String text);
        void onDone();
        void onError(Exception error);
    }

    public UpstreamClient() {
    }

    /**
     * Non-streaming completion. Collects all text and returns cleaned result.
     */
    public String complete(String prompt) throws Exception {
        StringBuilder sb = new StringBuilder();
        stream(prompt, sb::append);
        return AdFilter.cleanAll(sb.toString()).trim();
    }

    public void streamAsync(String prompt, StreamCallback callback) {
        new Thread(() -> {
            try {
                stream(prompt, callback::onDelta);
                callback.onDone();
            } catch (Exception e) {
                callback.onError(e);
            }
        }, "cnmwx-native-stream").start();
    }

    public void cancel() {
        okhttp3.Call call = currentCall;
        if (call != null) {
            call.cancel();
        }
    }

    /**
     * Streaming completion. Calls callback for each text delta.
     * Handles the non-standard SSE format from the upstream API.
     */
    public void stream(String prompt, DeltaCallback callback) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("prompt", prompt == null ? "" : prompt);

        Request request = new Request.Builder()
                .url(ENDPOINT)
                .post(RequestBody.create(payload.toString(), JSON))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .header("Cache-Control", "no-cache")
                .build();

        okhttp3.Call call = client.newCall(request);
        currentCall = call;
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("服务暂时不可用: " + response.code());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IllegalStateException("服务暂时没有响应");
            }

            String contentType = response.header("content-type", "");
            if (contentType.contains("text/event-stream")) {
                // SSE streaming mode
                readSseStream(body, callback);
            } else {
                // Plain text mode (fallback)
                String text = body.string();
                String cleaned = AdFilter.cleanAll(text);
                if (!cleaned.isEmpty()) {
                    callback.onDelta(cleaned);
                }
            }
        } finally {
            if (currentCall == call) {
                currentCall = null;
            }
        }
    }

    /**
     * Read and parse the non-standard SSE stream from the upstream API.
     * Format: "data:content" (no space after colon), no [DONE] terminator.
     */
    private void readSseStream(ResponseBody body, DeltaCallback callback) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8), 8192);

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                // Empty line = SSE event boundary, skip
                continue;
            }

            String data = extractSseData(line);
            if (data == null) {
                // Not a data line, skip
                continue;
            }

            if (data.trim().isEmpty()) {
                // Empty data frame (heartbeat/separator), skip
                continue;
            }

            // Check for OpenAI-style [DONE] terminator (upstream doesn't send it, but just in case)
            if ("[DONE]".equals(data.trim())) {
                break;
            }

            // Clean ad content from delta
            String clean = AdFilter.cleanDelta(data);
            if (!clean.isEmpty()) {
                callback.onDelta(clean);
            }
        }
    }

    /**
     * Extract data content from an SSE line.
     * Handles both "data:content" (no space) and "data: content" (with space).
     */
    private String extractSseData(String line) {
        if (line == null) return null;

        // Standard SSE: "data: content" (with space)
        if (line.startsWith("data: ")) {
            return line.substring(6);
        }
        // Non-standard: "data:content" (no space, used by upstream)
        if (line.startsWith("data:")) {
            return line.substring(5);
        }
        // Not a data line
        return null;
    }
}
