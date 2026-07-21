package com.smsai.smsfrauddetector

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private lateinit var apiClient: FraudApiClient
    private lateinit var baseUrlInput: EditText
    private lateinit var messageInput: EditText
    private lateinit var connectionStatus: TextView
    private lateinit var analysisStatus: TextView
    private lateinit var resultLabel: TextView
    private lateinit var resultConfidence: TextView
    private lateinit var resultExplanation: TextView
    private lateinit var statsSummary: TextView
    private lateinit var historyContainer: LinearLayout
    private lateinit var errorPanel: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button
    private var lastRetryAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(themeColor(R.color.sms_bg))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        errorPanel = card(this)
        errorPanel.visibility = View.GONE
        errorText = sectionText("Something went wrong")
        retryButton = actionButton("Retry")
        retryButton.setOnClickListener {
            lastRetryAction?.invoke()
        }
        errorPanel.addView(errorText)
        errorPanel.addView(space(dp(10)))
        errorPanel.addView(retryButton)

        root.addView(errorPanel)
        root.addView(space(dp(16)))
        root.addView(buildHeroCard())
        root.addView(space(dp(16)))
        root.addView(buildConnectionCard())
        root.addView(space(dp(16)))
        root.addView(buildAnalyzeCard())
        root.addView(space(dp(16)))
        root.addView(buildResultCard())
        root.addView(space(dp(16)))
        root.addView(buildStatsCard())
        root.addView(space(dp(16)))
        root.addView(buildHistoryCard())
        root.addView(space(dp(24)))

        scrollView.addView(root)
        setContentView(scrollView)

        apiClient = FraudApiClient { baseUrlInput.text?.toString().orEmpty() }
        loadSavedBaseUrl()
        refreshConnection()
        refreshHistory()
        refreshStats()
    }

    private fun buildHeroCard(): View {
        val container = card(this)
        container.addView(sectionTitle("AI SMS Fraud Detector"))
        container.addView(sectionSubtitle("A fast, mobile-first client for real SMS screening, history, and live backend analysis."))
        return container
    }

    private fun buildConnectionCard(): View {
        val container = card(this)
        container.addView(sectionTitle("Backend connection"))

        baseUrlInput = textInput("Backend base URL")
        baseUrlInput.setText(prefs.getString(KEY_BASE_URL, BuildConfig.DEFAULT_BASE_URL))
        container.addView(baseUrlInput)

        connectionStatus = sectionSubtitle("Not connected yet")
        container.addView(space(dp(10)))
        container.addView(connectionStatus)

        val row = horizontalRow()
        val saveButton = actionButton("Save URL")
        val testButton = actionButton("Test")

        saveButton.setOnClickListener {
            persistBaseUrl()
            Toast.makeText(this, "Backend URL saved", Toast.LENGTH_SHORT).show()
        }
        testButton.setOnClickListener { refreshConnection() }

        row.addView(saveButton)
        row.addView(space(dp(12), horizontal = true))
        row.addView(testButton)
        container.addView(space(dp(12)))
        container.addView(row)
        return container
    }

    private fun buildAnalyzeCard(): View {
        val container = card(this)
        container.addView(sectionTitle("Analyze SMS"))

        messageInput = textInput("Paste an SMS message here")
        messageInput.minLines = 4
        messageInput.maxLines = 6
        messageInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        container.addView(messageInput)

        analysisStatus = sectionSubtitle("Ready to analyze")
        container.addView(space(dp(10)))
        container.addView(analysisStatus)

        val row = horizontalRow()
        val analyzeButton = actionButton("Analyze")
        val clearButton = actionButton("Clear")

        analyzeButton.setOnClickListener { analyzeMessage() }
        clearButton.setOnClickListener {
            messageInput.text?.clear()
            showAnalysisReady()
        }

        row.addView(analyzeButton)
        row.addView(space(dp(12), horizontal = true))
        row.addView(clearButton)
        container.addView(space(dp(12)))
        container.addView(row)
        return container
    }

    private fun buildResultCard(): View {
        val container = card(this)
        container.addView(sectionTitle("Latest result"))

        resultLabel = resultText("No analysis yet")
        resultConfidence = valueText("Confidence: -")
        resultExplanation = sectionSubtitle("Run an analysis to see the model verdict and explanation here.")

        container.addView(resultLabel)
        container.addView(space(dp(8)))
        container.addView(resultConfidence)
        container.addView(space(dp(8)))
        container.addView(resultExplanation)
        return container
    }

    private fun buildStatsCard(): View {
        val container = card(this)
        container.addView(sectionTitle("Dashboard stats"))
        statsSummary = sectionSubtitle("No stats loaded yet.")
        container.addView(statsSummary)

        val refreshButton = actionButton("Refresh stats")
        refreshButton.setOnClickListener { refreshStats() }
        container.addView(space(dp(12)))
        container.addView(refreshButton)
        return container
    }

    private fun buildHistoryCard(): View {
        val container = card(this)
        container.addView(sectionTitle("Prediction history"))

        val refreshButton = actionButton("Reload history")
        refreshButton.setOnClickListener { refreshHistory() }
        container.addView(refreshButton)
        container.addView(space(dp(12)))

        historyContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(historyContainer)
        return container
    }

    private fun refreshConnection() {
        setLoadingState("Checking backend connection...", retry = this::refreshConnection)
        runBackground(
            onStart = {},
            work = { apiClient.ping() },
            onSuccess = { message ->
                connectionStatus.text = "Connected: $message"
                connectionStatus.setTextColor(themeColor(R.color.sms_primary))
                hideError()
                showAnalysisReady()
            },
            onError = { error ->
                showError("Backend connection failed: ${error.message ?: "Unknown error"}", retry = this::refreshConnection)
                connectionStatus.text = "Connection failed"
                connectionStatus.setTextColor(themeColor(R.color.sms_error))
            },
        )
    }

    private fun analyzeMessage() {
        val message = messageInput.text?.toString().orEmpty().trim()
        if (message.isBlank()) {
            showError("Please enter an SMS message to analyze.", retry = null)
            return
        }

        persistBaseUrl()
        setLoadingState("Analyzing message...", retry = this::analyzeMessage)
        runBackground(
            onStart = {},
            work = { apiClient.analyze(message) },
            onSuccess = { result ->
                hideError()
                updateResult(result)
                analysisStatus.text = "Analysis complete"
                analysisStatus.setTextColor(themeColor(R.color.sms_primary))
                refreshHistory()
                refreshStats()
            },
            onError = { error ->
                showError("Analysis failed: ${error.message ?: "Unknown error"}", retry = this::analyzeMessage)
                analysisStatus.text = "Analysis failed"
                analysisStatus.setTextColor(themeColor(R.color.sms_error))
            },
        )
    }

    private fun refreshHistory() {
        setLoadingState("Loading recent history...", retry = this::refreshHistory)
        runBackground(
            onStart = {},
            work = { apiClient.history() },
            onSuccess = { items ->
                hideError()
                renderHistory(items)
            },
            onError = { error ->
                showError("History unavailable: ${error.message ?: "Unknown error"}", retry = this::refreshHistory)
                renderHistory(emptyList())
            },
        )
    }

    private fun refreshStats() {
        runBackground(
            onStart = {},
            work = { apiClient.stats() },
            onSuccess = { stats ->
                statsSummary.text = if (stats == null) {
                    "The backend did not return dashboard totals yet."
                } else {
                    "Total: ${stats.totalAnalyses}   Legitimate: ${stats.legitimateCount}   Spam: ${stats.spamCount}   Fraud: ${stats.fraudCount}"
                }
            },
            onError = {
                statsSummary.text = "Stats unavailable right now."
            },
        )
    }

    private fun loadSavedBaseUrl() {
        val baseUrl = prefs.getString(KEY_BASE_URL, BuildConfig.DEFAULT_BASE_URL).orEmpty()
        baseUrlInput.setText(baseUrl)
    }

    private fun persistBaseUrl() {
        prefs.edit().putString(KEY_BASE_URL, baseUrlInput.text?.toString().orEmpty()).apply()
    }

    private fun updateResult(result: AnalysisResult) {
        val verdict = if (result.isFraud) "Fraud or spam detected" else "Likely legitimate"
        resultLabel.text = verdict
        resultLabel.setTextColor(if (result.isFraud) themeColor(R.color.sms_error) else themeColor(R.color.sms_primary))
        resultConfidence.text = "Confidence: ${formatPercent(result.confidence)}"
        resultExplanation.text = result.explanation.ifBlank { "The backend returned no explanation." }
    }

    private fun renderHistory(items: List<HistoryItem>) {
        historyContainer.removeAllViews()
        if (items.isEmpty()) {
            historyContainer.addView(sectionSubtitle("No history available yet. Once the backend returns records, they will appear here."))
            return
        }

        items.take(8).forEach { item ->
            val itemCard = card(this)
            itemCard.setPadding(dp(16), dp(16), dp(16), dp(16))

            val message = sectionText(item.message.ifBlank { "(empty message)" })
            val label = sectionSubtitle("Label: ${item.label.ifBlank { "Unknown" }}")
            val confidence = sectionSubtitle("Confidence: ${formatPercent(item.confidence)}")
            val createdAt = sectionSubtitle(item.createdAt.ifBlank { "Unknown time" })

            itemCard.addView(message)
            itemCard.addView(space(dp(6)))
            itemCard.addView(label)
            itemCard.addView(confidence)
            itemCard.addView(createdAt)
            historyContainer.addView(itemCard)
            historyContainer.addView(space(dp(12)))
        }
    }

    private fun setLoadingState(message: String, retry: (() -> Unit)?) {
        analysisStatus.text = message
        analysisStatus.setTextColor(themeColor(R.color.sms_secondary))
        lastRetryAction = retry
        retryButton.isEnabled = retry != null
        if (retry != null) {
            errorPanel.visibility = View.VISIBLE
            errorText.text = message
        }
    }

    private fun showAnalysisReady() {
        analysisStatus.text = "Ready to analyze"
        analysisStatus.setTextColor(themeColor(R.color.sms_text_muted))
    }

    private fun showError(message: String, retry: (() -> Unit)?) {
        errorPanel.visibility = View.VISIBLE
        errorText.text = message
        lastRetryAction = retry
        retryButton.isEnabled = retry != null
        if (retry != null) {
            retryButton.text = "Retry"
        }
    }

    private fun hideError() {
        errorPanel.visibility = View.GONE
        lastRetryAction = null
        retryButton.isEnabled = false
    }

    private fun <T> runBackground(
        onStart: () -> Unit,
        work: () -> T,
        onSuccess: (T) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        onStart()
        Thread {
            try {
                val result = work()
                mainHandler.post { onSuccess(result) }
            } catch (throwable: Throwable) {
                mainHandler.post { onError(throwable) }
            }
        }.start()
    }

    private fun card(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = gradientCard()
            elevation = dp(2).toFloat()
        }
    }

    private fun gradientCard(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                themeColor(R.color.sms_surface),
                themeColor(R.color.sms_surface_alt),
            ),
        ).apply {
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1), themeColor(R.color.sms_surface_alt))
        }
    }

    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(themeColor(R.color.sms_text))
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun sectionSubtitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(themeColor(R.color.sms_text_muted))
            textSize = 14f
        }
    }

    private fun sectionText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(themeColor(R.color.sms_text))
            textSize = 16f
        }
    }

    private fun resultText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(themeColor(R.color.sms_text))
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun valueText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(themeColor(R.color.sms_secondary))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun textInput(hint: String): EditText {
        return EditText(this).apply {
            this.hint = hint
            setTextColor(themeColor(R.color.sms_text))
            setHintTextColor(themeColor(R.color.sms_text_muted))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = inputBackground()
            setSingleLine(false)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
    }

    private fun actionButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(themeColor(R.color.sms_bg))
            background = buttonBackground()
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
    }

    private fun inputBackground(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(themeColor(R.color.sms_bg))
            setStroke(dp(1), themeColor(R.color.sms_surface_alt))
        }
    }

    private fun buttonBackground(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(themeColor(R.color.sms_primary))
        }
    }

    private fun horizontalRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun space(size: Int, horizontal: Boolean = false): View {
        return Space(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                if (horizontal) size else ViewGroup.LayoutParams.MATCH_PARENT,
                if (horizontal) ViewGroup.LayoutParams.MATCH_PARENT else size,
            )
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun themeColor(colorRes: Int): Int = resources.getColor(colorRes, theme)

    private fun formatPercent(value: Double): String {
        val clamped = when {
            value > 1.0 -> value
            value < 0.0 -> 0.0
            else -> value * 100.0
        }
        return "${clamped.toInt()}%"
    }

    companion object {
        private const val PREFS_NAME = "sms_fraud_detector_prefs"
        private const val KEY_BASE_URL = "base_url"
    }
}
