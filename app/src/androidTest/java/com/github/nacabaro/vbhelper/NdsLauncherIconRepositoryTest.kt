package com.github.nacabaro.vbhelper

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.nacabaro.vbhelper.gamesave.NdsLauncherBanner
import com.github.nacabaro.vbhelper.navigation.TransferGame
import com.github.nacabaro.vbhelper.source.gameSave.NdsLauncherIconRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NdsLauncherIconRepositoryTest {
    private lateinit var context: Context
    private lateinit var root: File

    @Before fun clear() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "transfer-launcher-icons")
        root.deleteRecursively()
    }

    @After fun cleanup() {
        root.deleteRecursively()
    }

    @Test fun storesOnlyVerifiedTitleBannersAndReadsThemBack() = runBlocking {
        val repository = NdsLauncherIconRepository(context)
        val pixels = IntArray(1024) { 0xFF112233.toInt() }

        assertFalse(repository.hasIcon(TransferGame.WORLD_DS))
        repository.import(TransferGame.WORLD_DS, NdsLauncherBanner("ADNE", pixels))

        assertTrue(repository.hasIcon(TransferGame.WORLD_DS))
        val icon = repository.icon(TransferGame.WORLD_DS)!!
        assertEquals(32, icon.width)
        assertEquals(32, icon.height)
        assertEquals(0xFF112233.toInt(), icon.getPixel(31, 31))
    }

    @Test fun rejectsWrongTitleCodeAndUnsupportedTitle() = runBlocking {
        val repository = NdsLauncherIconRepository(context)
        val banner = NdsLauncherBanner("ADNE", IntArray(1024))

        try {
            repository.import(TransferGame.DAWN, banner)
            fail("Dawn accepted a World DS banner")
        } catch (_: IllegalArgumentException) { }

        try {
            repository.import(TransferGame.LOST_EVOLUTION, banner)
            fail("Unsupported title accepted a banner")
        } catch (_: IllegalArgumentException) { }
    }
}
