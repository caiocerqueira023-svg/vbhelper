package com.github.nacabaro.vbhelper.source

import android.content.SharedPreferences
import com.github.nacabaro.vbhelper.species.SpritePreference
import org.junit.Assert.assertEquals
import org.junit.Test

class SpritePreferenceRepositoryTest {
    private class FakePreferences(private val values: MutableMap<String?, String?> = mutableMapOf()) : SharedPreferences {
        override fun getString(key: String?, defValue: String?) = values[key] ?: defValue
        override fun edit() = object : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?) = apply { values[key] = value }
            override fun remove(key: String?) = apply { values.remove(key) }
            override fun clear() = apply { values.clear() }
            override fun putStringSet(key: String?, v: MutableSet<String>?) = this
            override fun putInt(key: String?, v: Int) = this
            override fun putLong(key: String?, v: Long) = this
            override fun putFloat(key: String?, v: Float) = this
            override fun putBoolean(key: String?, v: Boolean) = this
            override fun commit() = true
            override fun apply() {}
        }
        override fun getAll() = emptyMap<String, Any?>()
        override fun getStringSet(key: String?, defValues: MutableSet<String>?) = defValues
        override fun getInt(key: String?, defValue: Int) = defValue
        override fun getLong(key: String?, defValue: Long) = defValue
        override fun getFloat(key: String?, defValue: Float) = defValue
        override fun getBoolean(key: String?, defValue: Boolean) = defValue
        override fun contains(key: String?) = values.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    @Test fun newInstallationsDefaultToVbFirst() {
        assertEquals(SpritePreference.VB_FIRST, SpritePreferenceRepository(FakePreferences()).current)
    }

    @Test fun preferencePersistsAcrossRestart() {
        val preferences = FakePreferences()
        SpritePreferenceRepository(preferences).setPreference(SpritePreference.DOT_FIRST)
        assertEquals(SpritePreference.DOT_FIRST, SpritePreferenceRepository(preferences).current)
    }

    @Test fun unknownStoredValueFallsBackToVitalBracelet() {
        val preferences = FakePreferences(mutableMapOf<String?, String?>("game_mode" to "SOMETHING_ELSE"))
        assertEquals(SpritePreference.VB_FIRST, SpritePreferenceRepository(preferences).current)
    }

    @Test fun retiredExpandedSettingMigratesToDotFirstWithoutAPlayMode() {
        val preferences = FakePreferences(mutableMapOf("game_mode" to "EXPANDED"))
        val repository = SpritePreferenceRepository(preferences)
        assertEquals(SpritePreference.DOT_FIRST, repository.current)
        repository.setPreference(SpritePreference.VB_FIRST)
        assertEquals(SpritePreference.VB_FIRST, SpritePreferenceRepository(preferences).current)
        org.junit.Assert.assertFalse(preferences.contains("game_mode"))
    }

    @Test fun explicitArtworkPreferenceWinsOverRetiredModeValue() {
        val preferences = FakePreferences(mutableMapOf("game_mode" to "EXPANDED", "sprite_preference" to "VB_FIRST"))
        assertEquals(SpritePreference.VB_FIRST, SpritePreferenceRepository(preferences).current)
    }
}
