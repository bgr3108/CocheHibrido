package com.bgr3108.kilonom.chargers

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Manual release-maintenance tool. It converts a validated RIPREE CSV already
 * pushed to /data/local/tmp into the exact Room database used by the app.
 * Normal Android test runs skip it unless the explicit instrumentation flag is
 * supplied; builds never download RIPREE.
 */
class ChargerSeedGeneratorTest {
    @Test
    fun generateSeedFromValidatedLocalRipreeExport() {
        val arguments = InstrumentationRegistry.getArguments()
        if (arguments.getString("generateChargerSeed") != "true") return

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "charger_seed_generator.db"
        context.deleteDatabase(databaseName)
        val database = Room.databaseBuilder(context, ChargerCacheDatabase::class.java, databaseName)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
        try {
            val csvFile = File(context.getExternalFilesDir(null), "kilonom-ripree.csv")
            require(csvFile.exists()) { "No se encuentra el CSV RIPREE local para generar la seed" }
            val parsed = InputStreamReader(
                FileInputStream(csvFile),
                StandardCharsets.UTF_16LE
            ).buffered().use(MitecoChargersParser::parse)
            require(parsed.installations.isNotEmpty() && parsed.connectors.isNotEmpty()) {
                "El CSV RIPREE validado no contiene instalaciones o conectores"
            }
            assertTrue(
                "El CSV RIPREE no contiene el conjunto esperado: ${parsed.installations.size} instalaciones",
                parsed.installations.size > 10_000
            )
            assertTrue(
                "El CSV RIPREE se truncó antes de procesarlo: ${parsed.connectors.size} conectores",
                parsed.connectors.size > 30_000
            )
            runBlocking {
                database.chargerCacheDao().replaceCache(
                    parsed.installations.withConnectorSummaries(parsed.connectors),
                    parsed.connectors,
                    ChargerCacheMetadataEntity(
                        downloadedAtMillis = arguments.getString("seedGeneratedAtMillis")?.toLongOrNull()
                            ?: System.currentTimeMillis(),
                        sourceUpdatedAtMillis = parsed.sourceUpdatedAtMillis,
                        sourceUrl = MITECO_CHARGERS_URL
                    )
                )
            }
            val databaseFile = context.getDatabasePath(databaseName)
            assertTrue("No se creó la base seed", File(databaseFile.path).exists())
        } finally {
            database.close()
        }
    }
}
