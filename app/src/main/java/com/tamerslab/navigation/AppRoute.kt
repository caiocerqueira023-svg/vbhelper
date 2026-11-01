package com.tamerslab.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The single navigation contract. Serial names intentionally match the pre-Phase-3 route
 * patterns so existing deep links and restored back stacks keep resolving.
 */
@Serializable
sealed interface AppRoute {
    @Serializable @SerialName("Battle") data object Battles : AppRoute
    @Serializable @SerialName("Home") data object Home : AppRoute
    @Serializable @SerialName("World") data object World : AppRoute
    @Serializable @SerialName("Radar") data object Radar : AppRoute
    @Serializable @SerialName("Digiline") data object Digiline : AppRoute
    @Serializable @SerialName("Quests") data object Quests : AppRoute
    @Serializable @SerialName("WorldRecruits") data object WorldRecruits : AppRoute
    @Serializable @SerialName("Storage") data object Storage : AppRoute
    @Serializable @SerialName("Dex") data object Dex : AppRoute
    @Serializable @SerialName("Settings") data object Settings : AppRoute
    @Serializable @SerialName("Lorebook") data object Lorebook : AppRoute
    @Serializable @SerialName("Viewer") data object Viewer : AppRoute
    @Serializable @SerialName("Adventure") data object Adventure : AppRoute
    @Serializable @SerialName("Credits") data object Credits : AppRoute

    @Serializable @SerialName("FarmGroup")
    data class FarmGroup(val farmId: String) : AppRoute

    @Serializable @SerialName("WorldConversation")
    data class WorldConversation(val interactionId: String) : AppRoute

    @Serializable @SerialName("WildContact")
    data class WildContact(val individualId: String, val cardCharacterId: Long) : AppRoute

    @Serializable @SerialName("Transfer")
    data class Transfer(
        val section: String = TransferSection.SAVES.key,
        val characterId: Long? = null,
        val individualId: String? = null,
    ) : AppRoute

    @Serializable @SerialName("TechniqueLoadout")
    data class TechniqueLoadout(val characterId: Long) : AppRoute

    @Serializable @SerialName("Scan")
    data class Scan(val characterId: Long? = null) : AppRoute

    @Serializable @SerialName("Card")
    data class CardView(val cardId: Long) : AppRoute

    @Serializable @SerialName("Items")
    data class Items(val tab: String = "mine") : AppRoute

    @Serializable @SerialName("ApplyItem")
    data class ApplyItem(val itemId: Long) : AppRoute

    @Serializable @SerialName("CardAdventure")
    data class CardAdventure(val cardId: Long) : AppRoute

    @Serializable @SerialName("Chat")
    data class Chat(val characterId: Long) : AppRoute

    @Serializable @SerialName("WorldChat")
    data class WorldChat(val spawnId: Long) : AppRoute
}

/** Pure intent mapping used by MainActivity and by the deep-link tests. */
fun appRouteFromUri(uri: String?): AppRoute? {
    val parsed = uri?.let { runCatching { java.net.URI(it) }.getOrNull() } ?: return null
    return when {
        parsed.scheme == "vbhelper" && parsed.host == "auth" -> AppRoute.Battles
        parsed.scheme in setOf("http", "https") &&
            parsed.host in setOf("localhost", "127.0.0.1") &&
            parsed.port == 8080 && parsed.path?.startsWith("/authenticate") == true -> AppRoute.Battles
        else -> null
    }
}

