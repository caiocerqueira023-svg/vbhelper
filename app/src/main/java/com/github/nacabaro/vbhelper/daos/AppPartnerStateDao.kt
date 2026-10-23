package com.github.nacabaro.vbhelper.daos

import androidx.room.*
import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.domain.species.AppPartnerProgress
import kotlinx.coroutines.flow.Flow

data class AppHomeStats(@Embedded val progress: AppPartnerProgress, val stage: Int, val attribute: NfcCharacter.Attribute,
    val baseHp: Int, val baseBp: Int, val baseAp: Int, val assignedDefinitionId: Long?, val assignedBem: Boolean) {
    /** Effective card-rule values; the independent progress object is never overwritten. */
    val trainingHp: Int get() = if (assignedDefinitionId != null) 0 else progress.trainingHp
    val trainingBp: Int get() = if (assignedDefinitionId != null) 0 else progress.trainingBp
    val trainingAp: Int get() = if (assignedDefinitionId != null) 0 else progress.trainingAp
}

@Dao
interface AppPartnerStateDao {
    @Query("""SELECT p.*,COALESCE(a.stage,c.stage) AS stage,COALESCE(a.attribute,c.attribute) AS attribute,
        COALESCE(a.baseHp,c.baseHp) AS baseHp,COALESCE(a.baseBp,c.baseBp) AS baseBp,COALESCE(a.baseAp,c.baseAp) AS baseAp,
        a.id AS assignedDefinitionId,COALESCE(card.isBEm,0) AS assignedBem FROM UserCharacter u
        JOIN CardCharacter c ON c.id=u.charId JOIN AppPartnerProgress p ON p.individualId=u.individualId
        LEFT JOIN CardCharacter a ON a.id=u.exportCardCharacterId AND a.cardId IS NOT NULL AND a.speciesId=c.speciesId
        LEFT JOIN Card card ON card.id=a.cardId WHERE u.id=:id AND u.characterType='APP'""")
    fun home(id: Long): Flow<AppHomeStats?>
    @Query("SELECT * FROM AppPartnerProgress WHERE individualId=:id") suspend fun state(id: String): AppPartnerProgress?
    @Update suspend fun update(state: AppPartnerProgress)
}
