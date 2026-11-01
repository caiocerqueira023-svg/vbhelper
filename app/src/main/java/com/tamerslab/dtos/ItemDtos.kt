package com.tamerslab.dtos

import com.tamerslab.domain.items.ItemType


object ItemDtos {
    data class ItemsWithQuantities(
        val id: Long,
        val name: String,
        val description: String,
        val itemIcon: Int,
        val itemLength: Int,
        val price: Int,
        val quantity: Int,
        val itemType: ItemType
    )

    data class PurchasedItem(
        val itemId: Long,
        val itemName: String,
        val itemDescription: String,
        val itemIcon: Int,
        val itemLength: Int,
        val itemAmount: Int,
        val itemType: ItemType
    )
}