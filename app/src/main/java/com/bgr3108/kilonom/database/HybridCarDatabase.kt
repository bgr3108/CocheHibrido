package com.bgr3108.kilonom.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bgr3108.kilonom.data.Car
import com.bgr3108.kilonom.data.FuelEntry
import com.bgr3108.kilonom.data.VehicleEntity


@Database(
    entities = [Car::class, FuelEntry::class, VehicleEntity::class],
    version = 11,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class HybridCarDatabase : RoomDatabase() {

    abstract fun carDao(): CarDao
    abstract fun fuelEntryDao(): FuelEntryDao
    abstract fun vehicleDao(): VehicleDao

    companion object {
        @Volatile
        private var Instance: HybridCarDatabase? = null

        fun getDatabase(context: Context): HybridCarDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    HybridCarDatabase::class.java,
                    "hybrid_car_database"
                )
                    .fallbackToDestructiveMigrationFrom(
                        dropAllTables = false,
                        1, 2, 3, 4, 5, 6, 7, 8
                    )
                    .addMigrations(MIGRATION_9_10, MIGRATION_10_11)
                    .build()
                    .also { Instance = it }
            }
        }

        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE fuel_entries ADD COLUMN fuelLevelAfter REAL"
                )
            }
        }

        /**
         * Creates a Room-owned recovery vehicle before introducing the mandatory foreign key.
         * DataStore cannot participate in a Room migration, so the app later enriches this
         * intentionally incomplete record from the legacy preferences without ever leaving
         * historical entries unlinked.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vehicles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `category` TEXT NOT NULL,
                        `brand` TEXT NOT NULL,
                        `model` TEXT NOT NULL,
                        `year` INTEGER,
                        `type` TEXT,
                        `fuelTankCapacity` REAL NOT NULL,
                        `batteryCapacity` REAL NOT NULL,
                        `initialKm` REAL NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                // Only create an internal anchor if entries actually need a parent. A configured
                // legacy installation with no entries is imported later by the idempotent bootstrap.
                db.execSQL(
                    """
                    INSERT INTO `vehicles` (
                        `category`, `brand`, `model`, `year`, `type`, `fuelTankCapacity`,
                        `batteryCapacity`, `initialKm`, `createdAt`
                    )
                    SELECT 'COCHE', '', '', NULL, NULL, 0.0, 0.0, 0.0, 0
                    WHERE EXISTS (SELECT 1 FROM `fuel_entries`)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `fuel_entries_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `fecha` INTEGER NOT NULL,
                        `cantidad` REAL NOT NULL,
                        `precio` REAL NOT NULL,
                        `tipo` TEXT NOT NULL,
                        `km` REAL NOT NULL,
                        `fullTank` INTEGER NOT NULL,
                        `fuelLevelAfter` REAL,
                        `vehicleId` INTEGER NOT NULL,
                        FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `fuel_entries_new` (
                        `id`, `fecha`, `cantidad`, `precio`, `tipo`, `km`, `fullTank`,
                        `fuelLevelAfter`, `vehicleId`
                    )
                    SELECT `id`, `fecha`, `cantidad`, `precio`, `tipo`, `km`, `fullTank`,
                        `fuelLevelAfter`, 1
                    FROM `fuel_entries`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `fuel_entries`")
                db.execSQL("ALTER TABLE `fuel_entries_new` RENAME TO `fuel_entries`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_fuel_entries_vehicleId_fecha` ON `fuel_entries` (`vehicleId`, `fecha`)"
                )
            }
        }
    }
}
