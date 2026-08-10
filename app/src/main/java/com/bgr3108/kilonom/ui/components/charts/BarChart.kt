package com.bgr3108.kilonom.ui.components.charts

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun BarChart(
    points: List<ChartPoint>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    xLabelFormatter: (Int) -> String = { it.toString() },
    yLabelFormatter: (Double) -> String = { it.toString() },
    barColor: Color = MaterialTheme.colorScheme.primary,
    axisColor: Color = MaterialTheme.colorScheme.outline,
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    if (points.isEmpty()) return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
            .semantics { this.contentDescription = contentDescription }
    ) {
        val textPaint = Paint().apply {
            color = textColor.toArgb()
            textSize = 12.dp.toPx()
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val xTextPaint = Paint().apply {
            color = textColor.toArgb()
            textSize = 12.dp.toPx()
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val topPadding = ChartTopPadding.toPx()
        val bottomPadding = ChartBottomPadding.toPx()
        val rightPadding = ChartRightPadding.toPx()
        val maximumValue = points.maxOf { it.y.toDouble() }.coerceAtLeast(1.0)
        val yAxis = buildAxis(min = 0.0, max = maximumValue)
        val leftPadding = maxOf(
            ChartMinimumLeftPadding.toPx(),
            yAxis.ticks.maxOf { tick ->
                textPaint.measureText(yLabelFormatter(tick))
            } + ChartYAxisLabelGap.toPx() + ChartYAxisLabelStartInset.toPx()
        )
        val chartWidth = (size.width - leftPadding - rightPadding).coerceAtLeast(0f)
        val chartHeight = (size.height - topPadding - bottomPadding).coerceAtLeast(0f)
        val chartBottom = topPadding + chartHeight
        val yRange = (yAxis.max - yAxis.min).takeIf { it > 0.0 } ?: 1.0
        val mapY: (Double) -> Float = { value ->
            topPadding + chartHeight -
                (((value - yAxis.min) / yRange) * chartHeight).toFloat()
        }
        val style = ChartStyle(
            axisColor = axisColor,
            gridColor = gridColor.copy(alpha = 0.45f),
            textColor = textColor
        )
        val emptyXAxis = Axis(
            min = 0.0,
            max = 1.0,
            step = 1.0,
            ticks = emptyList()
        )

        drawGrid(
            style = style,
            yAxis = yAxis,
            leftPadding = leftPadding,
            chartWidth = chartWidth,
            mapY = mapY
        )
        drawAxes(
            style = style,
            xAxis = emptyXAxis,
            yAxis = yAxis,
            leftPadding = leftPadding,
            topPadding = topPadding,
            chartWidth = chartWidth,
            chartHeight = chartHeight,
            mapX = { leftPadding },
            mapY = mapY
        )
        drawAxisLabels(
            xAxis = emptyXAxis,
            yAxis = yAxis,
            leftPadding = leftPadding,
            topPadding = topPadding,
            chartHeight = chartHeight,
            mapX = { leftPadding },
            mapY = mapY,
            textPaint = textPaint,
            xTextPaint = xTextPaint,
            xLabelFormatter = { it.toString() },
            yLabelFormatter = yLabelFormatter,
            yLabelGap = ChartYAxisLabelGap.toPx()
        )

        val slotWidth = chartWidth / points.size
        val barWidth = (slotWidth * 0.6f).coerceAtMost(48.dp.toPx())
        val minimumBarHeight = 1.dp.toPx()

        points.forEachIndexed { index, point ->
            val centerX = leftPadding + slotWidth * (index + 0.5f)
            val top = mapY(point.y.toDouble())
            val height = (chartBottom - top).coerceAtLeast(minimumBarHeight)

            drawRect(
                color = barColor,
                topLeft = Offset(centerX - barWidth / 2, chartBottom - height),
                size = Size(barWidth, height)
            )
            drawLine(
                color = axisColor,
                start = Offset(centerX, chartBottom),
                end = Offset(centerX, chartBottom + 5.dp.toPx()),
                strokeWidth = 2f
            )
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    xLabelFormatter(index),
                    centerX,
                    chartBottom + 18.dp.toPx(),
                    xTextPaint
                )
            }
        }
    }
}
