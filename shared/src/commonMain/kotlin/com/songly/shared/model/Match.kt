package com.songly.shared.model

import kotlinx.serialization.Serializable

/**
 * Dopasowanie po gatunkach (backend: matching.dto.MatchResponse).
 *
 * [score] to współczynnik Jaccarda w zakresie 0..1 (część wspólna gatunków /
 * suma unikalnych — Claude.md §8). [sharedGenreIds] to id wspólnych gatunków;
 * nazwy UI dokłada, mapując je po liście wszystkich gatunków.
 */
@Serializable
data class Match(
	val userId: String,
	val displayName: String,
	val avatarUrl: String? = null,
	val score: Double,
	val sharedGenreIds: List<String> = emptyList(),
) {
	/** Dopasowanie jako procent 0..100 (do wyświetlenia). */
	val scorePercent: Int
		get() = (score * 100).toInt()
}
