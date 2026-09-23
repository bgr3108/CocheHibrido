package com.bgr3108.kilonom.chargers

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Public RIPREE data is deliberately isolated from vehicles and from MITECO fuel-station cache. */
@Database(
    entities = [ChargerInstallationEntity::class, ChargerConnectorEntity::class, ChargerCacheMetadataEntity::class],
    version = 4,
    exportSchema = true
)
abstract class ChargerCacheDatabase : RoomDatabase() {
    abstract fun chargerCacheDao(): ChargerCacheDao

    companion object {
        const val DATABASE_NAME = "charger_cache"
        const val SEED_ASSET_PATH = "databases/charger_cache.db"

        @Volatile private var instance: ChargerCacheDatabase? = null
        fun getDatabase(context: Context): ChargerCacheDatabase = instance ?: synchronized(this) {
            buildDatabase(context.applicationContext, DATABASE_NAME)
                .also { instance = it }
        }

        /**
         * The seed is copied only when this public cache has no local database yet. Room also
         * uses it after a future destructive cache recreation, never over an existing cache.
         */
        internal fun buildDatabase(context: Context, databaseName: String): ChargerCacheDatabase =
            Room.databaseBuilder(context.applicationContext, ChargerCacheDatabase::class.java, databaseName)
                .createFromAsset(SEED_ASSET_PATH)
                // RIPREE data is a replaceable public cache, separate from the personal database.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
