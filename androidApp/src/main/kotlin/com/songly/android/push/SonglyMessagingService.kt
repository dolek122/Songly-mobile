package com.songly.android.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.songly.android.MainActivity
import com.songly.android.R
import com.songly.android.SonglyApplication
import kotlinx.coroutines.launch

/**
 * Odbiór pushy (backend ADR 0040). Backend wysyła wiadomości wyłącznie z `data`,
 * więc ta metoda dostaje je także w tle i to aplikacja decyduje, jak wygląda
 * powiadomienie i co otwiera stuknięcie.
 */
class SonglyMessagingService : FirebaseMessagingService() {

	/**
	 * FCM wymienia token (reinstalacja, czyszczenie danych). Bez zgłoszenia nowego
	 * backend wysyłałby na stary, aż FCM nie powie mu, że go nie ma.
	 */
	override fun onNewToken(token: String) {
		val app = application as SonglyApplication
		app.appScope.launch {
			if (app.apiClient.isLoggedIn()) {
				runCatching { app.apiClient.registerPushDevice(token, PushRegistration.PLATFORM) }
			}
		}
	}

	override fun onMessageReceived(message: RemoteMessage) {
		val title = message.data["title"] ?: return
		val body = message.data["body"] ?: return
		// Bez zgody (Android 13+) systemowe notify i tak by niczego nie pokazało.
		if (Build.VERSION.SDK_INT >= 33 &&
			ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
			PackageManager.PERMISSION_GRANTED
		) {
			return
		}
		val open = Intent(this, MainActivity::class.java).apply {
			action = MainActivity.ACTION_OPEN_NOTIFICATIONS
			flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
		}
		val pendingIntent = PendingIntent.getActivity(
			this,
			0,
			open,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
		)
		val notification = NotificationCompat.Builder(this, PushRegistration.CHANNEL_ID)
			.setSmallIcon(R.drawable.ic_stat_songly)
			.setContentTitle(title)
			.setContentText(body)
			.setAutoCancel(true)
			.setContentIntent(pendingIntent)
			.build()
		// Id z powiadomienia backendu: ten sam push dostarczony dwa razy nie zrobi dwóch wpisów.
		val id = message.data["notificationId"]?.hashCode() ?: System.currentTimeMillis().toInt()
		NotificationManagerCompat.from(this).notify(id, notification)
	}
}
