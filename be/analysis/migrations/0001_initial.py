# Generated manually to match the current analysis model state.

from django.conf import settings
from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):

    initial = True

    dependencies = [
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.CreateModel(
            name="SMSAnalysis",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("message", models.TextField()),
                ("normalized_message", models.TextField(blank=True, default="")),
                ("prediction", models.CharField(choices=[("LEGITIMATE", "Legitimate"), ("SPAM", "Spam"), ("FRAUD", "Fraud")], max_length=20)),
                ("confidence", models.DecimalField(decimal_places=2, max_digits=5)),
                ("risk_score", models.PositiveSmallIntegerField(default=0)),
                ("is_suspicious", models.BooleanField(default=False)),
                ("matched_signals", models.JSONField(blank=True, default=list)),
                ("model_name", models.CharField(default="HybridKeywordDetector", max_length=100)),
                ("model_version", models.CharField(default="1.0", max_length=20)),
                ("explanation", models.TextField(blank=True, default="")),
                ("processing_time_ms", models.PositiveIntegerField(default=0)),
                ("analyzed_at", models.DateTimeField(auto_now_add=True)),
                (
                    "user",
                    models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="analyses", to=settings.AUTH_USER_MODEL),
                ),
            ],
            options={
                "ordering": ["-analyzed_at"],
            },
        ),
        migrations.AddIndex(
            model_name="smsanalysis",
            index=models.Index(fields=["user", "analyzed_at"], name="analysis_s_user_id_2d2b7f_idx"),
        ),
        migrations.AddIndex(
            model_name="smsanalysis",
            index=models.Index(fields=["prediction", "is_suspicious"], name="analysis_s_predic_1b4ed0_idx"),
        ),
    ]
