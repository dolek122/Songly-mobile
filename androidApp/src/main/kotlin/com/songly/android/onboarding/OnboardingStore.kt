package com.songly.android.onboarding

import android.content.Context

/**
 * Pamięć „ten użytkownik ma onboarding za sobą" — osobno dla każdego konta, na
 * tym urządzeniu. Backend nie trzyma takiej flagi, a sam brak gatunków nie
 * wystarcza: kto świadomie pominął wybór, nie może dostawać tego samego ekranu
 * przy każdym logowaniu.
 *
 * Interfejs, bo testy podstawiają wersję w pamięci.
 */
interface OnboardingStore {
	fun isDone(userId: String): Boolean
	fun markDone(userId: String)
}

/** [OnboardingStore] w SharedPreferences — przeżywa restart i wylogowanie. */
class SharedPreferencesOnboardingStore(context: Context) : OnboardingStore {

	private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

	override fun isDone(userId: String): Boolean = prefs.getBoolean(key(userId), false)

	override fun markDone(userId: String) {
		prefs.edit().putBoolean(key(userId), true).apply()
	}

	private fun key(userId: String) = "genres_done_$userId"

	private companion object {
		const val PREFS_NAME = "songly_onboarding"
	}
}
