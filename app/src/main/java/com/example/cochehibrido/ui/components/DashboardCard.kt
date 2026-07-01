package com.example.cochehibrido.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cochehibrido.ui.theme.CardBlueDark
import com.example.cochehibrido.ui.theme.CardBlueLight

@Composable
fun DashboardCard(

    modifier: Modifier = Modifier,

    title: String,

    value: String,

    subtitle: String = "",

    icon: ImageVector? = null,

    showDetailArrow: Boolean = false,

    onClick: () -> Unit = {},

    colors: CardColors = CardDefaults.cardColors(
        containerColor =
            if (isSystemInDarkTheme())
                CardBlueDark
            else
                CardBlueLight
    )

) {

    Card(

        modifier = modifier
            .height(145.dp)
            .clickable {
                onClick()
            },

        colors = colors,

        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )

    ) {

        Column(

            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)

        ) {

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween,

                verticalAlignment =
                    Alignment.CenterVertically

            ) {

                Text(

                    text = title,

                    style = MaterialTheme.typography.titleSmall

                )

                icon?.let {

                    Icon(

                        imageVector = it,

                        contentDescription = null,

                        tint = MaterialTheme.colorScheme.primary

                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(

                text = value,

                style = MaterialTheme.typography.headlineMedium,

                fontWeight = FontWeight.Bold

            )

            if (subtitle.isNotBlank()) {

                Spacer(modifier = Modifier.height(4.dp))

                Text(

                    text = subtitle,

                    style = MaterialTheme.typography.bodyMedium,

                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant

                )
            }

            if (showDetailArrow) {

                Spacer(modifier = Modifier.height(12.dp))

                Text(

                    text = "Ver detalle >",

                    style = MaterialTheme.typography.labelMedium,

                    color = MaterialTheme.colorScheme.primary

                )
            }
        }
    }
}