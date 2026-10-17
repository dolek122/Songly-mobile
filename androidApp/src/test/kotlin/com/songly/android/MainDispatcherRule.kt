package com.songly.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Podstawia dyspozytor główny na czas testu.
 *
 * ViewModele odpalają korutyny w `viewModelScope`, czyli na `Dispatchers.Main`,
 * którego w teście jednostkowym nie ma. [UnconfinedTestDispatcher] wykonuje je
 * od razu, więc po `setContent` stan jest już policzony i test nie musi zgadywać,
 * ile czekać — a tam, gdzie mimo wszystko trzeba poczekać na rekompozycję,
 * zostaje `waitUntil`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {

	override fun starting(description: Description) {
		Dispatchers.setMain(UnconfinedTestDispatcher())
	}

	override fun finished(description: Description) {
		Dispatchers.resetMain()
	}
}
