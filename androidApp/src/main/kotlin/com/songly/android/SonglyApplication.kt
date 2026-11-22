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

open class SonglyApplication : Application() {

	val apiClient: SonglyApiClient by lazy { createClient() }

	val onboardingStore: OnboardingStore by lazy { SharedPreferencesOnboardingStore(this) }

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

		fun apiConfigFor(debug: Boolean): ApiConfig =
			if (debug) ApiConfig.LOCAL_ANDROID_EMULATOR else ApiConfig.STAGING.copy(enableNetworkLogging = false)
	}
}
