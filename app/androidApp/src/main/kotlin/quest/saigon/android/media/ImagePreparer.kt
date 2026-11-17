package quest.saigon.android.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import quest.saigon.Limits
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImagePreparer {
    suspend fun preparePostPhoto(context: Context, uri: Uri): ByteArray? =
        prepareBitmap(context, uri, maxEdge = Limits.PHOTO_MAX_EDGE, targetAspect = 9f / 16f)

    suspend fun prepareAvatarPhoto(context: Context, uri: Uri): ByteArray? =
        prepareBitmap(context, uri, maxEdge = Limits.AVATAR_MAX_EDGE, targetAspect = 1f)

    suspend fun prepareSquareOrOriginal(context: Context, uri: Uri): ByteArray? =
        prepareBitmap(context, uri, maxEdge = Limits.PHOTO_MAX_EDGE, targetAspect = null)

    private suspend fun prepareBitmap(
        context: Context,
        uri: Uri,
        maxEdge: Int,
        targetAspect: Float?,
    ): ByteArray? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return@withContext null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val orientation = resolver.openInputStream(uri)?.use { stream ->
            try {
                ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } catch (_: Throwable) {
                ExifInterface.ORIENTATION_NORMAL
            }
        } ?: ExifInterface.ORIENTATION_NORMAL

        val sampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxEdge * 2)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
            ?: return@withContext null

        val oriented = applyExifOrientation(raw, orientation)
        if (oriented !== raw) raw.recycle()

        val cropped = if (targetAspect != null) centerCropAspect(oriented, targetAspect) else oriented
        if (cropped !== oriented) oriented.recycle()

        val scaled = scaleDownToMaxEdge(cropped, maxEdge)
        if (scaled !== cropped) cropped.recycle()

        val quality = (Limits.JPEG_QUALITY * 100.0).roundToInt().coerceIn(50, 95)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        scaled.recycle()
        out.toByteArray()
    }

    private fun computeInSampleSize(width: Int, height: Int, reqMaxEdge: Int): Int {
        var inSampleSize = 1
        var longest = max(width, height)
        while (longest / 2 >= reqMaxEdge) {
            inSampleSize *= 2
            longest /= 2
        }
        return inSampleSize
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun centerCropAspect(bitmap: Bitmap, aspect: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val current = w.toFloat() / h.toFloat()
        if (kotlin.math.abs(current - aspect) < 0.01f) return bitmap
        val targetW: Int
        val targetH: Int
        if (current > aspect) {
            targetH = h
            targetW = (h * aspect).roundToInt().coerceIn(1, w)
        } else {
            targetW = w
            targetH = (w / aspect).roundToInt().coerceIn(1, h)
        }
        val x = ((w - targetW) / 2).coerceAtLeast(0)
        val y = ((h - targetH) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(bitmap, x, y, targetW, targetH)
    }

    private fun scaleDownToMaxEdge(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest.toFloat()
        val w = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, min(w, maxEdge), min(h, maxEdge), true)
    }
}
