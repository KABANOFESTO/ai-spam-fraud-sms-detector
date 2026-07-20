# Generated manually to store evaluation reports for trained models.

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("ml_models", "0002_training_metadata"),
    ]

    operations = [
        migrations.AddField(
            model_name="mlmodel",
            name="evaluation_report",
            field=models.JSONField(blank=True, default=dict),
        ),
    ]
