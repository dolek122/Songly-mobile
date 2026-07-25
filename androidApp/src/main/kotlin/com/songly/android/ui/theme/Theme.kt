package com.songly.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightScheme = lightColorScheme(
	primary = SonglyPalette.Primary,
	onPrimary = SonglyPalette.OnPrimary,
	primaryContainer = SonglyPalette.PrimaryContainer,
	onPrimaryContainer = SonglyPalette.OnPrimaryContainer,
	secondary = SonglyPalette.Secondary,
	onSecondary = SonglyPalette.OnSecondary,
	secondaryContainer = SonglyPalette.SecondaryContainer,
	onSecondaryContainer = SonglyPalette.OnSecondaryContainer,
	tertiary = SonglyPalette.Tertiary,
	onTertiary = SonglyPalette.OnTertiary,
	tertiaryContainer = SonglyPalette.TertiaryContainer,
	onTertiaryContainer = SonglyPalette.OnTertiaryContainer,
	background = SonglyPalette.Background,
	onBackground = SonglyPalette.OnBackground,
	surface = SonglyPalette.Surface,
	onSurface = SonglyPalette.OnSurface,
	surfaceVariant = SonglyPalette.SurfaceVariant,
	onSurfaceVariant = SonglyPalette.OnSurfaceVariant,
	outline = SonglyPalette.Outline,
	error = SonglyPalette.Error,
	onError = SonglyPalette.OnError,
	errorContainer = SonglyPalette.ErrorContainer,
	onErrorContainer = SonglyPalette.OnErrorContainer,
)

private val DarkScheme = darkColorScheme(
	primary = SonglyPalette.DarkPrimary,
	onPrimary = SonglyPalette.DarkOnPrimary,
	primaryContainer = SonglyPalette.DarkPrimaryContainer,
	onPrimaryContainer = SonglyPalette.DarkOnPrimaryContainer,
	secondary = SonglyPalette.DarkSecondary,
	onSecondary = SonglyPalette.DarkOnSecondary,
	secondaryContainer = SonglyPalette.DarkSecondaryContainer,
	onSecondaryContainer = SonglyPalette.DarkOnSecondaryContainer,
	tertiary = SonglyPalette.DarkTertiary,
	onTertiary = SonglyPalette.DarkOnTertiary,
	tertiaryContainer = SonglyPalette.DarkTertiaryContainer,
	onTertiaryContainer = SonglyPalette.DarkOnTertiaryContainer,
	background = SonglyPalette.DarkBackground,
	onBackground = SonglyPalette.DarkOnBackground,
	surface = SonglyPalette.DarkSurface,
	onSurface = SonglyPalette.DarkOnSurface,
	surfaceVariant = SonglyPalette.DarkSurfaceVariant,
	onSurfaceVariant = SonglyPalette.DarkOnSurfaceVariant,
	outline = SonglyPalette.DarkOutline,
	error = SonglyPalette.DarkError,
	onError = SonglyPalette.DarkOnError,
	errorContainer = SonglyPalette.DarkErrorContainer,
	onErrorContainer = SonglyPalette.DarkOnErrorContainer,
)

/**
 * Motyw aplikacji. Do tej pory ekrany dostawały goły [MaterialTheme], czyli
 * bazową paletę Material 3 — ten sam fiolet, co każda aplikacja bez motywu, i
 * <b>zawsze jasny</b>, niezależnie od ustawienia systemu.
 *
 * [darkTheme] domyślnie idzie za systemem. Aplikacja nie ma własnego
 * przełącznika i nie potrzebuje: Android ma go globalnie, a duplikowanie takich
 * ustawień w każdej aplikacji tylko myli.
 */
@Composable
fun SonglyTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
	val view = LocalView.current
	if (!view.isInEditMode) {
		// Ikony w pasku statusu muszą być odwrotnością tła, inaczej w ciemnym motywie
		// robią się białe na białym. Compose nie robi tego sam.
		SideEffect {
			val window = (view.context as Activity).window
			WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
		}
	}

	MaterialTheme(
		colorScheme = if (darkTheme) DarkScheme else LightScheme,
		content = content,
	)
}
