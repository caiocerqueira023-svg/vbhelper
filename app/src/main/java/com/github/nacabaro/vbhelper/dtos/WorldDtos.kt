package com.github.nacabaro.vbhelper.dtos

object WorldDtos {
    data class SpawnWithDetails(
        val id: Long,
        val cardCharacterId: Long,
        val individualId: String,
        val latitude: Double,
        val longitude: Double,
        val spawnedAt: Long,
        val expiresAt: Long,
        val interacted: Boolean,
        val charaIndex: Int,
        val cardId: Long,
        val spriteIdle: ByteArray,
        val spriteWidth: Int,
        val spriteHeight: Int,
        val speciesName: String?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SpawnWithDetails) return false
            return id == other.id &&
                individualId == other.individualId &&
                spriteIdle.contentEquals(other.spriteIdle)
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + individualId.hashCode()
            result = 31 * result + spriteIdle.contentHashCode()
            return result
        }
    }
}
