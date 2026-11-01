package com.tamerslab.data.database

import androidx.sqlite.db.SupportSQLiteDatabase

/** Evidence tiers do not retroactively promote legacy candidate bindings to verified identity. */
object ProvisionalSnapshotSchema {
    fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE SaveImportDraft ADD COLUMN identityReviewRequired INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ExternalIndividualBinding ADD COLUMN identityConfidence TEXT NOT NULL DEFAULT 'PROVISIONAL'")
    }
}
