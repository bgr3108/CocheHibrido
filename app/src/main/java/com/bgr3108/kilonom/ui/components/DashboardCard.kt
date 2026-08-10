package com.bgr3108.kilonom.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bgr3108.kilonom.ui.theme.CardBlueDark
import com.bgr3108.kilonom.ui.theme.CardBlueLight

@Composable
fun DashboardCard(

    modifier: Modifier = Modifier,

    title: String,

    icon: ImageVector? = null,

    showDetailArrow: Boolean = false,

    onClick: () -> Unit = {},

    colors: CardColors = CardDefaults.cardColors(
        containerColor =
            if (isSystemInDarkTheme())
                CardBlueDark
            else
                CardBlueLight
    ),

    content: @Composable ColumnScope.() -> Unit

) {

    Card(

        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },

        colors = colors,

        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )

    ) {

        Column(

            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)

        ) {

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.SpaceBetween,

                verticalAlignment = Alignment.CenterVertically

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

            Spacer(modifier = Modifier.height(16.dp))

            content()

            if (showDetailArrow) {

                Spacer(modifier = Modifier.height(10.dp))

                Text(

                    text = "Ver detalle >",

                    style = MaterialTheme.typography.labelMedium,

                    color = MaterialTheme.colorScheme.primary

                )
            }
        }
    }
}
