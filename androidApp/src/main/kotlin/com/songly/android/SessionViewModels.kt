package com.songly.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * ViewModele ekranów żyją tyle co jedna sesja (albo jeden pobyt na ekranach
 * logowania), a nie tyle co Activity.
 *
 * Domyślnie `viewModel()` bierze magazyn Activity, więc ViewModel przeżywał
 * wylogowanie. Logowanie wracało wtedy ze stanem „zalogowano" z poprzedniego razu
 * i od razu wpuszczało do aplikacji bez sesji — 401, „sesja wygasła", wylogowanie
 * i tak w kółko, kilkaset żądań na sekundę. W drugą stronę feed, profil i reszta
 * pokazałyby kolejnej osobie zalogowanej na tym telefonie stan poprzedniej.
 *
 * Każda zmiana [generation] dostaje świeży magazyn, a poprzedni jest czyszczony —
 * `onCleared` przerywa też żądania, które stara sesja jeszcze miała w locie.
 * Sam magazyn trzyma ViewModel Activity, więc obrót ekranu go nie gubi.
 */
@Composable
fun SessionViewModels(generation: Int, content: @Composable () -> Unit) {
	val stores: SessionStores = viewModel()
	val owner = remember(generation) {
		object : ViewModelStoreOwner {
			override val viewModelStore = stores.storeFor(generation)
		}
	}
	CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

internal class SessionStores : ViewModel() {

	private var generation: Int? = null
	private var store = ViewModelStore()

	fun storeFor(generation: Int): ViewModelStore {
		if (generation != this.generation) {
			store.clear()
			store = ViewModelStore()
			this.generation = generation
		}
		return store
	}

	override fun onCleared() {
		store.clear()
	}
}
