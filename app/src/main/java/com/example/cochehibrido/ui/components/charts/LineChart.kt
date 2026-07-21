package com.example.cochehibrido.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.cochehibrido.domain.ChartPoint
import androidx.compose.ui.graphics.nativeCanvas
import java.util.Locale
import androidx.compose.ui.graphics.toArgb
import android.graphics.Paint
import java.text.NumberFormat

private val ChartLeftPadding = 40.dp
private val ChartRightPadding = 20.dp
private val ChartTopPadding = 12.dp
private val ChartBottomPadding = 20.dp
private val AxisColor = Color(0xFF505866)



@Composable
fun LineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier
) {

    if (points.size < 2) return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
    ) {
        val textPaint = Paint().apply {
            color = AxisColor.toArgb()
            textSize = 12.dp.toPx()
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val xTextPaint = Paint().apply {
            color = AxisColor.toArgb()
            textSize = 12.dp.toPx()
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val leftPadding = ChartLeftPadding.toPx()
        val rightPadding = ChartRightPadding.toPx()
        val topPadding = ChartTopPadding.toPx()
        val bottomPadding = ChartBottomPadding.toPx()

        val chartWidth = size.width - leftPadding - rightPadding
        val chartHeight = size.height - topPadding - bottomPadding

        val rawMax = points.maxOf { it.y }
        val rawMin = points.minOf { it.y }

        val padding =
            if (rawMax == rawMin) {
                1f
            } else {
                maxOf((rawMax - rawMin) * 0.15f, 0.5f)
            }

        val maxValue = rawMax + padding
        val minValue = rawMin - padding

        val yAxis = buildAxis(
            min = minValue.toDouble(),
            max = maxValue.toDouble()
        )

        val rawMinX = points.minOf { it.x }
        val rawMaxX = points.maxOf { it.x }

        val xAxis = buildAxis(rawMinX, rawMaxX)

        val xRange = xAxis.max - xAxis.min

        fun mapX(value: Double): Float =
            leftPadding +
                    (((value - xAxis.min) / xRange) * chartWidth).toFloat()

        val yRange = yAxis.max - yAxis.min

        fun mapY(value: Double): Float =
            topPadding +
                    chartHeight -
                    (((value - yAxis.min) / yRange) * chartHeight).toFloat()

        drawLine(
            color = AxisColor,
            start = Offset(leftPadding, topPadding),
            end = Offset(leftPadding, topPadding + chartHeight),
            strokeWidth = 2f
        )

        yAxis.ticks.forEach { tick ->

            val y = mapY(tick)

            drawLine(
                color = AxisColor,
                start = Offset(leftPadding - 5.dp.toPx(), y),
                end = Offset(leftPadding, y),
                strokeWidth = 2f
            )
        }
        yAxis.ticks.forEach { tick ->

            val y = mapY(tick)

            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.getDefault(), "%.1f", tick),
                leftPadding - 14.dp.toPx(),
                y + 4.dp.toPx(),
                textPaint
            )
        }

        drawLine(
            color = AxisColor,
            start = Offset(leftPadding, topPadding + chartHeight),
            end = Offset(leftPadding + chartWidth, topPadding + chartHeight),
            strokeWidth = 2f
        )

        xAxis.ticks.forEach { tick ->

            val x = mapX(tick)

            drawLine(
                color = AxisColor,
                start = Offset(x, topPadding + chartHeight),
                end = Offset(x, topPadding + chartHeight + 5.dp.toPx()),
                strokeWidth = 2f
            )
        }

        xAxis.ticks.forEach { tick ->

            val x = mapX(tick)

            drawContext.canvas.nativeCanvas.drawText(
                NumberFormat.getIntegerInstance().format(tick.toInt()),
                x,
                topPadding + chartHeight + 18.dp.toPx(),
                xTextPaint
            )
        }

        yAxis.ticks.forEach { tick ->

            val y = mapY(tick)

            drawLine(
                color = AxisColor.copy(alpha = 0.20f),
                start = Offset(leftPadding, y),
                end = Offset(leftPadding + chartWidth, y),
                strokeWidth = 1f
            )
        }

        for (i in 0 until points.lastIndex) {

            val start = Offset(
                x = mapX(points[i].x),
                y = mapY(points[i].y.toDouble())
            )

            val end = Offset(
                x = mapX(points[i + 1].x),
                y = mapY(points[i + 1].y.toDouble())
            )

            drawLine(
                color = Color(0xFF1976D2),
                start = start,
                end = end,
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }

        points.forEach { point ->

            drawCircle(
                color = Color(0xFF1976D2),
                radius = 6f,
                center = Offset(
                    x = mapX(point.x),
                    y = mapY(point.y.toDouble())
                )
            )
        }
    }
}