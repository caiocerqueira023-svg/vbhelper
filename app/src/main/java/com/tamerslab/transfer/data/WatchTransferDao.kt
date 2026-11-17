package com.tamerslab.transfer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tamerslab.domain.identity.WatchTransfer
import com.tamerslab.domain.identity.WatchImportReceipt
import com.tamerslab.domain.identity.WatchRepresentationSnapshot

@Dao
interface WatchTransferDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun retainSnapshot(snapshot: WatchRepresentationSnapshot): Long

    @Query("SELECT * FROM WatchRepresentationSnapshot WHERE transferToken=:token")
    fun snapshot(token: String): WatchRepresentationSnapshot?

    /** Most recent device observation for an individual; baselines outlive their transfer tokens. */
    @Query("""SELECT * FROM WatchRepresentationSnapshot WHERE individualId = :individualId
        ORDER BY capturedAt DESC LIMIT 1""")
    fun latestSnapshot(individualId: String): WatchRepresentationSnapshot?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun record(transfer: WatchTransfer)

    @Query("SELECT * FROM WatchTransfer WHERE token = :token")
    fun get(token: String): WatchTransfer?

    @Query("SELECT * FROM WatchTransfer WHERE individualId = :individualId")
    fun getByIndividualId(individualId: String): WatchTransfer?

    @Query("SELECT * FROM WatchTransfer WHERE deviceKey = :deviceKey OR deviceKey = '' OR :deviceKey = ''")
    fun getPendingForWatch(deviceKey: String): List<WatchTransfer>

    @Insert
    fun recordImport(receipt: WatchImportReceipt)

    @Query("SELECT * FROM WatchImportReceipt WHERE fingerprint = :fingerprint")
    fun getImport(fingerprint: String): WatchImportReceipt?

    @Query("DELETE FROM WatchTransfer WHERE token = :token")
    fun consume(token: String)

    @Query("SELECT EXISTS(SELECT 1 FROM UserCharacter WHERE individualId = :individualId) OR EXISTS(SELECT 1 FROM WorldSpawn WHERE individualId = :individualId)")
    fun isPresentLocally(individualId: String): Boolean
}
