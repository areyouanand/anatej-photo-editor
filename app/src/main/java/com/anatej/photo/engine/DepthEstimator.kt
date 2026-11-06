package com.anatej.photo.engine

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Bundled MiDaS-small (256x256, fp16 weights) monocular depth estimator.
 *
 * The model outputs relative inverse depth (near = bright). It is executed through the
 * TensorFlow Lite CPU interpreter, fully offline. We keep the raw inverse-depth field and
 * convert it to normals in [RelightMap] rather than shipping an image-sized bitmap.
 */
class DepthEstimator(context: Context) {

    data class DepthMap(val size: Int, val values: FloatArray)

    private val appContext = context.applicationContext
    private val mutex = Mutex()

    private val interpreterDelegate = lazy {
        runCatching {
            val model = loadModel(appContext)
            Interpreter(
                model,
                Interpreter.Options().apply { setNumThreads(4) }
            )
        }.getOrNull()
    }

    private val interpreter: Interpreter?
        get() = interpreterDelegate.value

    suspend fun estimate(bitmap: Bitmap): DepthMap? = withContext(Dispatchers.Default) {
        mutex.withLock {
            val engine = interpreter ?: run {
                Log.w(TAG, "MiDaS model unavailable; falling back to luminance normals")
                return@withLock null
            }
            val inputTensor = engine.getInputTensor(0)
            val outputTensor = engine.getOutputTensor(0)
            if (inputTensor.numElements() != INPUT_SIZE * INPUT_SIZE * 3) {
                return@withLock null
            }

            val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
            if (scaled !== bitmap) scaled.recycle()

            val input = ByteBuffer.allocateDirect(inputTensor.numElements() * 4)
                .order(ByteOrder.nativeOrder())
            for (pixel in pixels) {
                val r = ((pixel shr 16) and 0xFF) / 255f
                val g = ((pixel shr 8) and 0xFF) / 255f
                val b = (pixel and 0xFF) / 255f
                input.putFloat((r - MEAN_R) / STD_R)
                input.putFloat((g - MEAN_G) / STD_G)
                input.putFloat((b - MEAN_B) / STD_B)
            }
            input.rewind()

            val elements = outputTensor.numElements()
            val side = sqrt(elements.toFloat()).toInt()
            if (side <= 0 || side * side != elements) return@withLock null

            val started = SystemClock.elapsedRealtime()
            val output = ByteBuffer.allocateDirect(elements * 4)
                .order(ByteOrder.nativeOrder())
            engine.run(input, output)
            output.rewind()
            val values = FloatArray(elements)
            output.asFloatBuffer().get(values)

            Log.d(TAG, "depth ${side}x${side} in ${SystemClock.elapsedRealtime() - started} ms")
            DepthMap(side, values)
        }
    }

    fun close() {
        if (interpreterDelegate.isInitialized()) {
            runCatching { interpreterDelegate.value?.close() }
        }
    }

    private fun loadModel(context: Context): ByteBuffer {
        val name = MODEL_ASSET
        return runCatching {
            context.assets.openFd(name).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                    channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        descriptor.startOffset,
                        descriptor.declaredLength
                    )
                }
            }
        }.getOrElse {
            // Fallback for compressed assets or unusual packaging.
            val bytes = context.assets.open(name).use { it.readBytes() }
            ByteBuffer.allocateDirect(bytes.size)
                .order(ByteOrder.nativeOrder())
                .apply { put(bytes) }
                .also { it.rewind() }
        }
    }

    private companion object {
        const val TAG = "AnatejDepth"
        const val MODEL_ASSET = "midas_small_256_fp16.tflite"
        const val INPUT_SIZE = 256

        // ImageNet normalization used by the MiDaS small transform.
        const val MEAN_R = 0.485f
        const val MEAN_G = 0.456f
        const val MEAN_B = 0.406f
        const val STD_R = 0.229f
        const val STD_G = 0.224f
        const val STD_B = 0.225f
    }
}
