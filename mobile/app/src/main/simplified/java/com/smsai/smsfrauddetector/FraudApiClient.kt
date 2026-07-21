package com.smsai.smsfrauddetector

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

class FraudApiClient(private val baseUrlProvider: () -> String) {
    fun ping(): String {
        val attempts = listOf("/health", "/api/health", "/")
        var lastError: IOException? = null

        for (path in attempts) {
            try {
                val payload = request(path)
                return payload.optString("detail")
                    .ifBlank { payload.optString("message") }
                    .ifBlank { "Backend reachable" }
            } catch (error: IOException) {
                lastError = error
            }
        }

        throw lastError ?: IOException("Unable to reach backend")
    }

    fun analyze(message: String): AnalysisResult {
        val payload = JSONObject()
            .put("message", message)
            .put("sms_text", message)
            .put("text", message)

        val response = request("/api/analyze", "POST", payload)
        val rawJson = response.toString()
        val label = firstString(response, "label", "prediction", "result", "classification", "class")
        val explanation = firstString(response, "reason", "explanation", "detail", "message")
        val confidence = firstDouble(response, "confidence", "score", "probability", "prob", "certainty")
        val normalizedLabel = label.ifBlank { "Unknown" }

        return AnalysisResult(
            label = normalizedLabel,
            confidence = confidence,
            explanation = explanation.ifBlank { "The backend returned a prediction without an explanation." },
            isFraud = normalizedLabel.contains("fraud", ignoreCase = true) ||
                normalizedLabel.contains("spam", ignoreCase = true),
            rawJson = rawJson,
        )
    }

    fun history(): List<HistoryItem> {
        val response = request("/api/history")
        val entries = firstArray(response, "items", "results", "data", "history", "analysis_history")
        if (entries != null) {
            return entries.toHistoryItems()
        }

        val nested = response.optJSONObject("data")
            ?: response.optJSONObject("results")
            ?: response.optJSONObject("history")

        val nestedArray = nested?.let { firstArray(it, "items", "results", "data") }
        return nestedArray?.toHistoryItems().orEmpty()
    }

    fun stats(): DashboardStats? {
        val response = request("/api/stats")
        return DashboardStats(
            totalAnalyses = firstInt(response, "total_analyses", "total", "count", "analysis_count"),
            legitimateCount = firstInt(response, "legitimate", "legitimate_count", "safe_count"),
            spamCount = firstInt(response, "spam", "spam_count"),
            fraudCount = firstInt(response, "fraud", "fraud_count", "fraudulent_count"),
        )
    }

    private fun request(
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
    ): JSONObject {
        val connection = (URL(resolveUrl(path)).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            useCaches = false
            doInput = true
            setRequestProperty("Accept", "application/json")
        }

        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { stream ->
                stream.write(body.toString().toByteArray(Charset.forName("UTF-8")))
            }
        }

        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

        if (responseCode !in 200..299) {
            throw IOException(
                "HTTP $responseCode${if (responseText.isNotBlank()) ": $responseText" else ""}",
            )
        }

        return if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
    }

    private fun resolveUrl(path: String): String {
        val base = baseUrlProvider().trim()
            .ifBlank { BuildConfig.DEFAULT_BASE_URL }
            .trimEnd('/')
        val suffix = path.trimStart('/')
        return "$base/$suffix"
    }

    private fun firstString(json: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val value = json.optString(key)
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private fun firstDouble(json: JSONObject, vararg keys: String): Double {
        for (key in keys) {
            val value = json.opt(key) ?: continue
            when (value) {
                is Number -> return value.toDouble()
                is String -> value.toDoubleOrNull()?.let { return it }
            }
        }
        return 0.0
    }

    private fun firstInt(json: JSONObject, vararg keys: String): Int {
        for (key in keys) {
            val value = json.opt(key) ?: continue
            when (value) {
                is Number -> return value.toInt()
                is String -> value.toIntOrNull()?.let { return it }
            }
        }
        return 0
    }

    private fun firstArray(json: JSONObject, vararg keys: String): JSONArray? {
        for (key in keys) {
            val array = json.optJSONArray(key)
            if (array != null) return array
        }
        return null
    }

    private fun JSONArray.toHistoryItems(): List<HistoryItem> {
        val items = ArrayList<HistoryItem>(length())
        for (index in 0 until length()) {
            val entry = optJSONObject(index) ?: continue
            items += HistoryItem(
                message = firstString(entry, "message", "sms_text", "text", "body"),
                label = firstString(entry, "label", "prediction", "result", "classification", "class"),
                confidence = firstDouble(entry, "confidence", "score", "probability", "prob", "certainty"),
                createdAt = firstString(entry, "created_at", "createdAt", "timestamp", "date", "time"),
            )
        }
        return items
    }
}
