package com.github.nacabaro.vbhelper.screens.tamerArena

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.battle.offline.core.BattleSpeciesIdentity
import com.github.nacabaro.vbhelper.battle.offline.tamers.*
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.screens.OfflineBattleParticipant
import com.github.nacabaro.vbhelper.ui.theme.AppTheme
import com.github.nacabaro.vbhelper.ui.theme.VBHelperTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class TamerArenaLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun fixture(format: Int = 1): TamerArenaState = runBlocking {
        val app = context.applicationContext as VBHelper
        val roster = app.assets.open(TamerCatalog.ASSET_PATH).bufferedReader().use { TamerCatalog.parse(it.readText()) }
        val species = ArenaSpeciesIndex.load(app, app.container.db)
        val agumon = species.first { BattleSpeciesIdentity.normalize(it.name) == "agumon" && it.stage == 2 }
        val gabumon = species.first { BattleSpeciesIdentity.normalize(it.name) == "gabumon" && it.stage == 2 }
        val partners = listOf(agumon, gabumon).mapIndexed { index, art -> OfflineBattleParticipant(null, art.assetId,
            art.name, 1, 1, stage = 2, battleInstanceId = "layout-fixture-$index", cardCharacterId = art.cardCharacterId,
            speciesName = art.name, attribute = art.attribute) }
        TamerArenaState(loading = false, resolver = TamerTeamResolver(roster, species), partners = partners,
            selectedPartnerIds = partners.map { it.stableId }, format = format)
    }

    @Test fun searchBriefingAndBackKeepThePrimaryActionClearInEveryTheme() {
        val state = mutableStateOf(fixture())
        val theme = mutableStateOf(AppTheme.VB_HELPER)
        var tab by mutableIntStateOf(0)
        var query by mutableStateOf("")
        var ready by mutableStateOf(true)
        var challenges = 0
        val actions = ArenaUiActions(onSelectTamer = { state.value = state.value.copy(selectedTamerId = it) },
            onChallenge = { challenges++ }, onShowBracket = { state.value = state.value.copy(activeRunId = it) })
        compose.setContent {
            Fixture(theme.value) { TamerArenaLobby(state.value, actions, tab, { tab = it }, query, { query = it },
                null, {}, ready, { ready = it }) }
        }
        compose.onNodeWithTag("tamer-arena-search").performTextInput("Takato")
        compose.onNodeWithTag("tamer-row-takato").performScrollTo().performClick()
        compose.onNodeWithTag("tamer-arena-challenge").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, challenges) }
        for (palette in AppTheme.entries) {
            compose.runOnIdle { theme.value = palette }
            compose.onNodeWithTag("tamer-arena-challenge").assertIsDisplayed()
            capture("${palette.preferenceValue}-briefing")
        }
        compose.onNodeWithContentDescription(context.getString(R.string.ui_back)).performClick()
        compose.onNodeWithTag("tamer-arena-search").assertIsDisplayed()
        compose.runOnIdle { assertEquals("Takato", query) }
        capture("roster-search")
    }

    @Test fun teamEditorHasLabeledFormatsAndAReachableDoneActionAtLargeFont() {
        val state = mutableStateOf(fixture())
        compose.setContent {
            Fixture(AppTheme.VB_ARENA, fontScale = 1.3f) {
                TamerArenaLobby(state.value, ArenaUiActions(onFormat = { state.value = state.value.copy(format = it) }),
                    0, {}, "", {}, null, {}, true, {})
            }
        }
        compose.onNodeWithTag("arena-edit-team").performClick()
        compose.onNodeWithText(context.getString(R.string.arena_format_label)).assertIsDisplayed()
        compose.onNodeWithText("2 × 2").performClick()
        compose.onNodeWithText(context.getString(R.string.arena_done)).assertIsDisplayed()
        capture("arena-large-font-editor")
        compose.onNodeWithText(context.getString(R.string.arena_done)).performClick()
        compose.onNodeWithTag("tamer-arena-search").assertIsDisplayed()
    }

    @Test fun twoPartnerBriefingKeepsTheChallengeOutsideTheScroll() {
        val state = fixture(2).copy(selectedTamerId = "nokia")
        compose.setContent { Fixture(AppTheme.DIGIMON_NET, fontScale = 1.3f) {
            TamerArenaLobby(state, ArenaUiActions(), 0, {}, "", {}, null, {}, true, {})
        } }
        compose.onNodeWithTag("tamer-arena-challenge").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.arena_their_team)).assertIsDisplayed()
        capture("net-large-font-two-partners")
        compose.onNodeWithText(context.getString(R.string.arena_supplies)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("tamer-arena-challenge").assertIsDisplayed()
        capture("net-large-font-loadout")
    }

    @Test fun emptyRecordsOfferARouteBackToOpponentsAndCupCreationHasOneAction() {
        val state = fixture()
        var tab by mutableIntStateOf(2)
        compose.setContent { Fixture(AppTheme.VB_LAB) {
            TamerArenaLobby(state, ArenaUiActions(), tab, { tab = it }, "", {}, null, {}, true, {})
        } }
        compose.onNodeWithText(context.getString(R.string.arena_browse_tamers)).assertIsDisplayed().performClick()
        compose.onNodeWithTag("tamer-arena-search").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.arena_tournaments)).performClick()
        compose.onNodeWithText(context.getString(R.string.arena_start_cup)).performScrollTo().assertIsDisplayed()
        capture("lab-cup-creation")
    }

    @Composable
    private fun Fixture(theme: AppTheme, fontScale: Float = 1f, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            VBHelperTheme(appTheme = theme) {
                Box(Modifier.fillMaxSize().background(theme.palette.background).safeDrawingPadding()) { content() }
            }
        }
    }

    private fun capture(name: String) {
        val folder = File(context.getExternalFilesDir(null), "tamer-arena-polish").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
