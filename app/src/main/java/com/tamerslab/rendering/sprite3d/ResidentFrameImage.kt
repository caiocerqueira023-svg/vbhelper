package com.tamerslab.rendering.sprite3d

/** A decoded sprite frame. [argb] uses Android ARGB packing, with alpha 0 as transparent. */
data class ResidentFrameImage(
    val argb: IntArray,
    val width: Int,
    val height: Int
)
