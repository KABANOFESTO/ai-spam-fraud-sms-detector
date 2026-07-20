from django.contrib import admin
from django.urls import include, path
from django.conf import settings
from django.conf.urls.static import static
from rest_framework.response import Response
from rest_framework.decorators import api_view
from rest_framework.permissions import AllowAny
from rest_framework import status
from drf_yasg.views import get_schema_view
from drf_yasg import openapi
from analysis.services import detector_is_ready
from ml_models.models import MLModel


schema_view = get_schema_view(
    openapi.Info(
        title="AI-Based Spam/Fraud SMS Detector API",
        default_version="v1",
        description="REST API for SMS classification, reporting, audit logs, and authentication.",
    ),
    public=True,
    permission_classes=(AllowAny,),
)


@api_view(["GET"])
def health_check(request):
    active_model = MLModel.objects.filter(is_active=True).order_by("-trained_at").first()
    return Response(
        {
            "status": "ok",
            "service": "ai-spam-fraud-sms-detector",
            "model_ready": detector_is_ready(),
            "active_model": None
            if active_model is None
            else {
                "model_name": active_model.model_name,
                "version": active_model.version,
                "accuracy": active_model.accuracy,
                "artifact_path": active_model.artifact_path,
            },
        },
        status=status.HTTP_200_OK,
    )

urlpatterns = [
    path('admin/', admin.site.urls),
    path('api/health/', health_check, name='health-check'),
    path('api/auth/', include('authapi.urls')),
    path('api/analysis/', include('analysis.urls')),
    path('api/reports/', include('reports.urls')),
    path('api/audit-log/', include('audit_log.urls')),
    path('api/docs/', schema_view.with_ui('swagger', cache_timeout=0), name='swagger-ui'),
    path('api/schema.json', schema_view.without_ui(cache_timeout=0), name='schema-json'),
] + static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
