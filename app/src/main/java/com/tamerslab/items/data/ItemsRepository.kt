package com.tamerslab.items.data

import com.tamerslab.data.database.AppDatabase
import com.tamerslab.dtos.ItemDtos
import kotlinx.coroutines.flow.Flow

class ItemsRepository(
    private val db: AppDatabase
) {
    fun getAllItems(): Flow<List<ItemDtos.ItemsWithQuantities>> {
        return db.itemDao().getAllItems()
    }

    fun getUserItems(): Flow<List<ItemDtos.ItemsWithQuantities>> {
        return db.itemDao().getAllUserItems()
    }
}