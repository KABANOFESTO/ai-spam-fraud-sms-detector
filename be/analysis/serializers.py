from rest_framework import serializers

from .models import SMSAnalysis


class AnalyzeRequestSerializer(serializers.Serializer):
    message = serializers.CharField(max_length=5000, allow_blank=False, trim_whitespace=True)


class SMSAnalysisSerializer(serializers.ModelSerializer):
    class Meta:
        model = SMSAnalysis
        fields = (
            "id",
            "message",
            "normalized_message",
            "prediction",
            "confidence",
            "risk_score",
            "is_suspicious",
            "matched_signals",
            "model_name",
            "model_version",
            "explanation",
            "processing_time_ms",
            "analyzed_at",
        )
        read_only_fields = fields


class BulkAnalyzeRequestSerializer(serializers.Serializer):
    messages = serializers.ListField(child=serializers.CharField(max_length=5000), allow_empty=False)


class AnalysisStatsSerializer(serializers.Serializer):
    total_analyses = serializers.IntegerField()
    legitimate_count = serializers.IntegerField()
    spam_count = serializers.IntegerField()
    fraud_count = serializers.IntegerField()
    suspicious_count = serializers.IntegerField()
    suspicious_rate = serializers.FloatField()
    average_confidence = serializers.FloatField()


class TrendPointSerializer(serializers.Serializer):
    date = serializers.DateField()
    total = serializers.IntegerField()
    legitimate = serializers.IntegerField()
    spam = serializers.IntegerField()
    fraud = serializers.IntegerField()
