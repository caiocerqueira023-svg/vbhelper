package com.github.nacabaro.vbhelper.navigation

enum class TransferSection(val key: String) { SAVES("saves"), STORAGE("storage"), DEVICES("devices"), SEND("send") }

/** All external transfer setup is reached through the same destination, with optional partner context. */
object TransferNavigation {
    const val PATTERN = "Transfer?section={section}&characterId={characterId}&individualId={individualId}"
    fun route(section: TransferSection = TransferSection.SAVES, characterId: Long? = null, individualId: String? = null): String {
        require(characterId == null || characterId > 0)
        return "Transfer?section=${section.key}" + (characterId?.let { "&characterId=$it" } ?: "") +
            (individualId?.let { "&individualId=$it" } ?: "")
    }
    fun section(value: String?): TransferSection = TransferSection.entries.firstOrNull { it.key == value } ?: TransferSection.SAVES
    fun character(value: String?): Long? = value?.toLongOrNull()?.takeIf { it > 0 }
    fun individual(value: String?): String? = value?.takeIf { it.isNotBlank() }
}
