package com.bgr3108.kilonom.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bgr3108.kilonom.data.MaintenanceItemEntity
import com.bgr3108.kilonom.data.MaintenanceType
import com.bgr3108.kilonom.data.VehicleCategory
import com.bgr3108.kilonom.data.VehicleEntity
import com.bgr3108.kilonom.data.VehicleType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaintenanceItemWithoutReminderTest {

    private val database = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        HybridCarDatabase::class.java
    ).allowMainThreadQueries().build()

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun emptyFollowUp_persistsIsUniqueAndCanBeCreatedAgainAfterDeletion() = runBlocking {
        val vehicleId = database.vehicleDao().insert(vehicle())
        val dao = database.maintenanceDao()

        val itemId = dao.insertItem(emptyBatteryItem(vehicleId))
        val persisted = dao.observeItems(vehicleId).first().single()

        assertEquals(itemId, persisted.id)
        assertEquals(vehicleId, persisted.vehicleId)
        assertEquals(MaintenanceType.BATTERY_12V, persisted.type)
        assertEquals("BATTERY_12V", persisted.trackingKey)
        assertNull(persisted.nextDueKm)
        assertNull(persisted.nextDueDate)
        assertNull(persisted.intervalKm)
        assertNull(persisted.intervalTimeValue)
        assertNull(persisted.intervalTimeUnit)
        assertTrue(dao.observeRecordsForVehicle(vehicleId).first().isEmpty())

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insertItem(emptyBatteryItem(vehicleId)) }
        }

        assertEquals(1, dao.deleteItemForVehicle(itemId, vehicleId))
        val recreatedId = dao.insertItem(emptyBatteryItem(vehicleId))
        assertTrue(recreatedId > itemId)
        assertEquals(1, dao.observeItems(vehicleId).first().size)
    }

    private fun emptyBatteryItem(vehicleId: Long) = MaintenanceItemEntity(
        vehicleId = vehicleId,
        type = MaintenanceType.BATTERY_12V,
        trackingKey = "BATTERY_12V",
        createdAt = 10,
        updatedAt = 10
    )

    private fun vehicle() = VehicleEntity(
        category = VehicleCategory.COCHE,
        brand = "SEAT",
        model = "León",
        year = 2026,
        type = VehicleType.GASOLINA,
        fuelTankCapacity = 40.0,
        batteryCapacity = 0.0,
        initialKm = 0.0,
        createdAt = 1
    )
}
