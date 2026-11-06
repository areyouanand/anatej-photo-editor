package com.anatej.photo.engine

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.coroutines.resume

/**
 * Bundled ML Kit selfie segmentation. Runs fully offline (the TFLite model ships in the
 * APK) and returns a soft alpha mask of the person.
 */
class SubjectSegmenter {

    private val client = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
    )

    suspend fun segment(source: Bitmap): Bitmap? = suspendCancellableCoroutine { continuation ->
        val longest = max(source.width, source.height)
        val scale = min(1f, 640f / longest)
        val input = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).roundToInt().coerceAtLeast(1),
                (source.height * scale).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            source
        }

        client.process(InputImage.fromBitmap(input, 0))
            .addOnSuccessListener { mask ->
                val w = mask.width
                val h = mask.height
                val floats = FloatArray(w * h)
                mask.buffer.rewind()
                mask.buffer.asFloatBuffer().get(floats)
                val pixels = IntArray(w * h) { index ->
                    val alpha = (floats[index].coerceIn(0f, 1f) * 255f).toInt()
                    (alpha shl 24) or 0x00FFFFFF
                }
                val bitmap = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
                if (continuation.isActive) {
                    continuation.resume(bitmap)
                } else {
                    bitmap.recycle()
                }
            }
            .addOnFailureListener {
                if (continuation.isActive) continuation.resume(null)
            }
    }

    fun close() {
        client.close()
    }
}
