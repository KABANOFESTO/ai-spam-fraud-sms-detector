package com.smsai.smsfrauddetector.core.utils

import java.util.Locale

fun Double.toSafePercent(decimals: Int = 1): String {
    val normalized = when {
        isNaN() || isInfinite() -> 0.0
        this < 0.0 -> 0.0
        this <= 1.0 -> (this * 100.0).coerceIn(0.0, 100.0)
        this <= 100.0 -> coerceIn(0.0, 100.0)
        else -> 100.0
    }
    val format = "%.${decimals}f%%"
    return String.format(Locale.getDefault(), format, normalized)
}
