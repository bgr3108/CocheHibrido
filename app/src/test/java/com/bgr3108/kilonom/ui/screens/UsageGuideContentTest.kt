package com.bgr3108.kilonom.ui.screens

import com.bgr3108.kilonom.R
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageGuideContentTest {

    @Test
    fun sectionsContainTheCompleteGuideInTheExpectedOrder() {
        assertEquals(
            listOf(
                R.string.usage_guide_first_steps_title,
                R.string.usage_guide_my_vehicles_title,
                R.string.usage_guide_consumptions_title,
                R.string.usage_guide_partial_refuels_title,
                R.string.usage_guide_electric_charges_title,
                R.string.usage_guide_statistics_title,
                R.string.usage_guide_periods_title,
                R.string.usage_guide_trends_title,
                R.string.usage_guide_maintenance_title,
                R.string.usage_guide_reminders_title,
                R.string.usage_guide_current_km_title,
                R.string.usage_guide_phev_title,
                R.string.usage_guide_stations_title,
                R.string.usage_guide_privacy_title
            ),
            usageGuideSections.map(UsageGuideSection::titleRes)
        )
    }
}
