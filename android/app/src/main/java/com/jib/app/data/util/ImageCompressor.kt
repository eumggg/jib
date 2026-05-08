package com.jib.app.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decodes a content URI, scales the longest edge down to MAX_DIMENSION, and re-encodes
 * as JPEG below MAX_BYTES. Quality is dropped iteratively if the first encode is too large.
 */
@Singleton
class ImageCompressor @Inject constructor() {

    suspend fun compress(context: Context, uri: Uri): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Pass 1: read bounds only so we can compute an inSampleSize that
                // keeps memory usage modest even for very large images.
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, boundsOpts)
                } ?: error("Could not open image stream")

                val sampleSize = computeInSampleSize(
                    boundsOpts.outWidth,
                    boundsOpts.outHeight,
                    MAX_DIMENSION,
                )

                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val raw = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOpts)
                } ?: error("Decode failed")

                val scaled = scaleToMaxDimension(raw, MAX_DIMENSION)
                if (scaled !== raw) raw.recycle()

                val bytes = encodeUnderSize(scaled, MAX_BYTES)
                scaled.recycle()
                bytes
            }
        }

    private fun computeInSampleSize(width: Int, height: Int, max: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= max || h / 2 >= max) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleToMaxDimension(bitmap: Bitmap, max: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= max && h <= max) return bitmap
        val ratio = max.toFloat() / maxOf(w, h)
        val newW = (w * ratio).toInt().coerceAtLeast(1)
        val newH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun encodeUnderSize(bitmap: Bitmap, maxBytes: Int): ByteArray {
        var quality = 90
        while (true) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val bytes = out.toByteArray()
            if (bytes.size <= maxBytes || quality <= 40) return bytes
            quality -= 10
        }
    }

    companion object {
        const val MAX_DIMENSION = 1024
        const val MAX_BYTES = 500 * 1024 // 500 KB
    }
}
