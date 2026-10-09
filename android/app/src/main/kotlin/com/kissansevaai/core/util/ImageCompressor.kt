package com.kissansevaai.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max

/**
 * Downscales and compresses images before network upload.
 * Targets rural bandwidth constraints:
 * - Max long edge: 1280px
 * - Compression: JPEG quality ~80%
 * - Preserves EXIF orientation rotation
 */
object ImageCompressor {
    const val MAX_LONG_EDGE = 1280
    const val JPEG_QUALITY = 80

    /**
     * Compresses the image referenced by the URI to a JPEG byte array.
     */
    fun compressImage(context: Context, uri: Uri): ByteArray {
        val orientation = getExifOrientation(context, uri)

        // 1. Read bounds to determine sampling
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        val origWidth = boundsOptions.outWidth
        val origHeight = boundsOptions.outHeight
        if (origWidth <= 0 || origHeight <= 0) {
            // Fallback: read raw bytes
            return context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        }

        // 2. Compute sample size
        val sampleSize = calculateInSampleSize(origWidth, origHeight, MAX_LONG_EDGE)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decodedBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IllegalStateException("Failed to decode image from uri: $uri")

        return try {
            compressBitmap(decodedBitmap, orientation)
        } finally {
            decodedBitmap.recycle()
        }
    }

    /**
     * Compresses a raw [Bitmap], applying any required orientation rotation and downscaling.
     */
    fun compressBitmap(
        bitmap: Bitmap,
        orientation: Int = ExifInterface.ORIENTATION_NORMAL,
        maxLongEdge: Int = MAX_LONG_EDGE,
        quality: Int = JPEG_QUALITY
    ): ByteArray {
        val currentLongEdge = max(bitmap.width, bitmap.height)
        val scale = if (currentLongEdge > maxLongEdge) {
            maxLongEdge.toFloat() / currentLongEdge.toFloat()
        } else {
            1.0f
        }

        val matrix = Matrix()
        if (scale < 1.0f) {
            matrix.postScale(scale, scale)
        }

        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }

        val finalBitmap = if (!matrix.isIdentity) {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }

        val baos = ByteArrayOutputStream()
        try {
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            return baos.toByteArray()
        } finally {
            if (finalBitmap != bitmap) {
                finalBitmap.recycle()
            }
        }
    }

    /**
     * Compresses and writes to a temporary cache file.
     */
    fun compressToTempFile(context: Context, uri: Uri, prefix: String = "compressed_"): File {
        val bytes = compressImage(context, uri)
        val file = File(context.cacheDir, "${prefix}${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { it.write(bytes) }
        return file
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var inSampleSize = 1
        val longEdge = max(width, height)
        while (longEdge / (inSampleSize * 2) >= maxEdge) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun getExifOrientation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }
}
