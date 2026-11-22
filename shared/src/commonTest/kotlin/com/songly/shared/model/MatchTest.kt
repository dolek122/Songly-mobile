package com.songly.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals

class MatchTest {

	private fun match(confidence: Double) = Match(userId = "u", displayName = "Ola", score = 0.4, confidence = confidence)

	@Test
	fun confidenceMapsToThreeLevels() {
		assertEquals(MatchConfidence.LOW, match(0.35).confidenceLevel)
		assertEquals(MatchConfidence.MEDIUM, match(0.6).confidenceLevel)
		assertEquals(MatchConfidence.HIGH, match(0.9).confidenceLevel)
	}
}
