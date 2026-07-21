package com.smsai.smsfrauddetector.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        content()
    }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    trailingIcon: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
        if (trailingIcon) {
            Icon(imageVector = Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
fun StatusBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f)),
        color = Color.Transparent,
    ) {
        Text(
            text = text,
            color = color,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    SurfaceCard(modifier = modifier) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
            Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    accent: Brush = Brush.horizontalGradient(
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f),
        ),
    ),
    onClick: () -> Unit,
) {
    SurfaceCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(accent)
                .clickable(onClick = onClick)
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
            }
            Icon(imageVector = Icons.Rounded.ChevronRight, contentDescription = null)
        }
    }
}
