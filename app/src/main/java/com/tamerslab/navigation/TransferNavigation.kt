package com.tamerslab.navigation

enum class TransferSection(val key: String) { SAVES("saves"), STORAGE("storage"), DEVICES("devices"), SEND("send") }

/** All external transfer setup is reached through the same destination, with optional partner context. */
object TransferNavigation {
    fun route(section: TransferSection = TransferSection.SAVES, characterId: Long? = null, individualId: String? = null): AppRoute.Transfer {
        require(characterId == null || characterId > 0)
        return AppRoute.Transfer(section.key, characterId, individualId?.takeIf { it.isNotBlank() })
    }
    fun section(value: String?): TransferSection = TransferSection.entries.firstOrNull { it.key == value } ?: TransferSection.SAVES
    fun character(value: String?): Long? = value?.toLongOrNull()?.takeIf { it > 0 }
    fun individual(value: String?): String? = value?.takeIf { it.isNotBlank() }
}

