from django.urls import path

from .views import (
    ActiveModelView,
    AdminRetrainDetectorView,
    AnalysisDetailView,
    AnalysisHistoryView,
    AnalysisStatsView,
    AnalyzeSMSView,
    DashboardView,
)

urlpatterns = [
    path("analyze/", AnalyzeSMSView.as_view(), name="analysis-analyze"),
    path("bulk-analyze/", AnalyzeSMSView.as_view(), name="analysis-bulk-analyze"),
    path("history/", AnalysisHistoryView.as_view(), name="analysis-history"),
    path("history/<int:pk>/", AnalysisDetailView.as_view(), name="analysis-detail"),
    path("stats/", AnalysisStatsView.as_view(), name="analysis-stats"),
    path("dashboard/", DashboardView.as_view(), name="analysis-dashboard"),
    path("models/", ActiveModelView.as_view(), name="analysis-active-models"),
    path("admin/retrain/", AdminRetrainDetectorView.as_view(), name="analysis-admin-retrain"),
]
