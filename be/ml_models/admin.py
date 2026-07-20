from django.contrib import admin

from .models import MLModel


@admin.register(MLModel)
class MLModelAdmin(admin.ModelAdmin):
    list_display = (
        "id",
        "model_name",
        "version",
        "accuracy",
        "precision",
        "recall",
        "f1_score",
        "training_samples",
        "test_samples",
        "is_active",
        "trained_at",
    )
    list_filter = ("is_active", "trained_at")
    search_fields = ("model_name", "version")
