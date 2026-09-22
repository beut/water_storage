package pl.watershed.septictank.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: MeterReadingEntity.photoPath stał się nullable (FR-015, commit fad78c9).
 * SQLite nie obsługuje ALTER COLUMN, więc tabelę trzeba przebudować: nowa tabela z
 * poprawionym schematem, skopiowanie danych, podmiana nazw. Zachowuje dane użytkownika
 * przy aktualizacji aplikacji (zamiast fallbackToDestructiveMigration, które je kasowało).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE meter_readings_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestampMillis INTEGER NOT NULL,
                valueLiters INTEGER NOT NULL,
                photoPath TEXT,
                source TEXT NOT NULL,
                isAnomalous INTEGER NOT NULL,
                anomalyAcknowledged INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO meter_readings_new
                (id, timestampMillis, valueLiters, photoPath, source, isAnomalous, anomalyAcknowledged)
            SELECT id, timestampMillis, valueLiters, photoPath, source, isAnomalous, anomalyAcknowledged
            FROM meter_readings
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE meter_readings")
        db.execSQL("ALTER TABLE meter_readings_new RENAME TO meter_readings")
    }
}
