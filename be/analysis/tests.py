from __future__ import annotations

import io
import tempfile
from pathlib import Path

from django.contrib.auth import get_user_model
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import TestCase, override_settings
from rest_framework.test import APIRequestFactory, force_authenticate

from analysis.ml_pipeline import load_training_frame, train_and_save_detector, train_detector
from analysis.models import SMSAnalysis, TrainingDataset
from analysis.services import clear_detector_cache
from analysis.views import AnalyzeSMSView, DatasetImportView, EvaluationReportView
from ml_models.models import MLModel


class TrainingPipelineTests(TestCase):
    def setUp(self):
        self.csv_content = "\n".join(
            [
                "message,label",
                "Your OTP is 123456. Do not share it.,LEGITIMATE",
                "Meeting is at 2 PM today.,LEGITIMATE",
                "Please review the invoice and send feedback.,LEGITIMATE",
                "Your delivery is scheduled for tomorrow.,LEGITIMATE",
                "Free gift card available now. Click here.,SPAM",
                "Limited time promo. Buy now and save.,SPAM",
                "Huge discount on all items. Reply YES.,SPAM",
                "Subscribe to get exclusive deals.,SPAM",
                "Your bank account is blocked. Send your PIN.,FRAUD",
                "Urgent! Verify your account immediately.,FRAUD",
                "Your mobile money wallet is suspended. Click here.,FRAUD",
                "We need your OTP to confirm identity.,FRAUD",
            ]
        )

    def test_load_training_frame_normalizes_labels(self):
        frame = load_training_frame(io.StringIO(self.csv_content))
        self.assertEqual(len(frame), 12)
        self.assertEqual(set(frame["label"].unique()), {"LEGITIMATE", "SPAM", "FRAUD"})

    def test_train_detector_returns_evaluation_report(self):
        frame = load_training_frame(io.StringIO(self.csv_content))
        bundle, metrics, evaluation_report = train_detector(frame, model_name="SmsFraudTextClassifier", version="1.0.0")

        self.assertIn("accuracy", metrics)
        self.assertIn("confusion_matrix", evaluation_report)
        self.assertEqual(len(evaluation_report["labels"]), 3)
        self.assertEqual(bundle.model_name, "SmsFraudTextClassifier")

    def test_train_and_save_detector_creates_artifact(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            data_path = Path(tmpdir) / "training.csv"
            artifact_path = Path(tmpdir) / "detector.joblib"
            data_path.write_text(self.csv_content, encoding="utf-8")

            bundle, metrics, evaluation_report, saved_path = train_and_save_detector(
                data_path=data_path,
                artifact_path=artifact_path,
                model_name="SmsFraudTextClassifier",
                version="1.0.0",
            )

            self.assertTrue(saved_path.exists())
            self.assertTrue(evaluation_report["confusion_matrix"])
            self.assertGreaterEqual(metrics["accuracy"], 0.0)
            self.assertEqual(bundle.version, "1.0.0")


class AnalysisApiTests(TestCase):
    def setUp(self):
        self.factory = APIRequestFactory()
        self.User = get_user_model()
        self.admin = self.User.objects.create_user(
            username="admin",
            email="admin@example.com",
            password="password123!",
            role="Admin",
            status="Active",
        )
        self.user = self.User.objects.create_user(
            username="user",
            email="user@example.com",
            password="password123!",
            role="User",
            status="Active",
        )

    def _build_dataset_csv(self):
        return "\n".join(
            [
                "message,label",
                "Your OTP is 123456. Do not share it.,LEGITIMATE",
                "Your bank account has been blocked.,FRAUD",
                "Free voucher available now.,SPAM",
                "Meeting has been moved to tomorrow.,LEGITIMATE",
                "Please send your PIN to verify.,FRAUD",
                "Limited time discount. Buy now.,SPAM",
                "Thanks for the payment receipt.,LEGITIMATE",
                "Urgent! Your account is suspended.,FRAUD",
                "Promo alert. Click here for a bonus.,SPAM",
                "Dinner is at 7pm tonight.,LEGITIMATE",
                "Reply with your OTP to continue.,FRAUD",
                "Special offer available now.,SPAM",
            ]
        )

    def test_dataset_import_and_evaluation_report(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            media_root = Path(tmpdir) / "media"
            artifact_path = Path(tmpdir) / "detector.joblib"
            data_path = Path(tmpdir) / "training.csv"
            data_path.write_text(self._build_dataset_csv(), encoding="utf-8")

            with override_settings(
                MEDIA_ROOT=media_root,
                SMS_DETECTOR_MODEL_PATH=str(artifact_path),
                SMS_DETECTOR_TRAINING_DATA_PATH=str(data_path),
            ):
                clear_detector_cache()
                bundle, metrics, evaluation_report, saved_path = train_and_save_detector(
                    data_path=data_path,
                    artifact_path=artifact_path,
                    model_name="SmsFraudTextClassifier",
                    version="1.0.0",
                )

                MLModel.objects.create(
                    model_name="SmsFraudTextClassifier",
                    version="1.0.0",
                    artifact_path=str(saved_path),
                    training_data_path=str(data_path),
                    training_samples=int(metrics["train_size"]),
                    test_samples=int(metrics["test_size"]),
                    evaluation_report=evaluation_report,
                    accuracy=metrics["accuracy"],
                    precision=metrics["precision"],
                    recall=metrics["recall"],
                    f1_score=metrics["f1_score"],
                    is_active=True,
                )

                request = self.factory.get("/api/analysis/admin/evaluation/")
                force_authenticate(request, user=self.admin)
                response = EvaluationReportView.as_view()(request)
                self.assertEqual(response.status_code, 200)
                self.assertIn("confusion_matrix", response.data)
                self.assertIn("classification_report", response.data)
                self.assertEqual(response.data["model_name"], "SmsFraudTextClassifier")

    def test_dataset_upload_view_stores_managed_dataset(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            media_root = Path(tmpdir) / "media"
            csv_file = SimpleUploadedFile(
                "sms_training.csv",
                self._build_dataset_csv().encode("utf-8"),
                content_type="text/csv",
            )

            with override_settings(MEDIA_ROOT=media_root):
                request = self.factory.post(
                    "/api/analysis/admin/datasets/import/",
                    {"file": csv_file, "notes": "Imported from internal review"},
                    format="multipart",
                )
                force_authenticate(request, user=self.admin)
                response = DatasetImportView.as_view()(request)

                self.assertEqual(response.status_code, 201)
                self.assertEqual(TrainingDataset.objects.count(), 1)
                dataset = TrainingDataset.objects.first()
                self.assertEqual(dataset.row_count, 12)
                self.assertIn("FRAUD", dataset.label_distribution)

    def test_analysis_view_uses_trained_detector(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            media_root = Path(tmpdir) / "media"
            artifact_path = Path(tmpdir) / "detector.joblib"
            data_path = Path(tmpdir) / "training.csv"
            data_path.write_text(self._build_dataset_csv(), encoding="utf-8")

            with override_settings(
                MEDIA_ROOT=media_root,
                SMS_DETECTOR_MODEL_PATH=str(artifact_path),
                SMS_DETECTOR_TRAINING_DATA_PATH=str(data_path),
            ):
                clear_detector_cache()
                train_and_save_detector(
                    data_path=data_path,
                    artifact_path=artifact_path,
                    model_name="SmsFraudTextClassifier",
                    version="1.0.0",
                )

                request = self.factory.post(
                    "/api/analysis/analyze/",
                    {"message": "Your bank account has been blocked. Send your PIN now."},
                    format="json",
                )
                force_authenticate(request, user=self.user)
                response = AnalyzeSMSView.as_view()(request)

                self.assertEqual(response.status_code, 201)
                self.assertIn(response.data["prediction"], {"SPAM", "FRAUD", "LEGITIMATE"})
                self.assertTrue(SMSAnalysis.objects.filter(user=self.user).exists())
