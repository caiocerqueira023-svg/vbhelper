package com.tamerslab.hardware.vpet

/**
 * Virtual Pets supported by the reader, in the same order and with the same wire keys as the
 * recovered `lector.js` (`TIPOS`/`LISTAS`).
 *
 * [colorProtocol] is true for the Digital Monster Color / Pendulum Color, which answer with
 * 16-byte blocks (`C1-`/`C2-`) instead of 16-bit `r:XXXX` packets.
 */
enum class VpetModel(
    val wireKey: String,
    val displayName: String,
    val colorProtocol: Boolean,
) {
    DM20("dm20", "Digital Monster Ver.20th", false),
    DMX("dmx", "Digimon X", false),
    PEN20("pen20", "Pendulum Ver.20th", false),
    PENZ("penz", "Pendulum Z", false),
    DMC("dmc", "Digital Monster Color", true),
    PENC("penc", "Pendulum Color", true);

    companion object {
        /** Resolves a wire key (`dm20`, `dmx`, `pen20`, `penz`, `dmc`, `penc`); null when unknown. */
        fun fromKey(key: String): VpetModel? {
            val normalized = key.trim().lowercase()
            return entries.firstOrNull { it.wireKey == normalized }
        }
    }
}
