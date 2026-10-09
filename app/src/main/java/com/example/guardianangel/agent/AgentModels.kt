package com.example.guardianangel.agent

import com.example.guardianangel.audio.AudioEvent
import com.example.guardianangel.audio.TranscriptChunk
import com.example.guardianangel.domain.model.Codeword
import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.model.GeoPoint

/**
 * Parallel Factor Scores computed by dedicated domain agents.
 * The composite safety score is the real-time average of these active factors.
 */
data class FactorScores(
    val crimeScore: Int = 90,
    val weatherTimeScore: Int = 85,
    val crowdScore: Int = 80,
    val voiceToneScore: Int = 95,
    val acousticScore: Int = 95,
    val verbalScore: Int = 95,
) {
    /** The overall real-time safety score: the average of all parallel agent factor scores. */
    val compositeSafetyScore: Int
        get() = ((crimeScore + weatherTimeScore + crowdScore + voiceToneScore + acousticScore + verbalScore) / 6)
            .coerceIn(0, 100)
}

/**
 * A tamper-evident, timestamped entry in the evidence trail.
 */
data class EvidenceLogEntry(
    val id: String,
    val timestampMillis: Long,
    val formattedTime: String,
    val location: GeoPoint?,
    val address: String?,
    val speaker: String,
    val isVerifiedUser: Boolean,
    val transcriptText: String,
    val toneAnalysis: String,
    val audioEvents: List<String>,
    val decibels: Int?,
    val isDistressSignal: Boolean,
    val instantSafetyScore: Int,
)

/**
 * Multi-agent state surfaced by Angel to the UI and listening session.
 */
data class AngelPerceptionState(
    val isHyperAware: Boolean = false,
    val factors: FactorScores = FactorScores(),
    val activeDecisions: List<String> = emptyList(),
    val evidenceLog: List<EvidenceLogEntry> = emptyList(),
    val matchedCodeword: CodewordTier? = null,
    val isAmberAlertActive: Boolean = false,
    val lastAssessmentSummary: String = "Angel is active and monitoring surroundings.",
)
