package com.github.nacabaro.vbhelper.screens.worldScreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RadarOverheadAnnotationTest {
    @get:Rule val compose=createComposeRule()

    @Test fun longNamesAndLargeFontsFitTheirLabelAboveTheDigimon() {
        val name="A particularly long wild Digimon name"
        var spriteTop=0f
        compose.setContent {
            val original=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(original.density,2f)) {
                val density=LocalDensity.current
                spriteTop=with(density) { 240.dp.toPx() }
                MaterialTheme { Box(Modifier.size(320.dp,360.dp)) {
                    RadarOverheadAnnotation(with(density) { 160.dp.toPx() },spriteTop,with(density) { 320.dp.roundToPx() },
                        name,1234,true,null,null,"wild",0,false)
                } }
            }
        }
        compose.waitForIdle()
        val label=compose.onNodeWithTag("radar-entity-label").fetchSemanticsNode().boundsInRoot
        val text=compose.onNodeWithText(name,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue(label.bottom<spriteTop)
        assertTrue(text.left>=label.left && text.right<=label.right)
        assertTrue(text.top>=label.top && text.bottom<=label.bottom)
        compose.onNodeWithText("1234 m",useUnmergedTree=true).assertExists()
    }
}
