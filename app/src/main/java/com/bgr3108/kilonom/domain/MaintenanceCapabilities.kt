package com.bgr3108.kilonom.domain

import com.bgr3108.kilonom.data.MaintenanceType
import com.bgr3108.kilonom.data.VehicleCategory
import com.bgr3108.kilonom.data.VehicleType

fun availableMaintenanceTypes(
    category: VehicleCategory,
    vehicleType: VehicleType?
): Set<MaintenanceType> {
    val types = linkedSetOf(
        MaintenanceType.BRAKES,
        MaintenanceType.TYRES,
        MaintenanceType.BATTERY_12V,
        MaintenanceType.GENERAL_SERVICE,
        MaintenanceType.ITV,
        MaintenanceType.INSURANCE,
        MaintenanceType.CIRCULATION_TAX,
        MaintenanceType.OTHER
    )

    if (vehicleType != VehicleType.ELECTRICO) {
        types += MaintenanceType.OIL_AND_FILTER
    }
    if (category == VehicleCategory.MOTO) {
        types += MaintenanceType.CHAIN_AND_DRIVETRAIN
    }
    return types
}
