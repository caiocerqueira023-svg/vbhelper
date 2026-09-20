package com.github.nacabaro.vbhelper.screens.itemsScreen

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.domain.items.ItemType
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.screens.storageScreen.StorageCharacterPickerScreen
import com.github.nacabaro.vbhelper.source.StorageRepository
import kotlinx.coroutines.flow.first

@Composable
fun ChooseCharacterScreen(
    navController: NavController,
    itemsScreenController: ItemsScreenControllerImpl,
    itemId: Long
) {
    val application = LocalContext.current.applicationContext as VBHelper
    val storageRepository = remember { StorageRepository(application.container.db) }
    var characterList by remember {
        mutableStateOf<List<CharacterDtos.CharacterWithSprites>>(emptyList())
    }
    var isLoading by remember { mutableStateOf(true) }
    var applying by remember { mutableStateOf(false) }

    LaunchedEffect(itemId) {
        isLoading = true
        val item = storageRepository.getItem(itemId)
        characterList = when (item.itemType) {
            ItemType.BEITEM -> storageRepository.getBECharacters()
            ItemType.VBITEM, ItemType.SPECIALMISSION -> storageRepository.getVBCharacters()
            else -> storageRepository.getAllCharacters().first()
        }
        isLoading = false
    }

    StorageCharacterPickerScreen(
        characters = characterList,
        title = stringResource(R.string.choose_character_title),
        emptyMessage = stringResource(R.string.choose_character_no_available),
        isLoading = isLoading,
        onBack = { navController.popBackStack() },
        onCharacterSelected = { characterId ->
            if (applying) return@StorageCharacterPickerScreen
            applying = true
            itemsScreenController.applyItem(itemId, characterId) {
                Toast.makeText(
                    application.applicationContext,
                    application.getString(R.string.choose_character_item_applied),
                    Toast.LENGTH_SHORT
                ).show()
                navController.popBackStack()
            }
        }
    )
}
