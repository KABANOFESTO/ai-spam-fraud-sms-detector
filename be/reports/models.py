from django.db import models
from authapi.models import User


class FraudReport(models.Model):

    STATUS = (
        ("PENDING", "Pending"),
        ("REVIEWED", "Reviewed"),
    )

    user = models.ForeignKey(
        User,
        on_delete=models.CASCADE
    )

    sms_message = models.TextField()

    notes = models.TextField(blank=True)

    status = models.CharField(
        max_length=20,
        choices=STATUS,
        default="PENDING"
    )

    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return self.user.username