package com.bgr3108.kilonom.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.CardColors
import com.bgr3108.kilonom.ui.theme.CardBlueDark
import com.bgr3108.kilonom.ui.theme.CardBlueLight
import androidx.compose.foundation.layout.heightIn

@Composable
fun HomeInfoCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    value: String? = null,
    secondaryValue: String? = null,
    colors: CardColors = CardDefaults.cardColors(
        containerColor =
            if (isSystemInDarkTheme())
                CardBlueDark
            else
                CardBlueLight
    ),
    onClick: (() -> Unit)? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null
) {

    Card(
        modifier = modifier
            .heightIn(min = 180.dp)
            .then(
                if (onClick != null)
                    Modifier.clickable { onClick() }
                else
                    Modifier
            ),
        colors = colors,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ){

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (value != null) {

                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (secondaryValue != null) {

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = secondaryValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            content?.let {

                Spacer(modifier = Modifier.height(10.dp))

                it()
            }
        }
    }
}
