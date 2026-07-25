package com.example.cochehibrido.ui.components.charts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope

internal fun DrawScope.drawPoints(
    points: List<ChartPoint>,
    animationProgress: Float,
    style: ChartStyle,
    mapX: (Double) -> Float,
    mapY: (Double) -> Float
) {

    val totalSegments = points.lastIndex
    val progress = animationProgress * totalSegments

    points.forEachIndexed { index, point ->

        val pointProgress =
            (progress - (index - 1))
                .coerceIn(0f, 1f)

        drawCircle(
            color = style.pointColor,
            radius = style.pointRadius * pointProgress,
            center = Offset(
                x = mapX(point.x),
                y = mapY(point.y.toDouble())
            )
        )
    }
}