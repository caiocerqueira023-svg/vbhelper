package com.tamerslab.hardware.vpet

/**
 * One decoded VPet read.
 *
 * Field semantics follow the recovered `lector.js` decoders:
 * - [version] is the device version carried by the response (`d.version` in lector.js).
 * - [index] is the species number used by the recovered tables (`d.indice`).
 * - [name], [stage] and [attribute] come from the recovered table entry (`null` when the
 *   table has no entry for the index or when the VPet hides the number).
 * - [power] is the device-reported power value (`d.poder`), when the protocol carries it.
 * - [payloadHex] is the raw decoded payload exactly as lector.js prints it: space-separated
 *   4-digit hex words for 16-bit models, 8 space-separated words for Color blocks.
 * - [checksumValid] mirrors the lector.js `valido` flag. DMX and DMC carry no checksum in the
 *   protocol; for those models the flag reports whether the payload was complete
 *   (6 packets / one full block).
 */
data class VpetReadResult(
    val model: VpetModel,
    val version: Int,
    val index: Int,
    val name: String?,
    val stage: Int?,
    val attribute: Int?,
    val power: Int?,
    val payloadHex: String,
    val checksumValid: Boolean,
)

/** Reasons a [VpetReadSession] can end without a result. */
sealed class VpetReadFailure {
    /** The adapter did not deliver a decodable read after [attempts] requests (max. 8). */
    data class NoResponse(val attempts: Int) : VpetReadFailure()

    /** A payload arrived but could not be decoded/produced a result (defensive; lector.js retries). */
    data class InvalidData(val reason: String) : VpetReadFailure()

    /** The model key is not one of the six supported Virtual Pets. */
    data class UnsupportedModel(val wireKey: String) : VpetReadFailure()
}
