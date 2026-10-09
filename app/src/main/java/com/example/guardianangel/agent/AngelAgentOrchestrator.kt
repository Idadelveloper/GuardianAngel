package com.example.guardianangel.agent

import android.util.Log
import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.agents.ParallelAgent
import com.google.adk.kt.events.Event
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.example.guardianangel.audio.AudioEvent
import com.example.guardianangel.audio.TranscriptChunk
import com.example.guardianangel.data.crime.CrimeDataService
import com.example.guardianangel.domain.model.Codeword
import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.model.GeoPoint
import com.example.guardianangel.domain.repository.GuardianRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

private const val TAG = "AngelOrchestrator"

/**
 * The Central Multi-Agent Orchestrator for Guardian Angel powered by the Google ADK Kotlin SDK.
 *
 * Coordinates domain agents in parallel using [ParallelAgent], updates factor scores in real time,
 * produces composite safety scores, routes codeword actions, and maintains
 * the forensic evidence log.
 */
class AngelAgentOrchestrator(
    private val crimeDataService: CrimeDataService,
    private val scope: CoroutineScope,
) {
    val locationAgent = LocationCrimeAgent(crimeDataService)
    val weatherAgent = WeatherTimeAgent()
    val crowdAgent = CrowdActivityAgent()
    val voiceAgent = VoiceToneAgent()
    val acousticAgent = AcousticEnvironmentAgent()
    val transcriptAgent = TranscriptSemanticAgent()
    val amberAgent = AmberEscalationAgent()
    val evidenceAgent = BreadcrumbEvidenceAgent()

    val codewordAgents = mapOf<CodewordTier, CodewordActionAgent>(
        CodewordTier.Safe to SafeActionAgent(),
        CodewordTier.Caution to CautionActionAgent(),
        CodewordTier.Danger to DangerActionAgent(),
        CodewordTier.Emergency to EmergencyActionAgent(),
    )

    /**
     * Google ADK Kotlin ParallelAgent orchestrating the multi-factor perceptual evaluation workflow.
     */
    val parallelPerceptionWorkflow = ParallelAgent(
        name = "AngelPerceptionWorkflow",
        description = "Runs all perceptual, acoustic, and environmental safety agents concurrently in parallel",
        subAgents = listOf(
            locationAgent,
            weatherAgent,
            crowdAgent,
            voiceAgent,
            acousticAgent,
            transcriptAgent,
        ),
    )

    private val _state = MutableStateFlow(AngelPerceptionState())
    val state: StateFlow<AngelPerceptionState> = _state.asStateFlow()

    private var currentLocation: GeoPoint? = null
    private var currentAddress: String? = null
    private var isVerifiedUser: Boolean = true
    private var hasUnknownVoice: Boolean = false
    private var currentSpeakerCount: Int = 1
    private var lastDecibels: Int? = null
    private var recentTranscript = mutableListOf<TranscriptChunk>()
    private var recentAudioEvents = mutableListOf<AudioEvent>()

    fun setHyperAware(active: Boolean) {
        _state.update { it.copy(isHyperAware = active) }
    }

    fun onLocationUpdate(point: GeoPoint, address: String?) {
        currentLocation = point
        currentAddress = address
        recalculateFactors()
    }

    fun onSpeakerUpdate(verifiedUser: Boolean, unknownVoice: Boolean, count: Int) {
        isVerifiedUser = verifiedUser
        hasUnknownVoice = unknownVoice
        currentSpeakerCount = count
        recalculateFactors()
    }

    fun onAudioEvents(events: List<AudioEvent>, peakDb: Int?) {
        lastDecibels = peakDb
        recentAudioEvents.addAll(events)
        if (recentAudioEvents.size > 20) {
            recentAudioEvents = recentAudioEvents.takeLast(20).toMutableList()
        }
        recalculateFactors()
    }

    fun onTranscriptReceived(
        chunk: TranscriptChunk,
        guardianRepository: GuardianRepository,
        codewords: List<Codeword>,
    ) {
        recentTranscript.add(chunk)
        if (recentTranscript.size > 25) {
            recentTranscript = recentTranscript.takeLast(25).toMutableList()
        }

        // Run semantic analysis and codeword matching
        val semanticResult = transcriptAgent.assess(recentTranscript, codewords)
        val acousticResult = acousticAgent.assess(recentAudioEvents)
        val voiceResult = voiceAgent.assess(
            isVerifiedUser = isVerifiedUser,
            hasUnknownVoice = hasUnknownVoice,
            speakerCount = currentSpeakerCount,
            peakDecibels = lastDecibels,
        )

        // If a registered codeword was spoken, invoke the specialized agent for that codeword
        semanticResult.matchedCodeword?.let { tier ->
            scope.launch(Dispatchers.Default) {
                Log.i(TAG, "Triggering codeword action agent for tier: $tier")
                codewordAgents[tier]?.executeAction(guardianRepository, semanticResult.rationale)
            }
        }

        // Recalculate all factors and composite score
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val crimeScore = locationAgent.assess(currentLocation, hour)
        val weatherScore = weatherAgent.assess(hour)
        val havensCount = currentLocation?.let { crimeDataService.getNearbySafeHavens(it).size } ?: 1
        val crowdScore = crowdAgent.assess(hour, havensCount)

        val updatedFactors = FactorScores(
            crimeScore = crimeScore,
            weatherTimeScore = weatherScore,
            crowdScore = crowdScore,
            voiceToneScore = voiceResult.score,
            acousticScore = acousticResult.score,
            verbalScore = semanticResult.score,
        )

        val compositeScore = updatedFactors.compositeSafetyScore

        // Check Amber Alarm escalation
        val amberStatus = amberAgent.evaluate(
            compositeScore = compositeScore,
            matchedCodeword = semanticResult.matchedCodeword,
            dangerEvents = acousticResult.detectedDangerEvents,
            isVerbalDistress = semanticResult.isVerbalDistress,
        )

        // Create timestamped forensic evidence entry
        val speakerLabel = if (isVerifiedUser) "You" else "Unfamiliar Speaker"
        val dangerSounds = acousticResult.detectedDangerEvents.map { "${it.label} (${(it.confidence * 100).toInt()}%)" }
        val entry = evidenceAgent.createEntry(
            location = currentLocation,
            address = currentAddress,
            speaker = speakerLabel,
            isVerifiedUser = isVerifiedUser,
            transcriptText = chunk.text,
            toneAnalysis = voiceResult.toneDescription,
            audioEvents = dangerSounds,
            decibels = lastDecibels,
            isDistressSignal = semanticResult.isVerbalDistress || voiceResult.isDistressDetected,
            instantSafetyScore = compositeScore,
        )

        val activeDecisionsList = mutableListOf<String>()
        if (semanticResult.matchedCodeword != null) {
            activeDecisionsList.add("Executed codeword: ${semanticResult.matchedCodeword.name}")
        }
        if (amberStatus.isTriggered) {
            activeDecisionsList.add("Amber Escalate Alert Active: ${amberStatus.rationale}")
        }
        activeDecisionsList.add(semanticResult.rationale)
        activeDecisionsList.add(locationAgent.describeContext(currentLocation, hour))

        _state.update { current ->
            current.copy(
                factors = updatedFactors,
                matchedCodeword = semanticResult.matchedCodeword,
                isAmberAlertActive = current.isAmberAlertActive || amberStatus.isTriggered,
                evidenceLog = (current.evidenceLog + entry).takeLast(40),
                activeDecisions = activeDecisionsList,
                lastAssessmentSummary = semanticResult.rationale,
            )
        }
    }

    private fun recalculateFactors() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val crimeScore = locationAgent.assess(currentLocation, hour)
        val weatherScore = weatherAgent.assess(hour)
        val havensCount = currentLocation?.let { crimeDataService.getNearbySafeHavens(it).size } ?: 1
        val crowdScore = crowdAgent.assess(hour, havensCount)
        val acousticResult = acousticAgent.assess(recentAudioEvents)
        val voiceResult = voiceAgent.assess(
            isVerifiedUser = isVerifiedUser,
            hasUnknownVoice = hasUnknownVoice,
            speakerCount = currentSpeakerCount,
            peakDecibels = lastDecibels,
        )
        val currentVerbal = _state.value.factors.verbalScore

        val updatedFactors = FactorScores(
            crimeScore = crimeScore,
            weatherTimeScore = weatherScore,
            crowdScore = crowdScore,
            voiceToneScore = voiceResult.score,
            acousticScore = acousticResult.score,
            verbalScore = currentVerbal,
        )

        _state.update { it.copy(factors = updatedFactors) }
    }

    /**
     * Executes the Google ADK ParallelAgent perception workflow asynchronously, returning emitted Events.
     */
    @OptIn(kotlin.time.ExperimentalTime::class)
    suspend fun executeParallelPerception(): List<Event> {
        val sessionKey = SessionKey(appName = "GuardianAngel", userId = "local_user", id = "session-${System.currentTimeMillis()}")
        val context = InvocationContext(
            session = Session(key = sessionKey),
            agent = parallelPerceptionWorkflow,
        )
        val events = mutableListOf<Event>()
        parallelPerceptionWorkflow.runAsync(context).collect { event ->
            events.add(event)
        }
        return events
    }

    fun dismissAmberAlert() {
        _state.update { it.copy(isAmberAlertActive = false) }
    }
}
