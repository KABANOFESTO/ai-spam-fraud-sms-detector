package com.smsai.smsfrauddetector

data class AnalysisResult(
    val label: String,
    val confidence: Double,
    val explanation: String,
    val isFraud: Boolean,
    val rawJson: String,
)

data class HistoryItem(
    val message: String,
    val label: String,
    val confidence: Double,
    val createdAt: String,
)

data class DashboardStats(
    val totalAnalyses: Int,
    val legitimateCount: Int,
    val spamCount: Int,
    val fraudCount: Int,
)
