package com.github.nacabaro.vbhelper.dtos

data class FarmResidentWithDetails(
    val individualId: String,
    val farmId: String,
    val characterId: Long,
    val cardCharacterId: Long,
    val nickname: String?,
    val speciesName: String?,
    val spriteIdle: ByteArray,
    val spriteIdle2: ByteArray,
    val spriteWalk: ByteArray,
    val spriteWalk2: ByteArray,
    val spriteTrain: ByteArray,
    val spriteTrain2: ByteArray,
    val spriteHappy: ByteArray,
    val spriteSleep: ByteArray,
    val spriteWidth: Int,
    val spriteHeight: Int,
    val positionX: Float,
    val positionY: Float,
    val targetX: Float,
    val targetY: Float,
    val facingLeft: Boolean,
    val activity: String,
    val energy: Int,
    val satiety: Int,
    val social: Int,
    val funLevel: Int,
    val activityStartedAt: Long,
    val updatedAt: Long
) {
    val displayName: String get() = nickname?.takeIf(String::isNotBlank)
        ?: speciesName?.takeIf(String::isNotBlank)
        ?: "Digimon"

    override fun equals(other: Any?): Boolean = other is FarmResidentWithDetails &&
        individualId == other.individualId && positionX == other.positionX &&
        positionY == other.positionY && targetX == other.targetX && targetY == other.targetY &&
        activity == other.activity && energy == other.energy && satiety == other.satiety &&
        social == other.social && funLevel == other.funLevel &&
        spriteIdle.contentEquals(other.spriteIdle)

    override fun hashCode(): Int = individualId.hashCode()
}

data class DigilineStorageThread(
    val characterId: Long,
    val individualId: String,
    val nickname: String?,
    val speciesName: String?,
    val spriteIdle: ByteArray,
    val spriteWidth: Int,
    val spriteHeight: Int,
    val lastMessage: String?,
    val lastTimestamp: Long?,
    val unreadCount: Int
)

data class DigilineWildThread(
    val individualId: String,
    val cardCharacterId: Long,
    val speciesName: String?,
    val trust: Int,
    val recruitmentState: String,
    val lastMessage: String?,
    val lastTimestamp: Long?,
    val unreadCount: Int
)

data class DigilineFarmThread(
    val farmId: String,
    val farmName: String,
    val residentCount: Int,
    val lastMessage: String?,
    val lastTimestamp: Long?,
    val unreadCount: Int
)

data class FarmAssignment(
    val characterId: Long,
    val farmId: String,
    val farmName: String
)
