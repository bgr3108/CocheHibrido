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
    bottomPadding: Float
): ChartLayout {

    val chartWidth = size.width - leftPadding - rightPadding
    val chartHeight = size.height - topPadding - bottomPadding

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
    val xAxis = buildAxis(
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