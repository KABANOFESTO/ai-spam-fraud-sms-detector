from django.db import models


class MLModel(models.Model):

    model_name = models.CharField(max_length=100)

    version = models.CharField(max_length=20)

    artifact_path = models.CharField(max_length=500, blank=True, default="")

    training_data_path = models.CharField(max_length=500, blank=True, default="")

    training_samples = models.PositiveIntegerField(default=0)

    test_samples = models.PositiveIntegerField(default=0)

    accuracy = models.FloatField()

    precision = models.FloatField()

    recall = models.FloatField()

    f1_score = models.FloatField()

    trained_at = models.DateTimeField(auto_now_add=True)

    is_active = models.BooleanField(default=True)

    def __str__(self):
        return self.model_name
