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

/** Zdjęcie gotowe do wysłania: bajty i typ do nagłówka multiparta. */
class PreparedImage(val bytes: ByteArray, val contentType: String)

/**
 * Zamienia wybrane zdjęcie na coś, co backend przyjmie (ADR 0010): JPEG, PNG albo
 * WebP i najwyżej 5 MB.
 *
 * Wysyłanie oryginału nie działa w praktyce z dwóch powodów: zdjęcie z aparatu
 * telefonu ma zwykle kilka–kilkanaście MB (413 od serwera), a nowsze telefony
 * zapisują je jako HEIC, którego backend nie rozpoznaje (415). Dlatego zawsze
 * dekodujemy, pomniejszamy i kodujemy od nowa do JPEG — przy okazji znika EXIF,
 * w tym lokalizacja GPS, której nikt nie chciał publikować razem ze zdjęciem.
 */
object ImagePreparer {

	private const val MAX_DIMENSION = 1600
	private const val JPEG_QUALITY = 85

	suspend fun prepare(context: Context, uri: Uri): PreparedImage = withContext(Dispatchers.IO) {
		val resolver = context.contentResolver
		val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
		resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
		require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Nie udało się odczytać zdjęcia" }

		// Najpierw tanie zmniejszenie o potęgę dwójki już przy dekodowaniu — pełne
		// 12-megapikselowe zdjęcie w pamięci to ~48 MB i łatwy OutOfMemoryError.
		val options = BitmapFactory.Options().apply {
			inSampleSize = sampleSize(maxOf(bounds.outWidth, bounds.outHeight))
		}
		val decoded = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
			?: throw IllegalArgumentException("Nie udało się odczytać zdjęcia")

		// Bez EXIF-owej orientacji zdjęcie pionowe z aparatu wyszłoby obrócone, bo
		// przekodowanie gubi znacznik, którym przeglądarki je prostowały.
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
