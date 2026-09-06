package com.bgr3108.kilonom.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration10To11Test {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun deleteTestDatabase() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrate10To11_preservesEveryFuelEntryFieldAndAssociatesRecoveryVehicle() {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(TEST_DATABASE), null).apply {
            execSQL(
                """
                CREATE TABLE `car` (
                    `id` INTEGER NOT NULL,
                    `marca` TEXT NOT NULL,
                    `modelo` TEXT NOT NULL,
                    `matricula` TEXT NOT NULL,
                    `kmActuales` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE `fuel_entries` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `fecha` INTEGER NOT NULL,
                    `cantidad` REAL NOT NULL,
                    `precio` REAL NOT NULL,
                    `tipo` TEXT NOT NULL,
                    `km` REAL NOT NULL,
                    `fullTank` INTEGER NOT NULL,
                    `fuelLevelAfter` REAL
                )
                """.trimIndent()
            )
            execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            execSQL(
                "INSERT INTO room_master_table (id, identity_hash) VALUES(42, '9a8270375605a667d3935c9bc78e6042')"
            )
            execSQL(
                """
                INSERT INTO fuel_entries
                (id, fecha, cantidad, precio, tipo, km, fullTank, fuelLevelAfter)
                VALUES (7, 1000, 42.5, 68.75, 'GASOLINA', 50100.0, 1, NULL)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO fuel_entries
                (id, fecha, cantidad, precio, tipo, km, fullTank, fuelLevelAfter)
                VALUES (8, 2000, 12.25, 3.50, 'ELECTRICO', 50300.0, 0, 0.625)
                """.trimIndent()
            )
            setVersion(10)
            close()
        }

        val database = Room.databaseBuilder(context, HybridCarDatabase::class.java, TEST_DATABASE)
            .addMigrations(
                HybridCarDatabase.MIGRATION_10_11,
                HybridCarDatabase.MIGRATION_11_12,
                HybridCarDatabase.MIGRATION_12_13
            )
            .allowMainThreadQueries()
            .build()
        val migrated = database.openHelper.writableDatabase

        migrated.query("SELECT id, fecha, cantidad, precio, tipo, km, fullTank, fuelLevelAfter, vehicleId FROM fuel_entries ORDER BY id").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(7, cursor.getInt(0))
            assertEquals(1000L, cursor.getLong(1))
            assertEquals(42.5, cursor.getDouble(2), 0.0)
            assertEquals(68.75, cursor.getDouble(3), 0.0)
            assertEquals("GASOLINA", cursor.getString(4))
            assertEquals(50100.0, cursor.getDouble(5), 0.0)
            assertEquals(1, cursor.getInt(6))
            assertTrue(cursor.isNull(7))
            assertEquals(1L, cursor.getLong(8))

            assertTrue(cursor.moveToNext())
            assertEquals(8, cursor.getInt(0))
            assertEquals(2000L, cursor.getLong(1))
            assertEquals(12.25, cursor.getDouble(2), 0.0)
            assertEquals(3.50, cursor.getDouble(3), 0.0)
            assertEquals("ELECTRICO", cursor.getString(4))
            assertEquals(50300.0, cursor.getDouble(5), 0.0)
            assertEquals(0, cursor.getInt(6))
            assertEquals(0.625, cursor.getDouble(7), 0.0)
            assertEquals(1L, cursor.getLong(8))
            assertTrue(!cursor.moveToNext())
        }

        migrated.query("PRAGMA foreign_key_list(`fuel_entries`)").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("vehicles", cursor.getString(cursor.getColumnIndexOrThrow("table")))
            assertEquals("CASCADE", cursor.getString(cursor.getColumnIndexOrThrow("on_delete")))
        }

        migrated.query("PRAGMA index_list(`fuel_entries`)").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                "index_fuel_entries_vehicleId_fecha",
                cursor.getString(cursor.getColumnIndexOrThrow("name"))
            )
        }

        migrated.execSQL("DELETE FROM vehicles WHERE id = 1")
        migrated.query("SELECT COUNT(*) FROM fuel_entries").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        database.close()
    }

    private companion object {
        const val TEST_DATABASE = "migration-10-11-test"
    }
}
