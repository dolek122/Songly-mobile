package com.songly.android.push

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import com.google.firebase.messaging.RemoteMessage
import com.songly.android.MainActivity
import com.songly.android.TestSonglyApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestSonglyApplication::class)
class SonglyMessagingServiceTest {

	private val context: Context = RuntimeEnvironment.getApplication()
	private val notifications = context.getSystemService(NotificationManager::class.java)

	private fun message(vararg data: Pair<String, String>): RemoteMessage =
		RemoteMessage.Builder("songly@fcm").setData(mapOf(*data)).build()

	@Test
	fun dataMessageBecomesNotificationThatOpensTheInbox() {
		shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
		val service = Robolectric.setupService(SonglyMessagingService::class.java)

		service.onMessageReceived(
			message("title" to "Ola", "body" to "Polubienie Twojego posta", "notificationId" to "n-1", "type" to "POST_LIKED"),
		)

		val posted = shadowOf(notifications).allNotifications.single()
		val shadow = shadowOf(posted)
		assertEquals("Ola", shadow.contentTitle)
		assertEquals("Polubienie Twojego posta", shadow.contentText)
		assertEquals(PushRegistration.CHANNEL_ID, posted.channelId)
		val tapIntent = shadowOf(posted.contentIntent).savedIntent
		assertEquals(MainActivity.ACTION_OPEN_NOTIFICATIONS, tapIntent.action)
	}

	@Test
	fun withoutPermissionNothingIsPosted() {
		shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
		val service = Robolectric.setupService(SonglyMessagingService::class.java)

		service.onMessageReceived(message("title" to "Ola", "body" to "x", "notificationId" to "n-2"))

		assertTrue(shadowOf(notifications).allNotifications.isEmpty())
	}

	@Test
	fun messageWithoutTextIsIgnored() {
		shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
		val service = Robolectric.setupService(SonglyMessagingService::class.java)

		service.onMessageReceived(message("type" to "POST_LIKED"))

		assertTrue(shadowOf(notifications).allNotifications.isEmpty())
	}
}
