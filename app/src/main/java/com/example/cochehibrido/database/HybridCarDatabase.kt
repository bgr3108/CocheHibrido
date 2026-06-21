package com.example.cochehibrido.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.cochehibrido.data.Car
import com.example.cochehibrido.data.FuelEntry


@Database(
    entities = [Car::class, FuelEntry::class],
    version = 9, // 🔥 IMPORTANTE (sube versión)
    exportSchema = false
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
                    .fallbackToDestructiveMigration() // 🔥 AQUÍ VA
                    .build()
                    .also { Instance = it }
            }
        }
    }
}