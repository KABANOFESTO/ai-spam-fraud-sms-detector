from django.db import models
from authapi.models import User


class SMSAnalysis(models.Model):

    PREDICTION_CHOICES = (
        ("LEGITIMATE", "Legitimate"),
        ("SPAM", "Spam"),
        ("FRAUD", "Fraud"),
    )

    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="analyses")

    message = models.TextField()

    prediction = models.CharField(max_length=20, choices=PREDICTION_CHOICES)

    confidence = models.DecimalField(max_digits=5, decimal_places=2)

    is_suspicious = models.BooleanField(default=False)

    analyzed_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["-analyzed_at"]

    def __str__(self):
        return f"{self.prediction} ({self.confidence}%)"
