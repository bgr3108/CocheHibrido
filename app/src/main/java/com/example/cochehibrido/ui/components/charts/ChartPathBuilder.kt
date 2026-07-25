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

    val completedSegments =
        animation.toInt()
            .coerceAtMost(totalSegments)

    val segmentProgress =
        animation - completedSegments

    val first = points.first()

    val firstX = mapX(first.x)
    val firstY = mapY(first.y.toDouble())

    var currentX = firstX

    linePath.moveTo(firstX, firstY)
    areaPath?.moveTo(firstX, firstY)

    for (i in 0 until completedSegments) {

        val p0 = points.getOrElse(i - 1) { points[i] }
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points.getOrElse(i + 2) { points[i + 1] }

        linePath.catmullRomTo(
            p0,
            p1,
            p2,
            p3,
            mapX,
            mapY
        )

        areaPath?.catmullRomTo(
            p0,
            p1,
            p2,
            p3,
            mapX,
            mapY
        )
    }
    if (
        completedSegments < totalSegments
    ) {

        val start =
            points[completedSegments]

        val end =
            points[completedSegments + 1]

        val startX = mapX(start.x)
        val startY = mapY(start.y.toDouble())

        val endX = mapX(end.x)
        val endY = mapY(end.y.toDouble())

        val partialX =
            startX + (endX - startX) * segmentProgress

        val partialY =
            startY + (endY - startY) * segmentProgress

        currentX = partialX

        linePath.lineTo(
            partialX,
            partialY
        )

        areaPath?.lineTo(
            partialX,
            partialY
        )

    }

    if (completedSegments == totalSegments) {
        currentX = mapX(points.last().x)
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
private fun Path.catmullRomTo(
    p0: ChartPoint,
    p1: ChartPoint,
    p2: ChartPoint,
    p3: ChartPoint,
    mapX: (Double) -> Float,
    mapY: (Double) -> Float
) {

    val x1 = mapX(p1.x)
    val y1 = mapY(p1.y.toDouble())

    val x2 = mapX(p2.x)
    val y2 = mapY(p2.y.toDouble())

    val c1x = x1 + (mapX(p2.x) - mapX(p0.x)) / 6f
    val c1y = y1 + (mapY(p2.y.toDouble()) - mapY(p0.y.toDouble())) / 6f

    val c2x = x2 - (mapX(p3.x) - mapX(p1.x)) / 6f
    val c2y = y2 - (mapY(p3.y.toDouble()) - mapY(p1.y.toDouble())) / 6f

    cubicTo(
        c1x,
        c1y,
        c2x,
        c2y,
        x2,
        y2
    )
}