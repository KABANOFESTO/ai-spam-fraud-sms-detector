from django.urls import path

from .views import (
    AdminOnlyView,
    AdminUserCreateView,
    AdminUserDeleteView,
    AdminUserUpdateView,
    BootstrapAdminView,
    CurrentUserView,
    ForgotPasswordView,
    LogoutView,
    MyTokenObtainView,
    ProfileUpdateView,
    RegisterView,
    ResetPasswordView,
    UserActivateDeactivateView,
    UserDetailView,
    UserListView,
    UserOnlyView,
)

urlpatterns = [
    path('register/', RegisterView.as_view(), name='register'),
    path('bootstrap-admin/', BootstrapAdminView.as_view(), name='bootstrap-admin'),
    path('login/', MyTokenObtainView.as_view(), name='login'),
    path('logout/', LogoutView.as_view(), name='logout'),
    path('admin-data/', AdminOnlyView.as_view(), name='admin-data'),
    path('user-data/', UserOnlyView.as_view(), name='user-data'),
    path('update-profile/', ProfileUpdateView.as_view(), name='update-profile'),
    path('forgot-password/', ForgotPasswordView.as_view(), name='forgot-password'),
    path('reset-password/', ResetPasswordView.as_view(), name='reset-password'),
    path('users/', UserListView.as_view(), name='user-list'),
    path('users/<int:pk>/', UserDetailView.as_view(), name='user-detail'),
    path('admin/users/create/', AdminUserCreateView.as_view(), name='admin-user-create'),
    path('admin/users/<int:pk>/update/', AdminUserUpdateView.as_view(), name='admin-user-update'),
    path('admin/users/<int:pk>/delete/', AdminUserDeleteView.as_view(), name='admin-user-delete'),
    path('admin/users/<int:pk>/toggle-active/', UserActivateDeactivateView.as_view(), name='admin-user-toggle-active'),
    path('me/', CurrentUserView.as_view(), name='current-user'),
]
