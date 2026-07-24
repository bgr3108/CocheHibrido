package com.example.cochehibrido.ui.components.charts

import androidx.compose.ui.graphics.Path

internal fun buildChartPaths(
    points: List<ChartPoint>,
    progress: Float,
    mapX: (Double) -> Float,
    mapY: (Double) -> Float,
    style: ChartStyle,
    chartBottom: Float
): ChartPaths {

    val linePath = Path()
    val areaPath =
        if (style.showGradient) Path() else null

    val totalSegments = points.lastIndex

    val animation = progress * totalSegments
    val completedSegments = animation.toInt()
    val segmentProgress = animation - completedSegments

    val first = points.first()

    val firstX = mapX(first.x)
    val firstY = mapY(first.y.toDouble())

    linePath.moveTo(firstX, firstY)
    areaPath?.moveTo(firstX, firstY)

    for (i in 0 until completedSegments.coerceAtMost(totalSegments)) {

        val point = points[i + 1]

        val x = mapX(point.x)
        val y = mapY(point.y.toDouble())

        linePath.lineTo(x, y)
        areaPath?.lineTo(x, y)
    }

    val currentX =
        if (completedSegments < totalSegments) {

            val start = points[completedSegments]
            val end = points[completedSegments + 1]

            val startX = mapX(start.x)
            val startY = mapY(start.y.toDouble())

            val endX = mapX(end.x)
            val endY = mapY(end.y.toDouble())

            val partialX =
                startX + (endX - startX) * segmentProgress

            val partialY =
                startY + (endY - startY) * segmentProgress

            linePath.lineTo(partialX, partialY)
            areaPath?.lineTo(partialX, partialY)

            partialX

        } else {

            mapX(points.last().x)
        }

    areaPath?.apply {

        lineTo(
            currentX,
            chartBottom
        )

        lineTo(
            firstX,
            chartBottom
        )

        close()
    }

    return ChartPaths(
        line = linePath,
        area = areaPath
    )
}