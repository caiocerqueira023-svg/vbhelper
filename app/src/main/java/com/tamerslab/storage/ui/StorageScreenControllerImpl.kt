package com.tamerslab.storage.ui

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.tamerslab.components.showAppFeedback
import com.tamerslab.species.PartnerUnavailableException
import com.tamerslab.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StorageScreenControllerImpl(
    private val componentActivity: ComponentActivity
): StorageScreenController {
    private val storage = com.tamerslab.di.AppServices.storageRepository

    override fun setActive(characterId: Long, announce: Boolean, onCompletion: () -> Unit, onRejected: () -> Unit) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                storage.setOnlyActive(characterId)
            } catch (_: PartnerUnavailableException) {
                componentActivity.runOnUiThread {
                    componentActivity.showAppFeedback(R.string.participation_inactive_hint)
                    onRejected()
                }
                return@launch
            }
            componentActivity.runOnUiThread {
                com.tamerslab.widget.DigimonWidgetProvider
                    .notifyActiveCharacterChanged(componentActivity)
                if (announce) {
                    Toast.makeText(
                        componentActivity,
                        componentActivity.getString(R.string.immersion_active_changed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                onCompletion()
            }
        }
    }

    override fun setFavorite(characterId: Long, isFavorite: Boolean, onCompletion: () -> Unit) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            storage.setFavorite(characterId, isFavorite)
            componentActivity.runOnUiThread(onCompletion)
        }
    }

    override fun deleteCharacter(characterId: Long, onCompletion: () -> Unit) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            val individualId = storage.individualId(characterId)
            if (individualId != null &&
                com.tamerslab.di.AppServices.gameImportAcquisition.hasNativeArchive(individualId)) {
                com.tamerslab.di.AppServices.gameImportAcquisition.forgetIndividual(individualId)
            } else {
                storage.delete(characterId)
            }
            componentActivity.runOnUiThread {
                Toast.makeText(
                    componentActivity,
                    componentActivity.getString(R.string.immersion_stored_data_removed),
                    Toast.LENGTH_SHORT
                ).show()
                onCompletion()
            }
        }
    }
}
