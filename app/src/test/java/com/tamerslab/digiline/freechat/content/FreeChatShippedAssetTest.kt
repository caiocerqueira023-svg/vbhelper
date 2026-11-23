package com.tamerslab.digiline.freechat.content

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * CI gate for the shipped Free Talk pack (contract §1/§3/§6).
 *
 * Loads the real `app/src/main/assets/freechat/v1/en` pack through the production
 * [FreeChatContentLoader] with a file-backed reader — the same manifest -> intents -> speciesVoice
 * path the app uses through `fromAssets` — then runs [FreeChatContentValidator] explicitly instead
 * of trusting the loader's `BuildConfig.DEBUG` gate, so the shipped pack is asserted end-to-end even
 * in non-debug unit-test variants.
 *
 * Skips (rather than fails) when the assets are not checked out (e.g. a partial worktree).
 */
class FreeChatShippedAssetTest {

    @Test
    fun `shipped free chat pack loads and passes app validation`() {
        val assetsRoot = assetsBaseOrNull()
        assumeTrue("Free Talk assets are not present in this checkout", assetsRoot != null)
        val content = runBlocking {
            FreeChatContentLoader { path ->
                File(requireNotNull(assetsRoot), path).readText(Charsets.UTF_8)
            }.load(FreeChatContentSource.DEFAULT_ASSET_ROOT)
        }

        val errors = FreeChatContentValidator().validate(content)
        assertTrue(
            "Free Talk content validation errors:\n${errors.joinToString("\n")}",
            errors.isEmpty(),
        )
        assertTrue(
            "manifest contentVersion must be >= $SEED_CONTENT_VERSION, was ${content.contentVersion}",
            content.contentVersion >= SEED_CONTENT_VERSION,
        )
        assertTrue(
            "intent count ${content.pack.intents.size} is below the §1 seed target of $MIN_INTENTS",
            content.pack.intents.size >= MIN_INTENTS,
        )
        assertEquals(
            "species_voice.json must carry one entry per catalog species",
            SPECIES_VOICE_COUNT,
            content.speciesVoices.size,
        )
        val fallbackLevels = content.pack.fallbacks.map { it.level }.toSet()
        assertTrue(
            "authored fallback levels 1..4 are required, got $fallbackLevels",
            (1..4).all { it in fallbackLevels },
        )
    }

    /**
     * Assets live at `<module>/src/main/assets`; app unit tests run from the app module directory,
     * but the `app/` prefix is also accepted so the gate works from the repository root.
     */
    private fun assetsBaseOrNull(): File? = ASSET_BASE_CANDIDATES.firstOrNull { base ->
        File(base, "${FreeChatContentSource.DEFAULT_ASSET_ROOT}/${FreeChatContentFiles.MANIFEST}").isFile
    }

    companion object {
        /** Contract §1 seed target: >=120 authored intents. */
        private const val MIN_INTENTS = 120

        /** Contract §1: one `species_voice.json` entry for every catalog species. */
        private const val SPECIES_VOICE_COUNT = 1662

        /** Contract §1/§3: a usable pack never reports content version 0. */
        private const val SEED_CONTENT_VERSION = 1

        private val ASSET_BASE_CANDIDATES = listOf(
            File("src/main/assets"),
            File("app/src/main/assets"),
        )
    }
}
