package com.anatej.photo.engine

import com.anatej.photo.model.Lut3D

/**
 * Minimal, strict .cube parser for 3D LUTs (Adobe/Resolve/IRIDAS format).
 * Supports TITLE, LUT_3D_SIZE and RGB triples. 1D LUTs are intentionally rejected.
 *
 * The numeric rows are parsed straight into a preallocated float array (no boxing and no
 * per-line token lists) because a 33³ LUT is ~108k values and users may have dozens of
 * them: the naive version took hundreds of ms per file on device.
 */
object CubeLutParser {

    fun parse(text: String, fallbackTitle: String = "Imported LUT"): Lut3D {
        var size = 0
        var title = ""
        var saw1D = false
        var data: FloatArray? = null
        var count = 0
        val triple = FloatArray(3)

        for (raw in text.lineSequence()) {
            var line = raw
            val hash = line.indexOf('#')
            if (hash >= 0) line = line.substring(0, hash)
            line = line.trim()
            if (line.isEmpty()) continue

            val first = line[0]
            if (first.isLetter()) {
                when {
                    line.startsWith("TITLE", ignoreCase = true) -> {
                        title = line.substring(5).trim().trim('"')
                    }

                    line.startsWith("LUT_3D_SIZE", ignoreCase = true) -> {
                        size = line.substring(11).trim().toIntOrNull()
                            ?: throw IllegalArgumentException("Invalid LUT_3D_SIZE")
                        if (size < 2) throw IllegalArgumentException("Invalid LUT_3D_SIZE")
                        data = FloatArray(size * size * size * 3)
                        count = 0
                    }

                    line.startsWith("LUT_1D_SIZE", ignoreCase = true) -> saw1D = true
                    line.startsWith("DOMAIN_MIN", ignoreCase = true) -> Unit
                    line.startsWith("DOMAIN_MAX", ignoreCase = true) -> Unit
                    else -> Unit
                }
                continue
            }

            val target = data
                ?: throw IllegalArgumentException("Missing LUT_3D_SIZE in .cube file.")
            if (count * 3 + 3 > target.size) {
                // Extra rows beyond the declared size are ignored, matching Resolve.
                continue
            }
            if (parseTriple(line, triple)) {
                target[count * 3] = triple[0]
                target[count * 3 + 1] = triple[1]
                target[count * 3 + 2] = triple[2]
                count++
            }
        }

        if (saw1D && size == 0) {
            throw IllegalArgumentException("1D LUTs are not supported. Use a 3D .cube file.")
        }
        if (size == 0 || data == null) {
            throw IllegalArgumentException("Missing LUT_3D_SIZE in .cube file.")
        }
        val expected = size * size * size
        if (count < expected) {
            throw IllegalArgumentException(
                "LUT is incomplete: found $count of $expected entries."
            )
        }
        return Lut3D(title.ifBlank { fallbackTitle }, size, data)
    }

    /** Parses exactly three whitespace-separated floats; returns false if the row is not one. */
    private fun parseTriple(line: String, out: FloatArray): Boolean {
        var index = 0
        val length = line.length
        for (i in 0..2) {
            while (index < length && line[index].isWhitespace()) index++
            if (index >= length) return false
            val start = index
            if (line[index] == '-' || line[index] == '+') index++
            while (index < length) {
                val c = line[index]
                if (c.isDigit() || c == '.' || c == 'e' || c == 'E' ||
                    ((c == '-' || c == '+') && (line[index - 1] == 'e' || line[index - 1] == 'E'))
                ) {
                    index++
                } else {
                    break
                }
            }
            if (index == start) return false
            out[i] = line.substring(start, index).toFloatOrNull() ?: return false
        }
        return true
    }
}
