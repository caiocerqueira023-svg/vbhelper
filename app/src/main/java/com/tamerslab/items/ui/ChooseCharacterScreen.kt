package com.tamerslab.items.ui

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
import com.tamerslab.R
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.domain.items.ItemType
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.storage.ui.StorageCharacterPickerScreen
import com.tamerslab.storage.data.StorageRepository
import kotlinx.coroutines.flow.first

@Composable
fun ChooseCharacterScreen(
    navController: NavController,
    itemsScreenController: ItemsScreenControllerImpl,
    itemId: Long
) {
    val application = LocalContext.current.applicationContext as TamersLabApplication
    val storageRepository = remember { application.container.storageRepository }
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
