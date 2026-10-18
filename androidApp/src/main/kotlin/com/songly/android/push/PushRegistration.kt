package com.songly.android.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Zgłoszenie tego telefonu do pushy (backend ADR 0040).
 *
 * Bez `google-services.json` (CI, każdy bez projektu Firebase) FirebaseApp nie
 * istnieje i [FirebaseMessaging.getInstance] by rzucił — wtedy po prostu nie ma
 * pushy, a reszta aplikacji działa bez zmian.
 */
object PushRegistration {

	const val CHANNEL_ID = "songly_notifications"
	const val PLATFORM = "ANDROID"

	fun isAvailable(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

	/**
	 * Idempotentne po stronie backendu, więc wołamy przy każdym wejściu do
	 * zalogowanej części aplikacji: token mógł się zmienić, gdy aplikacja spała.
	 * Błąd nie dociera do UI — push to dodatek, nie warunek działania aplikacji.
	 */
	fun registerCurrentDevice(context: Context, client: SonglyApiClient, scope: CoroutineScope) {
		if (!isAvailable(context)) return
		FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
			scope.launch { runCatching { client.registerPushDevice(token, PLATFORM) } }
		}
	}

	/** Kanał musi istnieć, zanim przyjdzie pierwszy push — inaczej Android go nie pokaże. */
	fun createChannel(context: Context) {
		val channel = NotificationChannel(CHANNEL_ID, "Powiadomienia", NotificationManager.IMPORTANCE_DEFAULT).apply {
			description = "Polubienia, komentarze, zaproszenia i decyzje moderacji"
		}
		context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
	}
}
