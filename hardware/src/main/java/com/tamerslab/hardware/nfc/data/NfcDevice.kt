package com.tamerslab.hardware.nfc.data

import java.util.BitSet

open class NfcDevice(private val registeredDims: BitSet) {
    fun isDimRegistered(dimId: UShort): Boolean {
        return registeredDims[dimId.toInt()]
    }
}