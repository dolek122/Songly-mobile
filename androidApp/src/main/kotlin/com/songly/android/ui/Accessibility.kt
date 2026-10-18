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

/**
 * Klikalny element (tekst, awatar), który zachowuje się jak przycisk: TalkBack
 * ogłasza go jako przycisk, a cel dotyku ma co najmniej 48 dp (wytyczne Androida).
 * Gołe `clickable` na tekście dawało cel wielkości samych liter — ok. 20 dp — i
 * czytnik nie wiedział, że w ogóle da się w niego kliknąć.
 *
 * [description] zastępuje treść czytaną przez TalkBack, gdy tekst na ekranie sam
 * jej nie niesie (np. „♡ 3"); [state] mówi, w jakim stanie jest przełącznik
 * udający przycisk (np. „polubiony").
 */
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
	// Po clickable, nie przed: odwrotnie 48 dp rezerwowało tylko miejsce wokół, a
	// węzeł przycisku — ten, który widzą TalkBack i Accessibility Scanner — dalej
	// miał rozmiar samego tekstu.
	.minimumInteractiveComponentSize()

/**
 * Tekst, który czytnik ekranu ogłasza sam, gdy się pojawi albo zmieni — dla
 * komunikatów o błędzie. Bez tego osoba z TalkBackiem po „Zaloguj się" nie
 * słyszała niczego: błąd był widoczny, ale nikt go nie przeczytał.
 */
fun Modifier.announcedWhenChanged(): Modifier = semantics { liveRegion = LiveRegionMode.Polite }
