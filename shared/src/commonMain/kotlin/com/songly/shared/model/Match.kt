package com.songly.shared.model

import kotlinx.serialization.Serializable

/**
 * Dopasowanie po gatunkach (backend: matching.dto.MatchResponse).
 *
 * [score] to ważony współczynnik Jaccarda w zakresie (0, 1): wspólne gatunki
 * przez wszystkie, rzadkie ważą więcej, a mało danych przygasza wynik (backend
 * §8, ADR 0029 i 0033 — stąd 100% jest nieosiągalne). [sharedGenreIds] to id
 * wspólnych gatunków; nazwy UI dokłada, mapując je po liście wszystkich gatunków.
 */
@Serializable
data class Match(
	val userId: String,
	val displayName: String,
	val avatarUrl: String? = null,
	val score: Double,
	val sharedGenreIds: List<String> = emptyList(),
	// Ile wiemy o guście obu stron, (0, 1) — niski [score] przy niskiej pewności to
	// „za mało danych", nie „nie pasujecie" (backend ADR 0033).
	val confidence: Double = 0.0,
) {
	/** Dopasowanie jako procent 0..100 (do wyświetlenia). */
	val scorePercent: Int
		get() = (score * 100).toInt()

	/**
	 * Pewność w trzech stopniach, bo sama liczba (0.42) nic człowiekowi nie mówi.
	 * Progi są po stronie `shared`, żeby iOS opisywał ten sam wynik tymi samymi słowami.
	 */
	val confidenceLevel: MatchConfidence
		get() = when {
			confidence < 0.5 -> MatchConfidence.LOW
			confidence < 0.75 -> MatchConfidence.MEDIUM
			else -> MatchConfidence.HIGH
		}
}

/** Stopień pewności dopasowania — patrz [Match.confidenceLevel]. */
enum class MatchConfidence { LOW, MEDIUM, HIGH }
