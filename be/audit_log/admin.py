from django.contrib import admin

from .models import AuditLog


@admin.register(AuditLog)
class AuditLogAdmin(admin.ModelAdmin):
    list_display = ("id", "action", "user", "target_user", "ip_address", "timestamp")
    list_filter = ("action", "timestamp")
    search_fields = ("user__email", "user__username", "target_user__email", "target_user__username")
    readonly_fields = ("timestamp",)
