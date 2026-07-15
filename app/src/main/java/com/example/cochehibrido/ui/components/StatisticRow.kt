package com.example.cochehibrido.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

@Composable
fun StatisticRow(

    title: String,

    value: String

) {

    Row(

        modifier = Modifier.fillMaxWidth(),

        horizontalArrangement =
            Arrangement.SpaceBetween

    ) {

        Text(

            text = title,

            style = MaterialTheme.typography.bodyLarge

        )

        Text(

            text = value,

            style = MaterialTheme.typography.bodyLarge,

            fontWeight = FontWeight.Bold

        )
    }
}