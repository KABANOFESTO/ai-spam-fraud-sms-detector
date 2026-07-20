from django.db import models
from authapi.models import User


class FraudReport(models.Model):

    STATUS = (
        ("PENDING", "Pending"),
        ("REVIEWING", "Reviewing"),
        ("REVIEWED", "Reviewed"),
        ("RESOLVED", "Resolved"),
        ("REJECTED", "Rejected"),
    )

    user = models.ForeignKey(
        User,
        on_delete=models.CASCADE,
        related_name="fraud_reports"
    )

    analysis = models.ForeignKey(
        "analysis.SMSAnalysis",
        on_delete=models.SET_NULL,
        related_name="fraud_reports",
        null=True,
        blank=True,
    )

    sms_message = models.TextField()

    notes = models.TextField(blank=True)

    admin_notes = models.TextField(blank=True)

    status = models.CharField(
        max_length=20,
        choices=STATUS,
        default="PENDING"
    )

    reviewed_by = models.ForeignKey(
        User,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="reviewed_fraud_reports",
    )

    reviewed_at = models.DateTimeField(null=True, blank=True)

    created_at = models.DateTimeField(auto_now_add=True)

    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self):
        return f"{self.user.username} - {self.status}"
