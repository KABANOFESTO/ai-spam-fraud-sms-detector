package com.smsai.smsfrauddetector.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize

private val SmsDarkScheme = darkColorScheme(
    primary = Color(0xFF34D399),
    secondary = Color(0xFF60A5FA),
    tertiary = Color(0xFFFBBF24),
    background = Color(0xFF081018),
    surface = Color(0xFF101A26),
    surfaceVariant = Color(0xFF152131),
    onPrimary = Color(0xFF06110B),
    onSecondary = Color(0xFF06111C),
    onTertiary = Color(0xFF211700),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
)

private val SmsLightScheme = lightColorScheme(
    primary = Color(0xFF0F766E),
    secondary = Color(0xFF2563EB),
    tertiary = Color(0xFFD97706),
)

@Composable
fun SmsFraudTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> {
            dynamicDarkColorScheme(LocalContext.current)
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !darkTheme -> {
            dynamicLightColorScheme(LocalContext.current)
        }
        darkTheme -> SmsDarkScheme
        else -> SmsLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SmsTypography,
        content = content,
    )
}

@Composable
fun SmsGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF050B14),
                        Color(0xFF081018),
                        Color(0xFF0D1724),
                    ),
                ),
            ),
    ) {
        content()
    }
}

