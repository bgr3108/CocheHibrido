package com.bgr3108.kilonom.ui.components.charts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope

internal fun DrawScope.drawGrid(
    style: ChartStyle,
    yAxis: Axis,
    leftPadding: Float,
    chartWidth: Float,
    mapY: (Double) -> Float
) {

    yAxis.ticks.forEach { tick ->

        val y = mapY(tick)

        drawLine(
            color = style.gridColor,
            start = Offset(leftPadding, y),
            end = Offset(leftPadding + chartWidth, y),
            strokeWidth = 1f
        )
    }
}
