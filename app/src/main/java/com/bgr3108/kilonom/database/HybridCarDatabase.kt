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


@Database(
    entities = [Car::class, FuelEntry::class],
    version = 10,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class HybridCarDatabase : RoomDatabase() {

    abstract fun carDao(): CarDao
    abstract fun fuelEntryDao(): FuelEntryDao

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
                    .addMigrations(MIGRATION_9_10)
                    .build()
                    .also { Instance = it }
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE fuel_entries ADD COLUMN fuelLevelAfter REAL"
                )
            }
        }
    }
}
