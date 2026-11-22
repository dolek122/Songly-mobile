package com.songly.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

fun Modifier.accessibleAction(
	description: String? = null,
	state: String? = null,
	onClick: () -> Unit,
): Modifier = this
	.clickable(role = Role.Button, onClick = onClick)
	.semantics {
		if (description != null) contentDescription = description
		if (state != null) stateDescription = state
	}
	.minimumInteractiveComponentSize()

fun Modifier.announcedWhenChanged(): Modifier = semantics { liveRegion = LiveRegionMode.Polite }
