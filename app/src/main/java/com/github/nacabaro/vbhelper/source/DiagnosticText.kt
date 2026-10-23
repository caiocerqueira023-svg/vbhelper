package com.github.nacabaro.vbhelper.source

object DiagnosticText {
    private val bearer = Regex("(?i)bearer\\s+[a-z0-9._~+/=-]+")
    private val credential = Regex("(?i)(api[_ -]?key|authorization|access[_ -]?token|token|password)([\"']?\\s*[:=]\\s*[\"']?)[^\\s,;&\"'}]+")

    fun sanitize(details: String): String = credential.replace(
        bearer.replace(details.take(16_384), "Bearer [redacted]")
    ) { "${it.groupValues[1]}${it.groupValues[2]}[redacted]" }.take(4_000)

    fun feedback(summary: String, details: String?, debugEnabled: Boolean, detailsLabel: String): String =
        if (debugEnabled && !details.isNullOrBlank()) "$summary\n$detailsLabel: ${sanitize(details)}" else summary
}
