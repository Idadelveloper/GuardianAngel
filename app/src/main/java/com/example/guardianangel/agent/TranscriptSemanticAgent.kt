package com.example.guardianangel.agent

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.events.Event
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.example.guardianangel.audio.TranscriptChunk
import com.example.guardianangel.domain.model.Codeword
import com.example.guardianangel.domain.model.CodewordTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.min

/**
 * Google ADK Kotlin Agent responsible for real-time natural language interpretation, distress detection,
 * and Codeword recognition.
 */
class TranscriptSemanticAgent : BaseAgent(
    name = "TranscriptSemanticAgent",
    description = "Performs natural language interpretation, distress detection, and codeword recognition",
) {

    data class SemanticResult(
        val score: Int,
        val matchedCodeword: CodewordTier?,
        val isVerbalDistress: Boolean,
        val rationale: String,
    )

    private var lastResult = SemanticResult(
        score = 95,
        matchedCodeword = null,
        isVerbalDistress = false,
        rationale = "No speech detected",
    )

    fun assess(
        chunks: List<TranscriptChunk>,
        codewords: List<Codeword>,
    ): SemanticResult {
        if (chunks.isEmpty()) {
            val emptyRes = SemanticResult(
                score = 95,
                matchedCodeword = null,
                isVerbalDistress = false,
                rationale = "No speech detected",
            )
            lastResult = emptyRes
            return emptyRes
        }

        val fullText = chunks.joinToString(" ") { it.text }.lowercase()

        // 1. Check for registered Codewords (priority order: Emergency -> Danger -> Caution -> Safe)
        var detectedCodeword: CodewordTier? = null
        for (tier in listOf(CodewordTier.Emergency, CodewordTier.Danger, CodewordTier.Caution, CodewordTier.Safe)) {
            val registered = codewords.firstOrNull { it.tier == tier }
            if (registered != null && registered.phrase.isNotBlank()) {
                val phrase = registered.phrase.trim().lowercase()
                if (Regex("\\b${Regex.escape(phrase)}\\b").containsMatchIn(fullText)) {
                    detectedCodeword = tier
                    break
                }
            }
        }

        // 2. Analyze distress markers
        var score = 95
        var isDistress = false

        val refusalCount = REFUSAL_PATTERNS.sumOf { pattern ->
            Regex("\\b${Regex.escape(pattern)}\\b").findAll(fullText).count()
        }
        if (refusalCount > 0) {
            score -= min(40, refusalCount * 15)
            isDistress = true
        }

        val distressMatches = DISTRESS_PHRASES.filter { it in fullText }
        if (distressMatches.isNotEmpty()) {
            score -= 45
            isDistress = true
        }

        // Codeword directly influences the verbal score
        when (detectedCodeword) {
            CodewordTier.Emergency -> score = 10
            CodewordTier.Danger -> score = 25
            CodewordTier.Caution -> score = 55
            CodewordTier.Safe -> score = 98
            null -> Unit
        }

        val rationale = when {
            detectedCodeword != null -> "Spoken codeword matched: ${detectedCodeword.name}"
            distressMatches.isNotEmpty() -> "Explicit distress plea detected in transcript"
            refusalCount >= 2 -> "Repeated refusal phrases ($refusalCount times)"
            refusalCount == 1 -> "Verbal refusal detected"
            else -> "Speech is calm and non-confrontational"
        }

        val result = SemanticResult(
            score = score.coerceIn(5, 100),
            matchedCodeword = detectedCodeword,
            isVerbalDistress = isDistress,
            rationale = rationale,
        )
        lastResult = result
        return result
    }

    override fun runAsyncImpl(context: InvocationContext): Flow<Event> = flow {
        emit(
            Event(
                invocationId = context.invocationId,
                author = name,
                content = Content(
                    role = Role.MODEL,
                    parts = listOf(Part(text = "SemanticScore: ${lastResult.score} | Codeword: ${lastResult.matchedCodeword?.name} | Rationale: ${lastResult.rationale}"))
                ),
                output = lastResult,
            )
        )
    }

    private companion object {
        val REFUSAL_PATTERNS = listOf("no", "stop", "don't", "dont", "leave me alone", "get off")
        val DISTRESS_PHRASES = listOf(
            "help me", "call the police", "let go", "i'm scared", "im scared",
            "go away", "get away from me", "someone help", "call 911"
        )
    }
}
