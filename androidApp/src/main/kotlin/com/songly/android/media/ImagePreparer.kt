package com.songly.android.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PreparedImage(val bytes: ByteArray, val contentType: String)

object ImagePreparer {

	private const val MAX_DIMENSION = 1600
	private const val JPEG_QUALITY = 85

	suspend fun prepare(context: Context, uri: Uri): PreparedImage = withContext(Dispatchers.IO) {
		val resolver = context.contentResolver
		val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
		resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
		require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Nie udało się odczytać zdjęcia" }

		val options = BitmapFactory.Options().apply {
			inSampleSize = sampleSize(maxOf(bounds.outWidth, bounds.outHeight))
		}
		val decoded = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
			?: throw IllegalArgumentException("Nie udało się odczytać zdjęcia")

		val rotation = runCatching {
			resolver.openInputStream(uri).use { stream ->
				when (ExifInterface(stream!!).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
					ExifInterface.ORIENTATION_ROTATE_90 -> 90f
					ExifInterface.ORIENTATION_ROTATE_180 -> 180f
					ExifInterface.ORIENTATION_ROTATE_270 -> 270f
					else -> 0f
				}
			}
		}.getOrDefault(0f)

		val scale = minOf(1f, MAX_DIMENSION.toFloat() / maxOf(decoded.width, decoded.height))
		val matrix = Matrix().apply {
			postScale(scale, scale)
			postRotate(rotation)
		}
		val output = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

		val bytes = ByteArrayOutputStream().use { out ->
			output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
			out.toByteArray()
		}
		if (output !== decoded) output.recycle()
		decoded.recycle()
		PreparedImage(bytes, "image/jpeg")
	}

	private fun sampleSize(longestSide: Int): Int {
		var sample = 1
		while (longestSide / (sample * 2) >= MAX_DIMENSION) sample *= 2
		return sample
	}
}
