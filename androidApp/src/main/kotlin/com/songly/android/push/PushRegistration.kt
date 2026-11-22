package com.songly.android.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object PushRegistration {

	const val CHANNEL_ID = "songly_notifications"
	const val PLATFORM = "ANDROID"

	fun isAvailable(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

	fun registerCurrentDevice(context: Context, client: SonglyApiClient, scope: CoroutineScope) {
		if (!isAvailable(context)) return
		FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
			scope.launch { runCatching { client.registerPushDevice(token, PLATFORM) } }
		}
	}

	fun createChannel(context: Context) {
		val channel = NotificationChannel(CHANNEL_ID, "Powiadomienia", NotificationManager.IMPORTANCE_DEFAULT).apply {
			description = "Polubienia, komentarze, zaproszenia i decyzje moderacji"
		}
		context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
	}
}
