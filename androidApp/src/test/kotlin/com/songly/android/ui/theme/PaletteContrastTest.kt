package com.songly.android.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

class PaletteContrastTest {

	private fun channelLuminance(channel: Float): Double {
		val c = channel.toDouble()
		return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
	}

	private fun relativeLuminance(color: Color): Double =
		0.2126 * channelLuminance(color.red) +
			0.7152 * channelLuminance(color.green) +
			0.0722 * channelLuminance(color.blue)

	private fun contrast(foreground: Color, background: Color): Double {
		val a = relativeLuminance(foreground)
		val b = relativeLuminance(background)
		return (max(a, b) + 0.05) / (min(a, b) + 0.05)
	}

	private fun assertContrast(name: String, foreground: Color, background: Color, minimum: Double = 4.5) {
		val ratio = contrast(foreground, background)
		assertTrue(ratio >= minimum, "$name: kontrast ${(ratio * 100).toInt() / 100.0}:1, wymagane min. $minimum:1")
	}

	@Test
	fun textIsReadableOnEverySurface() {
		with(SonglyPalette) {
			for ((surfaceName, surface) in listOf("tło" to Background, "karta" to SurfaceCard, "dialog" to SurfaceHigh)) {
				assertContrast("treść ($surfaceName)", TextPrimary, surface)
				assertContrast("tekst pomocniczy ($surfaceName)", TextSecondary, surface)
				assertContrast("komunikat statusu ($surfaceName)", Green, surface)
				assertContrast("komunikat błędu ($surfaceName)", Error, surface)
			}
			assertContrast("tekst pomocniczy na wyróżnionej karcie", TextSecondary, SurfaceVariant)
		}
	}

	@Test
	fun labelsAreReadableOnColoredContainers() {
		with(SonglyPalette) {
			assertContrast("etykieta na zielonym przycisku", OnGreen, Green)
			assertContrast("inicjały w awatarze", OnGreenContainer, GreenContainer)
			assertContrast("licznik na plakietce błędu", OnError, Error)
			assertContrast("treść w kontenerze błędu", OnErrorContainer, ErrorContainer)
		}
	}

	@Test
	fun fieldOutlineIsVisible() {
		with(SonglyPalette) {
			assertContrast("obramowanie pola", Outline, Background, minimum = 3.0)
		}
	}

	@Test
	fun surfacesGetLighterAsTheyRise() {
		with(SonglyPalette) {
			val surfaces = listOf(Black, Background, SurfaceLow, SurfaceCard, SurfaceRaised, SurfaceHigh)
			surfaces.zipWithNext().forEach { (lower, higher) ->
				assertTrue(
					relativeLuminance(higher) > relativeLuminance(lower),
					"płaszczyzna $higher musi być jaśniejsza niż leżąca pod nią $lower",
				)
			}
		}
	}
}
