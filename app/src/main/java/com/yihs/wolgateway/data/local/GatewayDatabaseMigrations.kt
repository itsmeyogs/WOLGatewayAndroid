package com.yihs.wolgateway.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS foundation_metadata")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS devices (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                ipAddress TEXT NOT NULL,
                macAddress TEXT NOT NULL,
                broadcastAddress TEXT NOT NULL,
                wolPort INTEGER NOT NULL,
                statusCheckMethod TEXT NOT NULL,
                statusCheckPort INTEGER NOT NULL,
                status TEXT NOT NULL,
                lastCheckAt INTEGER,
                lastCheckReason TEXT,
                lastWolResult TEXT NOT NULL,
                lastWolAt INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_devices_macAddress ON devices(macAddress)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS index_devices_ipAddress")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE devices ADD COLUMN lastCheckLatencyMs INTEGER")
    }
}
