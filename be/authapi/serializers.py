from django.contrib.auth.password_validation import validate_password
from rest_framework import serializers

from .models import User


class RegisterSerializer(serializers.ModelSerializer):
    password = serializers.CharField(write_only=True, required=True, validators=[validate_password])

    class Meta:
        model = User
        fields = ("username", "email", "password", "first_name", "last_name")

    def validate_email(self, value):
        if User.objects.filter(email__iexact=value).exists():
            raise serializers.ValidationError("A user with this email already exists.")
        return value

    def create(self, validated_data):
        user = User.objects.create_user(
            username=validated_data["username"],
            email=validated_data["email"].lower(),
            password=validated_data["password"],
            first_name=validated_data.get("first_name", ""),
            last_name=validated_data.get("last_name", ""),
            role="User",
            status="Active",
        )
        return user


class AdminUserCreateSerializer(serializers.ModelSerializer):
    temporary_password = serializers.CharField(read_only=True)

    class Meta:
        model = User
        fields = ("username", "email", "role", "first_name", "last_name", "status", "temporary_password")

    def validate_email(self, value):
        if User.objects.filter(email__iexact=value).exists():
            raise serializers.ValidationError("A user with this email already exists.")
        return value

    def create(self, validated_data):
        password = User.generate_random_password()
        user = User.objects.create_user(
            username=validated_data["username"],
            email=validated_data["email"].lower(),
            password=password,
            first_name=validated_data.get("first_name", ""),
            last_name=validated_data.get("last_name", ""),
            role=validated_data.get("role", "User"),
            status=validated_data.get("status", "Active"),
        )
        user.temporary_password = password
        return user


class UserSerializer(serializers.ModelSerializer):
    profile_picture_url = serializers.SerializerMethodField()

    class Meta:
        model = User
        fields = (
            "id",
            "username",
            "email",
            "first_name",
            "last_name",
            "role",
            "status",
            "is_active",
            "profile_picture",
            "profile_picture_url",
            "date_joined",
        )
        read_only_fields = ("id", "email", "is_active", "date_joined")

    def get_profile_picture_url(self, obj):
        request = self.context.get("request")
        if not obj.profile_picture:
            return None
        url = obj.profile_picture.url
        if request is not None:
            return request.build_absolute_uri(url)
        return url


class ProfileUpdateSerializer(serializers.ModelSerializer):
    current_password = serializers.CharField(write_only=True, required=False, allow_blank=False)
    new_password = serializers.CharField(write_only=True, required=False, validators=[validate_password])

    class Meta:
        model = User
        fields = ("username", "first_name", "last_name", "current_password", "new_password", "profile_picture")
        extra_kwargs = {
            "username": {"required": False},
            "first_name": {"required": False, "allow_blank": True},
            "last_name": {"required": False, "allow_blank": True},
            "profile_picture": {"required": False, "allow_null": True},
        }

    def validate(self, data):
        user = self.context["request"].user
        if data.get("new_password") and not data.get("current_password"):
            raise serializers.ValidationError({"current_password": "Current password is required to change password."})
        if data.get("new_password") and not user.check_password(data.get("current_password")):
            raise serializers.ValidationError({"current_password": "Current password is incorrect."})
        return data

    def update(self, instance, validated_data):
        for field in ("username", "first_name", "last_name"):
            if field in validated_data:
                setattr(instance, field, validated_data[field])

        if validated_data.get("new_password"):
            instance.set_password(validated_data["new_password"])

        if "profile_picture" in validated_data:
            if instance.profile_picture:
                instance.profile_picture.delete(save=False)
            instance.profile_picture = validated_data["profile_picture"]

        instance.save()
        return instance


class AuthTokenSerializer(serializers.Serializer):
    email = serializers.EmailField()
    password = serializers.CharField(write_only=True)


class LogoutSerializer(serializers.Serializer):
    refresh = serializers.CharField()
