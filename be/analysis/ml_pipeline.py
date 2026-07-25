from __future__ import annotations

import csv
import io
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, precision_recall_fscore_support
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline


LABEL_ALIASES = {
    "legitimate": "LEGITIMATE",
    "ham": "LEGITIMATE",
    "normal": "LEGITIMATE",
    "spam": "SPAM",
    "junk": "SPAM",
    "fraud": "FRAUD",
    "phishing": "FRAUD",
    "scam": "FRAUD",
}

VALID_LABELS = ("LEGITIMATE", "SPAM", "FRAUD")


@dataclass(frozen=True)
class DetectorBundle:
    pipeline: Pipeline
    model_name: str
    version: str
    metrics: dict[str, float]
    evaluation_report: dict[str, Any]
    classes: list[str]
    feature_name: str = "message"
    label_name: str = "label"


def normalize_label(label: Any) -> str:
    normalized = str(label).strip().lower()
    if normalized in LABEL_ALIASES:
        return LABEL_ALIASES[normalized]
    upper = str(label).strip().upper()
    if upper in VALID_LABELS:
        return upper
    raise ValueError(f"Unsupported label: {label}")


def load_training_frame(path: str | Path) -> pd.DataFrame:
    if hasattr(path, "read"):
        raw_text = path.read()
        if hasattr(path, "seek"):
            path.seek(0)
    else:
        with open(path, "r", encoding="utf-8-sig", newline="") as handle:
            raw_text = handle.read()

    if isinstance(raw_text, bytes):
        raw_text = raw_text.decode("utf-8-sig")

    if not raw_text or not raw_text.strip():
        raise ValueError("Training data is empty.")

    reader = csv.reader(io.StringIO(raw_text))
    rows = [row for row in reader if row and any(cell.strip() for cell in row)]
    if len(rows) < 2:
        raise ValueError("Training data must contain a header and at least one labeled row.")

    header = [column.strip() for column in rows[0]]
    header_lookup = {column.lower(): index for index, column in enumerate(header)}
    text_index = next((header_lookup[key] for key in ("message", "text", "sms") if key in header_lookup), None)
    label_index = next((header_lookup[key] for key in ("label", "category", "class") if key in header_lookup), None)

    if text_index is None or label_index is None:
        raise ValueError("Training data must contain message/text and label/category columns.")

    parsed_rows: list[dict[str, str]] = []
    for row in rows[1:]:
        if len(row) == len(header):
            message = row[text_index].strip()
            label = row[label_index].strip()
        elif len(header) == 2 and len(row) > 2:
            message = ",".join(row[:-1]).strip()
            label = row[-1].strip()
        else:
            raise ValueError(
                "Training data rows must contain either the declared columns or a message followed by a label."
            )

        if not message or not label:
            continue

        parsed_rows.append({"message": message, "label": normalize_label(label)})

    df = pd.DataFrame(parsed_rows, columns=["message", "label"])
    if df.empty:
        raise ValueError("Training data does not contain any valid labeled rows.")
    return df.reset_index(drop=True)


def build_pipeline() -> Pipeline:
    return Pipeline(
        steps=[
            (
                "tfidf",
                TfidfVectorizer(
                    ngram_range=(1, 2),
                    max_features=8000,
                    min_df=1,
                    stop_words="english",
                    sublinear_tf=True,
                ),
            ),
            (
                "classifier",
                LogisticRegression(
                    max_iter=3000,
                    class_weight="balanced",
                ),
            ),
        ]
    )


def train_detector(df: pd.DataFrame, model_name: str, version: str, test_size: float = 0.2, random_state: int = 42) -> tuple[DetectorBundle, dict[str, float], dict[str, Any]]:
    if df.empty:
        raise ValueError("Training data is empty.")

    class_counts = df["label"].value_counts()
    if class_counts.size < 2:
        raise ValueError("Training data must contain at least two classes.")

    stratify = df["label"] if class_counts.min() >= 2 and len(df) >= 10 else None
    train_df, test_df = train_test_split(df, test_size=test_size, random_state=random_state, stratify=stratify)

    pipeline = build_pipeline()
    pipeline.fit(train_df["message"], train_df["label"])

    y_true = test_df["label"]
    y_pred = pipeline.predict(test_df["message"])
    accuracy = accuracy_score(y_true, y_pred)
    precision, recall, f1, _ = precision_recall_fscore_support(y_true, y_pred, average="weighted", zero_division=0)
    labels = list(VALID_LABELS)
    cm = confusion_matrix(y_true, y_pred, labels=labels)
    report = classification_report(
        y_true,
        y_pred,
        labels=labels,
        target_names=labels,
        output_dict=True,
        zero_division=0,
    )

    bundle = DetectorBundle(
        pipeline=pipeline,
        model_name=model_name,
        version=version,
        metrics={
            "accuracy": float(accuracy),
            "precision": float(precision),
            "recall": float(recall),
            "f1_score": float(f1),
            "train_size": float(len(train_df)),
            "test_size": float(len(test_df)),
        },
        evaluation_report={
            "confusion_matrix": cm.tolist(),
            "labels": labels,
            "classification_report": report,
        },
        classes=list(pipeline.named_steps["classifier"].classes_),
    )
    return bundle, bundle.metrics, bundle.evaluation_report


def save_detector_bundle(bundle: DetectorBundle, path: str | Path) -> Path:
    artifact_path = Path(path)
    artifact_path.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(bundle, artifact_path)
    return artifact_path


def train_and_save_detector(
    data_path: str | Path,
    artifact_path: str | Path,
    model_name: str,
    version: str,
) -> tuple[DetectorBundle, dict[str, float], dict[str, Any], Path]:
    frame = load_training_frame(data_path)
    bundle, metrics, evaluation_report = train_detector(frame, model_name=model_name, version=version)
    saved_path = save_detector_bundle(bundle, artifact_path)
    return bundle, metrics, evaluation_report, saved_path


def load_detector_bundle(path: str | Path) -> DetectorBundle:
    artifact_path = Path(path)
    if not artifact_path.exists():
        raise FileNotFoundError(f"Detector artifact not found at {artifact_path}")
    bundle = joblib.load(artifact_path)
    if not isinstance(bundle, DetectorBundle):
        raise ValueError("Invalid detector artifact format.")
    return bundle


def _classifier_and_vectorizer(bundle: DetectorBundle):
    vectorizer = bundle.pipeline.named_steps["tfidf"]
    classifier = bundle.pipeline.named_steps["classifier"]
    return vectorizer, classifier


def _predict_probability_row(bundle: DetectorBundle, message: str):
    vectorizer, classifier = _classifier_and_vectorizer(bundle)
    features = vectorizer.transform([message])
    probabilities = classifier.predict_proba(features)[0]
    class_to_probability = dict(zip(classifier.classes_, probabilities, strict=True))
    predicted_class = max(class_to_probability, key=class_to_probability.get)
    return {
        "predicted_class": predicted_class,
        "probabilities": class_to_probability,
        "features": features,
        "vectorizer": vectorizer,
        "classifier": classifier,
    }


def build_prediction(bundle: DetectorBundle, message: str) -> dict[str, Any]:
    normalized_message = message.lower().strip()
    payload = _predict_probability_row(bundle, message)
    predicted_class = payload["predicted_class"]
    probability = payload["probabilities"][predicted_class]
    risk_probability = max(
        payload["probabilities"].get("SPAM", 0.0),
        payload["probabilities"].get("FRAUD", 0.0),
    )

    matched_signals = extract_signals(payload, predicted_class)
    confidence = round(float(probability * 100), 2)
    risk_score = int(round(risk_probability * 100))
    is_suspicious = predicted_class != "LEGITIMATE"

    return {
        "prediction": predicted_class,
        "confidence": confidence,
        "risk_score": risk_score,
        "is_suspicious": is_suspicious,
        "matched_signals": matched_signals,
        "explanation": build_explanation(predicted_class, matched_signals),
        "normalized_message": normalized_message,
    }


def extract_signals(payload: dict[str, Any], predicted_class: str, top_n: int = 5) -> list[str]:
    vectorizer = payload["vectorizer"]
    classifier = payload["classifier"]
    features = payload["features"]

    feature_names = vectorizer.get_feature_names_out()
    row = features[0]
    if row.nnz == 0:
        return []

    class_index = list(classifier.classes_).index(predicted_class)
    coefficients = classifier.coef_[class_index]
    contributions = []
    for feature_index, value in zip(row.indices, row.data, strict=True):
        contribution = float(value * coefficients[feature_index])
        contributions.append((abs(contribution), feature_names[feature_index], contribution))

    contributions.sort(reverse=True)
    signals = []
    for _, feature_name, contribution in contributions[:top_n]:
        sign = "positive" if contribution >= 0 else "negative"
        signals.append(f"{feature_name} ({sign})")
    return signals


def build_explanation(predicted_class: str, matched_signals: list[str]) -> str:
    if not matched_signals:
        return f"Predicted as {predicted_class.lower()} using the trained text classifier."
    return f"Predicted as {predicted_class.lower()} based on signals: {', '.join(matched_signals[:4])}."
