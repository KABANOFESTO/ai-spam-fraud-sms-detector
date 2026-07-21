package com.smsai.smsfrauddetector;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FraudApiClient {
    private final BaseUrlProvider baseUrlProvider;

    public FraudApiClient(BaseUrlProvider baseUrlProvider) {
        this.baseUrlProvider = baseUrlProvider;
    }

    public String ping() throws IOException {
        String[] attempts = {"/health", "/api/health", "/"};
        IOException lastError = null;

        for (String path : attempts) {
            try {
                JSONObject payload = request(path, "GET", null);
                String message = firstString(payload, "detail", "message");
                return message.isEmpty() ? "Backend reachable" : message;
            } catch (IOException error) {
                lastError = error;
            }
        }

        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("Unable to reach backend");
    }

    public AnalysisResult analyze(String message) throws IOException {
        JSONObject body = new JSONObject()
            .put("message", message)
            .put("sms_text", message)
            .put("text", message);

        JSONObject response = request("/api/analyze", "POST", body);
        String rawJson = response.toString();
        String label = firstString(response, "label", "prediction", "result", "classification", "class");
        String explanation = firstString(response, "reason", "explanation", "detail", "message");
        double confidence = firstDouble(response, "confidence", "score", "probability", "prob", "certainty");
        String normalizedLabel = label.isEmpty() ? "Unknown" : label;

        return new AnalysisResult(
            normalizedLabel,
            confidence,
            explanation.isEmpty() ? "The backend returned a prediction without an explanation." : explanation,
            normalizedLabel.toLowerCase().contains("fraud") || normalizedLabel.toLowerCase().contains("spam"),
            rawJson
        );
    }

    public List<HistoryItem> history() throws IOException {
        JSONObject response = request("/api/history", "GET", null);
        JSONArray array = firstArray(response, "items", "results", "data", "history", "analysis_history");
        if (array != null) {
            return toHistoryItems(array);
        }

        JSONObject nested = response.optJSONObject("data");
        if (nested == null) {
            nested = response.optJSONObject("results");
        }
        if (nested == null) {
            nested = response.optJSONObject("history");
        }
        JSONArray nestedArray = nested == null ? null : firstArray(nested, "items", "results", "data");
        return nestedArray == null ? new ArrayList<HistoryItem>() : toHistoryItems(nestedArray);
    }

    public DashboardStats stats() throws IOException {
        JSONObject response = request("/api/stats", "GET", null);
        return new DashboardStats(
            firstInt(response, "total_analyses", "total", "count", "analysis_count"),
            firstInt(response, "legitimate", "legitimate_count", "safe_count"),
            firstInt(response, "spam", "spam_count"),
            firstInt(response, "fraud", "fraud_count", "fraudulent_count")
        );
    }

    private JSONObject request(String path, String method, JSONObject body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(resolveUrl(path)).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setUseCaches(false);
        connection.setDoInput(true);
        connection.setRequestProperty("Accept", "application/json");

        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = connection.getResponseCode();
        String text = readBody(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());

        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + (text.isEmpty() ? "" : ": " + text));
        }

        return text.isEmpty() ? new JSONObject() : new JSONObject(text);
    }

    private String resolveUrl(String path) {
        String base = baseUrlProvider.getBaseUrl();
        if (base == null || base.trim().isEmpty()) {
            base = BuildConfig.DEFAULT_BASE_URL;
        }
        base = base.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        String suffix = path == null ? "" : path.trim();
        while (suffix.startsWith("/")) {
            suffix = suffix.substring(1);
        }

        return base + "/" + suffix;
    }

    private List<HistoryItem> toHistoryItems(JSONArray array) {
        List<HistoryItem> items = new ArrayList<>(array.length());
        for (int index = 0; index < array.length(); index++) {
            JSONObject entry = array.optJSONObject(index);
            if (entry == null) {
                continue;
            }
            items.add(new HistoryItem(
                firstString(entry, "message", "sms_text", "text", "body"),
                firstString(entry, "label", "prediction", "result", "classification", "class"),
                firstDouble(entry, "confidence", "score", "probability", "prob", "certainty"),
                firstString(entry, "created_at", "createdAt", "timestamp", "date", "time")
            ));
        }
        return items;
    }

    private String firstString(JSONObject json, String... keys) {
        for (String key : keys) {
            String value = json.optString(key, "");
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private double firstDouble(JSONObject json, String... keys) {
        for (String key : keys) {
            Object value = json.opt(key);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            if (value instanceof String) {
                try {
                    return Double.parseDouble((String) value);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0.0;
    }

    private int firstInt(JSONObject json, String... keys) {
        for (String key : keys) {
            Object value = json.opt(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            if (value instanceof String) {
                try {
                    return Integer.parseInt((String) value);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0;
    }

    private JSONArray firstArray(JSONObject json, String... keys) {
        for (String key : keys) {
            JSONArray array = json.optJSONArray(key);
            if (array != null) {
                return array;
            }
        }
        return null;
    }

    private String readBody(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    public interface BaseUrlProvider {
        String getBaseUrl();
    }
}
