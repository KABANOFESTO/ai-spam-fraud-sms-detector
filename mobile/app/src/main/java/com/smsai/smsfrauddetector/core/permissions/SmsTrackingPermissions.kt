package com.smsai.smsfrauddetector.core.permissions

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.content.ContextCompat

object SmsTrackingPermissions {
    fun isDefaultSmsApp(context: Context): Boolean {
        val legacyDefault = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        if (legacyDefault) return true

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
        } else {
            false
        }
    }

    fun isSmsPermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    }

    fun isNotificationPermissionGranted(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun canTrackAutomatically(context: Context): Boolean {
        return isDefaultSmsApp(context) && isSmsPermissionGranted(context) && isNotificationPermissionGranted(context)
    }

    fun buildDefaultSmsRoleIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.createRequestRoleIntent(RoleManager.ROLE_SMS)
                ?: buildLegacyDefaultSmsIntent(context)
        } else {
            buildLegacyDefaultSmsIntent(context)
        }
    }

    private fun buildLegacyDefaultSmsIntent(context: Context): Intent {
        return Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
            putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }
    }
}
