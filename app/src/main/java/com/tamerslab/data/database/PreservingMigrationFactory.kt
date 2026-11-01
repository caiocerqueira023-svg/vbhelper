package com.tamerslab.data.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/** Disable cascades before the upgrade transaction, then restore enforcement after Room validates it. */
class PreservingMigrationFactory : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val original = configuration.callback
        val callback = object : SupportSQLiteOpenHelper.Callback(original.version) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                original.onConfigure(db)
                // Legacy definition rebuilds and 52→53 native-nullability rebuild must not cascade children.
                if (db.version in 1..52) db.setForeignKeyConstraintsEnabled(false)
            }
            override fun onCreate(db: SupportSQLiteDatabase) = original.onCreate(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = original.onUpgrade(db, oldVersion, newVersion)
            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = original.onDowngrade(db, oldVersion, newVersion)
            override fun onOpen(db: SupportSQLiteDatabase) {
                original.onOpen(db)
                db.setForeignKeyConstraintsEnabled(true)
            }
            override fun onCorruption(db: SupportSQLiteDatabase) = original.onCorruption(db)
        }
        return FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
            .name(configuration.name).callback(callback).noBackupDirectory(configuration.useNoBackupDirectory).build())
    }
}
