package com.anatej.photo.engine

import com.anatej.photo.model.Lut3D

/**
 * Minimal, strict .cube parser for 3D LUTs (Adobe/Resolve/IRIDAS format).
 * Supports TITLE, LUT_3D_SIZE and RGB triples. 1D LUTs are intentionally rejected.
 */
object CubeLutParser {

    fun parse(text: String, fallbackTitle: String = "Imported LUT"): Lut3D {
        var size = 0
        var title = ""
        val values = ArrayList<Float>(33 * 33 * 33 * 3)
        var saw1D = false

        for (raw in text.lineSequence()) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val parts = line.split(Regex("\\s+"))
            when {
                parts[0].equals("TITLE", ignoreCase = true) -> {
                    title = line.substringAfter("TITLE").trim().trim('"')
                }

                parts[0].equals("LUT_3D_SIZE", ignoreCase = true) -> {
                    size = parts.getOrNull(1)?.toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid LUT_3D_SIZE")
                }

                parts[0].equals("LUT_1D_SIZE", ignoreCase = true) -> {
                    saw1D = true
                }

                parts[0].equals("DOMAIN_MIN", ignoreCase = true) ||
                    parts[0].equals("DOMAIN_MAX", ignoreCase = true) -> Unit

                parts.size >= 3 -> {
                    val r = parts[0].toFloatOrNull()
                    val g = parts[1].toFloatOrNull()
                    val b = parts[2].toFloatOrNull()
                    if (r != null && g != null && b != null) {
                        values.add(r)
                        values.add(g)
                        values.add(b)
                    }
                }
            }
        }

        if (saw1D && size == 0) {
            throw IllegalArgumentException("1D LUTs are not supported. Use a 3D .cube file.")
        }
        if (size == 0) {
            throw IllegalArgumentException("Missing LUT_3D_SIZE in .cube file.")
        }
        val expected = size * size * size * 3
        if (values.size < expected) {
            throw IllegalArgumentException(
                "LUT is incomplete: found ${values.size / 3} of ${expected / 3} entries."
            )
        }

        val data = FloatArray(expected)
        for (i in 0 until expected) data[i] = values[i]
        return Lut3D(title.ifBlank { fallbackTitle }, size, data)
    }
}
