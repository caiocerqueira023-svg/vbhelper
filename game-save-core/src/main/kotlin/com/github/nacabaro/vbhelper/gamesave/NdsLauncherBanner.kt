package com.github.nacabaro.vbhelper.gamesave

import java.io.InputStream

/** Static 32x32 DS launcher artwork. Presentation metadata never grants save-format/write support. */
data class NdsLauncherBanner(val gameCode: String, val argb: IntArray) {
    companion object {
        /** Reads only a short header and banner, without retaining or modifying any ROM content. */
        fun read(input: InputStream): NdsLauncherBanner {
            val header = readExact(input, 0x70)
            val code = String(header, 0xC, 4, Charsets.US_ASCII)
            require(Regex("[A-Z0-9]{4}").matches(code)) { "Invalid NDS game code" }
            val offset = (0 until 4).fold(0L) { value, i ->
                value or ((header[0x68 + i].toLong() and 255) shl (i * 8))
            }
            header.fill(0)
            require(offset in 0x160L..(512L * 1024 * 1024)) { "Invalid NDS banner offset" }
            var remaining = offset - 0x70
            while (remaining > 0) {
                val skipped = input.skip(remaining)
                if (skipped > 0) remaining -= skipped else {
                    require(input.read() >= 0) { "Truncated NDS banner" }
                    remaining--
                }
            }
            val banner = readExact(input, 0x240)
            try {
                fun u16(at: Int) = (banner[at].toInt() and 255) or ((banner[at + 1].toInt() and 255) shl 8)
                require(u16(0) in setOf(1, 2, 3, 0x103)) { "Unsupported NDS banner version" }
                val palette = IntArray(16) { index ->
                    val color = u16(0x220 + index * 2)
                    fun channel(shift: Int) = ((color ushr shift) and 31).let { (it shl 3) or (it ushr 2) }
                    if (index == 0) 0 else 0xFF000000.toInt() or (channel(0) shl 16) or (channel(5) shl 8) or channel(10)
                }
                return NdsLauncherBanner(code, IntArray(1024) { pixel ->
                    val x = pixel % 32
                    val y = pixel / 32
                    val tile = (y / 8) * 4 + x / 8
                    val packed = banner[0x20 + tile * 32 + (y % 8) * 4 + (x % 8) / 2].toInt() and 255
                    palette[(packed ushr ((x % 2) * 4)) and 15]
                })
            } finally { banner.fill(0) }
        }

        private fun readExact(input: InputStream, count: Int): ByteArray {
            val bytes = ByteArray(count)
            var offset = 0
            while (offset < count) {
                val read = input.read(bytes, offset, count - offset)
                require(read >= 0) { "Truncated NDS metadata" }
                if (read == 0) {
                    val value = input.read()
                    require(value >= 0) { "Truncated NDS metadata" }
                    bytes[offset++] = value.toByte()
                } else offset += read
            }
            return bytes
        }
    }
}
