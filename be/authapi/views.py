import logging

from django.conf import settings
from django.contrib.auth import authenticate
from django.contrib.auth.tokens import default_token_generator
from django.core.mail import send_mail
from django.utils.encoding import force_bytes, force_str
from django.utils.http import urlsafe_base64_decode, urlsafe_base64_encode
from rest_framework import generics, permissions, status
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework.parsers import FormParser, JSONParser, MultiPartParser
from rest_framework_simplejwt.tokens import RefreshToken

from audit_log.audit_log_utils import log_action

from .models import User
from .permissions import IsAdmin, IsOwnerOrAdmin, IsUser
from .serializers import (
    AdminUserCreateSerializer,
    BootstrapAdminSerializer,
    AuthTokenSerializer,
    LogoutSerializer,
    ProfileUpdateSerializer,
    RegisterSerializer,
    UserSerializer,
)

logger = logging.getLogger(__name__)


def _build_tokens(user):
    refresh = RefreshToken.for_user(user)
    return {
        "refresh": str(refresh),
        "access": str(refresh.access_token),
    }


class RegisterView(generics.CreateAPIView):
    serializer_class = RegisterSerializer
    permission_classes = [permissions.AllowAny]

    def create(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()

        log_action(
            request,
            "USER_CREATE",
            target_user=user,
            additional_data={"registration_method": "self_registration"},
        )

        tokens = _build_tokens(user)
        return Response(
            {
                "message": "Registration successful.",
                "tokens": tokens,
                "user": UserSerializer(user, context={"request": request}).data,
            },
            status=status.HTTP_201_CREATED,
        )


class BootstrapAdminView(generics.CreateAPIView):
    serializer_class = BootstrapAdminSerializer
    permission_classes = [permissions.AllowAny]

    def create(self, request, *args, **kwargs):
        if User.objects.filter(role="Admin").exists():
            return Response(
                {"error": "An admin account already exists. Use the authenticated admin create endpoint."},
                status=status.HTTP_400_BAD_REQUEST,
            )

        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()

        log_action(
            request,
            "USER_CREATE",
            target_user=user,
            additional_data={
                "registration_method": "bootstrap_admin",
                "role": user.role,
                "bootstrap_admin": True,
            },
        )

        tokens = _build_tokens(user)
        return Response(
            {
                "message": "Initial admin account created successfully.",
                "tokens": tokens,
                "user": UserSerializer(user, context={"request": request}).data,
                "temporary_password": user.temporary_password,
            },
            status=status.HTTP_201_CREATED,
        )


class AdminUserCreateView(generics.CreateAPIView):
    serializer_class = AdminUserCreateSerializer
    permission_classes = [permissions.IsAuthenticated, IsAdmin]

    def create(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()

        log_action(
            request,
            "USER_CREATE",
            target_user=user,
            additional_data={
                "registration_method": "admin_created",
                "created_by": request.user.email,
                "role": user.role,
                "temporary_password_sent": True,
            },
        )

        frontend_login_url = getattr(settings, "FRONTEND_LOGIN_URL", "http://localhost:3000/login")
        try:
            send_mail(
                subject="Your Account Has Been Created",
                message=(
                    f"Hello {user.first_name or user.username},\n\n"
                    f"An administrator has created an account for you with the following details:\n\n"
                    f"Username: {user.username}\n"
                    f"Email: {user.email}\n"
                    f"Temporary Password: {user.temporary_password}\n"
                    f"Role: {user.get_role_display()}\n\n"
                    f"Please log in at {frontend_login_url} and change your password immediately.\n\n"
                    f"If you didn't expect this email, please contact your system administrator."
                ),
                from_email=settings.DEFAULT_FROM_EMAIL,
                recipient_list=[user.email],
                fail_silently=False,
            )
        except Exception as exc:
            logger.exception("Failed to send user creation email to %s", user.email)
            log_action(
                request,
                "EMAIL_SEND_FAILURE",
                target_user=user,
                additional_data={"email_type": "user_creation", "error": str(exc)},
            )
            return Response(
                {
                    "message": "User created successfully but failed to send email.",
                    "user_id": user.id,
                    "email": user.email,
                    "temporary_password": user.temporary_password,
                },
                status=status.HTTP_201_CREATED,
            )

        return Response(
            {
                "message": "User created successfully. Email with credentials sent.",
                "user_id": user.id,
                "email": user.email,
            },
            status=status.HTTP_201_CREATED,
        )


class MyTokenObtainView(APIView):
    permission_classes = [permissions.AllowAny]

    def post(self, request):
        serializer = AuthTokenSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        email = serializer.validated_data["email"].lower()
        password = serializer.validated_data["password"]

        user = authenticate(username=email, password=password)
        if not user:
            log_action(
                request,
                "LOGIN",
                additional_data={
                    "status": "failed",
                    "reason": "invalid_credentials",
                    "attempted_email": email,
                },
            )
            return Response({"error": "Invalid credentials"}, status=status.HTTP_401_UNAUTHORIZED)

        if not user.is_active:
            log_action(
                request,
                "LOGIN",
                target_user=user,
                additional_data={"status": "failed", "reason": "account_deactivated"},
            )
            return Response({"error": "Account is deactivated"}, status=status.HTTP_401_UNAUTHORIZED)

        tokens = _build_tokens(user)
        log_action(
            request,
            "LOGIN",
            target_user=user,
            additional_data={"login_method": "email_password"},
        )
        return Response(
            {
                **tokens,
                "user": UserSerializer(user, context={"request": request}).data,
            },
            status=status.HTTP_200_OK,
        )


class LogoutView(APIView):
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request):
        serializer = LogoutSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        try:
            token = RefreshToken(serializer.validated_data["refresh"])
            token.blacklist()
        except Exception:
            return Response({"error": "Invalid refresh token."}, status=status.HTTP_400_BAD_REQUEST)

        log_action(request, "LOGOUT", target_user=request.user)
        return Response({"message": "Logged out successfully."}, status=status.HTTP_200_OK)


class AdminOnlyView(APIView):
    permission_classes = [permissions.IsAuthenticated, IsAdmin]

    def get(self, request):
        return Response({"message": "Hello Admin"})


class UserOnlyView(APIView):
    permission_classes = [permissions.IsAuthenticated, IsUser]

    def get(self, request):
        return Response({"message": "Hello User"})


class ProfileUpdateView(generics.UpdateAPIView):
    serializer_class = ProfileUpdateSerializer
    permission_classes = [permissions.IsAuthenticated]
    parser_classes = [MultiPartParser, FormParser, JSONParser]

    def get_object(self):
        return self.request.user

    def _get_changed_fields(self, old_data, new_data):
        changed = {}
        for key in old_data:
            if old_data[key] != new_data.get(key):
                changed[key] = {"old": old_data[key], "new": new_data.get(key)}
        return changed

    def put(self, request, *args, **kwargs):
        old_data = UserSerializer(self.get_object(), context={"request": request}).data
        response = super().put(request, *args, **kwargs)
        log_action(
            request,
            "PROFILE_UPDATE",
            target_user=request.user,
            additional_data={"action_type": "full_update", "changes": self._get_changed_fields(old_data, response.data)},
        )
        return response

    def patch(self, request, *args, **kwargs):
        old_data = UserSerializer(self.get_object(), context={"request": request}).data
        response = super().patch(request, *args, **kwargs)
        log_action(
            request,
            "PROFILE_UPDATE",
            target_user=request.user,
            additional_data={"action_type": "partial_update", "changes": self._get_changed_fields(old_data, response.data)},
        )
        return response


class ForgotPasswordView(APIView):
    permission_classes = [permissions.AllowAny]

    def post(self, request):
        email = request.data.get("email")
        if not email:
            return Response({"error": "Email is required."}, status=status.HTTP_400_BAD_REQUEST)

        try:
            user = User.objects.get(email__iexact=email)
        except User.DoesNotExist:
            log_action(
                request,
                "PASSWORD_RESET_REQUEST",
                additional_data={"status": "failed", "reason": "user_not_found", "attempted_email": email},
            )
            return Response(
                {"message": "If an account with this email exists, a password reset link has been sent."},
                status=status.HTTP_200_OK,
            )

        log_action(request, "PASSWORD_RESET_REQUEST", target_user=user)
        uid = urlsafe_base64_encode(force_bytes(user.pk))
        token = default_token_generator.make_token(user)
        frontend_url = getattr(settings, "FRONTEND_URL", "http://localhost:3000")
        reset_link = f"{frontend_url}/reset-password/{uid}/{token}"
        app_link = f"smsfrauddetector://reset-password/{uid}/{token}"

        try:
            send_mail(
                subject="Password Reset Request",
                message=(
                    f"Hello {user.first_name or user.username},\n\n"
                    f"You requested a password reset. Click the link below to reset your password:\n"
                    f"{reset_link}\n\n"
                    f"If you are using the Android app, you can also open this link:\n"
                    f"{app_link}\n\n"
                    f"This link will expire in 24 hours.\n\n"
                    f"If you didn't request this, please ignore this email."
                ),
                from_email=settings.DEFAULT_FROM_EMAIL,
                recipient_list=[user.email],
                fail_silently=False,
            )
        except Exception as exc:
            logger.exception("Failed to send password reset email to %s", email)
            log_action(
                request,
                "EMAIL_SEND_FAILURE",
                target_user=user,
                additional_data={"email_type": "password_reset", "error": str(exc)},
            )
            return Response(
                {"error": "Failed to send email. Please try again later."},
                status=status.HTTP_500_INTERNAL_SERVER_ERROR,
            )

        return Response(
            {"message": "If an account with this email exists, a password reset link has been sent."},
            status=status.HTTP_200_OK,
        )


class ResetPasswordView(APIView):
    permission_classes = [permissions.AllowAny]

    def post(self, request):
        uid = request.data.get("uid")
        token = request.data.get("token")
        new_password = request.data.get("new_password")
        confirm_password = request.data.get("confirm_password")

        if not all([uid, token, new_password]):
            return Response(
                {"error": "UID, token, and new password are required."},
                status=status.HTTP_400_BAD_REQUEST,
            )

        if confirm_password and new_password != confirm_password:
            return Response({"error": "Passwords do not match."}, status=status.HTTP_400_BAD_REQUEST)

        if len(new_password) < 8:
            return Response(
                {"error": "Password must be at least 8 characters long."},
                status=status.HTTP_400_BAD_REQUEST,
            )

        try:
            user_id = force_str(urlsafe_base64_decode(uid))
            user = User.objects.get(pk=user_id)
        except (TypeError, ValueError, OverflowError, User.DoesNotExist):
            log_action(
                request,
                "PASSWORD_RESET_COMPLETE",
                additional_data={"status": "failed", "reason": "invalid_reset_link"},
            )
            return Response({"error": "Invalid reset link."}, status=status.HTTP_400_BAD_REQUEST)

        if not default_token_generator.check_token(user, token):
            log_action(
                request,
                "PASSWORD_RESET_COMPLETE",
                target_user=user,
                additional_data={"status": "failed", "reason": "invalid_token"},
            )
            return Response({"error": "Invalid or expired reset link."}, status=status.HTTP_400_BAD_REQUEST)

        user.set_password(new_password)
        user.save()
        log_action(request, "PASSWORD_RESET_COMPLETE", target_user=user)
        return Response({"message": "Password has been reset successfully."}, status=status.HTTP_200_OK)


class UserListView(generics.ListAPIView):
    queryset = User.objects.all()
    serializer_class = UserSerializer
    permission_classes = [permissions.IsAuthenticated, IsAdmin]
    search_fields = ["username", "email", "first_name", "last_name", "role", "status"]
    ordering_fields = ["date_joined", "username", "email", "role", "status"]
    ordering = ["-date_joined"]

    def get_serializer_context(self):
        context = super().get_serializer_context()
        context["request"] = self.request
        return context

    def get(self, request, *args, **kwargs):
        log_action(request, "USER_LIST_ACCESS", additional_data={"accessed_by_role": request.user.role})
        return super().get(request, *args, **kwargs)


class UserDetailView(generics.RetrieveUpdateDestroyAPIView):
    queryset = User.objects.all()
    serializer_class = UserSerializer
    permission_classes = [permissions.IsAuthenticated, IsAdmin]

    def get_serializer_context(self):
        context = super().get_serializer_context()
        context["request"] = self.request
        return context

    def _get_changed_fields(self, old_data, new_data):
        changed = {}
        for key in old_data:
            if old_data[key] != new_data.get(key):
                changed[key] = {"old": old_data[key], "new": new_data.get(key)}
        return changed

    def retrieve(self, request, *args, **kwargs):
        instance = self.get_object()
        log_action(request, "USER_DETAIL_ACCESS", target_user=instance, additional_data={"accessed_by": request.user.email})
        return super().retrieve(request, *args, **kwargs)

    def update(self, request, *args, **kwargs):
        partial = kwargs.pop("partial", False)
        instance = self.get_object()

        if instance == request.user:
            return Response(
                {"error": "Cannot update your own account through this endpoint. Use profile update instead."},
                status=status.HTTP_400_BAD_REQUEST,
            )

        serializer = self.get_serializer(instance, data=request.data, partial=partial)
        serializer.is_valid(raise_exception=True)
        original_data = UserSerializer(instance, context={"request": request}).data
        self.perform_update(serializer)

        changes = self._get_changed_fields(original_data, serializer.data)
        log_action(
            request,
            "USER_UPDATE",
            target_user=serializer.instance,
            additional_data={
                "old_data": original_data,
                "new_data": serializer.data,
                "changed_fields": changes,
                "update_type": "partial" if partial else "full",
            },
        )
        return Response(serializer.data)

    def destroy(self, request, *args, **kwargs):
        instance = self.get_object()
        if instance == request.user:
            return Response({"error": "Cannot delete your own account."}, status=status.HTTP_400_BAD_REQUEST)

        user_data = UserSerializer(instance, context={"request": request}).data
        log_action(request, "USER_DELETE", target_user=instance, additional_data={"user_data": user_data})
        self.perform_destroy(instance)
        return Response({"message": f"User {user_data['email']} has been successfully deleted."}, status=status.HTTP_204_NO_CONTENT)


class AdminUserUpdateView(generics.UpdateAPIView):
    queryset = User.objects.all()
    serializer_class = UserSerializer
    permission_classes = [permissions.IsAuthenticated, IsAdmin]

    def get_serializer_context(self):
        context = super().get_serializer_context()
        context["request"] = self.request
        return context

    def _get_changed_fields(self, old_data, new_data):
        changed = {}
        for key in old_data:
            if old_data[key] != new_data.get(key):
                changed[key] = {"old": old_data[key], "new": new_data.get(key)}
        return changed

    def update(self, request, *args, **kwargs):
        partial = kwargs.pop("partial", False)
        instance = self.get_object()

        if instance == request.user:
            return Response(
                {"error": "Cannot update your own account through admin endpoints. Use profile update instead."},
                status=status.HTTP_400_BAD_REQUEST,
            )

        old_data = UserSerializer(instance, context={"request": request}).data
        serializer = self.get_serializer(instance, data=request.data, partial=partial)
        serializer.is_valid(raise_exception=True)
        self.perform_update(serializer)

        changes = self._get_changed_fields(old_data, serializer.data)
        log_action(
            request,
            "USER_UPDATE",
            target_user=instance,
            additional_data={
                "old_data": old_data,
                "new_data": serializer.data,
                "changed_fields": changes,
                "update_type": "partial" if partial else "full",
            },
        )
        return Response({"message": "User updated successfully.", "user": serializer.data})


class AdminUserDeleteView(generics.DestroyAPIView):
    queryset = User.objects.all()
    permission_classes = [permissions.IsAuthenticated, IsAdmin]

    def destroy(self, request, *args, **kwargs):
        instance = self.get_object()

        if instance == request.user:
            return Response({"error": "Cannot delete your own account."}, status=status.HTTP_400_BAD_REQUEST)

        if instance.role == "Admin":
            admin_count = User.objects.filter(role="Admin", is_active=True).count()
            if admin_count <= 1:
                return Response({"error": "Cannot delete the last active admin account."}, status=status.HTTP_400_BAD_REQUEST)

        user_data = UserSerializer(instance, context={"request": request}).data
        log_action(request, "USER_DELETE", target_user=instance, additional_data={"user_data": user_data})
        deleted_user_email = instance.email
        self.perform_destroy(instance)
        return Response(
            {"message": f"User {deleted_user_email} has been successfully deleted."},
            status=status.HTTP_200_OK,
        )


class UserActivateDeactivateView(APIView):
    permission_classes = [permissions.IsAuthenticated, IsAdmin]

    def patch(self, request, pk):
        try:
            user = User.objects.get(pk=pk)
        except User.DoesNotExist:
            return Response({"error": "User not found."}, status=status.HTTP_404_NOT_FOUND)

        if user == request.user:
            return Response({"error": "Cannot deactivate your own account."}, status=status.HTTP_400_BAD_REQUEST)

        if user.role == "Admin" and user.status == "Active":
            active_admin_count = User.objects.filter(role="Admin", status="Active").count()
            if active_admin_count <= 1:
                return Response({"error": "Cannot deactivate the last active admin account."}, status=status.HTTP_400_BAD_REQUEST)

        original_status = user.status
        original_is_active = user.is_active

        if user.status == "Active":
            user.deactivate()
            action = "deactivated"
            audit_action = "USER_DEACTIVATE"
        else:
            user.activate()
            action = "activated"
            audit_action = "USER_ACTIVATE"

        user.refresh_from_db()
        log_action(
            request,
            audit_action,
            target_user=user,
            additional_data={
                "previous_status": original_status,
                "new_status": user.status,
                "previous_is_active": original_is_active,
                "new_is_active": user.is_active,
            },
        )
        return Response(
            {
                "message": f"User {user.email} has been {action}.",
                "user": UserSerializer(user, context={"request": request}).data,
                "previous_status": original_status,
                "new_status": user.status,
            }
        )


class CurrentUserView(APIView):
    permission_classes = [permissions.IsAuthenticated]

    def get(self, request):
        return Response(UserSerializer(request.user, context={"request": request}).data)
