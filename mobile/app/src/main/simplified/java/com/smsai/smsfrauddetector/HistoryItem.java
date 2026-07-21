package com.smsai.smsfrauddetector;

public class HistoryItem {
    public final String message;
    public final String label;
    public final double confidence;
    public final String createdAt;

    public HistoryItem(String message, String label, double confidence, String createdAt) {
        this.message = message;
        this.label = label;
        this.confidence = confidence;
        this.createdAt = createdAt;
    }
}
