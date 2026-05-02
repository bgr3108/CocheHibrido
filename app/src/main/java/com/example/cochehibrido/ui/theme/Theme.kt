package com.example.cochehibrido.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BluePrimary,
    secondary = BlueSecondary,
    background = Color.White,           // fondo limpio
    surface = Color(0xFFE3F2FD),        // azul muy suave (tarjetas)
    onPrimary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black
)

private val DarkColors = darkColorScheme(
    primary = DarkBluePrimary,
    secondary = BlueSecondary, // 🔥 IMPORTANTE
    background = DarkBlueBackground,
    surface = DarkBlueSurface,
    onPrimary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
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