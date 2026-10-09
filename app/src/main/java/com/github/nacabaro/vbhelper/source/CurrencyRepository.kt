package com.github.nacabaro.vbhelper.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.quests.QuestWallet
import androidx.room.withTransaction

class CurrencyRepository (
    private val dataStore: DataStore<Preferences>,
    private val database: AppDatabase? = null
) {

    private companion object {
        val CURRENCY_VALUE = intPreferencesKey("currency_value")
    }

    val currencyValue: Flow<Int> = if (database == null) {
        dataStore.data.map { it[CURRENCY_VALUE] ?: 10000 }
    } else flow {
        initializeWallet()
        emitAll(database.questDao().observeBalance().map { it ?: 0 })
    }

    /** INSERT IGNORE makes the legacy preference import safe under concurrent first use. */
    suspend fun initializeWallet() = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext
        if (db.questDao().wallet() == null) {
            val legacy = dataStore.data.first()[CURRENCY_VALUE] ?: 10000
            db.questDao().initializeWallet(QuestWallet(balance = legacy.coerceAtLeast(0)))
        }
    }

    suspend fun credit(amount: Int) {
        require(amount >= 0)
        if (database == null) dataStore.edit {
            val previous = it[CURRENCY_VALUE] ?: 10000
            require(previous <= Int.MAX_VALUE - amount)
            it[CURRENCY_VALUE] = previous + amount
        } else {
            initializeWallet()
            withContext(Dispatchers.IO) {
                database.withTransaction { check(database.questDao().addBits(amount) == 1) }
            }
        }
    }

    suspend fun setCurrencyValue(newValue: Int) {
        require(newValue >= 0)
        if (database == null) dataStore.edit { it[CURRENCY_VALUE] = newValue }
        else {
            initializeWallet()
            withContext(Dispatchers.IO) { check(database.questDao().setBalance(newValue) == 1) }
        }
    }
}
