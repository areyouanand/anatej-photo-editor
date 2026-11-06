package com.anatej.photo.engine

import com.anatej.photo.model.Adjustments
import com.anatej.photo.model.CropRect
import com.anatej.photo.model.LookSeries
import com.anatej.photo.model.Looks
import com.anatej.photo.model.SquareBgStyle
import com.anatej.photo.ui.editor.straightenZoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the newer editor surface: crop/straighten geometry, the four extra tone
 * sliders, the split 3D LUT series, and the Square Pic background styles.
 */
class EditorModelsTest {

    @Test
    fun fullCropIsNotCroppedAndStraightenAloneCountsAsEdited() {
        assertFalse(CropRect.Full.isCropped)
        assertFalse(CropRect.Full.copy(straighten = 0.01f).isCropped)
        assertTrue(CropRect.Full.copy(straighten = 1.5f).isCropped)
        assertTrue(CropRect.Full.copy(left = 0.05f).isCropped)
    }

    @Test
    fun straightenZoomGrowsWithAngleAndIsIdentityAtZero() {
        assertEquals(1f, straightenZoom(0f, 1920f, 1080f), 0.0001f)
        val small = straightenZoom(3f, 1920f, 1080f)
        val large = straightenZoom(20f, 1920f, 1080f)
        assertTrue(small > 1f)
        assertTrue(large > small)
        // The inscribed fit is transposition-symmetric: a rotated landscape and a
        // rotated portrait frame need the same zoom factor.
        assertEquals(
            straightenZoom(10f, 1080f, 1920f),
            straightenZoom(10f, 1920f, 1080f),
            0.0001f
        )
        // Never exceeds the documented ceiling.
        assertTrue(straightenZoom(45f, 4000f, 3000f) <= 2.6f)
    }

    @Test
    fun toneSlidersAreNeutralOnlyWhenAllZeroAndMoveThePreviewMatrix() {
        assertTrue(Adjustments.Neutral.isNeutral)
        assertFalse(Adjustments(shadows = 0.4f).isNeutral)
        assertFalse(Adjustments(highlights = -0.2f).isNeutral)
        assertFalse(Adjustments(whites = 0.2f).isNeutral)
        assertFalse(Adjustments(blacks = -0.2f).isNeutral)

        val lifted = PhotoRenderer.previewMatrix(
            Adjustments(shadows = 0.6f),
            Looks.Original,
            1f
        )
        val deepen = PhotoRenderer.previewMatrix(
            Adjustments(shadows = -0.6f),
            Looks.Original,
            1f
        )
        // Shadow lift raises the channel offsets, shadow crush lowers them.
        assertTrue(lifted[4] > 0f)
        assertTrue(deepen[4] < 0f)
    }

    @Test
    fun lutSeriesIsItsOwnSectionSeparateFromLookStocks() {
        val lutStocks = Looks.forSeries(LookSeries.Lut)
        assertTrue("3D LUT must not contain baked look presets", lutStocks.isEmpty())
        assertTrue("Favourites is a UI-only bucket", Looks.forSeries(LookSeries.Favourites).isEmpty())
        // Every real stock series still has content and no look files itself under Lut.
        LookSeries.entries
            .filter { it != LookSeries.Lut && it != LookSeries.Favourites }
            .forEach { series ->
                assertTrue("$series should have looks", Looks.forSeries(series).isNotEmpty())
            }
        assertTrue(Looks.all.none { it.series == LookSeries.Lut })
    }

    @Test
    fun favouriteLookIdsResolveBackToLooks() {
        val ids = listOf("cine.2383", "kodak.portra400", "core.original")
        for (id in ids) {
            assertTrue("$id must resolve from Looks.byId", Looks.byId.containsKey(id))
        }
    }

    @Test
    fun squareBackgroundStylesExposeStableLabels() {
        assertEquals("BLUR", SquareBgStyle.Blur.label)
        assertEquals("BLACK", SquareBgStyle.Black.label)
        assertEquals("WHITE", SquareBgStyle.White.label)
    }

    @Test
    fun parserAccepts65CubeLuts() {
        val size = 65
        val text = buildString {
            appendLine("TITLE \"Big 65\"")
            appendLine("LUT_3D_SIZE $size")
            for (i in 0 until size * size * size) {
                appendLine("0.25 0.5 0.75")
            }
        }
        val lut = CubeLutParser.parse(text)
        assertEquals(65, lut.size)
        assertEquals("Big 65", lut.title)
        val out = FloatArray(3)
        lut.sample(0.4f, 0.6f, 0.8f, out)
        assertEquals(0.25f, out[0], 0.0001f)
        assertEquals(0.5f, out[1], 0.0001f)
        assertEquals(0.75f, out[2], 0.0001f)
    }
}
