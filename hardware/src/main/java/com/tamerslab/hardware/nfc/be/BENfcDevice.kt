package com.tamerslab.hardware.nfc.be

import com.tamerslab.hardware.nfc.data.NfcDevice
import java.util.BitSet

class BENfcDevice(
    val gender: Gender,
    val registedDims: ByteArray,
    val currDays: Byte,


    ):
    NfcDevice(
        BitSet(),
    ) {
    enum class Gender {
        Male,
        Female
    }
}