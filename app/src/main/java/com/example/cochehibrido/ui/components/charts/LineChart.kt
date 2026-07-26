package com.example.cochehibrido.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    },
    xTicks: List<Double>? = null
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
        val rightPadding = ChartRightPadding.toPx()
        val topPadding = ChartTopPadding.toPx()
        val bottomPadding = ChartBottomPadding.toPx()

        val initialLayout = buildChartLayout(
            size = size,
            points = points,
            leftPadding = ChartMinimumLeftPadding.toPx(),
            rightPadding = rightPadding,
            topPadding = topPadding,
            bottomPadding = bottomPadding,
            xTicks = xTicks
        )

        val longestYLabelWidth = initialLayout.yAxis.ticks
            .maxOf { tick ->
                textPaint.measureText(yLabelFormatter(tick))
            }

        val leftPadding = maxOf(
            ChartMinimumLeftPadding.toPx(),
            longestYLabelWidth +
                    ChartYAxisLabelGap.toPx() +
                    ChartYAxisLabelStartInset.toPx()
        )

        val layout = buildChartLayout(
            size = size,
            points = points,
            leftPadding = leftPadding,
            rightPadding = rightPadding,
            topPadding = topPadding,
            bottomPadding = bottomPadding,
            xTicks = xTicks
        )

        drawGrid(
            style = style,
            yAxis = layout.yAxis,
            leftPadding = layout.leftPadding,
            chartWidth = layout.chartWidth,
            mapY = layout::mapY
        )

        drawAxes(
            style = style,
            xAxis = layout.xAxis,
            yAxis = layout.yAxis,
            leftPadding = layout.leftPadding,
            topPadding = layout.topPadding,
            chartWidth = layout.chartWidth,
            chartHeight = layout.chartHeight,
            mapX = layout::mapX,
            mapY = layout::mapY
        )

        drawAxisLabels(
            xAxis = layout.xAxis,
            yAxis = layout.yAxis,
            leftPadding = layout.leftPadding,
            topPadding = layout.topPadding,
            chartHeight = layout.chartHeight,
            mapX = layout::mapX,
            mapY = layout::mapY,
            textPaint = textPaint,
            xTextPaint = xTextPaint,
            xLabelFormatter = xLabelFormatter,
            yLabelFormatter = yLabelFormatter,
            yLabelGap = ChartYAxisLabelGap.toPx()
        )

        val paths = buildChartPaths(
            points = points,
            progress = animationProgress.value,
            mapX = layout::mapX,
            mapY = layout::mapY,
            style = style,
            chartBottom = layout.topPadding + layout.chartHeight
        )

        paths.area?.let {
            drawPath(
                path = it,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        style.lineColor.copy(alpha = style.gradientAlpha),
                        style.lineColor.copy(alpha = 0f)
                    ),
                    startY = layout.topPadding,
                    endY = layout.topPadding + layout.chartHeight
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
        drawPoints(
            points = points,
            animationProgress = animationProgress.value,
            style = style,
            mapX = layout::mapX,
            mapY = layout::mapY
        )
    }
}
