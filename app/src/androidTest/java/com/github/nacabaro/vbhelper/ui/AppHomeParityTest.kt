package com.github.nacabaro.vbhelper.ui

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.daos.AppHomeStats
import com.github.nacabaro.vbhelper.domain.species.AppPartnerProgress
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.screens.homeScreens.HomeScreenControllerImpl
import com.github.nacabaro.vbhelper.screens.homeScreens.screens.VBDiMHomeScreen
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.DeviceType
import org.junit.Rule
import org.junit.Test

class AppHomeParityTest {
    @get:Rule val compose = createComposeRule()
    @Test fun cardIndependentPartnerUsesFullDimHomeWithoutFakeDeviceRows() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".integritycheck"))
        val pixel = byteArrayOf(0,0)
        val mon = CharacterDtos.CharacterWithSprites(id=1,charId=-1,stage=2,attribute=NfcCharacter.Attribute.Vaccine,
            ageInDays=3,mood=80,vitalPoints=1234,transformationCountdown=120,injuryStatus=NfcCharacter.InjuryStatus.None,
            trophies=5,currentPhaseBattlesWon=3,currentPhaseBattlesLost=1,totalBattlesWon=8,totalBattlesLost=2,
            activityLevel=0,heartRateCurrent=0,characterType=DeviceType.APP,spriteIdle=pixel,spriteIdle2=pixel,
            spriteRun1=pixel,spriteRun2=pixel,spriteWidth=1,spriteHeight=1,nameSprite=pixel,nameSpriteWidth=1,nameSpriteHeight=1,
            isBemCard=false,nickname="Partner",speciesName="Agumon",isInAdventure=false,active=true,isFavorite=false)
        compose.setContent {
            val activity = generateSequence(LocalContext.current) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<ComponentActivity>().first()
            MaterialTheme {
                VBDiMHomeScreen(mon, BitmapData(pixel,1,1), null, emptyList(), HomeScreenControllerImpl(activity), emptyList(),
                    "Partner", "Agumon", PaddingValues(0.dp), { _, _ -> },
                    appData=AppHomeStats(AppPartnerProgress("parity"),2,NfcCharacter.Attribute.Vaccine,3,10,2,null,false))
            }
        }
        compose.onNodeWithText(context.getString(R.string.home_vbdim_mood), ignoreCase=true).assertExists()
        compose.onNodeWithText(context.getString(R.string.home_vbdim_trophies), ignoreCase=true).assertExists()
        compose.onNodeWithText(context.getString(R.string.home_vbdim_next_timer), ignoreCase=true).assertExists()
        compose.onNodeWithText(context.getString(R.string.home_be_training_hp), ignoreCase=true).assertExists()
    }
}
