package com.example.cochehibrido.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = BluePrimary,
    onPrimary = androidx.compose.ui.graphics.Color.White,

    secondary = BlueSecondary,
    onSecondary = androidx.compose.ui.graphics.Color.White,

    background = BlueBackground,
    onBackground = androidx.compose.ui.graphics.Color.Black,

    surface = BlueCard,
    onSurface = androidx.compose.ui.graphics.Color.Black
)

private val DarkColors = darkColorScheme(
    primary = BluePrimaryDark,
    onPrimary = androidx.compose.ui.graphics.Color.White,

    secondary = BlueSecondaryDark,
    onSecondary = androidx.compose.ui.graphics.Color.Black,

    background = BlueBackgroundDark,
    onBackground = androidx.compose.ui.graphics.Color.White,

    surface = BlueCardDark,
    onSurface = androidx.compose.ui.graphics.Color.White
)

@Composable
fun CocheHibridoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), // 🔥 automático
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
}