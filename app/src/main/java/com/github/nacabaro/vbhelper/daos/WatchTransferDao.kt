package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.nacabaro.vbhelper.domain.identity.WatchTransfer
import com.github.nacabaro.vbhelper.domain.identity.WatchImportReceipt

@Dao
interface WatchTransferDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun record(transfer: WatchTransfer)

    @Query("SELECT * FROM WatchTransfer WHERE token = :token")
    fun get(token: String): WatchTransfer?

    @Query("SELECT * FROM WatchTransfer WHERE individualId = :individualId")
    fun getByIndividualId(individualId: String): WatchTransfer?

    @Query("SELECT * FROM WatchTransfer WHERE deviceKey = :deviceKey OR deviceKey = ''")
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
