package com.anatej.photo.model

/**
 * A standard 3D colour LUT. Data is stored in .cube order: red varies fastest,
 * then green, then blue.
 */
class Lut3D(
    val title: String,
    val size: Int,
    val data: FloatArray
) {
    init {
        require(size in 2..64) { "Unsupported LUT size $size" }
        require(data.size >= size * size * size * 3) {
            "LUT has ${data.size / 3} entries, expected ${size * size * size}"
        }
    }

    private fun channel(r: Int, g: Int, b: Int, c: Int): Float {
        val index = ((b * size + g) * size + r) * 3 + c
        return data[index]
    }

    /**
     * Trilinear LUT lookup. Writes linear RGB (0..1) into [out] at offsets 0,1,2.
     */
    fun sample(r: Float, g: Float, b: Float, out: FloatArray) {
        val maxIndex = size - 1
        val rf = r.coerceIn(0f, 1f) * maxIndex
        val gf = g.coerceIn(0f, 1f) * maxIndex
        val bf = b.coerceIn(0f, 1f) * maxIndex

        val r0 = rf.toInt().coerceAtMost(maxIndex - 1)
        val g0 = gf.toInt().coerceAtMost(maxIndex - 1)
        val b0 = bf.toInt().coerceAtMost(maxIndex - 1)
        val r1 = (r0 + 1).coerceAtMost(maxIndex)
        val g1 = (g0 + 1).coerceAtMost(maxIndex)
        val b1 = (b0 + 1).coerceAtMost(maxIndex)
        val fr = rf - r0
        val fg = gf - g0
        val fb = bf - b0

        for (c in 0..2) {
            val c000 = channel(r0, g0, b0, c)
            val c100 = channel(r1, g0, b0, c)
            val c010 = channel(r0, g1, b0, c)
            val c110 = channel(r1, g1, b0, c)
            val c001 = channel(r0, g0, b1, c)
            val c101 = channel(r1, g0, b1, c)
            val c011 = channel(r0, g1, b1, c)
            val c111 = channel(r1, g1, b1, c)

            val c00 = c000 + (c100 - c000) * fr
            val c10 = c010 + (c110 - c010) * fr
            val c01 = c001 + (c101 - c001) * fr
            val c11 = c011 + (c111 - c011) * fr
            val c0 = c00 + (c10 - c00) * fg
            val c1 = c01 + (c11 - c01) * fg
            out[c] = c0 + (c1 - c0) * fb
        }
    }
}
