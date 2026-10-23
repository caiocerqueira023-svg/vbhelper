package com.github.nacabaro.vbhelper.gamesave

enum class RefreshChoice { KEEP_APP, USE_SOURCE }
data class RefreshChoices(val name: RefreshChoice?, val personality: RefreshChoice?)
data class RefreshAppValues(val nickname: String?, val personality: String, val personalitySystemVersion: Int, val personalityUpdatedAt: Long)
data class RefreshFieldReview(
    val nameConflict: Boolean,
    val personalityConflict: Boolean,
    val nameRecommendation: RefreshChoice?,
    val personalityRecommendation: RefreshChoice?,
) {
    fun recommendedChoices() = RefreshChoices(nameRecommendation, personalityRecommendation)
}

/** Three-way review. Native stored names are not app nicknames unless explicitly chosen. */
object NativeRefreshPlanner {
    fun fields(oldName: String, incomingName: String, oldPersonality: String, incomingPersonality: String,
        app: RefreshAppValues): RefreshFieldReview {
        val nameConflict = app.nickname != null && oldName != incomingName && app.nickname != incomingName
        val sourcePersonalityChanged = oldPersonality != incomingPersonality
        val appPersonalityChanged = app.personality != oldPersonality
        val personalityConflict = sourcePersonalityChanged && appPersonalityChanged && app.personality != incomingPersonality
        return RefreshFieldReview(nameConflict, personalityConflict,
            if (nameConflict) null else RefreshChoice.KEEP_APP,
            when { personalityConflict -> null
                sourcePersonalityChanged && !appPersonalityChanged -> RefreshChoice.USE_SOURCE
                else -> RefreshChoice.KEEP_APP })
    }

    fun resolve(review: RefreshFieldReview, choices: RefreshChoices, incomingName: String, incomingPersonality: String,
        app: RefreshAppValues, now: Long): RefreshAppValues {
        val nameChoice = choices.name ?: review.nameRecommendation
        val personalityChoice = choices.personality ?: review.personalityRecommendation
        require(nameChoice != null && personalityChoice != null) { "Every conflicting field requires a resolved choice" }
        val name = if (nameChoice == RefreshChoice.USE_SOURCE) incomingName.takeIf { it.isNotEmpty() } else app.nickname
        val personality = if (personalityChoice == RefreshChoice.USE_SOURCE) incomingPersonality else app.personality
        return app.copy(nickname = name, personality = personality,
            personalityUpdatedAt = if (personality != app.personality) now else app.personalityUpdatedAt)
    }
}
