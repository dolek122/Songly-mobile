package com.songly.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Strzałka „wstecz" w pasku ekranu. Zastępuje napis „Wróć": strzałka w lewym
 * górnym rogu to miejsce, w którym każdy jej szuka, a czytnik ekranu dostaje to
 * samo słowo co wcześniej.
 */
@Composable
fun BackButton(onClick: () -> Unit) {
	IconButton(onClick = onClick) {
		Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
	}
}

/**
 * Główna akcja ekranu (nowy post, nowa społeczność): zielone kółko z czarną
 * ikoną — jedyny tak jaskrawy element na ekranie, więc od razu wiadomo, gdzie
 * zacząć.
 */
@Composable
fun SonglyFab(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
	FloatingActionButton(
		onClick = onClick,
		shape = CircleShape,
		containerColor = MaterialTheme.colorScheme.primary,
		contentColor = MaterialTheme.colorScheme.onPrimary,
	) {
		Icon(icon, contentDescription = contentDescription)
	}
}

/**
 * Wiersz prowadzący do innego ekranu: ikona, tytuł, opcjonalny opis i strzałka.
 * Cały wiersz jest jednym przyciskiem, więc palcem trafia się bez celowania, a
 * TalkBack czyta tytuł z opisem jako jedną rzecz.
 *
 * [tint] pozwala wyróżnić wiersz (np. czerwone „Wyloguj się"); [showChevron]
 * chowa strzałkę przy akcjach, które nigdzie nie prowadzą.
 */
@Composable
fun NavigationRow(
	icon: ImageVector,
	title: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	subtitle: String? = null,
	tint: Color = MaterialTheme.colorScheme.onSurface,
	showChevron: Boolean = true,
) {
	Row(
		modifier = modifier
			.fillMaxWidth()
			.heightIn(min = 56.dp)
			.clip(RoundedCornerShape(8.dp))
			.clickable(role = Role.Button, onClick = onClick)
			.padding(horizontal = 4.dp, vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(16.dp),
	) {
		Box(
			modifier = Modifier
				.size(40.dp)
				.clip(RoundedCornerShape(8.dp))
				.background(MaterialTheme.colorScheme.surfaceVariant),
			contentAlignment = Alignment.Center,
		) {
			Icon(icon, contentDescription = null, tint = tint)
		}
		Column(modifier = Modifier.weight(1f)) {
			Text(text = title, style = MaterialTheme.typography.titleMedium, color = tint)
			if (subtitle != null) {
				Text(
					text = subtitle,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		}
		if (showChevron) {
			Icon(
				Icons.AutoMirrored.Filled.KeyboardArrowRight,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}

/**
 * Pusty stan albo błąd na środku ekranu: duża, wyszarzona ikona, jedno zdanie i
 * (opcjonalnie) przycisk. Ikona od razu mówi, o czym jest ekran, nawet zanim ktoś
 * przeczyta tekst.
 */
@Composable
fun EmptyState(
	icon: ImageVector,
	message: String,
	modifier: Modifier = Modifier,
	action: (@Composable () -> Unit)? = null,
) {
	Column(
		modifier = modifier.fillMaxSize().padding(32.dp),
		verticalArrangement = Arrangement.Center,
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		Icon(
			icon,
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.size(56.dp),
		)
		Text(
			text = message,
			style = MaterialTheme.typography.bodyLarge,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			textAlign = TextAlign.Center,
			modifier = Modifier.padding(top = 16.dp),
		)
		if (action != null) {
			Box(modifier = Modifier.padding(top = 20.dp)) { action() }
		}
	}
}

/** Nagłówek sekcji na długim ekranie — pogrubiony, z oddechem nad sobą. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
	Text(
		text = text,
		style = MaterialTheme.typography.titleLarge,
		modifier = modifier.padding(top = 16.dp, bottom = 4.dp),
	)
}

/**
 * Pigułka wyboru (filtr listy, gatunek): wybrana świeci zielenią z czarnym
 * napisem, niewybrana jest ciemnoszara — bez obramowań, które na czarnym tle
 * tylko szumią.
 */
@Composable
fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
	FilterChip(
		selected = selected,
		onClick = onClick,
		label = { Text(label) },
		enabled = enabled,
		shape = CircleShape,
		colors = FilterChipDefaults.filterChipColors(
			containerColor = MaterialTheme.colorScheme.surfaceVariant,
			labelColor = MaterialTheme.colorScheme.onSurface,
		),
		border = null,
	)
}
