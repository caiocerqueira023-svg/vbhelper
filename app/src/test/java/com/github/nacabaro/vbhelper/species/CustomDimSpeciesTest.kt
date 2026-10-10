package com.github.nacabaro.vbhelper.species

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.github.nacabaro.vbhelper.domain.characters.Sprite
import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile
import com.github.nacabaro.vbhelper.domain.species.SpeciesSource
import com.github.nacabaro.vbhelper.source.SpeciesSettingsRepository
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class CustomDimSpeciesTest {
    private val offlineService = object : SpeciesDatabaseService {
        override suspend fun getSpeciesDatabase(url: String): okhttp3.ResponseBody =
            error("Sprite recognition must not fetch remote species data")
    }
    private val settings = SpeciesSettingsRepository(object : DataStore<Preferences> {
        override val data = flowOf(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(emptyPreferences())
    })

    private fun sprite(source: List<SpriteMatchFrame>): Sprite {
        val width = source.maxOf { it.width }
        val height = source.maxOf { it.height }
        // Reconstruct fixed-size DIM canvases from the pose-trimmed PNG exports.
        val frames = source.map { frame ->
            val pixels = ByteArray(width * height * 2) { if (it % 2 == 0) 0xe0.toByte() else 7 }
            for (y in 0 until frame.height) frame.pixels.copyInto(pixels, y * width * 2,
                y * frame.width * 2, (y + 1) * frame.width * 2)
            SpriteMatchFrame(width, height, pixels)
        }
        return Sprite(
            width = width, height = height,
            spriteIdle1 = frames[0].pixels, spriteIdle2 = frames[1].pixels,
            spriteWalk1 = frames[2].pixels, spriteWalk2 = frames[3].pixels,
            spriteRun1 = frames[4].pixels, spriteRun2 = frames[5].pixels,
            spriteTrain1 = frames[6].pixels, spriteTrain2 = frames[7].pixels,
            spriteHappy = frames[8].pixels, spriteSleep = frames[9].pixels,
            spriteAttack = frames[10].pixels, spriteDodge = frames[11].pixels
        )
    }

    @Test fun bundledDimAndBemArtResolveCompleteJsonProfilesWithoutImportedOfficialCards() {
        val root = File("src/main/assets/battle_sprites/extracted_assets/sprites")
        fun load(id: String, number: Int): SpriteMatchFrame? {
            val file = File(root, "$id/${id}_${number.toString().padStart(2, '0')}.png")
            if (!file.exists()) return null
            val image = ImageIO.read(file) ?: return null
            return SpriteMatchFrame.fromArgb(image.width, image.height,
                image.getRGB(0, 0, image.width, image.height, null, 0, image.width))
        }
        val matcher = OfficialSpriteMatcher({ root.list()!!.toList() }, ::load)
        val repo = SpeciesRepository(settingsRepository = settings, service = offlineService, spriteMatcher = matcher)
        val json = repo.parseDatabase(File("src/main/assets/species.json").readText())
        for ((id, cardNumber, index) in listOf(Triple("dim034_mon01", 34, 0), Triple("dim034_mon02", 34, 1),
            Triple("dim034_mon03", 34, 2), Triple("dim131_mon03", 131, 2))) {
            // The first two DIM stages have only five raw poses. Import repeats them
            // across the animation slots instead of reading twelve consecutive images.
            val compactDim = cardNumber < 124 && index < 2
            val nativeFrameOrder = if (compactDim)
                listOf(1, 2, 1, 4, 1, 4, 1, 4, 9, 10, 2, 4) else (1..12).toList()
            val importedFrames = nativeFrameOrder.map { checkNotNull(load(id, it)) }
            val importedSprite = sprite(importedFrames)
            val expected = repo.findCharacterEntry(repo.findSpeciesForCard(json, cardNumber, cardNumber >= 124)!!, index)!!
            val profile = repo.profileForSpriteMatch(9000L, importedSprite, json, null, compactDim)
            val candidates = matcher.findMatches(importedSprite, compactDim).map { identity ->
                identity to repo.findSpeciesForCard(json, identity.cardNumber, false)?.let {
                    repo.findCharacterEntry(it, identity.charaIndex)?.name
                }
            }
            assertNotNull("Bundled art for $id must resolve species; candidates=$candidates; " +
                "sizes=${importedFrames.map { it.width to it.height }}", profile)
            assertEquals(expected.name, profile!!.speciesName)
            assertEquals(expected.name, profile.matchedName)
            assertEquals(expected.level, profile.level)
            assertEquals(expected.type, profile.type)
            assertEquals(expected.profile, profile.profileDescription)
            assertEquals(expected.specialMoves, profile.specialMoves)
            assertEquals(9000L, profile.cardCharacterId)
            assertEquals(SpeciesSource.OFFICIAL_MATCHED, profile.source)
        }
    }

    private val frames = (1..12).map { SpriteMatchFrame(1, 1, byteArrayOf(it.toByte(), 0)) }
    private fun repository(ids: List<String> = listOf("dim034_mon03")) = SpeciesRepository(
        settingsRepository = settings, service = offlineService,
        spriteMatcher = OfficialSpriteMatcher({ ids }) { _, n -> frames[n - 1] }
    )

    @Test fun duplicateExactMatchesForTheSameSpeciesAreAllowedButDifferentSpeciesAreAmbiguous() {
        val repo = repository(listOf("dim001_mon01", "dim002_mon01"))
        val entry = SpeciesEntryDto("Agumon", "Child", "Reptile", "Description", listOf("Baby Flame"))
        val sameSpecies = SpeciesDatabaseDto(species = mapOf("1" to mapOf("1" to entry), "2" to mapOf("1" to entry)))
        assertEquals("Agumon", repo.profileForSpriteMatch(5L, sprite(frames), sameSpecies, null)?.speciesName)
        val ambiguous = sameSpecies.copy(species = sameSpecies.species + ("2" to mapOf("1" to entry.copy(name = "Othermon"))))
        assertNull(repo.profileForSpriteMatch(5L, sprite(frames), ambiguous, null))
    }

    @Test fun paletteShiftedImportStillResolvesTheOfficialSpeciesEntry() {
        val shifted = frames.map { frame ->
            val color = (frame.pixels[0].toInt() and 0xff) or ((frame.pixels[1].toInt() and 0xff) shl 8)
            val encoded = color xor 0x0800
            SpriteMatchFrame(frame.width, frame.height,
                byteArrayOf((encoded and 0xff).toByte(), ((encoded ushr 8) and 0xff).toByte()))
        }
        val json = SpeciesDatabaseDto(species = mapOf("34" to mapOf("3" to SpeciesEntryDto("Zubamon"))))
        assertEquals("Zubamon", repository().profileForSpriteMatch(5L, sprite(shifted), json, null)?.speciesName)
    }

    @Test fun missingJsonEntriesAndModifiedArtDoNotFallBackToTheCustomCardNumber() {
        val repo = repository()
        val unrelated = SpeciesDatabaseDto(species = mapOf("1" to mapOf("1" to SpeciesEntryDto("Wrongmon"))))
        assertNull(repo.profileForSpriteMatch(5L, sprite(frames), unrelated, null))
        val json = SpeciesDatabaseDto(species = mapOf("34" to mapOf("3" to SpeciesEntryDto("Zubamon"))))
        assertNull(repo.profileForSpriteMatch(5L, sprite(frames).copy(spriteAttack = byteArrayOf(31, 0)), json, null))
    }

    @Test fun manualSpeciesInformationIsNeverOverwrittenByAutomaticSpriteMatching() {
        val current = SpeciesProfile(5L, "My custom species", null, "Custom level", source = SpeciesSource.MANUAL)
        val json = SpeciesDatabaseDto(species = mapOf("34" to mapOf("3" to SpeciesEntryDto("Zubamon"))))
        assertNull(repository().profileForSpriteMatch(5L, sprite(frames), json, current))
    }

    @Test fun missingDecimalCardAndOneIndexedSlotCannotAliasHexOrNeighborEntries() {
        val json = SpeciesDatabaseDto(species = mapOf("18" to mapOf("1" to SpeciesEntryDto("Wrongmon")),
            "34" to mapOf("2" to SpeciesEntryDto("NeighborMon"))))
        assertNull(repository(listOf("dim024_mon01")).profileForSpriteMatch(5L, sprite(frames), json, null))
        assertNull(repository().profileForSpriteMatch(5L, sprite(frames), json, null))
    }

    @Test fun paddedDecimalAndExplicitHexJsonKeysResolveTheSameCatalogIdentity() {
        val json = SpeciesDatabaseDto(species = mapOf("034" to mapOf("03" to SpeciesEntryDto("Zubamon"))))
        assertEquals("Zubamon", repository().profileForSpriteMatch(5L, sprite(frames), json, null)?.speciesName)
        val hex = SpeciesDatabaseDto(species = mapOf("0x22" to mapOf("0x3" to SpeciesEntryDto("Zubamon"))))
        assertEquals("Zubamon", repository().profileForSpriteMatch(5L, sprite(frames), hex, null)?.speciesName)
    }

    @Test fun recognizedSpeciesDoesNotInheritFieldsFromAnOldIncorrectIdentification() {
        val current = SpeciesProfile(5L, "Wrongmon", "Wrongmon", "Old level", "Old type", "Old profile",
            listOf("Old move"), SpeciesSource.OFFICIAL_MATCHED)
        val json = SpeciesDatabaseDto(species = mapOf("34" to mapOf("3" to SpeciesEntryDto("Zubamon"))))
        val profile = repository().profileForSpriteMatch(5L, sprite(frames), json, current)!!
        assertEquals("Zubamon", profile.speciesName)
        assertNull(profile.level)
        assertNull(profile.type)
        assertNull(profile.profileDescription)
        assertTrue(profile.specialMoves.isEmpty())
    }
}
