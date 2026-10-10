package com.example.guardianangel.data.local

import android.content.Context
import com.example.guardianangel.domain.model.DiarizedEntry
import com.example.guardianangel.domain.model.MonitoredSession
import com.example.guardianangel.domain.model.SpeakerKind
import com.example.guardianangel.domain.repository.LocationBreadcrumb
import com.example.guardianangel.domain.repository.TranscriptExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes a session as a plain-text record.
 *
 * ## Why text and not PDF
 *
 * The previous implementation returned the string `"guardian-angel-<id>.pdf"` after a
 * delay and wrote nothing. A PDF needs a rendering library and produces a file that is
 * harder to read, harder to search, and harder to verify than the thing it describes.
 * Plain text opens anywhere, survives being pasted into an email, and nothing about it
 * can quietly fail.
 *
 * ## What it says, and what it does not
 *
 * The header states plainly that times are device-local, that the transcript is
 * machine-generated, and that speaker labels are a model's best guess. Anyone relying on
 * this as evidence needs to know its provenance, and a file that reads as more certain
 * than it is would be worse than no file.
 *
 * Written to `getExternalFilesDir("exports")` — app-scoped, so no storage permission is
 * needed on any supported version, and it survives the app being uninstalled long enough
 * to be copied off.
 */
class FileTranscriptExporter(private val context: Context) : TranscriptExporter {

    override suspend fun export(
        session: MonitoredSession,
        entries: List<DiarizedEntry>,
        trail: List<LocationBreadcrumb>,
    ): String = withContext(Dispatchers.IO) {
        val directory = File(
            context.getExternalFilesDir(null) ?: context.filesDir,
            "exports",
        ).apply { mkdirs() }

        val stamp = FILE_STAMP.format(Date(session.startedAtEpochMillis))
        val file = File(directory, "guardian-angel-$stamp.txt")
        file.writeText(render(session, entries, trail))
        file.absolutePath
    }

    private fun render(
        session: MonitoredSession,
        entries: List<DiarizedEntry>,
        trail: List<LocationBreadcrumb>,
    ): String = buildString {
        appendLine("GUARDIAN ANGEL — SESSION RECORD")
        appendLine("=" .repeat(62))
        appendLine()
        appendLine("Session:      ${session.title}")
        appendLine("Type:         ${session.kind.name}")
        appendLine("Started:      ${TIMESTAMP.format(Date(session.startedAtEpochMillis))}")
        appendLine(
            "Ended:        " + (session.endedAtEpochMillis
                ?.let { TIMESTAMP.format(Date(it)) }
                ?: "not recorded — the session did not end cleanly")
        )
        session.durationMinutes?.let { appendLine("Duration:     $it min") }
        appendLine("Location:     ${session.locationLabel}")
        session.peakDecibels?.let { appendLine("Peak level:   $it dB (uncalibrated)") }
        session.lowestSafetyScore?.let { appendLine("Lowest score: $it/100") }
        if (session.guardiansNotified.isNotEmpty()) {
            appendLine("Guardians alerted: ${session.guardiansNotified.joinToString(", ")}")
        }
        appendLine()
        appendLine("ABOUT THIS RECORD")
        appendLine("-".repeat(62))
        appendLine(PROVENANCE.trimIndent())
        appendLine()
        appendLine("TIMELINE")
        appendLine("-".repeat(62))

        if (entries.isEmpty()) {
            appendLine("(nothing was transcribed during this session)")
        } else {
            entries.forEach { entry ->
                val time = CLOCK.format(Date(entry.atEpochMillis))
                val flag = if (entry.isFlagged) "  [flagged]" else ""
                if (entry.speakerKind == SpeakerKind.SoundEvent) {
                    appendLine("[$time] (sound) ${entry.text}$flag")
                } else {
                    val qualifier = entry.speakerQualifier?.let { " ($it)" }.orEmpty()
                    appendLine("[$time] ${entry.speakerLabel}$qualifier: ${entry.text}$flag")
                }
            }
        }

        if (trail.isNotEmpty()) {
            appendLine()
            appendLine("LOCATION TRAIL")
            appendLine("-".repeat(62))
            trail.forEach { point ->
                val time = CLOCK.format(Date(point.atMillis))
                val place = point.placeLabel?.let { " — $it" }.orEmpty()
                val score = point.safetyScore?.let { " (score $it)" }.orEmpty()
                appendLine(
                    "[$time] %.5f, %.5f%s%s".format(
                        point.latitude,
                        point.longitude,
                        place,
                        score,
                    )
                )
            }
        }

        appendLine()
        appendLine("=" .repeat(62))
        appendLine("Exported ${TIMESTAMP.format(Date())} from this device.")
    }

    private companion object {
        val TIMESTAMP = SimpleDateFormat("EEE d MMM yyyy, HH:mm:ss z", Locale.getDefault())
        val CLOCK = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val FILE_STAMP = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US)

        /**
         * Stated up front rather than in a footnote.
         *
         * Someone reading this to decide whether to act on it needs to know how it was
         * made. Overstating it would be the more dangerous mistake.
         */
        const val PROVENANCE = """
            Times are this device's local clock. Speech was transcribed on the device by
            an automatic model and has not been reviewed by a person: wording may be
            wrong, and quiet or overlapping speech may be missing entirely. Speaker
            labels are the model's best guess from voice characteristics, not an
            identification. "Flagged" means the app's own heuristics treated a line as a
            possible danger signal; it is not a finding of fact. Sounds in brackets are
            automatic classifications of non-speech audio.
        """
    }
}
