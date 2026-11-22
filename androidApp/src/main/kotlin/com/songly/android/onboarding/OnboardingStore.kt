package com.songly.android.onboarding

import android.content.Context

interface OnboardingStore {
	fun isDone(userId: String): Boolean
	fun markDone(userId: String)
}

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
