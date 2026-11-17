package com.tamerslab.transfer.ui.vpet

import android.content.Context
import com.tamerslab.hardware.vpet.VpetModel

/**
 * Small synchronous store for the two facts the VPet reader remembers locally:
 * the last selected device model and the firmware version learned from a successful read.
 *
 * Imported reads and pending reads are business data and live in the repository, never here.
 */
class VpetPreferences(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun lastModel(): VpetModel =
        VpetModel.fromKey(preferences.getString(KEY_LAST_MODEL, null).orEmpty()) ?: VpetModel.DM20

    fun setLastModel(model: VpetModel) {
        preferences.edit().putString(KEY_LAST_MODEL, model.wireKey).apply()
    }

    fun learnedVersion(model: VpetModel): Int? =
        preferences.getInt(versionKey(model), NO_VERSION).takeIf { it >= 0 }

    fun setLearnedVersion(model: VpetModel, version: Int) {
        if (version < 0) return
        preferences.edit().putInt(versionKey(model), version).apply()
    }

    private fun versionKey(model: VpetModel): String = "$KEY_VERSION_PREFIX${model.wireKey}"

    private companion object {
        const val PREFERENCES_NAME = "vpet_reader"
        const val KEY_LAST_MODEL = "last_model"
        const val KEY_VERSION_PREFIX = "learned_version_"
        const val NO_VERSION = -1
    }
}
