package com.bgr3108.kilonom.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BluePrimary,
    secondary = BlueSecondary,
    tertiary = BlueSecondary,
    primaryContainer = LightBlueContainer,
    secondaryContainer = LightBlueContainer,
    tertiaryContainer = LightBlueContainer,
    onPrimaryContainer = DarkBlueBackground,
    onSecondary = DarkBlueBackground,
    onSecondaryContainer = DarkBlueBackground,
    onTertiary = DarkBlueBackground,
    onTertiaryContainer = DarkBlueBackground,
    background = Color.White,
    surface = LightBlueSurface,
    surfaceVariant = CardBlueLight,
    surfaceContainerLow = LightBlueSurface,
    surfaceContainer = LightBlueSurface,
    surfaceContainerHigh = CardBlueLight,
    surfaceContainerHighest = CardBlueLight,
    outline = LightBlueOutline,
    outlineVariant = LightBlueOutlineVariant,
    onPrimary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
    onSurfaceVariant = LightBlueOnSurfaceVariant
)

private val DarkColors = darkColorScheme(
    primary = DarkBluePrimary,
    secondary = BlueSecondary,
    tertiary = BlueSecondary,
    primaryContainer = DarkBlueContainer,
    secondaryContainer = DarkBlueContainer,
    tertiaryContainer = DarkBlueContainer,
    onPrimaryContainer = Color.White,
    onSecondary = DarkBlueBackground,
    onSecondaryContainer = Color.White,
    onTertiary = DarkBlueBackground,
    onTertiaryContainer = Color.White,
    background = DarkBlueBackground,
    surface = DarkBlueSurface,
    surfaceVariant = CardBlueDark,
    surfaceContainerLow = DarkBlueSurface,
    surfaceContainer = DarkBlueSurface,
    surfaceContainerHigh = CardBlueDark,
    surfaceContainerHighest = CardBlueDark,
    outline = DarkBlueOutline,
    outlineVariant = DarkBlueOutlineVariant,
    onPrimary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = DarkBlueOnSurfaceVariant
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
