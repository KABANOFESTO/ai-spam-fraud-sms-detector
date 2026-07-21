package com.smsai.smsfrauddetector.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UserDto(
    val id: Int,
    val username: String,
    val email: String,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null,
    val role: String,
    val status: String,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("profile_picture") val profilePicture: String? = null,
    @SerializedName("profile_picture_url") val profilePictureUrl: String? = null,
    @SerializedName("date_joined") val dateJoined: String? = null,
)

data class AuthResponseDto(
    val refresh: String,
    val access: String,
    val user: UserDto,
)

data class LoginRequestDto(
    val email: String,
    val password: String,
)

data class RegisterRequestDto(
    val username: String,
    val email: String,
    val password: String,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null,
)

data class LogoutRequestDto(
    val refresh: String,
)

data class ProfileUpdateResponseDto(
    val username: String? = null,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null,
    @SerializedName("profile_picture_url") val profilePictureUrl: String? = null,
)

data class AnalyzeRequestDto(
    val message: String,
)

data class AnalysisResultDto(
    val id: Int,
    val message: String,
    @SerializedName("normalized_message") val normalizedMessage: String,
    val prediction: String,
    val confidence: Double,
    @SerializedName("risk_score") val riskScore: Int,
    @SerializedName("is_suspicious") val isSuspicious: Boolean,
    @SerializedName("matched_signals") val matchedSignals: List<String> = emptyList(),
    @SerializedName("model_name") val modelName: String,
    @SerializedName("model_version") val modelVersion: String,
    val explanation: String? = null,
    @SerializedName("processing_time_ms") val processingTimeMs: Int = 0,
    @SerializedName("analyzed_at") val analyzedAt: String? = null,
)

data class HistoryResponseDto(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<AnalysisResultDto> = emptyList(),
)

data class PaginatedResponse<T>(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<T> = emptyList(),
)

data class StatsDto(
    @SerializedName("total_analyses") val totalAnalyses: Int,
    @SerializedName("legitimate_count") val legitimateCount: Int,
    @SerializedName("spam_count") val spamCount: Int,
    @SerializedName("fraud_count") val fraudCount: Int,
    @SerializedName("suspicious_count") val suspiciousCount: Int,
    @SerializedName("suspicious_rate") val suspiciousRate: Double,
    @SerializedName("average_confidence") val averageConfidence: Double,
)

data class DashboardTotalsDto(
    @SerializedName("total_analyses") val totalAnalyses: Int,
    @SerializedName("suspicious_count") val suspiciousCount: Int,
    @SerializedName("legitimate_count") val legitimateCount: Int,
    @SerializedName("spam_count") val spamCount: Int,
    @SerializedName("fraud_count") val fraudCount: Int,
)

data class DashboardTrendDto(
    val day: String? = null,
    val total: Int = 0,
    val legitimate: Int = 0,
    val spam: Int = 0,
    val fraud: Int = 0,
)

data class ActiveModelDto(
    val id: Int,
    @SerializedName("model_name") val modelName: String,
    val version: String,
    val accuracy: Double,
    val precision: Double,
    val recall: Double,
    @SerializedName("f1_score") val f1Score: Double,
    @SerializedName("trained_at") val trainedAt: String? = null,
    @SerializedName("artifact_path") val artifactPath: String? = null,
)

data class DashboardResponseDto(
    val totals: DashboardTotalsDto,
    @SerializedName("recent_trend") val recentTrend: List<DashboardTrendDto> = emptyList(),
    @SerializedName("top_users") val topUsers: List<Map<String, Any>> = emptyList(),
    @SerializedName("active_model") val activeModel: ActiveModelDto? = null,
)

data class ReportRequestDto(
    @SerializedName("sms_message") val smsMessage: String,
    val notes: String = "",
    @SerializedName("analysis_id") val analysisId: Int? = null,
)

data class FraudReportDto(
    val id: Int,
    val user: String? = null,
    val analysis: AnalysisResultDto? = null,
    @SerializedName("sms_message") val smsMessage: String,
    val notes: String = "",
    @SerializedName("admin_notes") val adminNotes: String = "",
    val status: String,
    @SerializedName("reviewed_by") val reviewedBy: String? = null,
    @SerializedName("reviewed_at") val reviewedAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

data class DatasetDto(
    val id: Int,
    @SerializedName("original_filename") val originalFilename: String,
    @SerializedName("stored_file") val storedFile: String,
    @SerializedName("row_count") val rowCount: Int,
    @SerializedName("label_distribution") val labelDistribution: Map<String, Int> = emptyMap(),
    val notes: String = "",
    @SerializedName("imported_by") val importedBy: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class EvaluationReportDto(
    @SerializedName("model_name") val modelName: String,
    val version: String,
    val accuracy: Double,
    val precision: Double,
    val recall: Double,
    @SerializedName("f1_score") val f1Score: Double,
    @SerializedName("training_samples") val trainingSamples: Int,
    @SerializedName("test_samples") val testSamples: Int,
    @SerializedName("confusion_matrix") val confusionMatrix: List<List<Int>>,
    val labels: List<String>,
    @SerializedName("classification_report") val classificationReport: Map<String, Any>,
)

data class ModelDto(
    val id: Int,
    @SerializedName("model_name") val modelName: String,
    val version: String,
    @SerializedName("artifact_path") val artifactPath: String? = null,
    @SerializedName("training_data_path") val trainingDataPath: String? = null,
    @SerializedName("training_samples") val trainingSamples: Int,
    @SerializedName("test_samples") val testSamples: Int,
    @SerializedName("evaluation_report") val evaluationReport: EvaluationReportDto? = null,
    val accuracy: Double,
    val precision: Double,
    val recall: Double,
    @SerializedName("f1_score") val f1Score: Double,
    @SerializedName("trained_at") val trainedAt: String? = null,
    @SerializedName("is_active") val isActive: Boolean,
)

data class HealthDto(
    val status: String,
    val service: String,
    @SerializedName("model_ready") val modelReady: Boolean,
    @SerializedName("active_model") val activeModel: ActiveModelDto? = null,
)
