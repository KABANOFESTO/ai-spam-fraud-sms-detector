package com.smsai.smsfrauddetector.features.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsai.smsfrauddetector.core.designsystem.components.ErrorStateCard
import com.smsai.smsfrauddetector.core.designsystem.theme.SmsGradientBackground

@Composable
fun SplashScreen(
    loading: Boolean,
    message: String,
    onRetry: (() -> Unit)? = null,
) {
    SmsGradientBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "SMS Fraud Detector",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (loading) {
                CircularProgressIndicator()
            } else if (message.isNotBlank() && onRetry != null) {
                ErrorStateCard(
                    message = message,
                    retryText = "Try again",
                    onRetry = onRetry,
                )
            }
        }
    }
}
