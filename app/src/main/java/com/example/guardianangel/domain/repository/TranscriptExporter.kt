package com.example.guardianangel.domain.repository

import com.example.guardianangel.domain.model.DiarizedEntry
import com.example.guardianangel.domain.model.MonitoredSession

/**
 * Writes a session out as a file the user can keep or hand to someone.
 *
 * A domain interface because the *format* is a product decision, not a storage detail:
 * this file may end up being read by a friend, a lawyer or a police officer, and what it
 * contains — and what it deliberately leaves out — matters more than where it is written.
 */
interface TranscriptExporter {

    /**
     * Writes the session and returns a user-facing description of where it went.
     *
     * @param trail breadcrumbs, used to say where each part of the conversation happened.
     *   Location is matched to lines by timestamp rather than stored per line.
     */
    suspend fun export(
        session: MonitoredSession,
        entries: List<DiarizedEntry>,
        trail: List<LocationBreadcrumb>,
    ): String
}

/** One point on a session's path. Mirrors the stored row without exposing Room. */
data class LocationBreadcrumb(
    val atMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val placeLabel: String?,
    val safetyScore: Int?,
)
