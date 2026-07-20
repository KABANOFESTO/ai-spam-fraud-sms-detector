from rest_framework import serializers

from analysis.serializers import SMSAnalysisSerializer

from .models import FraudReport


class FraudReportSerializer(serializers.ModelSerializer):
    analysis = SMSAnalysisSerializer(read_only=True)
    user = serializers.StringRelatedField(read_only=True)
    reviewed_by = serializers.StringRelatedField(read_only=True)

    class Meta:
        model = FraudReport
        fields = (
            "id",
            "user",
            "analysis",
            "sms_message",
            "notes",
            "admin_notes",
            "status",
            "reviewed_by",
            "reviewed_at",
            "created_at",
            "updated_at",
        )
        read_only_fields = (
            "id",
            "user",
            "admin_notes",
            "status",
            "reviewed_by",
            "reviewed_at",
            "created_at",
            "updated_at",
        )


class FraudReportCreateSerializer(serializers.ModelSerializer):
    analysis_id = serializers.IntegerField(required=False, allow_null=True)

    class Meta:
        model = FraudReport
        fields = ("sms_message", "notes", "analysis_id")

    def validate_analysis_id(self, value):
        if value is None:
            return value
        from analysis.models import SMSAnalysis

        request = self.context["request"]
        queryset = SMSAnalysis.objects.all()
        if getattr(request.user, "role", None) != "Admin":
            queryset = queryset.filter(user=request.user)
        if not queryset.filter(id=value).exists():
            raise serializers.ValidationError("Analysis not found.")
        return value

    def create(self, validated_data):
        analysis_id = validated_data.pop("analysis_id", None)
        analysis = None
        if analysis_id:
            from analysis.models import SMSAnalysis

            analysis = SMSAnalysis.objects.get(id=analysis_id)

        return FraudReport.objects.create(
            user=self.context["request"].user,
            analysis=analysis,
            **validated_data,
        )


class FraudReportReviewSerializer(serializers.ModelSerializer):
    class Meta:
        model = FraudReport
        fields = ("status", "admin_notes")

    def validate_status(self, value):
        allowed = {"PENDING", "REVIEWING", "REVIEWED", "RESOLVED", "REJECTED"}
        if value not in allowed:
            raise serializers.ValidationError("Invalid status.")
        return value
