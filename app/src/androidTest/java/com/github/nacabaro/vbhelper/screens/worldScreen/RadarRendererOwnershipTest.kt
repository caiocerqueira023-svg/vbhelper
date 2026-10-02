package com.github.nacabaro.vbhelper.screens.worldScreen

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RadarRendererOwnershipTest {
    @get:Rule val compose=createComposeRule()

    @Test fun sceneRequestAndDetachUpdateTheUiWithoutAnotherManualScreenAction() {
        val ownership=RadarRendererOwnership()
        compose.setContent {
            val state by ownership.state.collectAsState()
            MaterialTheme { Column {
                Text(if(state.releasing) "Switching" else state.owner.name)
                Button(onClick={ownership.request(RadarRendererOwner.FIRST_PERSON)}) { Text("First person") }
                Button(onClick={ownership.request(RadarRendererOwner.BATTLE)}) { Text("Battle") }
            } }
        }
        compose.onNodeWithText("First person").performClick()
        compose.onNodeWithText("FIRST_PERSON").assertExists()
        compose.onNodeWithText("Battle").performClick()
        compose.onNodeWithText("Switching").assertExists()
        compose.runOnIdle { ownership.released(RadarRendererOwner.FIRST_PERSON,ownership.generation) }
        compose.onNodeWithText("BATTLE").assertExists()
    }
}
