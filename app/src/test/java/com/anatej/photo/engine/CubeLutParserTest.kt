package com.anatej.photo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class CubeLutParserTest {

    @Test
    fun parsesIdentityLutAndSamplesEndpoints() {
        val text = buildString {
            appendLine("TITLE \"Identity 2\"")
            appendLine("LUT_3D_SIZE 2")
            appendLine("DOMAIN_MIN 0 0 0")
            appendLine("DOMAIN_MAX 1 1 1")
            for (b in 0..1) {
                for (g in 0..1) {
                    for (r in 0..1) {
                        appendLine("$r $g $b")
                    }
                }
            }
        }

        val lut = CubeLutParser.parse(text)
        assertEquals("Identity 2", lut.title)
        assertEquals(2, lut.size)

        val out = FloatArray(3)
        lut.sample(0f, 0f, 0f, out)
        assertEquals(0f, out[0], 0.0001f)
        assertEquals(0f, out[1], 0.0001f)
        assertEquals(0f, out[2], 0.0001f)

        lut.sample(1f, 1f, 1f, out)
        assertEquals(1f, out[0], 0.0001f)
        assertEquals(1f, out[1], 0.0001f)
        assertEquals(1f, out[2], 0.0001f)
    }

    @Test
    fun interpolatesBetweenEntries() {
        // Output red = input red, green = 0.5, blue = 0 for every entry.
        val text = buildString {
            appendLine("LUT_3D_SIZE 2")
            for (b in 0..1) {
                for (g in 0..1) {
                    for (r in 0..1) {
                        appendLine("$r 0.5 0.0")
                    }
                }
            }
        }
        val lut = CubeLutParser.parse(text, "fallback")
        val out = FloatArray(3)
        lut.sample(0.5f, 0.25f, 0.75f, out)
        assertEquals(0.5f, out[0], 0.0001f)
        assertEquals(0.5f, out[1], 0.0001f)
        assertEquals(0f, out[2], 0.0001f)
    }

    @Test
    fun rejectsOneDimensionalLut() {
        try {
            CubeLutParser.parse("LUT_1D_SIZE 16\n0 0 0\n1 1 1\n")
            fail("Expected 1D LUT rejection")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun rejectsIncompleteLut() {
        try {
            CubeLutParser.parse("LUT_3D_SIZE 2\n0 0 0\n")
            fail("Expected incomplete LUT rejection")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun looksIncludeCinemaAndKodakMultiEffectStocks() {
        val cinema = com.anatej.photo.model.Looks.forSeries(com.anatej.photo.model.LookSeries.Cinema)
        val kodak = com.anatej.photo.model.Looks.forSeries(com.anatej.photo.model.LookSeries.Kodak)
        org.junit.Assert.assertTrue(cinema.size >= 6)
        org.junit.Assert.assertTrue(kodak.size >= 4)
        val night800t = com.anatej.photo.model.Looks.byId.getValue("cine.800t")
        org.junit.Assert.assertTrue(night800t.halation > 0.3f)
        org.junit.Assert.assertTrue(night800t.grain > 0.1f)
        org.junit.Assert.assertTrue(night800t.bloom > 0.1f)
        org.junit.Assert.assertTrue(night800t.shadowTint < -0.1f)
    }

    @Test
    fun cropRectTracksCroppedStateAndPreviewMatrixIncludesSplitTone() {
        val full = com.anatej.photo.model.CropRect.Full
        org.junit.Assert.assertFalse(full.isCropped)
        val cropped = full.copy(left = 0.1f, right = 0.9f)
        org.junit.Assert.assertTrue(cropped.isCropped)
        org.junit.Assert.assertEquals(0.8f, cropped.width, 0.0001f)

        val matrix = PhotoRenderer.previewMatrix(
            com.anatej.photo.model.Adjustments(shadowTint = -0.5f, highlightTint = 0.5f),
            com.anatej.photo.model.Looks.Original,
            1f
        )
        // Teal shadow tint shifts red offset negatively and blue offset positively
        org.junit.Assert.assertTrue(matrix[4] < 0f)
        org.junit.Assert.assertTrue(matrix[14] > 0f)
    }
}
