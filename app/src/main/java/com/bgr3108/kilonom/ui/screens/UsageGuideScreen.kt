package com.bgr3108.kilonom.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bgr3108.kilonom.R
import com.bgr3108.kilonom.ui.theme.CardBlueDark
import com.bgr3108.kilonom.ui.theme.CardBlueLight

internal data class UsageGuideSection(
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int
)

internal val usageGuideSections = listOf(
    UsageGuideSection(R.string.usage_guide_first_steps_title, R.string.usage_guide_first_steps_body),
    UsageGuideSection(R.string.usage_guide_my_vehicles_title, R.string.usage_guide_my_vehicles_body),
    UsageGuideSection(R.string.usage_guide_consumptions_title, R.string.usage_guide_consumptions_body),
    UsageGuideSection(R.string.usage_guide_partial_refuels_title, R.string.usage_guide_partial_refuels_body),
    UsageGuideSection(R.string.usage_guide_electric_charges_title, R.string.usage_guide_electric_charges_body),
    UsageGuideSection(R.string.usage_guide_statistics_title, R.string.usage_guide_statistics_body),
    UsageGuideSection(R.string.usage_guide_periods_title, R.string.usage_guide_periods_body),
    UsageGuideSection(R.string.usage_guide_trends_title, R.string.usage_guide_trends_body),
    UsageGuideSection(R.string.usage_guide_maintenance_title, R.string.usage_guide_maintenance_body),
    UsageGuideSection(R.string.usage_guide_reminders_title, R.string.usage_guide_reminders_body),
    UsageGuideSection(R.string.usage_guide_current_km_title, R.string.usage_guide_current_km_body),
    UsageGuideSection(R.string.usage_guide_phev_title, R.string.usage_guide_phev_body),
    UsageGuideSection(R.string.usage_guide_privacy_title, R.string.usage_guide_privacy_body)
)

@Composable
fun UsageGuideScreen(innerPadding: PaddingValues) {
    val expandedSections = remember { mutableStateMapOf<Int, Boolean>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.usage_guide_introduction),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        usageGuideSections.forEachIndexed { index, section ->
            val expanded = expandedSections[index] == true
            UsageGuideSectionCard(
                section = section,
                expanded = expanded,
                onClick = { expandedSections[index] = !expanded }
            )
        }
    }
}

@Composable
private fun UsageGuideSectionCard(
    section: UsageGuideSection,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val state = if (expanded) {
        stringResource(R.string.usage_guide_expanded)
    } else {
        stringResource(R.string.usage_guide_collapsed)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                stateDescription = state
            }
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSystemInDarkTheme()) CardBlueDark else CardBlueLight
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(section.titleRes),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = stringResource(section.bodyRes),
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
