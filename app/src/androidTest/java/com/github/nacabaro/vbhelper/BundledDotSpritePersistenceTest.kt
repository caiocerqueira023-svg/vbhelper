package com.github.nacabaro.vbhelper

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.characters.Sprite
import com.github.nacabaro.vbhelper.species.BundledDotSpriteLibrary
import com.github.nacabaro.vbhelper.species.SpeciesCustomizationRepository
import com.github.nacabaro.vbhelper.species.SpeciesEntryDto
import com.github.nacabaro.vbhelper.species.SpeciesAppearanceRepository
import com.github.nacabaro.vbhelper.species.SpritePreference
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.createARGBIntArray
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Three bounded device checks: offline decoding, durable selection, and collision protection. */
@RunWith(AndroidJUnit4::class)
class BundledDotSpritePersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "bundled-dot-regression.db"
    private lateinit var db: AppDatabase
    private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()

    @Before fun setup() {
        check(context.packageName.endsWith(".integritycheck"))
        context.deleteDatabase(name)
        db = open()
    }
    @After fun teardown() { db.close(); context.deleteDatabase(name) }

    @Test fun offlineSelectionIsIdempotentAndSurvivesDatabaseReopenWithoutCreatingAPartner() = runBlocking {
        var library = BundledDotSpriteLibrary(context, db)
        val species = library.catalog().resolve("Agumon")!!
        val setId = species.dotSprites.defaultSetId!!
        val before = db.spriteDao().countSprites()
        val id = library.install(setId)
        assertTrue(id < -1)
        assertEquals(id, library.install(setId))
        assertEquals(before + 1, db.spriteDao().countSprites())
        db.speciesCustomizationDao().upsertSpriteChoice(com.github.nacabaro.vbhelper.domain.species.SpeciesSpriteChoice("agumon", id))
        db.close()
        db = open()
        library = BundledDotSpriteLibrary(context, db)
        assertEquals(id, db.speciesCustomizationDao().getSpriteId("agumon"))
        assertEquals(id, library.install(setId))
        assertEquals(before + 1, db.spriteDao().countSprites())
        assertTrue(db.userCharacterDao().getAllCharacters().first().isEmpty())
    }

    @Test fun selectedFramesDecodeWithTransparentBackgroundAndOpaqueArtwork() = runBlocking {
        val library = BundledDotSpriteLibrary(context, db)
        val set = library.catalog().setsForSpecies("Agumon").first()
        val sprite = db.spriteDao().getById(library.install(set.id))!!
        val pixels = BitmapData(sprite.spriteIdle1, sprite.width, sprite.height).createARGBIntArray()
        assertTrue(pixels.any { it ushr 24 == 0 })
        assertTrue(pixels.any { it ushr 24 == 255 })
        assertEquals(set.width, library.portrait(set.id).width)
        assertEquals(set.height, library.portrait(set.id).height)
    }

    @Test fun conflictingReservedIdentityNeverReplacesAnExistingSprite() = runBlocking {
        val library = BundledDotSpriteLibrary(context, db)
        val set = library.catalog().setsForSpecies("Agumon").first()
        val bytes = byteArrayOf(0, 0)
        val existing = Sprite(set.spriteId, bytes, bytes, bytes, bytes, bytes, bytes, bytes, bytes,
            bytes, bytes, bytes, bytes, 1, 1)
        db.spriteDao().insertSprite(existing)
        val error = runCatching { library.install(set.id) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(existing, db.spriteDao().getById(set.spriteId))
        assertEquals(1, db.spriteDao().countSprites())
    }

    @Test fun spritePrioritySwitchesArtworkWithoutOverwritingEitherSource() = runBlocking {
        val library = BundledDotSpriteLibrary(context, db)
        val bytes = byteArrayOf(0, 0)
        val native = Sprite(1, bytes, bytes, bytes, bytes, bytes, bytes, bytes, bytes, bytes, bytes, bytes, bytes, 1, 1)
        db.spriteDao().insertSprite(native)
        db.speciesCustomizationDao().upsertSpriteChoice(com.github.nacabaro.vbhelper.domain.species.SpeciesSpriteChoice("agumon", native.id))
        var mode = SpritePreference.VB_FIRST
        val appearances = SpeciesAppearanceRepository(db, library) { mode }
        assertEquals(native.id, appearances.spriteFor("Agumon")!!.id)
        mode = SpritePreference.DOT_FIRST
        val set = library.catalog().setsForSpecies("Agumon").first()
        assertEquals(set.spriteId, appearances.spriteFor("Agumon")!!.id)
        assertEquals(native, db.spriteDao().getById(native.id))
        mode = SpritePreference.VB_FIRST
        assertEquals(native.id, appearances.spriteFor("Agumon")!!.id)
        assertEquals(native.id, db.speciesCustomizationDao().getSpriteId("agumon"))
    }

    @Test fun batchInstallationKeepsDuplicateSetsIdempotentAndDoesNotCreatePartners() = runBlocking {
        val library = BundledDotSpriteLibrary(context, db)
        val sets = listOf("Agumon", "Diaboromon", "Allomon").map { library.catalog().setsForSpecies(it).first() }
        library.installAll(sets.map { it.id } + sets.first().id)
        assertEquals(sets.size, db.spriteDao().countSprites())
        sets.forEach { assertEquals(it.width, db.spriteDao().getById(it.spriteId)!!.width) }
        assertTrue(db.userCharacterDao().getAllCharacters().first().isEmpty())
    }

    @Test fun catalogDigimonWithoutDotArtworkStillGetsAUsableNormalAppRepresentation() = runBlocking {
        val library = BundledDotSpriteLibrary(context, db)
        val catalog = library.catalog()
        val missingArt = catalog.data.species.first { it.kind == "DIGIMON" && it.dotSprites.defaultSetId == null }
        val definitions = com.github.nacabaro.vbhelper.species.ExpandedCatalogRepository(db, { library }, { SpritePreference.DOT_FIRST })
        db.participation = com.github.nacabaro.vbhelper.species.PartnerParticipationGate(db, { catalog })
        val definition = definitions.definitionFor(missingArt.id)
        assertEquals(com.github.nacabaro.vbhelper.species.PlaceholderSprite.ID.toLong(), definition.spriteId)
        val character = db.withTransaction {
            com.github.nacabaro.vbhelper.species.AppPartnerFactory.createLocked(db, definition, "no-art-partner", 1, activate = true)
        }
        assertTrue(db.participation!!.snapshot().owned(character).usable)
        assertTrue(db.userCharacterDao().getCharacter(character).isActive)
        assertEquals(32, db.spriteDao().getForCharacter(definition.id)!!.width)
        assertNull(definition.cardId)
    }
}
