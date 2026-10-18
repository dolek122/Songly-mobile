package com.songly.android

import android.app.Application
import com.songly.shared.config.ApiConfig
import com.songly.shared.network.SonglyApiClient
import com.songly.shared.session.AndroidKeystoreTokenStorage

/**
 * Właściciel [SonglyApiClient] — jeden klient na proces, nie na Activity.
 *
 * Klient trzymany w Activity i zamykany w jej `onDestroy` psuł aplikację przy każdym
 * obrocie ekranu i zmianie motywu: Activity ginie i powstaje od nowa, ale ViewModele
 * przeżywają tę zmianę i dalej trzymały **zamknięty** klient — więc po obróceniu
 * telefonu każda akcja kończyła się „Brak połączenia z serwerem". Klient żyje teraz
 * tyle co proces i nie jest zamykany wcale (system sprząta go razem z procesem).
 *
 * [createClient] jest otwarte wyłącznie dla testów, żeby podstawić atrapę backendu
 * pod całą aplikację, łącznie z `MainActivity`.
 */
open class SonglyApplication : Application() {

	val apiClient: SonglyApiClient by lazy { createClient() }

	protected open fun createClient(): SonglyApiClient = SonglyApiClient(
		config = ApiConfig.LOCAL_ANDROID_EMULATOR,
		storage = AndroidKeystoreTokenStorage(this),
	)
}
