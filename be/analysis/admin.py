from django.contrib import admin

from .models import SMSAnalysis


@admin.register(SMSAnalysis)
class SMSAnalysisAdmin(admin.ModelAdmin):
    list_display = (
        "id",
        "user",
        "prediction",
        "confidence",
        "risk_score",
        "is_suspicious",
        "model_name",
        "analyzed_at",
    )
    list_filter = ("prediction", "is_suspicious", "model_name", "model_version", "analyzed_at")
    search_fields = ("message", "normalized_message", "explanation", "user__email", "user__username")
    readonly_fields = ("normalized_message", "matched_signals", "explanation", "processing_time_ms", "analyzed_at")
