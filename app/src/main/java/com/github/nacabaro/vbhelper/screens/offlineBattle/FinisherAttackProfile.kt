package com.github.nacabaro.vbhelper.screens.offlineBattle

import java.util.Locale

enum class FinisherAttackStyle { PROJECTILE, BEAM, MELEE, BARRAGE }

/** Render metadata only: the imported species profile remains the source of the move name. */
data class FinisherAttackProfile(
    val style: FinisherAttackStyle = FinisherAttackStyle.PROJECTILE,
    val colorArgb: Int,
    val emitterHeight: Float = 0.7f,
)

private val neutralFinisherProfile = FinisherAttackProfile(colorArgb = 0xFFE0E0E0.toInt())
private val terraForceProfile = FinisherAttackProfile(colorArgb = 0xFFFFB74D.toInt())
private val greySwordProfile = FinisherAttackProfile(FinisherAttackStyle.MELEE, 0xFFFFE0B2.toInt())
private val garuruCannonProfile = FinisherAttackProfile(FinisherAttackStyle.BEAM, 0xFF90CAF9.toInt())
private val stingStrikeProfile = FinisherAttackProfile(FinisherAttackStyle.MELEE, 0xFF80CBC4.toInt())
private val desperadoBlasterProfile = FinisherAttackProfile(FinisherAttackStyle.BARRAGE, 0xFFFF8A65.toInt())

/** Exact normalized move aliases; unknown moves never infer a style from a species or keyword. */
fun resolveFinisherAttackProfile(specialName: String?): FinisherAttackProfile =
    when (specialName.orEmpty().lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }) {
        "terraforce", "gaiaforce" -> terraForceProfile
        "greysword", "transcendentsword" -> greySwordProfile
        "garurucannon", "supremecannon" -> garuruCannonProfile
        "stingstrike", "spikingstrike" -> stingStrikeProfile
        "desperadoblaster", "deathparadeblaster" -> desperadoBlasterProfile
        else -> neutralFinisherProfile
    }
