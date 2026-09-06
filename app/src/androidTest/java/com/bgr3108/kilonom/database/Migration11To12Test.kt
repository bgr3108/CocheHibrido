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
class Migration11To12Test {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun deleteTestDatabase() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrate11To12_preservesExistingDataAndCreatesMaintenanceCascade() {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(TEST_DATABASE), null).apply {
            execSQL("CREATE TABLE `car` (`id` INTEGER NOT NULL, `marca` TEXT NOT NULL, `modelo` TEXT NOT NULL, `matricula` TEXT NOT NULL, `kmActuales` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            execSQL(
                """
                CREATE TABLE `vehicles` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `category` TEXT NOT NULL, `brand` TEXT NOT NULL, `model` TEXT NOT NULL,
                    `year` INTEGER, `type` TEXT, `fuelTankCapacity` REAL NOT NULL,
                    `batteryCapacity` REAL NOT NULL, `initialKm` REAL NOT NULL, `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE `fuel_entries` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fecha` INTEGER NOT NULL,
                    `cantidad` REAL NOT NULL, `precio` REAL NOT NULL, `tipo` TEXT NOT NULL,
                    `km` REAL NOT NULL, `fullTank` INTEGER NOT NULL, `fuelLevelAfter` REAL,
                    `vehicleId` INTEGER NOT NULL,
                    FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            execSQL("CREATE INDEX `index_fuel_entries_vehicleId_fecha` ON `fuel_entries` (`vehicleId`, `fecha`)")
            execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            execSQL("INSERT INTO room_master_table (id, identity_hash) VALUES(42, '16d0bac784fd433c2756c32360fc6216')")
            execSQL("INSERT INTO vehicles VALUES(1, 'COCHE', 'SEAT', 'León', 2026, 'GASOLINA', 40.0, 0.0, 1000.0, 10)")
            execSQL("INSERT INTO vehicles VALUES(2, 'COCHE', 'Tesla', 'Model 3', 2026, 'ELECTRICO', 0.0, 60.0, 2000.0, 20)")
            execSQL("INSERT INTO fuel_entries VALUES(7, 1000, 42.5, 68.75, 'GASOLINA', 1500.0, 1, NULL, 1)")
            setVersion(11)
            close()
        }

        val database = Room.databaseBuilder(context, HybridCarDatabase::class.java, TEST_DATABASE)
            .addMigrations(HybridCarDatabase.MIGRATION_11_12, HybridCarDatabase.MIGRATION_12_13)
            .allowMainThreadQueries()
            .build()
        val migrated = database.openHelper.writableDatabase

        migrated.query("SELECT brand, initialKm FROM vehicles WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("SEAT", cursor.getString(0))
            assertEquals(1000.0, cursor.getDouble(1), 0.0)
        }
        migrated.query("SELECT id, km, vehicleId, fuelLevelAfter FROM fuel_entries WHERE id = 7").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(7, cursor.getInt(0))
            assertEquals(1500.0, cursor.getDouble(1), 0.0)
            assertEquals(1L, cursor.getLong(2))
            assertTrue(cursor.isNull(3))
        }

        assertIndex(migrated, "maintenance_items", "index_maintenance_items_vehicleId")
        assertIndex(migrated, "maintenance_items", "index_maintenance_items_vehicleId_trackingKey")
        assertIndex(migrated, "maintenance_records", "index_maintenance_records_itemId_performedDate")
        assertForeignKey(migrated, "maintenance_items", "vehicles")
        assertForeignKey(migrated, "maintenance_records", "maintenance_items")

        migrated.execSQL("INSERT INTO maintenance_items (`id`, `vehicleId`, `type`, `tyrePosition`, `customName`, `trackingKey`, `nextDueKm`, `nextDueDate`, `createdAt`, `updatedAt`) VALUES(10, 1, 'OIL_AND_FILTER', NULL, NULL, 'OIL_AND_FILTER', 5000, NULL, 10, 10)")
        migrated.execSQL("INSERT INTO maintenance_records VALUES(20, 10, 1000, 1500, 95.0, NULL, 10, 10)")
        migrated.execSQL("INSERT INTO maintenance_records VALUES(21, 10, 1100, 1600, NULL, 'Revisión', 11, 11)")
        migrated.execSQL("INSERT INTO maintenance_items (`id`, `vehicleId`, `type`, `tyrePosition`, `customName`, `trackingKey`, `nextDueKm`, `nextDueDate`, `createdAt`, `updatedAt`) VALUES(11, 2, 'ITV', NULL, NULL, 'ITV', NULL, 2000, 20, 20)")
        migrated.execSQL("INSERT INTO maintenance_records VALUES(22, 11, NULL, NULL, 0.0, NULL, 20, 20)")

        migrated.query("SELECT cost FROM maintenance_records WHERE id = 21").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }
        migrated.query("SELECT cost FROM maintenance_records WHERE id = 22").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0.0, cursor.getDouble(0), 0.0)
        }

        assertUniqueTrackingKey(migrated)
        migrated.execSQL("DELETE FROM vehicles WHERE id = 1")
        assertCount(migrated, "SELECT COUNT(*) FROM maintenance_items WHERE vehicleId = 1", 0)
        assertCount(migrated, "SELECT COUNT(*) FROM maintenance_records WHERE itemId = 10", 0)
        assertCount(migrated, "SELECT COUNT(*) FROM maintenance_items WHERE vehicleId = 2", 1)
        assertCount(migrated, "SELECT COUNT(*) FROM maintenance_records WHERE itemId = 11", 1)
        database.close()
    }

    private fun assertIndex(database: androidx.sqlite.db.SupportSQLiteDatabase, table: String, expected: String) {
        database.query("PRAGMA index_list(`$table`)").use { cursor ->
            val names = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertTrue("Falta el índice $expected", expected in names)
        }
    }

    private fun assertForeignKey(database: androidx.sqlite.db.SupportSQLiteDatabase, table: String, parent: String) {
        database.query("PRAGMA foreign_key_list(`$table`)").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(parent, cursor.getString(cursor.getColumnIndexOrThrow("table")))
            assertEquals("CASCADE", cursor.getString(cursor.getColumnIndexOrThrow("on_delete")))
        }
    }

    private fun assertUniqueTrackingKey(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        val result = runCatching {
            database.execSQL("INSERT INTO maintenance_items (`id`, `vehicleId`, `type`, `tyrePosition`, `customName`, `trackingKey`, `nextDueKm`, `nextDueDate`, `createdAt`, `updatedAt`) VALUES(12, 2, 'ITV', NULL, NULL, 'ITV', NULL, NULL, 20, 20)")
        }
        assertTrue(result.isFailure)
    }

    private fun assertCount(database: androidx.sqlite.db.SupportSQLiteDatabase, sql: String, expected: Int) {
        database.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(expected, cursor.getInt(0))
        }
    }

    private companion object {
        const val TEST_DATABASE = "migration-11-12-test"
    }
}
