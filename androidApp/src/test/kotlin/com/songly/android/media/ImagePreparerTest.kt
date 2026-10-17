package com.songly.android.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.robolectric.RuntimeEnvironment
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Prawdziwe dekodowanie i kodowanie bitmap (natywna grafika Robolectrica) — atrapa
 * nie powiedziałaby nic o tym, czy wynik zmieści się w limicie backendu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImagePreparerTest {

	private val context: android.content.Context = RuntimeEnvironment.getApplication()

	private fun pngFile(width: Int, height: Int): Uri {
		val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
		bitmap.eraseColor(android.graphics.Color.rgb(30, 144, 255))
		val file = File.createTempFile("source", ".png", context.cacheDir)
		file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
		return Uri.fromFile(file)
	}

	@Test
	fun largePhotoIsDownscaledToJpegUnderBackendLimit() {
		val prepared = runBlocking { ImagePreparer.prepare(context, pngFile(4000, 3000)) }

		assertEquals("image/jpeg", prepared.contentType)
		// JPEG zaczyna się od FF D8 — backend rozpoznaje format po tych bajtach.
		assertEquals(0xFF.toByte(), prepared.bytes[0])
		assertEquals(0xD8.toByte(), prepared.bytes[1])
		assertTrue("limit backendu to 5 MB", prepared.bytes.size < 5 * 1024 * 1024)

		val decoded = BitmapFactory.decodeByteArray(prepared.bytes, 0, prepared.bytes.size)
		assertEquals(1600, maxOf(decoded.width, decoded.height))
		// Proporcje zachowane (4:3).
		assertEquals(1200, minOf(decoded.width, decoded.height))
	}

	@Test
	fun smallImageIsNotUpscaled() {
		val prepared = runBlocking { ImagePreparer.prepare(context, pngFile(400, 300)) }

		val decoded = BitmapFactory.decodeByteArray(prepared.bytes, 0, prepared.bytes.size)
		assertEquals(400, decoded.width)
		assertEquals(300, decoded.height)
	}
}
