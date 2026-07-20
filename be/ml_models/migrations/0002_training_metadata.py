# Generated manually to add training metadata for detector artifacts.

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("ml_models", "0001_initial"),
    ]

    operations = [
        migrations.AddField(
            model_name="mlmodel",
            name="artifact_path",
            field=models.CharField(blank=True, default="", max_length=500),
        ),
        migrations.AddField(
            model_name="mlmodel",
            name="test_samples",
            field=models.PositiveIntegerField(default=0),
        ),
        migrations.AddField(
            model_name="mlmodel",
            name="training_data_path",
            field=models.CharField(blank=True, default="", max_length=500),
        ),
        migrations.AddField(
            model_name="mlmodel",
            name="training_samples",
            field=models.PositiveIntegerField(default=0),
        ),
    ]
