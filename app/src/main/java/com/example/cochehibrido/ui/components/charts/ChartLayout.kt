package com.example.cochehibrido.ui.components.charts

import androidx.compose.ui.geometry.Size

internal data class ChartLayout(
    val leftPadding: Float,
    val rightPadding: Float,
    val topPadding: Float,
    val bottomPadding: Float,
    val chartWidth: Float,
    val chartHeight: Float,
    val xAxis: Axis,
    val yAxis: Axis
) {

    fun mapX(value: Double): Float {

        val range = xAxis.max - xAxis.min

        return leftPadding +
                (((value - xAxis.min) / range) * chartWidth).toFloat()
    }

    fun mapY(value: Double): Float {

        val range = yAxis.max - yAxis.min

        return topPadding +
                chartHeight -
                (((value - yAxis.min) / range) * chartHeight).toFloat()
    }
}

internal fun buildChartLayout(
    size: Size,
    points: List<ChartPoint>,
    leftPadding: Float,
    rightPadding: Float,
    topPadding: Float,
    bottomPadding: Float,
    xTicks: List<Double>? = null
): ChartLayout {

    val chartWidth =
        (size.width - leftPadding - rightPadding)
            .coerceAtLeast(0f)

    val chartHeight =
        (size.height - topPadding - bottomPadding)
            .coerceAtLeast(0f)

    val rawMinX = points.minOf { it.x }
    val rawMaxX = points.maxOf { it.x }

    val rawMinY = points.minOf { it.y }
    val rawMaxY = points.maxOf { it.y }

    val padding =
        if (rawMaxY == rawMinY) {
            1f
        } else {
            maxOf(
                (rawMaxY - rawMinY) * ChartDefaults.PaddingPercentage,
                ChartDefaults.MinPadding
            )
        }

    val yAxis = buildAxis(
        min = (rawMinY - padding).toDouble(),
        max = (rawMaxY + padding).toDouble(),
        targetTicks = ChartDefaults.TargetTicks
    )
    val xAxis = xTicks
        ?.distinct()
        ?.sorted()
        ?.takeIf { it.size >= 2 }
        ?.let { ticks ->
            Axis(
                min = ticks.first(),
                max = ticks.last(),
                step = (ticks.last() - ticks.first()) / (ticks.size - 1),
                ticks = ticks
            )
        }
        ?: buildAxis(
            min = rawMinX,
            max = rawMaxX,
            targetTicks = ChartDefaults.TargetTicks
        )

    return ChartLayout(
        leftPadding = leftPadding,
        rightPadding = rightPadding,
        topPadding = topPadding,
        bottomPadding = bottomPadding,
        chartWidth = chartWidth,
        chartHeight = chartHeight,
        xAxis = xAxis,
        yAxis = yAxis
    )
}
