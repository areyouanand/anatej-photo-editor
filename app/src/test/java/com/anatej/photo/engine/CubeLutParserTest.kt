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
}
