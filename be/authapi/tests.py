from django.contrib.auth import get_user_model
from django.urls import reverse
from rest_framework import status
from rest_framework.test import APITestCase


class BootstrapAdminTests(APITestCase):
    def setUp(self):
        self.User = get_user_model()
        self.url = reverse("bootstrap-admin")

    def test_bootstrap_admin_creates_first_admin(self):
        payload = {
            "username": "admin1",
            "email": "admin1@example.com",
            "password": "StrongPass123!",
            "first_name": "First",
            "last_name": "Admin",
        }

        response = self.client.post(self.url, payload, format="json")

        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(self.User.objects.filter(role="Admin").count(), 1)
        user = self.User.objects.get(email="admin1@example.com")
        self.assertTrue(user.is_staff)
        self.assertTrue(user.is_superuser)
        self.assertEqual(response.data["user"]["role"], "Admin")

    def test_bootstrap_admin_rejects_second_admin(self):
        self.User.objects.create_user(
            username="existing",
            email="existing@example.com",
            password="StrongPass123!",
            role="Admin",
            status="Active",
            is_staff=True,
            is_superuser=True,
        )

        payload = {
            "username": "admin2",
            "email": "admin2@example.com",
            "password": "StrongPass123!",
            "first_name": "Second",
            "last_name": "Admin",
        }

        response = self.client.post(self.url, payload, format="json")

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn("already exists", response.data["error"])
