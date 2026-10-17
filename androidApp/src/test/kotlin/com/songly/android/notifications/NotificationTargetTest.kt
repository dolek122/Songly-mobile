package com.songly.android.notifications

import com.songly.shared.model.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `resourceId` znaczy co innego zależnie od typu (backend NotificationEventListener)
 * — pomyłka otworzyłaby ekran posta z identyfikatorem społeczności, czyli 404.
 */
class NotificationTargetTest {

	private fun notification(type: String) = Notification(
		id = "n1",
		type = type,
		actorId = "a1",
		actorDisplayName = "Ola",
		resourceId = "r1",
		createdAt = "2026-01-01T10:00:00Z",
	)

	@Test
	fun postNotificationsOpenPost() {
		listOf("POST_LIKED", "POST_COMMENTED", "POST_SHARED", "COMMUNITY_POST_REPORTED").forEach {
			assertEquals(it, NotificationTarget.Post("r1"), targetOf(notification(it)))
		}
	}

	@Test
	fun moderationNotificationsOpenCommunity() {
		listOf(
			"COMMUNITY_BANNED", "COMMUNITY_POST_REMOVED", "BAN_APPEAL_SUBMITTED", "BAN_APPEAL_REJECTED",
			"BAN_ESCALATION_REJECTED", "COMMUNITY_MODERATOR_GRANTED",
		).forEach {
			assertEquals(it, NotificationTarget.Community("r1"), targetOf(notification(it)))
		}
	}

	@Test
	fun friendRequestsHaveNoTarget() {
		assertNull(targetOf(notification("FRIEND_REQUEST_RECEIVED")))
	}

	/** Decyzję moderacyjną podpisuje funkcja, nie osoba (ADR 0015). */
	@Test
	fun banNotificationDoesNotNameModerator() {
		assertEquals("Moderacja zbanowała Cię w społeczności", notificationText(notification("COMMUNITY_BANNED")))
	}
}
