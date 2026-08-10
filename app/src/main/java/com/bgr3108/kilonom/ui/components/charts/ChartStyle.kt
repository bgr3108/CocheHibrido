package com.bgr3108.kilonom.ui.components.charts

import androidx.compose.ui.graphics.Color

data class ChartStyle(
    val lineColor: Color = Color(0xFF1976D2),
    val pointColor: Color = Color(0xFF1976D2),
    val axisColor: Color = Color(0xFF505866),
    val gridColor: Color = Color(0xFF505866).copy(alpha = 0.20f),
    val textColor: Color = Color(0xFF505866),
    val lineWidth: Float = 4f,
    val pointRadius: Float = 6f,
    val showGradient: Boolean = true,
    val gradientAlpha: Float = 0.20f
)
