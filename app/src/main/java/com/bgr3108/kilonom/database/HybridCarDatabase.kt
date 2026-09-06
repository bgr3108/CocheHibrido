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
import com.bgr3108.kilonom.data.MaintenanceItemEntity
import com.bgr3108.kilonom.data.MaintenanceRecordEntity
import com.bgr3108.kilonom.data.VehicleEntity


@Database(
    entities = [Car::class, FuelEntry::class, VehicleEntity::class, MaintenanceItemEntity::class, MaintenanceRecordEntity::class],
    version = 13,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class HybridCarDatabase : RoomDatabase() {

    abstract fun carDao(): CarDao
    abstract fun fuelEntryDao(): FuelEntryDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun maintenanceDao(): MaintenanceDao

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
                    .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
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

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `maintenance_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `vehicleId` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `tyrePosition` TEXT,
                        `customName` TEXT,
                        `trackingKey` TEXT NOT NULL,
                        `nextDueKm` INTEGER,
                        `nextDueDate` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_maintenance_items_vehicleId` ON `maintenance_items` (`vehicleId`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_maintenance_items_vehicleId_trackingKey` ON `maintenance_items` (`vehicleId`, `trackingKey`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `maintenance_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `itemId` INTEGER NOT NULL,
                        `performedDate` INTEGER,
                        `odometerKm` INTEGER,
                        `cost` REAL,
                        `notes` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`itemId`) REFERENCES `maintenance_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_maintenance_records_itemId_performedDate` ON `maintenance_records` (`itemId`, `performedDate`)"
                )
            }
        }

        /** Adds optional recurrence while preserving every existing concrete reminder. */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `maintenance_items` ADD COLUMN `intervalKm` INTEGER")
                db.execSQL("ALTER TABLE `maintenance_items` ADD COLUMN `intervalTimeValue` INTEGER")
                db.execSQL("ALTER TABLE `maintenance_items` ADD COLUMN `intervalTimeUnit` TEXT")
                db.execSQL("ALTER TABLE `maintenance_items` ADD COLUMN `reminderLeadKm` INTEGER NOT NULL DEFAULT 1000")
                db.execSQL("ALTER TABLE `maintenance_items` ADD COLUMN `reminderLeadDays` INTEGER NOT NULL DEFAULT 30")
            }
        }
    }
}
