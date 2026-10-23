package com.github.nacabaro.vbhelper.ui

/** A page is bounded before blobs are read; each decoded frame has a separate byte budget. */
object SpritePreviewPolicy {
    const val PAGE_SIZE = 4
    const val MAX_DIMENSION = 256
    const val MAX_FRAME_BYTES = MAX_DIMENSION * MAX_DIMENSION * 2
    const val CACHE_BYTES = 4 * 1024 * 1024

    fun validFrame(width: Int, height: Int, bytes: Int): Boolean =
        width in 1..MAX_DIMENSION && height in 1..MAX_DIMENSION &&
            bytes.toLong() == width.toLong() * height * 2 && bytes <= MAX_FRAME_BYTES
}
