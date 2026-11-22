package com.songly.shared.presence

import com.songly.shared.model.FriendNowPlaying

data class FriendsPresence(
	val friends: List<FriendNowPlaying> = emptyList(),
	val live: Boolean = false,
	val problem: PresenceProblem? = null,
)

enum class PresenceProblem {
	CONNECTION,

	TOO_MANY_STREAMS,
}

fun presenceProblemFor(statusCode: Int?): PresenceProblem =
	if (statusCode == HTTP_TOO_MANY_REQUESTS) PresenceProblem.TOO_MANY_STREAMS else PresenceProblem.CONNECTION

private const val HTTP_TOO_MANY_REQUESTS = 429

fun List<FriendNowPlaying>.applyPresenceChange(change: FriendNowPlaying): List<FriendNowPlaying> {
	val withoutSubject = filterNot { it.userId == change.userId }
	if (change.status == null) {
		return withoutSubject
	}
	return (withoutSubject + change).sortedWith(
		compareBy(nullsLast<String>()) { it.displayName?.lowercase() },
	)
}

fun reconnectDelayMillis(attempt: Int): Long {
	val capped = attempt.coerceIn(0, MAX_BACKOFF_SHIFT)
	return (INITIAL_RECONNECT_DELAY_MILLIS shl capped).coerceAtMost(MAX_RECONNECT_DELAY_MILLIS)
}

private const val INITIAL_RECONNECT_DELAY_MILLIS = 1_000L
private const val MAX_RECONNECT_DELAY_MILLIS = 30_000L
private const val MAX_BACKOFF_SHIFT = 5
