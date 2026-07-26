package com.example.cochehibrido.ui.components.charts

import androidx.compose.ui.geometry.Offset
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

        val p0 = points.getOrElse(completedSegments - 1) {
            points[completedSegments]
        }
        val p1 = points[completedSegments]
        val p2 = points[completedSegments + 1]
        val p3 = points.getOrElse(completedSegments + 2) {
            points[completedSegments + 1]
        }

        currentX = linePath.catmullRomPartialTo(
            p0,
            p1,
            p2,
            p3,
            segmentProgress,
            mapX,
            mapY
        ).x

        areaPath?.catmullRomPartialTo(
            p0,
            p1,
            p2,
            p3,
            segmentProgress,
            mapX,
            mapY
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

private fun Path.catmullRomPartialTo(
    p0: ChartPoint,
    p1: ChartPoint,
    p2: ChartPoint,
    p3: ChartPoint,
    progress: Float,
    mapX: (Double) -> Float,
    mapY: (Double) -> Float
): Offset {

    val start = Offset(
        x = mapX(p1.x),
        y = mapY(p1.y.toDouble())
    )
    val end = Offset(
        x = mapX(p2.x),
        y = mapY(p2.y.toDouble())
    )

    val control1 = Offset(
        x = start.x + (mapX(p2.x) - mapX(p0.x)) / 6f,
        y = start.y + (mapY(p2.y.toDouble()) - mapY(p0.y.toDouble())) / 6f
    )
    val control2 = Offset(
        x = end.x - (mapX(p3.x) - mapX(p1.x)) / 6f,
        y = end.y - (mapY(p3.y.toDouble()) - mapY(p1.y.toDouble())) / 6f
    )

    val firstLevel1 = start.lerpTo(control1, progress)
    val firstLevel2 = control1.lerpTo(control2, progress)
    val firstLevel3 = control2.lerpTo(end, progress)
    val secondLevel1 = firstLevel1.lerpTo(firstLevel2, progress)
    val secondLevel2 = firstLevel2.lerpTo(firstLevel3, progress)
    val partialEnd = secondLevel1.lerpTo(secondLevel2, progress)

    cubicTo(
        firstLevel1.x,
        firstLevel1.y,
        secondLevel1.x,
        secondLevel1.y,
        partialEnd.x,
        partialEnd.y
    )

    return partialEnd
}

private fun Offset.lerpTo(
    other: Offset,
    progress: Float
): Offset = Offset(
    x = x + (other.x - x) * progress,
    y = y + (other.y - y) * progress
)
