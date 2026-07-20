from django.utils import timezone
from rest_framework import generics, permissions, status
from rest_framework.response import Response
from rest_framework.views import APIView

from audit_log.audit_log_utils import log_action
from authapi.permissions import IsAdmin, IsOwnerOrAdmin

from .models import FraudReport
from .serializers import (
    FraudReportCreateSerializer,
    FraudReportReviewSerializer,
    FraudReportSerializer,
)


class FraudReportListCreateView(generics.ListCreateAPIView):
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        queryset = FraudReport.objects.select_related("user", "analysis", "reviewed_by").all()
        if getattr(self.request.user, "role", None) != "Admin":
            queryset = queryset.filter(user=self.request.user)
        return queryset.order_by("-created_at")

    def get_serializer_class(self):
        if self.request.method == "POST":
            return FraudReportCreateSerializer
        return FraudReportSerializer

    def perform_create(self, serializer):
        report = serializer.save()
        log_action(
            self.request,
            "FRAUD_REPORT_CREATE",
            target_user=self.request.user,
            additional_data={"report_id": report.id, "status": report.status},
        )

    def create(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        report = serializer.save()
        response_serializer = FraudReportSerializer(report, context={"request": request})
        log_action(
            request,
            "FRAUD_REPORT_CREATE",
            target_user=request.user,
            additional_data={"report_id": report.id, "status": report.status},
        )
        return Response(response_serializer.data, status=status.HTTP_201_CREATED)


class FraudReportDetailView(generics.RetrieveUpdateDestroyAPIView):
    permission_classes = [permissions.IsAuthenticated, IsOwnerOrAdmin]

    def get_queryset(self):
        queryset = FraudReport.objects.select_related("user", "analysis", "reviewed_by").all()
        if getattr(self.request.user, "role", None) != "Admin":
            queryset = queryset.filter(user=self.request.user)
        return queryset

    def get_serializer_class(self):
        if self.request.user.role == "Admin" and self.request.method in {"PUT", "PATCH"}:
            return FraudReportReviewSerializer
        return FraudReportSerializer

    def update(self, request, *args, **kwargs):
        report = self.get_object()
        serializer = self.get_serializer(report, data=request.data, partial=kwargs.pop("partial", False))
        serializer.is_valid(raise_exception=True)
        if request.user.role == "Admin":
            report = serializer.save(reviewed_by=request.user, reviewed_at=timezone.now())
        else:
            report = serializer.save()

        log_action(
            request,
            "FRAUD_REPORT_UPDATE",
            target_user=report.user,
            additional_data={"report_id": report.id, "status": report.status},
        )
        return Response(FraudReportSerializer(report, context={"request": request}).data)

    def destroy(self, request, *args, **kwargs):
        report = self.get_object()
        if request.user.role != "Admin" and report.status != "PENDING":
            return Response(
                {"error": "Only pending reports can be deleted by the reporter."},
                status=status.HTTP_400_BAD_REQUEST,
            )
        report_id = report.id
        self.perform_destroy(report)
        log_action(
            request,
            "FRAUD_REPORT_DELETE",
            target_user=report.user,
            additional_data={"report_id": report_id},
        )
        return Response({"message": "Report deleted successfully."}, status=status.HTTP_200_OK)


class FraudReportDashboardView(APIView):
    permission_classes = [permissions.IsAuthenticated, IsAdmin]

    def get(self, request):
        queryset = FraudReport.objects.all()
        return Response(
            {
                "total_reports": queryset.count(),
                "pending_reports": queryset.filter(status="PENDING").count(),
                "reviewing_reports": queryset.filter(status="REVIEWING").count(),
                "reviewed_reports": queryset.filter(status="REVIEWED").count(),
                "resolved_reports": queryset.filter(status="RESOLVED").count(),
                "rejected_reports": queryset.filter(status="REJECTED").count(),
            }
        )
