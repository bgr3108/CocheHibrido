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
class Migration14To15Test {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun deleteTestDatabase() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrate14To15_preservesVehicleSnapshotAndRelationsWithNullCatalogId() {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(TEST_DATABASE), null).apply {
            execSQL("CREATE TABLE `car` (`id` INTEGER NOT NULL, `marca` TEXT NOT NULL, `modelo` TEXT NOT NULL, `matricula` TEXT NOT NULL, `kmActuales` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            execSQL("CREATE TABLE `vehicles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category` TEXT NOT NULL, `brand` TEXT NOT NULL, `model` TEXT NOT NULL, `year` INTEGER, `type` TEXT, `fuelTankCapacity` REAL NOT NULL, `batteryCapacity` REAL NOT NULL, `initialKm` REAL NOT NULL, `createdAt` INTEGER NOT NULL)")
            execSQL("CREATE TABLE `fuel_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fecha` INTEGER NOT NULL, `cantidad` REAL NOT NULL, `precio` REAL NOT NULL, `tipo` TEXT NOT NULL, `km` REAL NOT NULL, `fullTank` INTEGER NOT NULL, `fuelLevelAfter` REAL, `electricChargeStartPercentage` REAL, `electricChargeEndPercentage` REAL, `vehicleId` INTEGER NOT NULL, FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            execSQL("CREATE INDEX `index_fuel_entries_vehicleId_fecha` ON `fuel_entries` (`vehicleId`, `fecha`)")
            execSQL("CREATE TABLE `maintenance_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `vehicleId` INTEGER NOT NULL, `type` TEXT NOT NULL, `tyrePosition` TEXT, `customName` TEXT, `trackingKey` TEXT NOT NULL, `nextDueKm` INTEGER, `nextDueDate` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `intervalKm` INTEGER, `intervalTimeValue` INTEGER, `intervalTimeUnit` TEXT, `reminderLeadKm` INTEGER NOT NULL DEFAULT 1000, `reminderLeadDays` INTEGER NOT NULL DEFAULT 30, FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            execSQL("CREATE INDEX `index_maintenance_items_vehicleId` ON `maintenance_items` (`vehicleId`)")
            execSQL("CREATE UNIQUE INDEX `index_maintenance_items_vehicleId_trackingKey` ON `maintenance_items` (`vehicleId`, `trackingKey`)")
            execSQL("CREATE TABLE `maintenance_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `itemId` INTEGER NOT NULL, `performedDate` INTEGER, `odometerKm` INTEGER, `cost` REAL, `notes` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`itemId`) REFERENCES `maintenance_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            execSQL("CREATE INDEX `index_maintenance_records_itemId_performedDate` ON `maintenance_records` (`itemId`, `performedDate`)")
            execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            execSQL("INSERT INTO room_master_table (id, identity_hash) VALUES(42, 'a704acca5c2b5140d895d489c3714ed8')")

            execSQL("INSERT INTO vehicles VALUES(7, 'COCHE', 'SEAT', 'León', 2025, 'HIBRIDO_ENCHUFABLE', 40.0, 19.7, 1200.0, 123)")
            execSQL("INSERT INTO fuel_entries VALUES(3, 1000, 20.0, 7.0, 'ELECTRICO', 1500.0, 0, NULL, 20.0, 80.0, 7)")
            execSQL("INSERT INTO maintenance_items VALUES(5, 7, 'OIL_AND_FILTER', NULL, NULL, 'OIL_AND_FILTER', NULL, NULL, 1, 2, NULL, NULL, NULL, 1000, 30)")
            execSQL("INSERT INTO maintenance_records VALUES(9, 5, 2000, 1600, 42.0, NULL, 3, 4)")
            setVersion(14)
            close()
        }

        val database = Room.databaseBuilder(context, HybridCarDatabase::class.java, TEST_DATABASE)
            .addMigrations(HybridCarDatabase.MIGRATION_14_15)
            .allowMainThreadQueries()
            .build()
        val migrated = database.openHelper.writableDatabase

        migrated.query("SELECT id, brand, model, year, type, fuelTankCapacity, batteryCapacity, initialKm, catalogId FROM vehicles WHERE id = 7").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(7L, cursor.getLong(0))
            assertEquals("SEAT", cursor.getString(1))
            assertEquals("León", cursor.getString(2))
            assertEquals(2025, cursor.getInt(3))
            assertEquals("HIBRIDO_ENCHUFABLE", cursor.getString(4))
            assertEquals(40.0, cursor.getDouble(5), 0.0)
            assertEquals(19.7, cursor.getDouble(6), 0.0)
            assertEquals(1200.0, cursor.getDouble(7), 0.0)
            assertTrue(cursor.isNull(8))
        }
        assertSingleRelation(migrated, "SELECT COUNT(*) FROM fuel_entries WHERE vehicleId = 7")
        assertSingleRelation(migrated, "SELECT COUNT(*) FROM maintenance_items WHERE vehicleId = 7")
        assertSingleRelation(migrated, "SELECT COUNT(*) FROM maintenance_records WHERE itemId = 5")
        database.close()
    }

    private fun assertSingleRelation(database: androidx.sqlite.db.SupportSQLiteDatabase, query: String) {
        database.query(query).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }

    private companion object {
        const val TEST_DATABASE = "migration-14-15-test"
    }
}
