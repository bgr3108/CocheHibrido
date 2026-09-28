package com.bgr3108.kilonom.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bgr3108.kilonom.data.FuelEntry
import com.bgr3108.kilonom.data.FuelType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration13To14Test {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun deleteTestDatabase() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrate13To14_preservesExistingEntriesAndAddsNullableChargePercentages() {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(TEST_DATABASE), null).apply {
            execSQL("CREATE TABLE `car` (`id` INTEGER NOT NULL, `marca` TEXT NOT NULL, `modelo` TEXT NOT NULL, `matricula` TEXT NOT NULL, `kmActuales` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            execSQL("CREATE TABLE `vehicles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category` TEXT NOT NULL, `brand` TEXT NOT NULL, `model` TEXT NOT NULL, `year` INTEGER, `type` TEXT, `fuelTankCapacity` REAL NOT NULL, `batteryCapacity` REAL NOT NULL, `initialKm` REAL NOT NULL, `createdAt` INTEGER NOT NULL)")
            execSQL("CREATE TABLE `fuel_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fecha` INTEGER NOT NULL, `cantidad` REAL NOT NULL, `precio` REAL NOT NULL, `tipo` TEXT NOT NULL, `km` REAL NOT NULL, `fullTank` INTEGER NOT NULL, `fuelLevelAfter` REAL, `vehicleId` INTEGER NOT NULL, FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            execSQL("CREATE INDEX `index_fuel_entries_vehicleId_fecha` ON `fuel_entries` (`vehicleId`, `fecha`)")
            execSQL("CREATE TABLE `maintenance_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `vehicleId` INTEGER NOT NULL, `type` TEXT NOT NULL, `tyrePosition` TEXT, `customName` TEXT, `trackingKey` TEXT NOT NULL, `nextDueKm` INTEGER, `nextDueDate` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `intervalKm` INTEGER, `intervalTimeValue` INTEGER, `intervalTimeUnit` TEXT, `reminderLeadKm` INTEGER NOT NULL DEFAULT 1000, `reminderLeadDays` INTEGER NOT NULL DEFAULT 30, FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            execSQL("CREATE INDEX `index_maintenance_items_vehicleId` ON `maintenance_items` (`vehicleId`)")
            execSQL("CREATE UNIQUE INDEX `index_maintenance_items_vehicleId_trackingKey` ON `maintenance_items` (`vehicleId`, `trackingKey`)")
            execSQL("CREATE TABLE `maintenance_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `itemId` INTEGER NOT NULL, `performedDate` INTEGER, `odometerKm` INTEGER, `cost` REAL, `notes` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`itemId`) REFERENCES `maintenance_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            execSQL("CREATE INDEX `index_maintenance_records_itemId_performedDate` ON `maintenance_records` (`itemId`, `performedDate`)")
            execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            execSQL("INSERT INTO room_master_table (id, identity_hash) VALUES(42, 'b9fde2a09f3bd8bf8635beded7ba24ff')")
            execSQL("INSERT INTO vehicles VALUES(1, 'COCHE', 'Tesla', 'Model 3', 2026, 'ELECTRICO', 0.0, 60.0, 2000.0, 10)")
            execSQL("INSERT INTO fuel_entries VALUES(7, 1000, 36.0, 8.5, 'ELECTRICO', 2500.0, 1, NULL, 1)")
            setVersion(13)
            close()
        }

        val database = Room.databaseBuilder(context, HybridCarDatabase::class.java, TEST_DATABASE)
            .addMigrations(HybridCarDatabase.MIGRATION_13_14)
            .allowMainThreadQueries()
            .build()
        val migrated = database.openHelper.writableDatabase

        migrated.query(
            "SELECT cantidad, precio, electricChargeStartPercentage, electricChargeEndPercentage FROM fuel_entries WHERE id = 7"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(36.0, cursor.getDouble(0), 0.0)
            assertEquals(8.5, cursor.getDouble(1), 0.0)
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
        }

        runBlocking {
            database.fuelEntryDao().updateEntry(
                FuelEntry(
                    id = 7,
                    fecha = 1000,
                    cantidad = 36.0,
                    precio = 8.5,
                    tipo = FuelType.ELECTRICO,
                    km = 2500.0,
                    vehicleId = 1,
                    electricChargeStartPercentage = 20.0,
                    electricChargeEndPercentage = 80.0
                )
            )
            val updated = database.fuelEntryDao().getEntryForVehicle(7, 1)
            assertEquals(20.0, requireNotNull(updated?.electricChargeStartPercentage), 0.0)
            assertEquals(80.0, requireNotNull(updated.electricChargeEndPercentage), 0.0)
        }
        database.close()
    }

    private companion object {
        const val TEST_DATABASE = "migration-13-14-test"
    }
}
