package com.tamerslab.data.database

import androidx.sqlite.db.SupportSQLiteDatabase

object NativeRefreshSchema {
    fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE NativeGameSnapshot ADD COLUMN reviewAuditJson TEXT DEFAULT NULL")
    }
}
