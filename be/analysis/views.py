from pathlib import Path

from datetime import timedelta

from django.conf import settings
from django.db.models import Avg, Count, Q
from django.db.models.functions import TruncDate
from django.utils import timezone
from rest_framework import generics, permissions, status
from rest_framework.pagination import PageNumberPagination
from rest_framework.response import Response
from rest_framework.views import APIView

from audit_log.audit_log_utils import log_action
from authapi.permissions import IsAdmin, IsOwnerOrAdmin
from ml_models.models import MLModel

from .models import SMSAnalysis
from .serializers import (
    AnalysisStatsSerializer,
    AnalyzeRequestSerializer,
    BulkAnalyzeRequestSerializer,
    SMSAnalysisSerializer,
)
from .training_serializers import RetrainDetectorRequestSerializer
from .ml_pipeline import train_and_save_detector
from .services import SmsFraudDetector, clear_detector_cache


detector = SmsFraudDetector()


class AnalysisPagination(PageNumberPagination):
    page_size = 20
    page_size_query_param = "page_size"
    max_page_size = 100


class AnalyzeSMSView(APIView):
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request):
        is_bulk = "messages" in request.data

        if is_bulk:
            serializer = BulkAnalyzeRequestSerializer(data=request.data)
        else:
            serializer = AnalyzeRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        if is_bulk:
            try:
                saved = []
                for message in serializer.validated_data["messages"]:
                    saved.append(self._analyze_and_save(request, message))
            except FileNotFoundError:
                return Response(
                    {
                        "error": "SMS detector model has not been trained yet.",
                        "detail": "Run the train_sms_detector management command first.",
                    },
                    status=status.HTTP_503_SERVICE_UNAVAILABLE,
                )
            return Response(
                {
                    "message": "Batch analysis completed.",
                    "results": [item["analysis"] for item in saved],
                    "summary": self._build_summary([item["analysis"] for item in saved]),
                },
                status=status.HTTP_201_CREATED,
            )

        try:
            result = self._analyze_and_save(request, serializer.validated_data["message"])
        except FileNotFoundError:
            return Response(
                {
                    "error": "SMS detector model has not been trained yet.",
                    "detail": "Run the train_sms_detector management command first.",
                },
                status=status.HTTP_503_SERVICE_UNAVAILABLE,
            )
        return Response(result["analysis"], status=status.HTTP_201_CREATED)

    def _analyze_and_save(self, request, message: str):
        result = detector.detect(message)
        analysis = SMSAnalysis.objects.create(
            user=request.user,
            message=message,
            normalized_message=result.normalized_message,
            prediction=result.prediction,
            confidence=result.confidence,
            risk_score=result.risk_score,
            is_suspicious=result.is_suspicious,
            matched_signals=result.matched_signals,
            model_name=result.model_name,
            model_version=result.model_version,
            explanation=result.explanation,
            processing_time_ms=result.processing_time_ms,
        )
        log_action(
            request,
            "ANALYSIS_CREATE",
            target_user=request.user,
            additional_data={
                "analysis_id": analysis.id,
                "prediction": analysis.prediction,
                "confidence": float(analysis.confidence),
                "risk_score": analysis.risk_score,
                "is_suspicious": analysis.is_suspicious,
            },
        )
        return {
            "analysis": SMSAnalysisSerializer(analysis).data,
            "analysis_id": analysis.id,
        }

    def _build_summary(self, results):
        total = len(results)
        return {
            "total": total,
            "legitimate": sum(1 for item in results if item["prediction"] == "LEGITIMATE"),
            "spam": sum(1 for item in results if item["prediction"] == "SPAM"),
            "fraud": sum(1 for item in results if item["prediction"] == "FRAUD"),
            "suspicious": sum(1 for item in results if item["is_suspicious"]),
        }


class AnalysisHistoryView(generics.ListAPIView):
    serializer_class = SMSAnalysisSerializer
    permission_classes = [permissions.IsAuthenticated]
    pagination_class = AnalysisPagination
    filterset_fields = ["prediction", "is_suspicious", "model_name", "model_version"]
    search_fields = ["message", "normalized_message", "explanation", "matched_signals"]
    ordering_fields = ["analyzed_at", "confidence", "risk_score"]
    ordering = ["-analyzed_at"]

    def get_queryset(self):
        queryset = SMSAnalysis.objects.all().select_related("user")
        if getattr(self.request.user, "role", None) != "Admin":
            queryset = queryset.filter(user=self.request.user)
        return queryset


class AnalysisDetailView(generics.RetrieveAPIView):
    serializer_class = SMSAnalysisSerializer
    permission_classes = [permissions.IsAuthenticated, IsOwnerOrAdmin]

    def get_queryset(self):
        queryset = SMSAnalysis.objects.select_related("user").all()
        if getattr(self.request.user, "role", None) != "Admin":
            queryset = queryset.filter(user=self.request.user)
        return queryset


class AnalysisStatsView(APIView):
    permission_classes = [permissions.IsAuthenticated]

    def get(self, request):
        queryset = SMSAnalysis.objects.all()
        if getattr(request.user, "role", None) != "Admin":
            queryset = queryset.filter(user=request.user)

        aggregate = queryset.aggregate(
            total_analyses=Count("id"),
            legitimate_count=Count("id", filter=Q(prediction="LEGITIMATE")),
            spam_count=Count("id", filter=Q(prediction="SPAM")),
            fraud_count=Count("id", filter=Q(prediction="FRAUD")),
            suspicious_count=Count("id", filter=Q(is_suspicious=True)),
            average_confidence=Avg("confidence"),
        )
        total = aggregate["total_analyses"] or 0
        suspicious_rate = round((aggregate["suspicious_count"] or 0) / total * 100, 2) if total else 0.0

        payload = {
            "total_analyses": total,
            "legitimate_count": aggregate["legitimate_count"] or 0,
            "spam_count": aggregate["spam_count"] or 0,
            "fraud_count": aggregate["fraud_count"] or 0,
            "suspicious_count": aggregate["suspicious_count"] or 0,
            "suspicious_rate": suspicious_rate,
            "average_confidence": round(float(aggregate["average_confidence"] or 0), 2),
        }
        return Response(AnalysisStatsSerializer(payload).data)


class DashboardView(APIView):
    permission_classes = [permissions.IsAuthenticated, IsAdmin]

    def get(self, request):
        today = timezone.localdate()
        last_7_days = today - timedelta(days=6)
        queryset = SMSAnalysis.objects.all()

        totals = queryset.aggregate(
            total_analyses=Count("id"),
            suspicious_count=Count("id", filter=Q(is_suspicious=True)),
            legitimate_count=Count("id", filter=Q(prediction="LEGITIMATE")),
            spam_count=Count("id", filter=Q(prediction="SPAM")),
            fraud_count=Count("id", filter=Q(prediction="FRAUD")),
        )

        recent_trend = (
            queryset.filter(analyzed_at__date__gte=last_7_days)
            .annotate(day=TruncDate("analyzed_at"))
            .values("day")
            .annotate(
                total=Count("id"),
                legitimate=Count("id", filter=Q(prediction="LEGITIMATE")),
                spam=Count("id", filter=Q(prediction="SPAM")),
                fraud=Count("id", filter=Q(prediction="FRAUD")),
            )
            .order_by("day")
        )

        top_users = (
            queryset.values("user__id", "user__username", "user__email")
            .annotate(total=Count("id"))
            .order_by("-total")[:5]
        )

        active_model = MLModel.objects.filter(is_active=True).order_by("-trained_at").first()

        return Response(
            {
                "totals": {
                    "total_analyses": totals["total_analyses"] or 0,
                    "suspicious_count": totals["suspicious_count"] or 0,
                    "legitimate_count": totals["legitimate_count"] or 0,
                    "spam_count": totals["spam_count"] or 0,
                    "fraud_count": totals["fraud_count"] or 0,
                },
                "recent_trend": list(recent_trend),
                "top_users": list(top_users),
                "active_model": None
                if active_model is None
                else {
                    "id": active_model.id,
                    "model_name": active_model.model_name,
                    "version": active_model.version,
                    "accuracy": active_model.accuracy,
                    "precision": active_model.precision,
                    "recall": active_model.recall,
                    "f1_score": active_model.f1_score,
                    "trained_at": active_model.trained_at,
                },
            }
        )


class ActiveModelView(generics.ListAPIView):
    permission_classes = [permissions.IsAuthenticated]
    queryset = MLModel.objects.filter(is_active=True).order_by("-trained_at")

    def list(self, request, *args, **kwargs):
        payload = [
            {
                "id": model.id,
                "model_name": model.model_name,
                "version": model.version,
                "accuracy": model.accuracy,
                "precision": model.precision,
                "recall": model.recall,
                "f1_score": model.f1_score,
                "trained_at": model.trained_at,
                "is_active": model.is_active,
            }
            for model in self.get_queryset()
        ]
        return Response(payload)


class AdminRetrainDetectorView(APIView):
    permission_classes = [permissions.IsAuthenticated, IsAdmin]

    def post(self, request):
        serializer = RetrainDetectorRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        data_path = Path(serializer.validated_data.get("data_path") or settings.SMS_DETECTOR_TRAINING_DATA_PATH)
        artifact_path = Path(serializer.validated_data.get("artifact_path") or settings.SMS_DETECTOR_MODEL_PATH)
        model_name = serializer.validated_data["model_name"]
        version = serializer.validated_data["version"]
        force = serializer.validated_data["force"]

        if not data_path.exists():
            return Response(
                {"error": f"Training data not found at {data_path}"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        if artifact_path.exists() and not force:
            return Response(
                {
                    "error": f"Artifact already exists at {artifact_path}",
                    "detail": "Use force=true to overwrite it.",
                },
                status=status.HTTP_409_CONFLICT,
            )

        try:
            bundle, metrics, saved_path = train_and_save_detector(
                data_path=data_path,
                artifact_path=artifact_path,
                model_name=model_name,
                version=version,
            )
        except Exception as exc:
            return Response(
                {"error": "Retraining failed.", "detail": str(exc)},
                status=status.HTTP_400_BAD_REQUEST,
            )

        model_record, _ = MLModel.objects.update_or_create(
            model_name=model_name,
            version=version,
            defaults={
                "artifact_path": str(saved_path),
                "training_data_path": str(data_path),
                "training_samples": int(metrics["train_size"]),
                "test_samples": int(metrics["test_size"]),
                "accuracy": metrics["accuracy"],
                "precision": metrics["precision"],
                "recall": metrics["recall"],
                "f1_score": metrics["f1_score"],
                "is_active": True,
            },
        )
        MLModel.objects.exclude(pk=model_record.pk).update(is_active=False)

        clear_detector_cache()

        log_action(
            request,
            "MODEL_RETRAIN",
            target_user=request.user,
            additional_data={
                "model_name": model_name,
                "version": version,
                "artifact_path": str(saved_path),
                "training_data_path": str(data_path),
                "metrics": metrics,
            },
        )

        return Response(
            {
                "message": "Detector retrained successfully.",
                "model": {
                    "id": model_record.id,
                    "model_name": model_record.model_name,
                    "version": model_record.version,
                    "artifact_path": model_record.artifact_path,
                    "training_data_path": model_record.training_data_path,
                    "training_samples": model_record.training_samples,
                    "test_samples": model_record.test_samples,
                    "accuracy": model_record.accuracy,
                    "precision": model_record.precision,
                    "recall": model_record.recall,
                    "f1_score": model_record.f1_score,
                    "is_active": model_record.is_active,
                },
                "metrics": metrics,
            },
            status=status.HTTP_200_OK,
        )
