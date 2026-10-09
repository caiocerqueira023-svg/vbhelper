package com.github.nacabaro.vbhelper

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.database.IndividualIntegrity
import com.github.nacabaro.vbhelper.domain.scan.DigimonScanProgress
import com.github.nacabaro.vbhelper.source.DigimonScanRepository
import com.github.nacabaro.vbhelper.source.ScanConversionResult
import com.github.nacabaro.vbhelper.utils.DeviceType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DigimonScanPersistenceTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: DigimonScanRepository

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".integritycheck"))
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(IndividualIntegrity.callback).build()
        repository = DigimonScanRepository(db) { 123_000L }
        seed("Card", mapOf("id" to 1, "name" to "DIM fixture"))
        seed("Card", mapOf("id" to 2, "name" to "BEM fixture", "isBEm" to 1))
        seed("Sprite", mapOf("id" to 1))
        seed("CardCharacter", mapOf("id" to 100, "cardId" to 1, "spriteId" to 1, "attribute" to "Virus"))
        seed("CardCharacter", mapOf("id" to 200, "cardId" to 2, "spriteId" to 1, "attribute" to "Data"))
    }

    @After fun cleanup() { db.close() }

    @Test fun incompleteScanCannotCreateAnIndividualOrConsumeData() = runBlocking {
        db.digimonScanDao().saveProgress(DigimonScanProgress(100, 80, 1))
        assertEquals(ScanConversionResult.NotReady, repository.convert(100))
        assertEquals(80, db.digimonScanDao().getProgress(100))
        assertEquals(0, count("UserCharacter"))
        assertEquals(0, count("DigimonIndividual"))
    }

    @Test fun conversionCreatesAFreshCompleteVbIndividualAndOnlyMarksTheConvertedVariantObtained() = runBlocking {
        seed("DigimonIndividual", mapOf("individualId" to "original-wild"))
        seed("ChatMessageEntity", mapOf("individualId" to "original-wild", "role" to "user", "content" to "private history"))
        db.digimonScanDao().saveProgress(DigimonScanProgress(100, 100, 1))
        val result = repository.convert(100, "  New partner  ") as ScanConversionResult.Converted
        val owned = db.userCharacterDao().getCharacterSync(result.characterId)!!
        assertNotEquals("original-wild", owned.individualId)
        assertFalse(owned.isActive)
        assertEquals(DeviceType.VBDevice, owned.characterType)
        assertEquals(1, owned.transformationCountdown)
        assertNotNull(db.userCharacterDao().getVbDataOrNull(owned.id))
        assertEquals(4, count("SpecialMissions"))
        assertEquals(1, count("TransformationHistory"))
        assertEquals(1, count("DigimonPersonalityTraits"))
        assertEquals(0, db.digimonScanDao().getProgress(100))
        assertEquals(1, count("Dex"))
        assertEquals(1, count("ChatMessageEntity"))
        assertEquals("New partner", db.openHelper.writableDatabase.query("SELECT nickname FROM DigimonIndividual WHERE individualId='${owned.individualId}'").use {
            it.moveToFirst(); it.getString(0)
        })
    }

    @Test fun bemConversionCreatesBeTrainingDataWithoutVbMissionSlots() = runBlocking {
        db.digimonScanDao().saveProgress(DigimonScanProgress(200, 100, 1))
        val result = repository.convert(200) as ScanConversionResult.Converted
        assertEquals(DeviceType.BEDevice, db.userCharacterDao().getCharacterSync(result.characterId)!!.characterType)
        assertEquals(0, db.userCharacterDao().getCharacterSync(result.characterId)!!.transformationCountdown)
        val be = db.userCharacterDao().getBeDataOrNull(result.characterId)!!
        assertEquals(6000, be.remainingTrainingTimeInMinutes)
        assertEquals(0, be.trainingHp)
        assertNull(db.userCharacterDao().getVbDataOrNull(result.characterId))
        assertEquals(0, count("SpecialMissions"))
    }

    @Test fun concurrentConversionsConsumeOneScanAndCreateOneIndividual() = runBlocking {
        db.digimonScanDao().saveProgress(DigimonScanProgress(100, 100, 1))
        val results = coroutineScope { listOf(async { repository.convert(100) }, async { repository.convert(100) }).map { it.await() } }
        assertEquals(1, results.count { it is ScanConversionResult.Converted })
        assertEquals(1, results.count { it == ScanConversionResult.NotReady })
        assertEquals(1, count("UserCharacter"))
    }

    @Test fun failedAuxiliaryInsertRollsBackScanIdentityAndAcquisition() = runBlocking {
        db.digimonScanDao().saveProgress(DigimonScanProgress(100, 100, 1))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_scan_profile BEFORE INSERT ON VBCharacterData BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        assertTrue(runCatching { repository.convert(100) }.isFailure)
        assertEquals(100, db.digimonScanDao().getProgress(100))
        assertEquals(0, count("DigimonIndividual"))
        assertEquals(0, count("UserCharacter"))
        assertEquals(0, count("Dex"))
    }

    @Test fun deletingACardRemovesItsScanAndDoesNotTouchOtherVariants() = runBlocking {
        db.digimonScanDao().saveProgress(DigimonScanProgress(100, 100, 1))
        db.digimonScanDao().saveProgress(DigimonScanProgress(200, 60, 1))
        db.cardDao().deleteCard(1)
        assertEquals(ScanConversionResult.Unavailable, repository.convert(100))
        assertNull(db.digimonScanDao().getProgress(100))
        assertEquals(60, db.digimonScanDao().getProgress(200))
    }

    private fun count(table: String) = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM `$table`").use {
        it.moveToFirst(); it.getInt(0)
    }

    private fun seed(table: String, overrides: Map<String, Any?>) {
        val values = linkedMapOf<String, Any?>()
        db.openHelper.writableDatabase.query("PRAGMA table_info(`$table`)").use { cursor ->
            while (cursor.moveToNext()) {
                val column = cursor.getString(1)
                if (column in overrides) values[column] = overrides[column]
                else if (cursor.getInt(3) != 0 && cursor.isNull(4) && column != "id") {
                    values[column] = when (cursor.getString(2)) { "TEXT" -> ""; "BLOB" -> byteArrayOf(0); else -> 0 }
                }
            }
        }
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO `$table` (${values.keys.joinToString { "`$it`" }}) VALUES (${values.keys.joinToString { "?" }})",
            values.values.toTypedArray())
    }
}
