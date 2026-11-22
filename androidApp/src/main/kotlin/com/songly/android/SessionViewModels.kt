package com.songly.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

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
