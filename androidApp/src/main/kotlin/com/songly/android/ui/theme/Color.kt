package com.songly.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta marki Songly. Indygo jako kolor wiodący, ciepły amber na akcenty
 * (polubienia, wyróżnienia) i morski na trzeci plan.
 *
 * Wartości są wypisane wprost, a nie brane z dynamic color Androida 12+:
 * kolorystyka jest częścią rozpoznawalności produktu, a dynamic color sprawiłby,
 * że Songly wygląda inaczej na każdym telefonie. Jeśli kiedyś uznamy, że
 * dopasowanie do tapety jest ważniejsze od spójności marki, to jedna zmiana w
 * [SonglyTheme].
 *
 * Odcienie w schemacie ciemnym są jaśniejsze niż w jasnym — to nie ozdoba, tylko
 * wymóg kontrastu: ten sam indygo #4338CA na czarnym tle jest nieczytelny.
 */
internal object SonglyPalette {

	// Jasny schemat
	val Primary = Color(0xFF4338CA)
	val OnPrimary = Color(0xFFFFFFFF)
	val PrimaryContainer = Color(0xFFE0E7FF)
	val OnPrimaryContainer = Color(0xFF1E1B4B)
	val Secondary = Color(0xFFB45309)
	val OnSecondary = Color(0xFFFFFFFF)
	val SecondaryContainer = Color(0xFFFEF3C7)
	val OnSecondaryContainer = Color(0xFF451A03)
	val Tertiary = Color(0xFF0F766E)
	val OnTertiary = Color(0xFFFFFFFF)
	val TertiaryContainer = Color(0xFFCCFBF1)
	val OnTertiaryContainer = Color(0xFF042F2E)
	val Background = Color(0xFFFDFCFF)
	val OnBackground = Color(0xFF1B1B1F)
	val Surface = Color(0xFFFDFCFF)
	val OnSurface = Color(0xFF1B1B1F)
	val SurfaceVariant = Color(0xFFE4E1EC)
	val OnSurfaceVariant = Color(0xFF47464F)
	val Outline = Color(0xFF78767F)
	val Error = Color(0xFFB3261E)
	val OnError = Color(0xFFFFFFFF)
	val ErrorContainer = Color(0xFFF9DEDC)
	val OnErrorContainer = Color(0xFF410E0B)

	// Ciemny schemat
	val DarkPrimary = Color(0xFFA5B4FC)
	val DarkOnPrimary = Color(0xFF1E1B4B)
	val DarkPrimaryContainer = Color(0xFF312E81)
	val DarkOnPrimaryContainer = Color(0xFFE0E7FF)
	val DarkSecondary = Color(0xFFFCD34D)
	val DarkOnSecondary = Color(0xFF451A03)
	val DarkSecondaryContainer = Color(0xFF78350F)
	val DarkOnSecondaryContainer = Color(0xFFFEF3C7)
	val DarkTertiary = Color(0xFF5EEAD4)
	val DarkOnTertiary = Color(0xFF042F2E)
	val DarkTertiaryContainer = Color(0xFF115E59)
	val DarkOnTertiaryContainer = Color(0xFFCCFBF1)
	val DarkBackground = Color(0xFF131316)
	val DarkOnBackground = Color(0xFFE5E1E6)
	val DarkSurface = Color(0xFF131316)
	val DarkOnSurface = Color(0xFFE5E1E6)
	val DarkSurfaceVariant = Color(0xFF47464F)
	val DarkOnSurfaceVariant = Color(0xFFC9C5D0)
	val DarkOutline = Color(0xFF938F99)
	val DarkError = Color(0xFFF2B8B5)
	val DarkOnError = Color(0xFF601410)
	val DarkErrorContainer = Color(0xFF8C1D18)
	val DarkOnErrorContainer = Color(0xFFF9DEDC)
}
