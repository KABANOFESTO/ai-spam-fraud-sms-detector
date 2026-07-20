from rest_framework import serializers


class RetrainDetectorRequestSerializer(serializers.Serializer):
    dataset_id = serializers.IntegerField(required=False, min_value=1)
    data_path = serializers.CharField(required=False, allow_blank=False)
    artifact_path = serializers.CharField(required=False, allow_blank=False)
    model_name = serializers.CharField(required=False, allow_blank=False, default="SmsFraudTextClassifier")
    version = serializers.CharField(required=False, allow_blank=False, default="1.0.0")
    force = serializers.BooleanField(required=False, default=False)
