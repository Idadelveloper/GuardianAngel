package com.example.guardianangel.agent

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.events.Event
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.example.guardianangel.domain.model.GeoPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Google ADK Kotlin Agent responsible for generating immutable, forensic evidence log entries during active recording.
 */
class BreadcrumbEvidenceAgent : BaseAgent(
    name = "BreadcrumbEvidenceAgent",
    description = "Generates immutable, timestamped forensic evidence logs during active surveillance",
) {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var lastEntry: EvidenceLogEntry? = null

    fun createEntry(
        location: GeoPoint?,
        address: String?,
        speaker: String,
        isVerifiedUser: Boolean,
        transcriptText: String,
        toneAnalysis: String,
        audioEvents: List<String>,
        decibels: Int?,
        isDistressSignal: Boolean,
        instantSafetyScore: Int,
    ): EvidenceLogEntry {
        val now = System.currentTimeMillis()
        val entry = EvidenceLogEntry(
            id = UUID.randomUUID().toString(),
            timestampMillis = now,
            formattedTime = timeFormat.format(Date(now)),
            location = location,
            address = address,
            speaker = speaker,
            isVerifiedUser = isVerifiedUser,
            transcriptText = transcriptText,
            toneAnalysis = toneAnalysis,
            audioEvents = audioEvents,
            decibels = decibels,
            isDistressSignal = isDistressSignal,
            instantSafetyScore = instantSafetyScore,
        )
        lastEntry = entry
        return entry
    }

    override fun runAsyncImpl(context: InvocationContext): Flow<Event> = flow {
        val entry = lastEntry
        emit(
            Event(
                invocationId = context.invocationId,
                author = name,
                content = Content(
                    role = Role.MODEL,
                    parts = listOf(Part(text = "ForensicEntry: ${entry?.transcriptText} at ${entry?.formattedTime}"))
                ),
                output = entry,
            )
        )
    }
}
