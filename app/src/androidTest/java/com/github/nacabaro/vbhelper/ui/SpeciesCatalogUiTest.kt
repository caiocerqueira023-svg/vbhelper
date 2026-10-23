package com.github.nacabaro.vbhelper.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.test.platform.app.InstrumentationRegistry
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.SpeciesPickerDialog
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.ui.theme.VBHelperTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SpeciesCatalogUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun addBesideSearchOpensABlankTemplateWithReachableSave() {
        assertTrue(context.packageName.endsWith(".integritycheck"))
        compose.setContent { VBHelperTheme { SpeciesPickerDialog(listOf("Agumon"), {}, {}) } }
        compose.onNodeWithContentDescription(context.getString(R.string.species_add)).assertIsDisplayed().performClick()
        compose.onNode(hasSetTextAction() and hasText(context.getString(R.string.ui_species_name_required)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText(context.getString(R.string.ui_save)).assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun holdingACatalogSpeciesOpensACopyTemplateThatKeepsTheOriginal() {
        assertTrue(context.packageName.endsWith(".integritycheck"))
        val app = context.applicationContext as VBHelper
        val expected = runBlocking { app.container.speciesRepository.getAllSpeciesEntries().first { it.name == "Agumon" } }
        compose.setContent { VBHelperTheme { SpeciesPickerDialog(listOf("Agumon"), {}, {}) } }
        compose.onNode(hasSetTextAction() and hasText(context.getString(R.string.ui_search_species))).performTextInput("Agumon")
        val speciesRow = hasText("Agumon") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodes(speciesRow).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(speciesRow).performTouchInput { longClick() }
        val nameField = hasSetTextAction() and hasText(context.getString(R.string.ui_species_name_required))
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodes(nameField and hasText("Agumon")).fetchSemanticsNodes().isNotEmpty()
        }
        expected.level?.let { compose.onNode(hasSetTextAction() and hasText(it)).assertExists() }
        expected.type?.let { compose.onNode(hasSetTextAction() and hasText(it)).assertExists() }
        compose.onNodeWithText(context.getString(R.string.species_copy_note)).assertIsDisplayed()
        // The copy needs its own name; saving under the original name stays disabled.
        compose.onNodeWithText(context.getString(R.string.species_copy_name_hint)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.ui_save)).assertIsDisplayed().assertIsNotEnabled()
        compose.onNode(nameField).performTextClearance()
        compose.onNode(nameField).performTextInput("Agumon Custom")
        compose.onNodeWithText(context.getString(R.string.ui_save)).assertIsDisplayed().assertIsEnabled()
    }
}
