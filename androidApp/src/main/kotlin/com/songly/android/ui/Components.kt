package com.songly.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun BackButton(onClick: () -> Unit) {
	IconButton(onClick = onClick) {
		Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabTopBar(
	title: @Composable () -> Unit,
	navigationIcon: @Composable () -> Unit = {},
	actions: @Composable RowScope.() -> Unit = {},
) {
	TopAppBar(
		title = title,
		navigationIcon = navigationIcon,
		actions = actions,
		colors = TopAppBarDefaults.topAppBarColors(
			containerColor = Color.Transparent,
			scrolledContainerColor = Color.Transparent,
		),
	)
}

@Composable
fun TabTopBar(
	title: String,
	navigationIcon: @Composable () -> Unit = {},
	actions: @Composable RowScope.() -> Unit = {},
) {
	TabTopBar(
		title = { Text(text = title, style = MaterialTheme.typography.headlineSmall) },
		navigationIcon = navigationIcon,
		actions = actions,
	)
}

@Composable
fun ScreenTopBar(
	title: String,
	navigationIcon: @Composable () -> Unit = {},
	actions: @Composable RowScope.() -> Unit = {},
) {
	TabTopBar(
		title = {
			Text(
				text = title,
				style = MaterialTheme.typography.titleLarge,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		},
		navigationIcon = navigationIcon,
		actions = actions,
	)
}

@Composable
fun SonglyFab(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
	FloatingActionButton(
		onClick = onClick,
		shape = TeardropShape,
		containerColor = MaterialTheme.colorScheme.primary,
		contentColor = MaterialTheme.colorScheme.onPrimary,
	) {
		Icon(icon, contentDescription = contentDescription)
	}
}

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

@Composable
fun CenteredScrollColumn(
	modifier: Modifier = Modifier,
	verticalArrangement: Arrangement.Vertical = Arrangement.Center,
	content: @Composable ColumnScope.() -> Unit,
) {
	BoxWithConstraints(modifier = modifier.fillMaxSize()) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
				.heightIn(min = maxHeight)
				.padding(24.dp),
			verticalArrangement = verticalArrangement,
			horizontalAlignment = Alignment.CenterHorizontally,
			content = content,
		)
	}
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
	Text(
		text = text,
		style = MaterialTheme.typography.titleLarge,
		modifier = modifier.padding(top = 16.dp, bottom = 4.dp),
	)
}

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
