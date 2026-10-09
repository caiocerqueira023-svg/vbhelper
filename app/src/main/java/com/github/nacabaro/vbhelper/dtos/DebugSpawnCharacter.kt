package com.github.nacabaro.vbhelper.dtos

/** Loaded card identity, including characters excluded from automatic World spawning. */
data class DebugSpawnCharacter(
    val id: Long,
    val charaIndex: Int,
    val cardName: String,
    val speciesName: String?,
    val spriteIdle: ByteArray,
    val spriteWidth: Int,
    val spriteHeight: Int
)
