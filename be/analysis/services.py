from __future__ import annotations

import time
from dataclasses import dataclass, field
from functools import lru_cache
from pathlib import Path

from django.conf import settings

from .ml_pipeline import DetectorBundle, build_prediction, load_detector_bundle


@dataclass
class DetectionResult:
    prediction: str
    confidence: float
    risk_score: int
    is_suspicious: bool
    matched_signals: list[str] = field(default_factory=list)
    explanation: str = ""
    normalized_message: str = ""
    model_name: str = "SmsFraudTextClassifier"
    model_version: str = "1.0.0"
    processing_time_ms: int = 0

    def to_dict(self):
        return {
            "prediction": self.prediction,
            "confidence": round(self.confidence, 2),
            "risk_score": self.risk_score,
            "is_suspicious": self.is_suspicious,
            "matched_signals": self.matched_signals,
            "explanation": self.explanation,
            "normalized_message": self.normalized_message,
            "model_name": self.model_name,
            "model_version": self.model_version,
            "processing_time_ms": self.processing_time_ms,
        }


@lru_cache(maxsize=1)
def _load_bundle() -> DetectorBundle:
    artifact_path = Path(settings.SMS_DETECTOR_MODEL_PATH)
    return load_detector_bundle(artifact_path)


class SmsFraudDetector:
    def detect(self, message: str) -> DetectionResult:
        started_at = time.perf_counter()
        bundle = _load_bundle()
        prediction = build_prediction(bundle, message)
        elapsed_ms = int((time.perf_counter() - started_at) * 1000)

        return DetectionResult(
            prediction=prediction["prediction"],
            confidence=prediction["confidence"],
            risk_score=prediction["risk_score"],
            is_suspicious=prediction["is_suspicious"],
            matched_signals=prediction["matched_signals"],
            explanation=prediction["explanation"],
            normalized_message=prediction["normalized_message"],
            model_name=bundle.model_name,
            model_version=bundle.version,
            processing_time_ms=elapsed_ms,
        )


def detector_is_ready() -> bool:
    artifact_path = Path(settings.SMS_DETECTOR_MODEL_PATH)
    return artifact_path.exists()


def clear_detector_cache() -> None:
    _load_bundle.cache_clear()
