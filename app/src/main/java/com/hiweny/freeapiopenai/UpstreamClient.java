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

public class UpstreamClient {
    private static final String ENDPOINT = "https://free-api.cnmwx.com/v1/completions";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build();

    public interface DeltaCallback {
        void onDelta(String text) throws Exception;
    }

    public String complete(String prompt) throws Exception {
        StringBuilder sb = new StringBuilder();
        stream(prompt, sb::append);
        return AdFilter.cleanAll(sb.toString()).trim();
    }

    public void stream(String prompt, DeltaCallback callback) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("prompt", prompt == null ? "" : prompt);

        Request request = new Request.Builder()
                .url(ENDPOINT)
                .post(RequestBody.create(payload.toString(), JSON))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("User-Agent", "Mozilla/5.0 Android OpenAI Proxy")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("上游接口错误：" + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) return;
            BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String data = extractData(line);
                if (data == null || data.trim().isEmpty()) continue;
                String clean = AdFilter.cleanDelta(data);
                if (!clean.isEmpty()) {
                    callback.onDelta(clean);
                }
            }
        }
    }

    private String extractData(String line) {
        if (line == null) return null;
        if (line.startsWith("data:")) {
            return line.substring(5);
        }
        return line;
    }
}
