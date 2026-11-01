package com.tamerslab.ui

enum class RapportCondition { LIMITED, DEVELOPING, ESTABLISHED }
enum class MoodCondition { LOW, SETTLED, POSITIVE }
enum class CarePriority { REST, FEED, SOCIALIZE, PLAY, NONE }

/** Presentation of current readings. These labels do not change social state or farm decisions. */
object PartnerCondition {
    fun rapport(value: Int): RapportCondition = when {
        value > 75 -> RapportCondition.ESTABLISHED
        value < 25 -> RapportCondition.LIMITED
        else -> RapportCondition.DEVELOPING
    }

    fun mood(value: Int): MoodCondition = when {
        value < 25 -> MoodCondition.LOW
        value >= 75 -> MoodCondition.POSITIVE
        else -> MoodCondition.SETTLED
    }

    fun care(energy: Int, satiety: Int, social: Int, funLevel: Int): CarePriority = when {
        energy < 20 -> CarePriority.REST
        satiety < 20 -> CarePriority.FEED
        social < 25 -> CarePriority.SOCIALIZE
        funLevel < 25 -> CarePriority.PLAY
        else -> CarePriority.NONE
    }
}
