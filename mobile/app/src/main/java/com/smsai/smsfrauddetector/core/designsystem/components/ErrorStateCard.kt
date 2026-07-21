package com.smsai.smsfrauddetector.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ErrorStateCard(
    message: String,
    modifier: Modifier = Modifier,
    retryText: String = "Retry",
    onRetry: (() -> Unit)? = null,
) {
    SurfaceCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Something needs attention",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
            if (onRetry != null) {
                PrimaryButton(
                    text = retryText,
                    onClick = onRetry,
                    trailingIcon = true,
                )
            }
        }
    }
}

