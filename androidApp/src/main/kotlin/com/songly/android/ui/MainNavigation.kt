package com.songly.android.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Główne miejsca aplikacji — zawsze widoczne w dolnym pasku, zamiast schowanych
 * w menu „⋮", gdzie trzeba było ich szukać. Pięć to górna granica, przy której
 * pasek jest jeszcze czytelny; reszta (powiadomienia, dopasowani, ustawienia) jest
 * o jedno stuknięcie od zakładki, do której tematycznie należy.
 */
enum class MainTab(val label: String, val selectedIcon: ImageVector, val icon: ImageVector) {
	HOME("Główna", Icons.Filled.Home, Icons.Outlined.Home),
	MUSIC("Muzyka", Icons.Filled.MusicNote, Icons.Outlined.MusicNote),
	COMMUNITIES("Społeczności", Icons.Filled.Groups, Icons.Outlined.Groups),
	FRIENDS("Znajomi", Icons.Filled.People, Icons.Outlined.People),
	PROFILE("Profil", Icons.Filled.Person, Icons.Outlined.Person),
}

/**
 * Dolny pasek nawigacji: czarny, wybrana zakładka biała z wypełnioną ikoną,
 * pozostałe szare z konturem. Bez kolorowej „pigułki" pod ikoną — różnicę widać
 * po jasności i kształcie, a zieleń zostaje dla akcji.
 */
@Composable
fun SonglyNavigationBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
	NavigationBar(
		containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
		tonalElevation = 0.dp,
	) {
		MainTab.entries.forEach { tab ->
			val isSelected = tab == selected
			NavigationBarItem(
				selected = isSelected,
				onClick = { onSelect(tab) },
				// Nazwę czyta czytnik z etykiety pod ikoną — ikona jest tylko ozdobą.
				icon = { Icon(if (isSelected) tab.selectedIcon else tab.icon, contentDescription = null) },
				label = {
					Text(
						text = tab.label,
						style = MaterialTheme.typography.labelSmall,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				},
				colors = NavigationBarItemDefaults.colors(
					selectedIconColor = MaterialTheme.colorScheme.onSurface,
					selectedTextColor = MaterialTheme.colorScheme.onSurface,
					indicatorColor = Color.Transparent,
					unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
					unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
				),
			)
		}
	}
}
