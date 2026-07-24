package com.example.cochehibrido.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import java.util.Locale
import androidx.compose.ui.graphics.toArgb
import android.graphics.Paint
import java.text.NumberFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke

private val ChartLeftPadding = 40.dp
private val ChartRightPadding = 20.dp
private val ChartTopPadding = 12.dp
private val ChartBottomPadding = 20.dp



@Composable
fun LineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    style: ChartStyle = ChartStyle(),
    xLabelFormatter: (Double) -> String = {
        NumberFormat.getIntegerInstance().format(it.toInt())
    },
    yLabelFormatter: (Double) -> String = {
        String.format(Locale.getDefault(), "%.1f", it)
    }
) {

    if (points.size < 2) return

    val animationProgress = remember {
        Animatable(0f)
    }

    LaunchedEffect(points) {

        animationProgress.snapTo(0f)

        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 1200,
                easing = FastOutSlowInEasing
            )
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
    ) {
        val textPaint = Paint().apply {
            color = style.textColor.toArgb()
            textSize = 12.dp.toPx()
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        val xTextPaint = Paint().apply {
            color = style.textColor.toArgb()
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
                maxOf(
                    (rawMax - rawMin) * ChartDefaults.PaddingPercentage,
                    ChartDefaults.MinPadding
                )
            }

        val maxValue = rawMax + padding
        val minValue = rawMin - padding

        val yAxis = buildAxis(
            min = minValue.toDouble(),
            max = maxValue.toDouble(),
            targetTicks = ChartDefaults.TargetTicks
        )

        val rawMinX = points.minOf { it.x }
        val rawMaxX = points.maxOf { it.x }

        val xAxis = buildAxis(
            rawMinX,
            rawMaxX,
            targetTicks = ChartDefaults.TargetTicks
        )

        val xRange = xAxis.max - xAxis.min

        fun mapX(value: Double): Float =
            leftPadding +
                    (((value - xAxis.min) / xRange) * chartWidth).toFloat()

        val yRange = yAxis.max - yAxis.min

        fun mapY(value: Double): Float =
            topPadding +
                    chartHeight -
                    (((value - yAxis.min) / yRange) * chartHeight).toFloat()

        drawGrid(
            style = style,
            yAxis = yAxis,
            leftPadding = leftPadding,
            chartWidth = chartWidth,
            mapY = ::mapY
        )

        drawAxes(
            style = style,
            xAxis = xAxis,
            yAxis = yAxis,
            leftPadding = leftPadding,
            topPadding = topPadding,
            chartWidth = chartWidth,
            chartHeight = chartHeight,
            mapX = ::mapX,
            mapY = ::mapY
        )

        drawAxisLabels(
            xAxis = xAxis,
            yAxis = yAxis,
            leftPadding = leftPadding,
            topPadding = topPadding,
            chartHeight = chartHeight,
            mapX = ::mapX,
            mapY = ::mapY,
            textPaint = textPaint,
            xTextPaint = xTextPaint,
            xLabelFormatter = xLabelFormatter,
            yLabelFormatter = yLabelFormatter
        )

        val paths = buildChartPaths(
            points = points,
            progress = animationProgress.value,
            mapX = ::mapX,
            mapY = ::mapY,
            style = style,
            chartBottom = topPadding + chartHeight
        )

        paths.area?.let {
            drawPath(
                path = it,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        style.lineColor.copy(alpha = style.gradientAlpha),
                        style.lineColor.copy(alpha = 0f)
                    ),
                    startY = topPadding,
                    endY = topPadding + chartHeight
                )
            )
        }

        drawPath(
            path = paths.line,
            color = style.lineColor,
            style = Stroke(
                width = style.lineWidth,
                cap = StrokeCap.Round
            )
        )

        val totalSegments = points.lastIndex
        val progress = animationProgress.value * totalSegments

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
}