package com.bgr3108.kilonom.ui.components.charts

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp

internal fun DrawScope.drawAxes(
    style: ChartStyle,
    xAxis: Axis,
    yAxis: Axis,
    leftPadding: Float,
    topPadding: Float,
    chartWidth: Float,
    chartHeight: Float,
    mapX: (Double) -> Float,
    mapY: (Double) -> Float
) {

    // Eje Y
    drawLine(
        color = style.axisColor,
        start = Offset(leftPadding, topPadding),
        end = Offset(leftPadding, topPadding + chartHeight),
        strokeWidth = 2f
    )

    yAxis.ticks.forEach { tick ->

        val y = mapY(tick)

        drawLine(
            color = style.axisColor,
            start = Offset(leftPadding - 5.dp.toPx(), y),
            end = Offset(leftPadding, y),
            strokeWidth = 2f
        )
    }

    // Eje X
    drawLine(
        color = style.axisColor,
        start = Offset(leftPadding, topPadding + chartHeight),
        end = Offset(leftPadding + chartWidth, topPadding + chartHeight),
        strokeWidth = 2f
    )

    xAxis.ticks.forEach { tick ->

        val x = mapX(tick)

        drawLine(
            color = style.axisColor,
            start = Offset(x, topPadding + chartHeight),
            end = Offset(x, topPadding + chartHeight + 5.dp.toPx()),
            strokeWidth = 2f
        )
    }
}

internal fun DrawScope.drawAxisLabels(
    xAxis: Axis,
    yAxis: Axis,
    leftPadding: Float,
    topPadding: Float,
    chartHeight: Float,
    mapX: (Double) -> Float,
    mapY: (Double) -> Float,
    textPaint: Paint,
    xTextPaint: Paint,
    xLabelFormatter: (Double) -> String,
    yLabelFormatter: (Double) -> String,
    yLabelGap: Float
) {

    drawIntoCanvas { canvas ->

        yAxis.ticks.forEach { tick ->

            val y = mapY(tick)

            canvas.nativeCanvas.drawText(
                yLabelFormatter(tick),
                leftPadding - yLabelGap,
                y + 4.dp.toPx(),
                textPaint
            )
        }

        xAxis.ticks.forEach { tick ->

            val x = mapX(tick)

            canvas.nativeCanvas.drawText(
                xLabelFormatter(tick),
                x,
                topPadding + chartHeight + 18.dp.toPx(),
                xTextPaint
            )
        }
    }
}
