package com.github.nacabaro.vbhelper.screens.itemsScreen

import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.components.CharacterEntry
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.domain.items.ItemType
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.source.StorageRepository
import com.github.nacabaro.vbhelper.utils.BitmapData
import kotlinx.coroutines.flow.first
import com.github.nacabaro.vbhelper.R

@Composable
fun ChooseCharacterScreen(
    navController: NavController,
    itemsScreenController: ItemsScreenControllerImpl,
    itemId: Long
) {
    val application = LocalContext.current.applicationContext as VBHelper
    val storageRepository = remember { StorageRepository(application.container.db) }
    val characterList = remember {
        mutableStateOf<List<CharacterDtos.CharacterWithSprites>>(emptyList())
    }

    var selectedCharacter by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(itemId) {
        val item = storageRepository.getItem(itemId)
        characterList.value = when (item.itemType) {
            ItemType.BEITEM -> storageRepository.getBECharacters()
            ItemType.VBITEM, ItemType.SPECIALMISSION -> storageRepository.getVBCharacters()
            else -> storageRepository.getAllCharacters().first()
        }
    }

    LaunchedEffect (selectedCharacter) {
        if (selectedCharacter != null) {
            itemsScreenController.applyItem(itemId, selectedCharacter!!) {
                Toast.makeText(
                    application.applicationContext,
                    application.getString(R.string.choose_character_item_applied),
                    Toast.LENGTH_SHORT
                ).show()
                navController.popBackStack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopBanner(
                text = stringResource(R.string.choose_character_title),
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    ) { contentPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier
                .padding(top = contentPadding.calculateTopPadding())
        ) {
            items(
                items = characterList.value,
                key = { it.id },
                contentType = { "item-character" }
            ) {
                CharacterEntry(
                    icon = BitmapData(
                        bitmap = it.spriteIdle,
                        width = it.spriteWidth,
                        height = it.spriteHeight
                    )
                ) {
                    selectedCharacter = it.id
                }
            }
        }
    }
}
