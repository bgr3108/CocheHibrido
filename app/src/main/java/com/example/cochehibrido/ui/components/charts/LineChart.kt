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

        val rawMinX = points.minOf { it.x }
        val rawMaxX = points.maxOf { it.x }

        val paddingX =
            if (rawMaxX == rawMinX) 100.0
            else (rawMaxX - rawMinX) * 0.05

        val minX = rawMinX
        val maxX = rawMaxX + paddingX

        val rangeX = maxX - minX

        val range =
            if (maxValue == minValue)
                1f
            else
                maxValue - minValue

        drawLine(
            color = AxisColor,
            start = Offset(leftPadding, topPadding),
            end = Offset(leftPadding, topPadding + chartHeight),
            strokeWidth = 2f
        )
        val yTicks = 4

        repeat(yTicks + 1) { i ->

            val y = topPadding + (chartHeight / yTicks) * i

            drawLine(
                color = AxisColor,
                start = Offset(leftPadding - 5.dp.toPx(), y),
                end = Offset(leftPadding, y),
                strokeWidth = 2f
            )
        }
        repeat(yTicks + 1) { i ->

            val y = topPadding + (chartHeight / yTicks) * i

            val value =
                maxValue - ((maxValue - minValue) / yTicks) * i

            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.getDefault(), "%.1f", value),
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
        val xTicks = 4

        repeat(xTicks + 1) { i ->

            val x = leftPadding + (chartWidth / xTicks) * i

            drawLine(
                color = AxisColor,
                start = Offset(x, topPadding + chartHeight),
                end = Offset(x, topPadding + chartHeight + 5.dp.toPx()),
                strokeWidth = 2f
            )
        }
        points.forEachIndexed { index, point ->

            val x = leftPadding +
                    (((point.x - minX) / rangeX) * chartWidth).toFloat()

            xTextPaint.textAlign =
                when (index) {
                    0 -> Paint.Align.LEFT
                    points.lastIndex -> Paint.Align.RIGHT
                    else -> Paint.Align.CENTER
                }

            drawContext.canvas.nativeCanvas.drawText(
                NumberFormat.getIntegerInstance().format(point.x),
                x,
                topPadding + chartHeight + 18.dp.toPx(),
                xTextPaint
            )
        }

        for (i in 0 until points.lastIndex) {

            val start = Offset(
                x = leftPadding +
                        (((points[i].x - minX) / rangeX) * chartWidth).toFloat(),

                y = topPadding +
                        chartHeight -
                        (((points[i].y - minValue) / range) * chartHeight)
            )
            val end = Offset(
                x = leftPadding +
                        (((points[i + 1].x - minX) / rangeX) * chartWidth).toFloat(),

                y = topPadding +
                        chartHeight -
                        (((points[i + 1].y - minValue) / range) * chartHeight)
            )
            repeat(yTicks + 1) { i ->

                val y = topPadding + (chartHeight / yTicks) * i

                drawLine(
                    color = AxisColor.copy(alpha = 0.15f),
                    start = Offset(leftPadding, y),
                    end = Offset(leftPadding + chartWidth, y),
                    strokeWidth = 1f
                )
            }

            drawLine(
                color = Color(0xFF1976D2),
                start = start,
                end = end,
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }

        points.forEachIndexed { _, point ->

            drawCircle(
                color = Color(0xFF1976D2),
                radius = 6f,
                center = Offset(
                    x = leftPadding +
                            (((point.x - minX) / rangeX) * chartWidth).toFloat(),

                    y = topPadding +
                            chartHeight -
                            (((point.y - minValue) / range) * chartHeight)
                )
            )
        }
    }
}