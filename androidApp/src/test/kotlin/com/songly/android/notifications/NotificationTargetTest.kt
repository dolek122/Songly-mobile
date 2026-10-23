package com.songly.android.notifications

import com.songly.shared.model.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
		listOf("POST_LIKED", "POST_COMMENTED", "POST_SHARED", "COMMUNITY_POST_REPORTED", "COMMUNITY_COMMENT_REMOVED").forEach {
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
	fun receivedFriendRequestOpensFriends() {
		assertEquals(NotificationTarget.Friends, targetOf(notification("FRIEND_REQUEST_RECEIVED")))
		assertNull(targetOf(notification("FRIEND_REQUEST_ACCEPTED")))
	}

	/** Decyzję moderacyjną podpisuje funkcja, nie osoba (ADR 0015). */
	@Test
	fun banNotificationDoesNotNameModerator() {
		assertEquals("Moderacja zbanowała Cię w społeczności", notificationText(notification("COMMUNITY_BANNED")))
	}

	/**
	 * Nowy typ z backendu (ADR 0045) — bez własnego tekstu trafiłby do ogólnego
	 * „Ktoś ma dla Ciebie powiadomienie”.
	 */
	@Test
	fun removedCommentHasItsOwnText() {
		assertEquals(
			"Moderacja usunęła Twój komentarz w społeczności",
			notificationText(notification("COMMUNITY_COMMENT_REMOVED")),
		)
	}

	/**
	 * Przy decyzjach karta nie pokazuje awatara ani nie prowadzi do profilu
	 * aktora — `actorId` wskazałby moderatora. Zmiana roli to zwykłe powiadomienie
	 * od właściciela i zostaje podpisana (ADR 0016).
	 */
	@Test
	fun moderationDecisionsHideTheModerator() {
		listOf(
			"COMMUNITY_BANNED", "COMMUNITY_POST_REMOVED", "COMMUNITY_COMMENT_REMOVED", "POST_REPORT_DISMISSED",
			"BAN_APPEAL_ACCEPTED", "BAN_APPEAL_REJECTED", "BAN_ESCALATION_ACCEPTED", "BAN_ESCALATION_REJECTED",
		).forEach { assertTrue(it, isModerationDecision(notification(it))) }
		listOf("POST_LIKED", "COMMUNITY_MODERATOR_GRANTED", "COMMUNITY_POST_REPORTED", "BAN_APPEAL_SUBMITTED")
			.forEach { assertFalse(it, isModerationDecision(notification(it))) }
	}
}
