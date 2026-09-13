package com.github.nacabaro.vbhelper.domain.device_data

import com.github.cfogrady.vbnfc.data.NfcCharacter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Watch dates have months 1..12 and are stored at UTC midnight in the app. */
object NfcEvolutionHistory {
    fun dateToEpochMillis(transformation: NfcCharacter.Transformation): Long? = runCatching {
        LocalDate.of(transformation.year.toInt(), transformation.month.toInt(), transformation.day.toInt())
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrNull()

    fun transformation(monIndex: Int, epochMillis: Long): NfcCharacter.Transformation {
        val date = Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC).toLocalDate()
        return NfcCharacter.Transformation(
            monIndex.toUByte(), date.year.toUShort(), date.monthValue.toUByte(), date.dayOfMonth.toUByte()
        )
    }

    fun pad(history: List<NfcCharacter.Transformation>, capacity: Int): Array<NfcCharacter.Transformation> {
        require(history.size <= capacity) { "Evolution history exceeds the watch's $capacity slots" }
        return Array(capacity) { index ->
            history.getOrNull(index) ?: NfcCharacter.Transformation(255u, 65535u, 255u, 255u)
        }
    }
}
