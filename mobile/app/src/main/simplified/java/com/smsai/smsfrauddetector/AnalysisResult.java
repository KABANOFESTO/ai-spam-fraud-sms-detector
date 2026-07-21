package com.smsai.smsfrauddetector;

public class AnalysisResult {
    public final String label;
    public final double confidence;
    public final String explanation;
    public final boolean fraud;
    public final String rawJson;

    public AnalysisResult(String label, double confidence, String explanation, boolean fraud, String rawJson) {
        this.label = label;
        this.confidence = confidence;
        this.explanation = explanation;
        this.fraud = fraud;
        this.rawJson = rawJson;
    }
}
