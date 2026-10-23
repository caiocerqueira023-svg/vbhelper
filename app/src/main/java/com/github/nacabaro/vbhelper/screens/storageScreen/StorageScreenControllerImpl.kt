package com.github.nacabaro.vbhelper.screens.storageScreen

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.github.nacabaro.vbhelper.di.VBHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.room.withTransaction
import com.github.nacabaro.vbhelper.components.showAppFeedback
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.species.PartnerUnavailableException

class StorageScreenControllerImpl(
    private val componentActivity: ComponentActivity
): StorageScreenController {
    private val application = componentActivity.applicationContext as VBHelper
    private val database = application.container.db

    override fun setActive(characterId: Long, announce: Boolean, onCompletion: () -> Unit, onRejected: () -> Unit) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                database.withTransaction {
                    database.participation?.snapshot()?.requireOwned(characterId)
                    database.userCharacterDao().setOnlyActiveCharacter(characterId)
                }
            } catch (failure: PartnerUnavailableException) {
                componentActivity.runOnUiThread {
                    componentActivity.showAppFeedback(R.string.participation_inactive_hint)
                    onRejected()
                }
                return@launch
            }

            componentActivity.runOnUiThread {
                com.github.nacabaro.vbhelper.widget.DigimonWidgetProvider
                    .notifyActiveCharacterChanged(componentActivity)
                if (announce) {
                    Toast.makeText(
                        componentActivity,
                        componentActivity.getString(com.github.nacabaro.vbhelper.R.string.immersion_active_changed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                onCompletion()
            }
        }
    }

    override fun setFavorite(characterId: Long, isFavorite: Boolean, onCompletion: () -> Unit) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            database.userCharacterDao().setFavorite(characterId, isFavorite)
            componentActivity.runOnUiThread(onCompletion)
        }
    }

    override fun deleteCharacter(characterId: Long, onCompletion: () -> Unit) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            val individualId = database.userCharacterDao().getCharacterSync(characterId)?.individualId
            if (individualId != null && application.container.gameImportAcquisition.hasNativeArchive(individualId)) {
                application.container.gameImportAcquisition.forgetIndividual(individualId)
            } else {
                database
                    .userCharacterDao()
                    .deleteCharacterById(characterId)
            }

            componentActivity.runOnUiThread {
                Toast.makeText(
                    componentActivity,
                    componentActivity.getString(com.github.nacabaro.vbhelper.R.string.immersion_stored_data_removed),
                    Toast.LENGTH_SHORT
                ).show()
                onCompletion()
            }
        }
    }
}
