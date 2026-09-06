package com.bgr3108.kilonom.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration12To13Test {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun deleteTestDatabase() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrate12To13_preservesMaintenanceHistoryAndAppliesCompatibleDefaults() {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(TEST_DATABASE), null).apply {
            execSQL("CREATE TABLE `car` (`id` INTEGER NOT NULL, `marca` TEXT NOT NULL, `modelo` TEXT NOT NULL, `matricula` TEXT NOT NULL, `kmActuales` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            execSQL("CREATE TABLE `vehicles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category` TEXT NOT NULL, `brand` TEXT NOT NULL, `model` TEXT NOT NULL, `year` INTEGER, `type` TEXT, `fuelTankCapacity` REAL NOT NULL, `batteryCapacity` REAL NOT NULL, `initialKm` REAL NOT NULL, `createdAt` INTEGER NOT NULL)")
            execSQL("CREATE TABLE `fuel_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fecha` INTEGER NOT NULL, `cantidad` REAL NOT NULL, `precio` REAL NOT NULL, `tipo` TEXT NOT NULL, `km` REAL NOT NULL, `fullTank` INTEGER NOT NULL, `fuelLevelAfter` REAL, `vehicleId` INTEGER NOT NULL, FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            execSQL("CREATE INDEX `index_fuel_entries_vehicleId_fecha` ON `fuel_entries` (`vehicleId`, `fecha`)")
            execSQL("CREATE TABLE `maintenance_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `vehicleId` INTEGER NOT NULL, `type` TEXT NOT NULL, `tyrePosition` TEXT, `customName` TEXT, `trackingKey` TEXT NOT NULL, `nextDueKm` INTEGER, `nextDueDate` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            execSQL("CREATE INDEX `index_maintenance_items_vehicleId` ON `maintenance_items` (`vehicleId`)")
            execSQL("CREATE UNIQUE INDEX `index_maintenance_items_vehicleId_trackingKey` ON `maintenance_items` (`vehicleId`, `trackingKey`)")
            execSQL("CREATE TABLE `maintenance_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `itemId` INTEGER NOT NULL, `performedDate` INTEGER, `odometerKm` INTEGER, `cost` REAL, `notes` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`itemId`) REFERENCES `maintenance_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            execSQL("CREATE INDEX `index_maintenance_records_itemId_performedDate` ON `maintenance_records` (`itemId`, `performedDate`)")
            execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            execSQL("INSERT INTO room_master_table (id, identity_hash) VALUES(42, '9e1fd037b66fcb7858c34a0fc2cba2d6')")
            execSQL("INSERT INTO vehicles VALUES(1, 'COCHE', 'SEAT', 'León', 2026, 'GASOLINA', 40.0, 0.0, 1000.0, 10)")
            execSQL("INSERT INTO maintenance_items VALUES(10, 1, 'OIL_AND_FILTER', NULL, NULL, 'OIL_AND_FILTER', 50000, 1900000000000, 10, 10)")
            execSQL("INSERT INTO maintenance_records VALUES(20, 10, 1800000000000, 20000, 95.0, 'Cambio previo', 10, 10)")
            setVersion(12)
            close()
        }

        val database = Room.databaseBuilder(context, HybridCarDatabase::class.java, TEST_DATABASE)
            .addMigrations(HybridCarDatabase.MIGRATION_12_13)
            .allowMainThreadQueries()
            .build()
        val migrated = database.openHelper.writableDatabase

        migrated.query(
            "SELECT nextDueKm, nextDueDate, intervalKm, intervalTimeValue, intervalTimeUnit, reminderLeadKm, reminderLeadDays FROM maintenance_items WHERE id = 10"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(50_000L, cursor.getLong(0))
            assertEquals(1_900_000_000_000L, cursor.getLong(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
            assertTrue(cursor.isNull(4))
            assertEquals(1_000L, cursor.getLong(5))
            assertEquals(30L, cursor.getLong(6))
        }
        migrated.query("SELECT itemId, performedDate, odometerKm, cost, notes FROM maintenance_records WHERE id = 20").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(10L, cursor.getLong(0))
            assertEquals(1_800_000_000_000L, cursor.getLong(1))
            assertEquals(20_000L, cursor.getLong(2))
            assertEquals(95.0, cursor.getDouble(3), 0.0)
            assertEquals("Cambio previo", cursor.getString(4))
        }
        database.close()
    }

    private companion object {
        const val TEST_DATABASE = "migration-12-13-test"
    }
}
