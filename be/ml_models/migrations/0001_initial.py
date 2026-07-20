# Generated manually to match the current ML model metadata state.

from django.db import migrations, models


class Migration(migrations.Migration):

    initial = True

    dependencies = []

    operations = [
        migrations.CreateModel(
            name="MLModel",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("model_name", models.CharField(max_length=100)),
                ("version", models.CharField(max_length=20)),
                ("accuracy", models.FloatField()),
                ("precision", models.FloatField()),
                ("recall", models.FloatField()),
                ("f1_score", models.FloatField()),
                ("trained_at", models.DateTimeField(auto_now_add=True)),
                ("is_active", models.BooleanField(default=True)),
            ],
        ),
    ]
