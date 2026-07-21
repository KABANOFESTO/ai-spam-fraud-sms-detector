package com.smsai.smsfrauddetector;

public class DashboardStats {
    public final int totalAnalyses;
    public final int legitimateCount;
    public final int spamCount;
    public final int fraudCount;

    public DashboardStats(int totalAnalyses, int legitimateCount, int spamCount, int fraudCount) {
        this.totalAnalyses = totalAnalyses;
        this.legitimateCount = legitimateCount;
        this.spamCount = spamCount;
        this.fraudCount = fraudCount;
    }
}
