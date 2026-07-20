from django.contrib import admin

from .models import FraudReport


@admin.register(FraudReport)
class FraudReportAdmin(admin.ModelAdmin):
    list_display = ("id", "user", "status", "analysis", "reviewed_by", "created_at", "updated_at")
    list_filter = ("status", "created_at", "updated_at")
    search_fields = ("user__email", "user__username", "sms_message", "notes", "admin_notes")
    readonly_fields = ("created_at", "updated_at", "reviewed_at")
