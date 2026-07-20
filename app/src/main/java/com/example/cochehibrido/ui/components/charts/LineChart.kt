package com.example.cochehibrido.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun LineChart(
    values: List<Float>,
    modifier: Modifier = Modifier
) {

    if (values.size < 2) return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {

        val maxValue = values.maxOrNull() ?: return@Canvas
        val minValue = values.minOrNull() ?: return@Canvas

        val range =
            if (maxValue == minValue)
                1f
            else
                maxValue - minValue

        val stepX = size.width / (values.size - 1)

        for (i in 0 until values.lastIndex) {

            val start = Offset(
                x = stepX * i,
                y = size.height - ((values[i] - minValue) / range) * size.height
            )

            val end = Offset(
                x = stepX * (i + 1),
                y = size.height - ((values[i + 1] - minValue) / range) * size.height
            )

            drawLine(
                color = Color(0xFF1976D2),
                start = start,
                end = end,
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
        }

        values.forEachIndexed { index, value ->

            drawCircle(
                color = Color(0xFF1976D2),
                radius = 8f,
                center = Offset(
                    x = stepX * index,
                    y = size.height - ((value - minValue) / range) * size.height
                )
            )
        }
    }
}