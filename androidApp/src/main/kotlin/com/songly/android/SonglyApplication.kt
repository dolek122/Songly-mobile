package com.songly.android

import android.app.Application
import com.songly.android.onboarding.OnboardingStore
import com.songly.android.onboarding.SharedPreferencesOnboardingStore
import com.songly.android.push.PushRegistration
import com.songly.shared.config.ApiConfig
import com.songly.shared.network.SonglyApiClient
import com.songly.shared.session.AndroidKeystoreTokenStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

	/** Kto przeszedł już onboarding z gatunkami — per konto, na tym urządzeniu. */
	val onboardingStore: OnboardingStore by lazy { SharedPreferencesOnboardingStore(this) }

	/**
	 * Zakres na pracę, która ma przeżyć ekran: rejestracja tokenu push kończy się w
	 * callbacku FCM, często już po wyjściu z ekranu, który ją zaczął.
	 */
	val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

	override fun onCreate() {
		super.onCreate()
		PushRegistration.createChannel(this)
	}

	protected open fun createClient(): SonglyApiClient = SonglyApiClient(
		config = apiConfigFor(debug = BuildConfig.DEBUG),
		storage = AndroidKeystoreTokenStorage(this),
	)

	companion object {

		/**
		 * Debug łączy się z backendem na komputerze (emulator widzi go pod 10.0.2.2),
		 * release — ze stagingiem, dopóki nie ma produkcji.
		 *
		 * Release **bez logowania sieci**: Ktor na poziomie INFO wypisuje do Logcata
		 * każdy URL, a w nich identyfikatory użytkowników i frazy wyszukiwania — na
		 * cudzym telefonie to wyciek danych do logu, który czyta każdy z dostępem ADB.
		 */
		fun apiConfigFor(debug: Boolean): ApiConfig =
			if (debug) ApiConfig.LOCAL_ANDROID_EMULATOR else ApiConfig.STAGING.copy(enableNetworkLogging = false)
	}
}
