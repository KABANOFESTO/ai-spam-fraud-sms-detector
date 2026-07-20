from django.urls import path

from .views import FraudReportDashboardView, FraudReportDetailView, FraudReportListCreateView

urlpatterns = [
    path("", FraudReportListCreateView.as_view(), name="fraud-report-list-create"),
    path("<int:pk>/", FraudReportDetailView.as_view(), name="fraud-report-detail"),
    path("dashboard/", FraudReportDashboardView.as_view(), name="fraud-report-dashboard"),
]
