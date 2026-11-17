package com.tamerslab.hardware.vpet

/**
 * Internal per-model behaviour table mirroring `TIPOS` in the recovered lector.js.
 *
 * @property color true for the 16-byte Color protocol (DMC/PenC).
 * @property packetCount number of `r:XXXX` packets needed for a 16-bit read (0 for Color).
 * @property onlyLowNibbleE whether packets whose low nibble is not 0xE are filtered out (DMX/PenZ).
 * @property defaultVersion version used when the caller has no stored version (lector.js
 *   `verGuardada`: Pendulum models default to 6, the others to 0).
 * @property versionValidator lector.js `verOk`: versions the device may report before the
 *   session accepts a version-learning re-request (null = accept any version).
 * @property resendColor whether the Color command is resent every 2.5 s while waiting (DMC;
 *   PenC sends it once and the adapter repeats it).
 * @property colorHeader 8-hex header of the model's Color data block (null for 16-bit models).
 */
internal class VpetModelSpec private constructor(
    val model: VpetModel,
    val color: Boolean,
    val packetCount: Int,
    val onlyLowNibbleE: Boolean,
    val defaultVersion: Int,
    val versionValidator: ((Int) -> Boolean)?,
    val resendColor: Boolean,
    val colorHeader: String?,
) {
    companion object {
        fun of(model: VpetModel): VpetModelSpec = when (model) {
            VpetModel.DM20 -> VpetModelSpec(
                model = model,
                color = false,
                packetCount = 10,
                onlyLowNibbleE = false,
                defaultVersion = 0,
                versionValidator = null,
                resendColor = false,
                colorHeader = null,
            )

            VpetModel.DMX -> VpetModelSpec(
                model = model,
                color = false,
                packetCount = 6,
                onlyLowNibbleE = true,
                defaultVersion = 0,
                versionValidator = null,
                resendColor = false,
                colorHeader = null,
            )

            VpetModel.PEN20 -> VpetModelSpec(
                model = model,
                color = false,
                packetCount = 10,
                onlyLowNibbleE = false,
                defaultVersion = 6,
                versionValidator = { it in VpetProtocol.PEN20_VERSIONS },
                resendColor = false,
                colorHeader = null,
            )

            VpetModel.PENZ -> VpetModelSpec(
                model = model,
                color = false,
                packetCount = 6,
                onlyLowNibbleE = true,
                defaultVersion = 6,
                versionValidator = { it in VpetProtocol.PENZ_VERSIONS },
                resendColor = false,
                colorHeader = null,
            )

            VpetModel.DMC -> VpetModelSpec(
                model = model,
                color = true,
                packetCount = 0,
                onlyLowNibbleE = false,
                defaultVersion = 0,
                versionValidator = null,
                resendColor = true,
                colorHeader = VpetProtocol.DMC_HEADER,
            )

            VpetModel.PENC -> VpetModelSpec(
                model = model,
                color = true,
                packetCount = 0,
                onlyLowNibbleE = false,
                defaultVersion = 0,
                versionValidator = null,
                resendColor = false,
                colorHeader = VpetProtocol.PENC_HEADER,
            )
        }
    }
}
