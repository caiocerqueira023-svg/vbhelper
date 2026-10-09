package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Query
import com.github.nacabaro.vbhelper.dtos.ItemDtos
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM Items ORDER BY id")
    fun questItems(): List<com.github.nacabaro.vbhelper.domain.items.Items>

    @Query("UPDATE Items SET quantity = quantity - :amount WHERE id = :id AND :amount > 0 AND quantity >= :amount")
    fun handOver(id: Long, amount: Int): Int

    @Query("UPDATE Items SET quantity = quantity + :amount WHERE id = :id AND :amount > 0 AND quantity <= 2147483647 - :amount")
    fun grantQuestItem(id: Long, amount: Int): Int
    @Query(
        """
        SELECT *
        FROM Items
        ORDER BY Items.itemIcon ASC
    """
    )
    fun getAllItems(): Flow<List<ItemDtos.ItemsWithQuantities>>

    @Query(
        """
        SELECT *
        FROM Items
        WHERE quantity > 0
    """
    )
    fun getAllUserItems(): Flow<List<ItemDtos.ItemsWithQuantities>>

    @Query(
        """
        SELECT *
        FROM Items
        WHERE Items.id = :itemId
    """
    )
    suspend fun getItem(itemId: Long): ItemDtos.ItemsWithQuantities

    @Query(
        """
        UPDATE Items
        SET quantity = quantity - 1
        WHERE id = :itemId AND quantity > 0
        """
    )
    suspend fun useItem(itemId: Long): Int

    @Query(
        """
        UPDATE Items
        SET quantity = quantity + :itemAmount
        WHERE id = :itemId
    """
    )
    suspend fun purchaseItem(itemId: Long, itemAmount: Int)
}
