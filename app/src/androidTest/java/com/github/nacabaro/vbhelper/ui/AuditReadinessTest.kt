package com.github.nacabaro.vbhelper.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.CollectionStatePanel
import com.github.nacabaro.vbhelper.components.rememberCollectionLoad
import com.github.nacabaro.vbhelper.ui.theme.VBHelperTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class AuditReadinessTest {
    @get:Rule val compose = createComposeRule()
    private val resources get() = InstrumentationRegistry.getInstrumentation().targetContext.resources

    @Test fun delayedFirstSnapshotDoesNotClaimTheCollectionIsEmpty() {
        val ready = CompletableDeferred<Unit>()
        compose.setContent { VBHelperTheme {
            val load = rememberCollectionLoad("delayed") { flow { ready.await(); emit(emptyList<String>()) } }
            if (!CollectionStatePanel(load)) Text("Collection empty")
        } }
        compose.onNodeWithText("Collection empty").assertDoesNotExist()
        compose.onNodeWithText(resources.getString(R.string.audit_loading_collection)).assertIsDisplayed()
        compose.runOnIdle { ready.complete(Unit) }
        compose.waitUntil { compose.onAllNodesWithText("Collection empty").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Collection empty").assertIsDisplayed()
    }

    @Test fun failedFirstLoadCanRetryIntoUsableRecords() {
        var attempts = 0
        compose.setContent { VBHelperTheme {
            val load = rememberCollectionLoad("retry") { flow {
                if (attempts++ == 0) throw IOException("Unavailable")
                emit(listOf("Agumon"))
            } }
            if (!CollectionStatePanel(load)) Text(load.data!!.single())
        } }
        compose.waitUntil { compose.onAllNodesWithText(resources.getString(R.string.app_retry)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(resources.getString(R.string.app_retry)).performClick()
        compose.waitUntil { compose.onAllNodesWithText("Agumon").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Agumon").assertIsDisplayed()
    }
}
