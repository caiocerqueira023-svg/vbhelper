package com.tamerslab.transfer.vpet

import com.tamerslab.domain.vpet.VpetReadSnapshot
import com.tamerslab.hardware.vpet.VpetReadResult

/**
 * Maps a protocol-level [VpetReadResult] from the `:hardware-vpet` module to the app-level
 * [VpetReadSnapshot] accepted by [com.tamerslab.domain.vpet.VpetRepository.import].
 *
 * [readAtMillis] is explicit so one read session keeps a single timestamp for its result card
 * and the imported partner; callers that have no session clock may omit it.
 */
fun VpetReadResult.toVpetReadSnapshot(readAtMillis: Long = System.currentTimeMillis()): VpetReadSnapshot =
    VpetReadSnapshot(
        modelKey = model.wireKey,
        modelLabel = model.displayName,
        version = version,
        index = index,
        name = name,
        stage = stage,
        attribute = attribute,
        power = power,
        payloadHex = payloadHex,
        valid = checksumValid,
        readAtMillis = readAtMillis,
    )
