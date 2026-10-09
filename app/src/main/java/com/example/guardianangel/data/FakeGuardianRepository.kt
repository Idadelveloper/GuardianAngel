package com.example.guardianangel.data

import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.model.EmergencyContact
import com.example.guardianangel.domain.model.GuardianMode
import com.example.guardianangel.domain.model.GuardianSnapshot
import com.example.guardianangel.domain.model.RecordingSession
import com.example.guardianangel.domain.repository.GuardianRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory stand-in for the real data layer.
 *
 * Exists so the home screen can be built, previewed and demoed before the database and
 * the speech pipeline land. It holds the same [GuardianSnapshot] shape the real source
 * will emit and mutates it in response to the same calls, so swapping it out should be a
 * one-line change in `GuardianAngelApp`.
 *
 * Everything here is sample content — no real numbers, no real people.
 */
class FakeGuardianRepository : GuardianRepository {

    private val state = MutableStateFlow(initialSnapshot())

    override fun observeSnapshot(): Flow<GuardianSnapshot> = state.asStateFlow()

    override suspend fun armGuardian() {
        // Arming is simulated as "heading out": the user has left her safe haven, so the
        // score moves off its anchored 100 and the away-from-base factors kick in. The
        // real implementation will get this from location rather than inferring it.
        state.update {
            it.copy(
                mode = GuardianMode.Listening,
                safetyScore = GuardianSamples.movingSafetyScore,
                safeHavenLabel = null,
                journey = GuardianSamples.journey,
            )
        }
    }

    override suspend fun disarmGuardian() {
        state.update {
            it.copy(
                mode = GuardianMode.Standby,
                activeSession = null,
                safetyScore = GuardianSamples.atHomeSafetyScore,
                safeHavenLabel = "home",
                journey = null,
            )
        }
    }

    override suspend fun startRecording(trigger: CodewordTier?) {
        state.update {
            it.copy(
                mode = GuardianMode.Recording,
                activeSession = RecordingSession(
                    id = "session-${System.currentTimeMillis()}",
                    startedAtEpochMillis = System.currentTimeMillis(),
                    triggeredBy = trigger,
                    transcriptPreview = emptyList(),
                    contactsNotified = when (trigger) {
                        CodewordTier.Danger, CodewordTier.Emergency ->
                            it.contacts.map(EmergencyContact::id)
                        else -> emptyList()
                    },
                    isPoliceDispatched = trigger == CodewordTier.Emergency,
                ),
            )
        }
    }

    override suspend fun stopRecording() {
        state.update { it.copy(mode = GuardianMode.Listening, activeSession = null) }
    }

    override suspend fun dispatchAlert(tier: CodewordTier) {
        state.update {
            val session = it.activeSession ?: RecordingSession(
                id = "session-${System.currentTimeMillis()}",
                startedAtEpochMillis = System.currentTimeMillis(),
                triggeredBy = tier,
                transcriptPreview = emptyList(),
            )
            it.copy(
                mode = GuardianMode.Recording,
                activeSession = session.copy(
                    triggeredBy = tier,
                    contactsNotified = it.contacts.map(EmergencyContact::id),
                    isPoliceDispatched = tier == CodewordTier.Emergency,
                ),
            )
        }
    }

    override suspend fun updateTranscript(line: com.example.guardianangel.domain.model.TranscriptLine) {
        state.update { current ->
            val session = current.activeSession ?: return@update current
            current.copy(
                activeSession = session.copy(
                    transcriptPreview = (session.transcriptPreview + line).takeLast(30)
                )
            )
        }
    }

    override suspend fun updateSafetyScore(score: com.example.guardianangel.domain.model.SafetyScore) {
        state.update { current ->
            current.copy(safetyScore = score)
        }
    }

    override suspend fun rescanArea() {
        state.update { it.copy(safetyScore = it.safetyScore.copy(isLiveScanning = true)) }
        delay(RESCAN_MILLIS)
        state.update {
            it.copy(safetyScore = it.safetyScore.copy(isLiveScanning = false))
        }
    }

    private companion object {
        const val RESCAN_MILLIS = 1_600L

        fun initialSnapshot() = GuardianSamples.snapshot(GuardianMode.Standby)
    }
}
