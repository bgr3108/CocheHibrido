package com.bgr3108.kilonom.stations

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** A public-data cache intentionally kept outside Kilonom's personal-data database. */
@Database(
    entities = [FuelStationEntity::class, FuelStationPriceEntity::class, StationCacheMetadataEntity::class],
    version = 3,
    exportSchema = true
)
abstract class StationCacheDatabase : RoomDatabase() {
    abstract fun stationCacheDao(): StationCacheDao

    companion object {
        @Volatile private var instance: StationCacheDatabase? = null

        fun getDatabase(context: Context): StationCacheDatabase = instance ?: synchronized(this) {
            Room.databaseBuilder(
                context.applicationContext,
                StationCacheDatabase::class.java,
                "station_cache"
            )
                // This database stores only downloadable MITECO public data. Rebuilding it is
                // deliberate when its query-oriented cache schema changes; personal Room data is separate.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                .also { instance = it }
        }
    }
}
