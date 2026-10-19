package com.songly.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta marki Songly: zieleń na czerni, jak w aplikacjach muzycznych, do których
 * użytkownicy są przyzwyczajeni. Zieleń jest zarezerwowana dla rzeczy, które coś
 * robią albo coś znaczą (główny przycisk, polubienie, „na żywo", aktywny wybór) —
 * reszta to czerń i szarości, żeby ekran nie krzyczał i od razu było widać, gdzie
 * kliknąć.
 *
 * Jeden, ciemny schemat — bez wariantu jasnego. Zielono-czarny wygląd jest
 * rozpoznawalnością produktu, a jasna wersja tej samej palety byłaby po prostu
 * inną aplikacją. Wartości są wypisane wprost, a nie brane z dynamic color
 * Androida 12+, z tego samego powodu.
 *
 * Szarości idą od tła w górę: im wyżej leży płaszczyzna (karta, dialog), tym jest
 * jaśniejsza — na czarnym tle cień prawie nie widać, więc to jasność mówi, co
 * jest nad czym.
 */
internal object SonglyPalette {

	// Zieleń marki — na czarnym tle i pod czarnym tekstem jest czytelna (kontrast
	// pilnuje PaletteContrastTest), więc przyciski mają czarne etykiety.
	val Green = Color(0xFF1ED760)
	val OnGreen = Color(0xFF000000)

	// Ciemna zieleń: tło inicjałów w awatarze i inne spokojne wyróżnienia.
	val GreenContainer = Color(0xFF14532D)
	val OnGreenContainer = Color(0xFFC8F7D8)

	// Czerń i szarości, od najniższej płaszczyzny do najwyższej.
	val Black = Color(0xFF000000) // dolny pasek nawigacji
	val Background = Color(0xFF121212) // tło ekranów
	val SurfaceLow = Color(0xFF181818)
	val SurfaceCard = Color(0xFF1F1F1F) // karty: posty, społeczności, powiadomienia
	val SurfaceRaised = Color(0xFF242424) // menu
	val SurfaceHigh = Color(0xFF282828) // dialogi
	val SurfaceVariant = Color(0xFF2A2A2A) // wyróżnione karty (np. nieprzeczytane)

	// Tekst.
	val TextPrimary = Color(0xFFFFFFFF)
	val TextSecondary = Color(0xFFB3B3B3)

	// Obramowania pól i separatory.
	val Outline = Color(0xFF727272)
	val OutlineVariant = Color(0xFF333333)

	// Błędy — ciepła czerwień zamiast ostrej, żeby nie gryzła się z zielenią.
	val Error = Color(0xFFF15E6C)
	val OnError = Color(0xFF000000)
	val ErrorContainer = Color(0xFF4A1A1F)
	val OnErrorContainer = Color(0xFFFFDADC)
}
