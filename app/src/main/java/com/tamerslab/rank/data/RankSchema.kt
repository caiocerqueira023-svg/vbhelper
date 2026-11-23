package com.tamerslab.rank.data

import androidx.sqlite.db.SupportSQLiteDatabase

/** Schema for the offline Tamer Rank tables (database version 58). */
object RankSchema {
    fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tamer_profile` (" +
                "`id` INTEGER NOT NULL, `totalTxp` INTEGER NOT NULL, `level` INTEGER NOT NULL, " +
                "`rank` INTEGER NOT NULL, `lastLevelUpAt` INTEGER, `lastRankUpAt` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tamer_txp_event` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `source` TEXT NOT NULL, " +
                "`amount` INTEGER NOT NULL, `idempotency_key` TEXT NOT NULL, " +
                "`digimonRef` TEXT, `origin` TEXT, `metadata` TEXT, `occurred_at` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tamer_txp_event_idempotency_key` " +
                "ON `tamer_txp_event` (`idempotency_key`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tamer_daily_cap` (" +
                "`day` TEXT NOT NULL, `source` TEXT NOT NULL, `total` INTEGER NOT NULL, " +
                "PRIMARY KEY(`day`, `source`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tamer_reward` (" +
                "`rank` INTEGER NOT NULL, `slot` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                "`amount` INTEGER NOT NULL, `labelKey` TEXT NOT NULL, `claimed_at` INTEGER, " +
                "PRIMARY KEY(`rank`, `slot`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tamer_activity` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `occurred_at` INTEGER NOT NULL, " +
                "`source` TEXT NOT NULL, `delta` INTEGER NOT NULL, `summary` TEXT NOT NULL)",
        )
    }
}
