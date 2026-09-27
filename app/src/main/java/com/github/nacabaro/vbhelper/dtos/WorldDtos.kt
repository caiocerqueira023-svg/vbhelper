package com.github.nacabaro.vbhelper.dtos

import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.domain.world.RecruitmentState

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
        val stage: Int,
        val cardId: Long,
        val attribute: NfcCharacter.Attribute,
        val baseHp: Int,
        val baseBp: Int,
        val baseAp: Int,
        val isBemCard: Boolean,
        val spriteIdle: ByteArray,
        val spriteIdle2: ByteArray,
        val spriteWalk: ByteArray,
        val spriteWalk2: ByteArray,
        val spriteRun: ByteArray,
        val spriteRun2: ByteArray,
        val spriteWidth: Int,
        val spriteHeight: Int,
        val speciesName: String?,
        val mood: Int,
        val recruitmentState: RecruitmentState,
        @Deprecated("Inert after Digiline migration.")
        val isFollowing: Boolean = false
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SpawnWithDetails) return false
            return id == other.id &&
                individualId == other.individualId &&
                mood == other.mood &&
                recruitmentState == other.recruitmentState &&
                isFollowing == other.isFollowing &&
                spriteIdle.contentEquals(other.spriteIdle) &&
                spriteIdle2.contentEquals(other.spriteIdle2)
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + individualId.hashCode()
            result = 31 * result + mood
            result = 31 * result + recruitmentState.hashCode()
            result = 31 * result + isFollowing.hashCode()
            result = 31 * result + spriteIdle.contentHashCode()
            result = 31 * result + spriteIdle2.contentHashCode()
            return result
        }
    }
}
