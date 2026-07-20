from django.db import models
from authapi.models import User


class TrainingDataset(models.Model):
    original_filename = models.CharField(max_length=255)
    stored_file = models.FileField(upload_to="datasets/")
    row_count = models.PositiveIntegerField(default=0)
    label_distribution = models.JSONField(default=dict, blank=True)
    imported_by = models.ForeignKey(User, on_delete=models.SET_NULL, null=True, blank=True, related_name="imported_datasets")
    notes = models.TextField(blank=True, default="")
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["-created_at"]

    def __str__(self):
        return self.original_filename


class SMSAnalysis(models.Model):

    PREDICTION_CHOICES = (
        ("LEGITIMATE", "Legitimate"),
        ("SPAM", "Spam"),
        ("FRAUD", "Fraud"),
    )

    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="analyses")

    message = models.TextField()

    normalized_message = models.TextField(blank=True, default="")

    prediction = models.CharField(max_length=20, choices=PREDICTION_CHOICES)

    confidence = models.DecimalField(max_digits=5, decimal_places=2)

    risk_score = models.PositiveSmallIntegerField(default=0)

    is_suspicious = models.BooleanField(default=False)

    matched_signals = models.JSONField(default=list, blank=True)

    model_name = models.CharField(max_length=100, default="HybridKeywordDetector")

    model_version = models.CharField(max_length=20, default="1.0")

    explanation = models.TextField(blank=True, default="")

    processing_time_ms = models.PositiveIntegerField(default=0)

    analyzed_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["-analyzed_at"]
        indexes = [
            models.Index(fields=["user", "analyzed_at"]),
            models.Index(fields=["prediction", "is_suspicious"]),
        ]

    def __str__(self):
        return f"{self.prediction} ({self.confidence}%)"
