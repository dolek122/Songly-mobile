package com.songly.android.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Kontrast palety liczony, nie oceniany na oko. Motyw to jedyna część UI, której
 * poprawność da się sprawdzić bez urządzenia — a „ten odcień chyba wystarczy" to
 * dokładnie ten rodzaj decyzji, po której tekst okazuje się nieczytalny na
 * słońcu albo w ciemnym motywie.
 *
 * Próg 4.5:1 to WCAG AA dla zwykłego tekstu. Sprawdzamy pary, które realnie
 * występują na ekranach: treść na tle, tekst pomocniczy, etykiety przycisków,
 * komunikaty błędów i statusu.
 */
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

	private fun assertReadable(name: String, foreground: Color, background: Color) {
		val ratio = contrast(foreground, background)
		assertTrue(ratio >= 4.5, "$name: kontrast ${(ratio * 100).toInt() / 100.0}:1, wymagane min. 4.5:1")
	}

	@Test
	fun lightSchemeIsReadable() {
		with(SonglyPalette) {
			assertReadable("treść na tle", OnBackground, Background)
			assertReadable("treść na powierzchni", OnSurface, Surface)
			assertReadable("tekst pomocniczy", OnSurfaceVariant, Surface)
			assertReadable("etykieta na przycisku", OnPrimary, Primary)
			assertReadable("komunikat statusu", Primary, Surface)
			assertReadable("komunikat błędu", Error, Surface)
			assertReadable("treść w kontenerze akcentu", OnSecondaryContainer, SecondaryContainer)
		}
	}

	@Test
	fun darkSchemeIsReadable() {
		with(SonglyPalette) {
			assertReadable("treść na tle", DarkOnBackground, DarkBackground)
			assertReadable("treść na powierzchni", DarkOnSurface, DarkSurface)
			assertReadable("tekst pomocniczy", DarkOnSurfaceVariant, DarkSurface)
			assertReadable("etykieta na przycisku", DarkOnPrimary, DarkPrimary)
			assertReadable("komunikat statusu", DarkPrimary, DarkSurface)
			assertReadable("komunikat błędu", DarkError, DarkSurface)
			assertReadable("treść w kontenerze akcentu", DarkOnSecondaryContainer, DarkSecondaryContainer)
		}
	}

	/**
	 * Ciemny schemat nie może być jasnym z zamienionymi rolami — odcienie wiodące
	 * muszą być jaśniejsze niż w jasnym motywie, inaczej znikają na czarnym tle.
	 */
	@Test
	fun darkSchemeLightensBrandColors() {
		with(SonglyPalette) {
			assertTrue(
				relativeLuminance(DarkPrimary) > relativeLuminance(Primary),
				"kolor wiodący w ciemnym motywie musi być jaśniejszy niż w jasnym",
			)
			assertTrue(
				relativeLuminance(DarkBackground) < relativeLuminance(Background),
				"tło ciemnego motywu musi być ciemniejsze",
			)
		}
	}
}
