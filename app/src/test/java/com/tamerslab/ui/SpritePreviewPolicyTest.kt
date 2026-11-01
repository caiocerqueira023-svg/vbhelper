package com.tamerslab.ui

import org.junit.Assert.*
import org.junit.Test

class SpritePreviewPolicyTest {
    @Test fun acceptsOrdinaryWatchFrames() { assertTrue(SpritePreviewPolicy.validFrame(32, 32, 2048)) }
    @Test fun acceptsTheBoundedMaximum() { assertTrue(SpritePreviewPolicy.validFrame(256, 256, 131072)) }
    @Test fun rejectsOversizedDimensionsBeforeAllocation() { assertFalse(SpritePreviewPolicy.validFrame(257, 32, 16448)) }
    @Test fun rejectsMissingAndShortPixelBuffers() {
        assertFalse(SpritePreviewPolicy.validFrame(32, 32, 0))
        assertFalse(SpritePreviewPolicy.validFrame(32, 32, 2047))
    }
    @Test fun rejectsTrailingDataInsteadOfHidingCorruptFrames() { assertFalse(SpritePreviewPolicy.validFrame(32, 32, 2049)) }
    @Test fun rejectsZeroNegativeAndOverflowingDimensions() {
        assertFalse(SpritePreviewPolicy.validFrame(0, 32, 0))
        assertFalse(SpritePreviewPolicy.validFrame(-1, 32, 64))
        assertFalse(SpritePreviewPolicy.validFrame(Int.MAX_VALUE, Int.MAX_VALUE, 2))
    }
}
