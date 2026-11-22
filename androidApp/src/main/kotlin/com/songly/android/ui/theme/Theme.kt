package com.songly.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val SonglyColors = darkColorScheme(
	primary = SonglyPalette.Green,
	onPrimary = SonglyPalette.OnGreen,
	primaryContainer = SonglyPalette.GreenContainer,
	onPrimaryContainer = SonglyPalette.OnGreenContainer,
	inversePrimary = SonglyPalette.GreenContainer,
	secondary = SonglyPalette.TextPrimary,
	onSecondary = SonglyPalette.Black,
	secondaryContainer = SonglyPalette.Green,
	onSecondaryContainer = SonglyPalette.OnGreen,
	tertiary = SonglyPalette.Green,
	onTertiary = SonglyPalette.OnGreen,
	tertiaryContainer = SonglyPalette.GreenContainer,
	onTertiaryContainer = SonglyPalette.OnGreenContainer,
	background = SonglyPalette.Background,
	onBackground = SonglyPalette.TextPrimary,
	surface = SonglyPalette.Background,
	onSurface = SonglyPalette.TextPrimary,
	surfaceVariant = SonglyPalette.SurfaceVariant,
	onSurfaceVariant = SonglyPalette.TextSecondary,
	surfaceTint = SonglyPalette.TextPrimary,
	inverseSurface = SonglyPalette.TextPrimary,
	inverseOnSurface = SonglyPalette.Background,
	error = SonglyPalette.Error,
	onError = SonglyPalette.OnError,
	errorContainer = SonglyPalette.ErrorContainer,
	onErrorContainer = SonglyPalette.OnErrorContainer,
	outline = SonglyPalette.Outline,
	outlineVariant = SonglyPalette.OutlineVariant,
	scrim = SonglyPalette.Black,
	surfaceDim = SonglyPalette.Background,
	surfaceBright = SonglyPalette.SurfaceHigh,
	surfaceContainerLowest = SonglyPalette.Black,
	surfaceContainerLow = SonglyPalette.SurfaceLow,
	surfaceContainer = SonglyPalette.SurfaceRaised,
	surfaceContainerHigh = SonglyPalette.SurfaceHigh,
	surfaceContainerHighest = SonglyPalette.SurfaceCard,
)

private val SonglyTypography = Typography().let { base ->
	Typography(
		displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold),
		displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold),
		displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
		headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
		headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
		headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
		titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
		titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
		titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
		bodyLarge = base.bodyLarge,
		bodyMedium = base.bodyMedium,
		bodySmall = base.bodySmall,
		labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
		labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
		labelSmall = base.labelSmall,
	)
}

private val SonglyShapes = Shapes(
	extraSmall = RoundedCornerShape(4.dp),
	small = RoundedCornerShape(8.dp),
	medium = RoundedCornerShape(8.dp),
	large = RoundedCornerShape(12.dp),
	extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun SonglyTheme(content: @Composable () -> Unit) {
	val view = LocalView.current
	if (!view.isInEditMode) {
		SideEffect {
			val window = (view.context as Activity).window
			val insetsController = WindowCompat.getInsetsController(window, view)
			insetsController.isAppearanceLightStatusBars = false
			insetsController.isAppearanceLightNavigationBars = false
		}
	}

	MaterialTheme(
		colorScheme = SonglyColors,
		typography = SonglyTypography,
		shapes = SonglyShapes,
		content = content,
	)
}
