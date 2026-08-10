package com.bgr3108.kilonom.ui.components.charts

data class Axis(
    val min: Double,
    val max: Double,
    val step: Double,
    val ticks: List<Double>
)
