package com.songly.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Match(
	val userId: String,
	val displayName: String,
	val avatarUrl: String? = null,
	val score: Double,
	val sharedGenreIds: List<String> = emptyList(),
	val confidence: Double = 0.0,
) {
	val scorePercent: Int
		get() = (score * 100).toInt()

	val confidenceLevel: MatchConfidence
		get() = when {
			confidence < 0.5 -> MatchConfidence.LOW
			confidence < 0.75 -> MatchConfidence.MEDIUM
			else -> MatchConfidence.HIGH
		}
}

enum class MatchConfidence { LOW, MEDIUM, HIGH }
