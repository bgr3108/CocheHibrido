package com.bgr3108.kilonom.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bgr3108.kilonom.BuildConfig

internal val AdBannerSlotHeight = 56.dp

/** Routes hosted by the main scaffold, where a future banner can be displayed safely. */
internal fun routeUsesAdBannerSlot(route: String?): Boolean = route in setOf(
    "home",
    "consumption",
    "stats",
    "stats/trends",
    "maintenance"
)

/** Release stays visually identical until a real advertising provider is deliberately integrated. */
internal fun adBannerSlotHeight(isDebugBuild: Boolean): Dp =
    if (isDebugBuild) AdBannerSlotHeight else 0.dp

/**
 * Reserved layout area for a future banner provider.
 *
 * The slot is a sibling of the bottom navigation in the app scaffold, so its height becomes
 * part of the content insets rather than overlaying scrollable content or floating actions.
 */
@Composable
internal fun AdBannerSlot(modifier: Modifier = Modifier) {
    if (!BuildConfig.DEBUG) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(adBannerSlotHeight(isDebugBuild = true)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "Espacio publicitario",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
