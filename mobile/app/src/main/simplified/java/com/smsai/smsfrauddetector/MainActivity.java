package com.smsai.smsfrauddetector;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "sms_fraud_detector_prefs";
    private static final String KEY_BASE_URL = "base_url";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private FraudApiClient apiClient;
    private EditText baseUrlInput;
    private EditText messageInput;
    private TextView connectionStatus;
    private TextView analysisStatus;
    private TextView resultLabel;
    private TextView resultConfidence;
    private TextView resultExplanation;
    private TextView statsSummary;
    private LinearLayout historyContainer;
    private LinearLayout errorPanel;
    private TextView errorText;
    private Button retryButton;
    private Runnable lastRetryAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(themeColor(R.color.sms_bg));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));

        errorPanel = card(this);
        errorPanel.setVisibility(View.GONE);
        errorText = sectionText("Something went wrong");
        retryButton = actionButton("Retry");
        retryButton.setOnClickListener(v -> {
            if (lastRetryAction != null) {
                lastRetryAction.run();
            }
        });
        errorPanel.addView(errorText);
        errorPanel.addView(space(dp(10), false));
        errorPanel.addView(retryButton);

        root.addView(errorPanel);
        root.addView(space(dp(16), false));
        root.addView(buildHeroCard());
        root.addView(space(dp(16), false));
        root.addView(buildConnectionCard());
        root.addView(space(dp(16), false));
        root.addView(buildAnalyzeCard());
        root.addView(space(dp(16), false));
        root.addView(buildResultCard());
        root.addView(space(dp(16), false));
        root.addView(buildStatsCard());
        root.addView(space(dp(16), false));
        root.addView(buildHistoryCard());
        root.addView(space(dp(24), false));

        scrollView.addView(root);
        setContentView(scrollView);

        apiClient = new FraudApiClient(() -> baseUrlInput.getText().toString());
        loadSavedBaseUrl();
        refreshConnection();
        refreshHistory();
        refreshStats();
    }

    private View buildHeroCard() {
        LinearLayout container = card(this);
        container.addView(sectionTitle("AI SMS Fraud Detector"));
        container.addView(sectionSubtitle("A fast, mobile-first client for real SMS screening, history, and live backend analysis."));
        return container;
    }

    private View buildConnectionCard() {
        LinearLayout container = card(this);
        container.addView(sectionTitle("Backend connection"));

        baseUrlInput = textInput("Backend base URL");
        baseUrlInput.setText(prefs.getString(KEY_BASE_URL, BuildConfig.DEFAULT_BASE_URL));
        container.addView(baseUrlInput);

        connectionStatus = sectionSubtitle("Not connected yet");
        container.addView(space(dp(10), false));
        container.addView(connectionStatus);

        LinearLayout row = horizontalRow();
        Button saveButton = actionButton("Save URL");
        Button testButton = actionButton("Test");

        saveButton.setOnClickListener(v -> {
            persistBaseUrl();
            Toast.makeText(this, "Backend URL saved", Toast.LENGTH_SHORT).show();
        });
        testButton.setOnClickListener(v -> refreshConnection());

        row.addView(saveButton);
        row.addView(space(dp(12), true));
        row.addView(testButton);
        container.addView(space(dp(12), false));
        container.addView(row);
        return container;
    }

    private View buildAnalyzeCard() {
        LinearLayout container = card(this);
        container.addView(sectionTitle("Analyze SMS"));

        messageInput = textInput("Paste an SMS message here");
        messageInput.setMinLines(4);
        messageInput.setMaxLines(6);
        messageInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        container.addView(messageInput);

        analysisStatus = sectionSubtitle("Ready to analyze");
        container.addView(space(dp(10), false));
        container.addView(analysisStatus);

        LinearLayout row = horizontalRow();
        Button analyzeButton = actionButton("Analyze");
        Button clearButton = actionButton("Clear");

        analyzeButton.setOnClickListener(v -> analyzeMessage());
        clearButton.setOnClickListener(v -> {
            messageInput.getText().clear();
            showAnalysisReady();
        });

        row.addView(analyzeButton);
        row.addView(space(dp(12), true));
        row.addView(clearButton);
        container.addView(space(dp(12), false));
        container.addView(row);
        return container;
    }

    private View buildResultCard() {
        LinearLayout container = card(this);
        container.addView(sectionTitle("Latest result"));

        resultLabel = resultText("No analysis yet");
        resultConfidence = valueText("Confidence: -");
        resultExplanation = sectionSubtitle("Run an analysis to see the model verdict and explanation here.");

        container.addView(resultLabel);
        container.addView(space(dp(8), false));
        container.addView(resultConfidence);
        container.addView(space(dp(8), false));
        container.addView(resultExplanation);
        return container;
    }

    private View buildStatsCard() {
        LinearLayout container = card(this);
        container.addView(sectionTitle("Dashboard stats"));
        statsSummary = sectionSubtitle("No stats loaded yet.");
        container.addView(statsSummary);

        Button refreshButton = actionButton("Refresh stats");
        refreshButton.setOnClickListener(v -> refreshStats());
        container.addView(space(dp(12), false));
        container.addView(refreshButton);
        return container;
    }

    private View buildHistoryCard() {
        LinearLayout container = card(this);
        container.addView(sectionTitle("Prediction history"));

        Button refreshButton = actionButton("Reload history");
        refreshButton.setOnClickListener(v -> refreshHistory());
        container.addView(refreshButton);
        container.addView(space(dp(12), false));

        historyContainer = new LinearLayout(this);
        historyContainer.setOrientation(LinearLayout.VERTICAL);
        container.addView(historyContainer);
        return container;
    }

    private void refreshConnection() {
        setLoadingState("Checking backend connection...", this::refreshConnection);
        runBackground(
            () -> apiClient.ping(),
            message -> {
                connectionStatus.setText("Connected: " + message);
                connectionStatus.setTextColor(themeColor(R.color.sms_primary));
                hideError();
                showAnalysisReady();
            },
            error -> {
                showError("Backend connection failed: " + safeMessage(error), this::refreshConnection);
                connectionStatus.setText("Connection failed");
                connectionStatus.setTextColor(themeColor(R.color.sms_error));
            }
        );
    }

    private void analyzeMessage() {
        String message = messageInput.getText().toString().trim();
        if (message.isEmpty()) {
            showError("Please enter an SMS message to analyze.", null);
            return;
        }

        persistBaseUrl();
        setLoadingState("Analyzing message...", this::analyzeMessage);
        runBackground(
            () -> apiClient.analyze(message),
            result -> {
                hideError();
                updateResult(result);
                analysisStatus.setText("Analysis complete");
                analysisStatus.setTextColor(themeColor(R.color.sms_primary));
                refreshHistory();
                refreshStats();
            },
            error -> {
                showError("Analysis failed: " + safeMessage(error), this::analyzeMessage);
                analysisStatus.setText("Analysis failed");
                analysisStatus.setTextColor(themeColor(R.color.sms_error));
            }
        );
    }

    private void refreshHistory() {
        setLoadingState("Loading recent history...", this::refreshHistory);
        runBackground(
            () -> apiClient.history(),
            items -> {
                hideError();
                renderHistory(items);
            },
            error -> {
                showError("History unavailable: " + safeMessage(error), this::refreshHistory);
                renderHistory(java.util.Collections.emptyList());
            }
        );
    }

    private void refreshStats() {
        runBackground(
            () -> apiClient.stats(),
            stats -> {
                if (stats == null) {
                    statsSummary.setText("The backend did not return dashboard totals yet.");
                } else {
                    statsSummary.setText(
                        "Total: " + stats.totalAnalyses +
                            "   Legitimate: " + stats.legitimateCount +
                            "   Spam: " + stats.spamCount +
                            "   Fraud: " + stats.fraudCount
                    );
                }
            },
            error -> statsSummary.setText("Stats unavailable right now.")
        );
    }

    private void loadSavedBaseUrl() {
        String baseUrl = prefs.getString(KEY_BASE_URL, BuildConfig.DEFAULT_BASE_URL);
        baseUrlInput.setText(baseUrl);
    }

    private void persistBaseUrl() {
        prefs.edit().putString(KEY_BASE_URL, baseUrlInput.getText().toString()).apply();
    }

    private void updateResult(AnalysisResult result) {
        String verdict = result.fraud ? "Fraud or spam detected" : "Likely legitimate";
        resultLabel.setText(verdict);
        resultLabel.setTextColor(result.fraud ? themeColor(R.color.sms_error) : themeColor(R.color.sms_primary));
        resultConfidence.setText("Confidence: " + formatPercent(result.confidence));
        resultExplanation.setText(
            result.explanation == null || result.explanation.trim().isEmpty()
                ? "The backend returned no explanation."
                : result.explanation
        );
    }

    private void renderHistory(List<HistoryItem> items) {
        historyContainer.removeAllViews();
        if (items == null || items.isEmpty()) {
            historyContainer.addView(sectionSubtitle("No history available yet. Once the backend returns records, they will appear here."));
            return;
        }

        int count = Math.min(items.size(), 8);
        for (int index = 0; index < count; index++) {
            HistoryItem item = items.get(index);
            LinearLayout itemCard = card(this);
            itemCard.setPadding(dp(16), dp(16), dp(16), dp(16));

            TextView message = sectionText(item.message == null || item.message.trim().isEmpty() ? "(empty message)" : item.message);
            TextView label = sectionSubtitle("Label: " + safeText(item.label, "Unknown"));
            TextView confidence = sectionSubtitle("Confidence: " + formatPercent(item.confidence));
            TextView createdAt = sectionSubtitle(safeText(item.createdAt, "Unknown time"));

            itemCard.addView(message);
            itemCard.addView(space(dp(6), false));
            itemCard.addView(label);
            itemCard.addView(confidence);
            itemCard.addView(createdAt);
            historyContainer.addView(itemCard);
            historyContainer.addView(space(dp(12), false));
        }
    }

    private void setLoadingState(String message, Runnable retry) {
        analysisStatus.setText(message);
        analysisStatus.setTextColor(themeColor(R.color.sms_secondary));
        lastRetryAction = retry;
        retryButton.setEnabled(retry != null);
        if (retry != null) {
            retryButton.setText("Retry");
        }
    }

    private void showAnalysisReady() {
        analysisStatus.setText("Ready to analyze");
        analysisStatus.setTextColor(themeColor(R.color.sms_text_muted));
    }

    private void showError(String message, Runnable retry) {
        errorPanel.setVisibility(View.VISIBLE);
        errorText.setText(message);
        lastRetryAction = retry;
        retryButton.setEnabled(retry != null);
        retryButton.setText(retry == null ? "Retry" : "Retry");
    }

    private void hideError() {
        errorPanel.setVisibility(View.GONE);
        lastRetryAction = null;
        retryButton.setEnabled(false);
    }

    private <T> void runBackground(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Thread thread = new Thread(() -> {
            try {
                T result = work.call();
                mainHandler.post(() -> onSuccess.accept(result));
            } catch (Throwable throwable) {
                mainHandler.post(() -> onError.accept(throwable));
            }
        });
        thread.start();
    }

    private LinearLayout card(Context context) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(18), dp(18), dp(18), dp(18));
        container.setBackground(gradientCard());
        container.setElevation(dp(2));
        return container;
    }

    private GradientDrawable gradientCard() {
        return new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{themeColor(R.color.sms_surface), themeColor(R.color.sms_surface_alt)}
        ) {{
            setCornerRadius(dp(22));
            setStroke(dp(1), themeColor(R.color.sms_surface_alt));
        }};
    }

    private TextView sectionTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(themeColor(R.color.sms_text));
        view.setTextSize(22f);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView sectionSubtitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(themeColor(R.color.sms_text_muted));
        view.setTextSize(14f);
        return view;
    }

    private TextView sectionText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(themeColor(R.color.sms_text));
        view.setTextSize(16f);
        return view;
    }

    private TextView resultText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(themeColor(R.color.sms_text));
        view.setTextSize(20f);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView valueText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(themeColor(R.color.sms_secondary));
        view.setTextSize(15f);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private EditText textInput(String hint) {
        EditText view = new EditText(this);
        view.setHint(hint);
        view.setTextColor(themeColor(R.color.sms_text));
        view.setHintTextColor(themeColor(R.color.sms_text_muted));
        view.setPadding(dp(14), dp(14), dp(14), dp(14));
        view.setBackground(inputBackground());
        view.setSingleLine(false);
        view.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return view;
    }

    private Button actionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(themeColor(R.color.sms_bg));
        button.setBackground(buttonBackground());
        button.setPadding(dp(18), dp(12), dp(18), dp(12));
        return button;
    }

    private GradientDrawable inputBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(16));
        drawable.setColor(themeColor(R.color.sms_bg));
        drawable.setStroke(dp(1), themeColor(R.color.sms_surface_alt));
        return drawable;
    }

    private GradientDrawable buttonBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(16));
        drawable.setColor(themeColor(R.color.sms_primary));
        return drawable;
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private View space(int size, boolean horizontal) {
        Space space = new Space(this);
        space.setLayoutParams(
            horizontal
                ? new LinearLayout.LayoutParams(size, 1)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, size)
        );
        return space;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private int themeColor(int colorRes) {
        return getResources().getColor(colorRes, getTheme());
    }

    private String formatPercent(double value) {
        double clamped = value > 1.0 ? value : Math.max(0.0, value) * 100.0;
        return (int) clamped + "%";
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? "" : throwable.getMessage();
        return message == null || message.trim().isEmpty() ? "Unknown error" : message;
    }

    private String safeText(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
