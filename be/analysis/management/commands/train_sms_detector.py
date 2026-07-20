from __future__ import annotations

from pathlib import Path

from django.conf import settings
from django.core.management.base import BaseCommand, CommandError

from analysis.ml_pipeline import train_and_save_detector
from ml_models.models import MLModel


class Command(BaseCommand):
    help = "Train the SMS fraud detector from a labeled CSV file and save a joblib artifact."

    def add_arguments(self, parser):
        parser.add_argument(
            "--data",
            dest="data_path",
            default=None,
            help="Path to a CSV file with message and label columns.",
        )
        parser.add_argument(
            "--artifact",
            dest="artifact_path",
            default=None,
            help="Path where the trained detector artifact should be saved.",
        )
        parser.add_argument(
            "--model-name",
            dest="model_name",
            default="SmsFraudTextClassifier",
            help="Logical name for the trained model record.",
        )
        parser.add_argument(
            "--version",
            dest="version",
            default="1.0.0",
            help="Model version to store in metadata.",
        )
        parser.add_argument(
            "--force",
            action="store_true",
            help="Overwrite any existing artifact at the target path.",
        )

    def handle(self, *args, **options):
        data_path = Path(options["data_path"] or settings.SMS_DETECTOR_TRAINING_DATA_PATH)
        artifact_path = Path(options["artifact_path"] or settings.SMS_DETECTOR_MODEL_PATH)
        model_name = options["model_name"]
        version = options["version"]

        if not data_path.exists():
            raise CommandError(f"Training data not found at {data_path}")

        if artifact_path.exists() and not options["force"]:
            self.stdout.write(self.style.WARNING(f"Artifact already exists at {artifact_path}. Use --force to overwrite it."))
            return

        self.stdout.write(f"Loading training data from {data_path}")
        bundle, metrics, saved_path = train_and_save_detector(
            data_path=data_path,
            artifact_path=artifact_path,
            model_name=model_name,
            version=version,
        )

        MLModel.objects.update_or_create(
            model_name=model_name,
            version=version,
            defaults={
                "artifact_path": str(saved_path),
                "training_data_path": str(data_path),
                "training_samples": int(metrics["train_size"]),
                "test_samples": int(metrics["test_size"]),
                "accuracy": metrics["accuracy"],
                "precision": metrics["precision"],
                "recall": metrics["recall"],
                "f1_score": metrics["f1_score"],
                "is_active": True,
            },
        )
        MLModel.objects.exclude(model_name=model_name, version=version).update(is_active=False)

        self.stdout.write(self.style.SUCCESS("SMS detector trained successfully."))
        self.stdout.write(f"Artifact saved to: {saved_path}")
        self.stdout.write(
            "Metrics: "
            f"accuracy={metrics['accuracy']:.4f}, "
            f"precision={metrics['precision']:.4f}, "
            f"recall={metrics['recall']:.4f}, "
            f"f1={metrics['f1_score']:.4f}"
        )
