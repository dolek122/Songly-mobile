package com.songly.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val TeardropShape = RoundedCornerShape(
	topStartPercent = 50,
	topEndPercent = 0,
	bottomEndPercent = 50,
	bottomStartPercent = 50,
)

private const val MARK_UNIT = 56f
private const val BARS_CENTER_X = 29.5f
private const val BARS_CENTER_Y = 26.5f
private const val BAR_SPACING = 7.5f
private const val BAR_WIDTH = 5f
private val BAR_HALF_HEIGHTS = floatArrayOf(4.5f, 10f, 16f, 10f, 4.5f)

@Composable
fun SonglyMark(
	modifier: Modifier = Modifier,
	size: Dp = 40.dp,
	color: Color = MaterialTheme.colorScheme.primary,
	barColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
	Box(
		modifier = modifier
			.size(size)
			.clip(TeardropShape)
			.background(color),
	) {
		Canvas(modifier = Modifier.size(size)) {
			val unit = this.size.width / MARK_UNIT
			BAR_HALF_HEIGHTS.forEachIndexed { index, half ->
				val x = (BARS_CENTER_X + (index - 2) * BAR_SPACING) * unit
				drawLine(
					color = barColor,
					start = Offset(x, (BARS_CENTER_Y - half) * unit),
					end = Offset(x, (BARS_CENTER_Y + half) * unit),
					strokeWidth = BAR_WIDTH * unit,
					cap = StrokeCap.Round,
				)
			}
		}
	}
}

@Composable
fun Modifier.songlyBackdrop(): Modifier {
	val glow = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
	val base = MaterialTheme.colorScheme.background
	return background(base).background(Brush.verticalGradient(0f to glow, 0.35f to base))
}
