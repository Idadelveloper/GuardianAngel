package com.example.guardianangel.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.guardianangel.data.ActivitySamples
import com.example.guardianangel.data.GuardianSamples
import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.model.GuardianCapability
import com.example.guardianangel.domain.model.GuardianSetup
import com.example.guardianangel.domain.model.guardianSetup
import com.example.guardianangel.domain.model.GuardianMode
import com.example.guardianangel.domain.model.GuardianSnapshot
import com.example.guardianangel.domain.model.ListeningRequirement
import com.example.guardianangel.domain.model.ListeningStatus
import com.example.guardianangel.domain.model.WakeWord
import com.example.guardianangel.domain.repository.CodewordRepository
import com.example.guardianangel.domain.repository.ContactsRepository
import com.example.guardianangel.domain.repository.PermissionProbe
import com.example.guardianangel.domain.repository.VoiceProfileRepository
import com.example.guardianangel.domain.repository.GuardianRepository
import com.example.guardianangel.domain.repository.ListeningRepository
import com.example.guardianangel.ui.components.GuardianCard
import com.example.guardianangel.ui.components.GuardianOutlinedButton
import com.example.guardianangel.ui.components.GuardianPrimaryButton
import com.example.guardianangel.ui.components.GuardianTabScaffold
import com.example.guardianangel.ui.home.components.ActiveRecordingPanel
import com.example.guardianangel.ui.home.components.AngelHeroCard
import com.example.guardianangel.ui.home.components.DuressTriggerCard
import com.example.guardianangel.ui.home.components.GuardianTopBar
import com.example.guardianangel.ui.home.components.GuardiansStrip
import com.example.guardianangel.ui.home.components.HandsFreeCard
import com.example.guardianangel.ui.home.components.MetricTile
import com.example.guardianangel.ui.home.components.SetupNeededCard
import com.example.guardianangel.ui.home.components.QuickActionRow
import com.example.guardianangel.ui.home.components.RecentActivityCard
import com.example.guardianangel.ui.home.components.SafetyGaugeCard
import com.example.guardianangel.ui.home.components.SectionHeader
import com.example.guardianangel.ui.home.components.TonalPill
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.mascot.AngelMood
import com.example.guardianangel.ui.theme.GuardianAngelTheme
import com.example.guardianangel.ui.theme.GuardianTheme

/**
 * Tab 1 — Home.
 *
 * Two faces of one screen, chosen by whether a guarded walk is under way:
 *
 *  - **Sanctuary** — the user is at a safe haven. Angel rests, the gauge is pinned at
 *    100%, and the only prominent action is starting a walk.
 *  - **Out & about** — a journey is live. Angel turns watchful, the gauge goes live, and
 *    the duress trigger and check-in take over the action area.
 *
 * Recording cuts across both: when a trigger fires, the recording panel expands above
 * everything else and the ordinary controls step back.
 */
@Composable
fun HomeRoute(
    repository: GuardianRepository,
    listeningRepository: ListeningRepository,
    codewordRepository: CodewordRepository,
    contactsRepository: ContactsRepository,
    voiceProfiles: VoiceProfileRepository,
    permissions: PermissionProbe,
    angelOrchestrator: com.example.guardianangel.agent.AngelAgentOrchestrator? = null,
    onOpenSession: (String) -> Unit,
    onPlanRoute: () -> Unit,
    onSetUpWakeWord: () -> Unit,
    onSetUpVoice: () -> Unit,
    onSetUpCodewords: () -> Unit,
    onSetUpGuardians: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(repository))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val handsFree = rememberHandsFreeController(listeningRepository)
    val listeningStatus by listeningRepository.observeStatus()
        .collectAsStateWithLifecycle(initialValue = ListeningStatus())
    val wakeWord by listeningRepository.observeWakeWord()
        .collectAsStateWithLifecycle(initialValue = WakeWord(phrase = ""))

    val angelState by (angelOrchestrator?.state ?: kotlinx.coroutines.flow.MutableStateFlow(com.example.guardianangel.agent.AngelPerceptionState()))
        .collectAsStateWithLifecycle()

    // Deliberately from the repositories that hold real rows, not from the snapshot:
    // the snapshot is still sample content, and a setup card built on samples would
    // reassure the user about things she has not actually set up.
    val codewords by codewordRepository.observeCodewords()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val guardians by contactsRepository.observeContacts()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val voiceProfile by voiceProfiles.observe()
        .collectAsStateWithLifecycle(initialValue = null)

    val setup = guardianSetup(
        hasWakeWord = wakeWord.isEnrolled,
        voiceprintUsable = voiceProfile?.isUsable == true,
        // Read through the probe so a permission granted in onboarding is reflected on
        // the first frame, rather than after a resume.
        hasLocationPermission = permissions.hasLocation(),
        hasMicrophonePermission = permissions.hasMicrophone(),
        // Only phrases she chose. The four seeded suggestions are not setup.
        customisedCodewordCount = codewords.count { it.isCustomised },
        guardianCount = guardians.size,
    )

    HomeScreen(
        state = state,
        listeningStatus = listeningStatus,
        wakeWord = wakeWord,
        isAmberAlertActive = angelState.isAmberAlertActive,
        amberRationale = angelState.activeDecisions.firstOrNull() ?: "Extreme threat corroboration detected",
        onDismissAmber = { angelOrchestrator?.dismissAmberAlert() },
        onAction = viewModel::onAction,
        onOpenSession = onOpenSession,
        onPlanRoute = onPlanRoute,
        onArm = handsFree::arm,
        onDisarm = handsFree::disarm,
        onRecordNow = {
            // Only show recording UI if recording actually started. Calling the action
            // unconditionally put the screen into its recording state while a permission
            // dialog was still up and nothing was being captured — the worst possible
            // thing for this app to be wrong about.
            if (handsFree.recordNow().isStarted) {
                viewModel.onAction(HomeAction.StartRecording(null))
            }
        },
        onStopRecording = {
            handsFree.stopRecording()
            // Stop *and* stand down, in that order. `StopRecording` alone leaves the
            // mode on Listening, so the badge read ACTIVE over a service that had just
            // been stopped — the app claiming to be listening when it was not.
            viewModel.onAction(HomeAction.StopRecording)
            viewModel.onAction(HomeAction.DisarmGuardian)
        },
        setup = setup,
        onFixCapability = { capability ->
            when (capability) {
                GuardianCapability.MicrophoneAccess ->
                    handsFree.resolve(ListeningRequirement.MicrophonePermission)
                GuardianCapability.HandsFree -> onSetUpWakeWord()
                GuardianCapability.VoiceMatch -> onSetUpVoice()
                GuardianCapability.LocationSharing -> handsFree.requestLocation()
                GuardianCapability.CodewordActions -> onSetUpCodewords()
                GuardianCapability.GuardianAlerts -> onSetUpGuardians()
            }
        },
        onFixBlocker = { requirement ->
            when (requirement) {
                ListeningRequirement.WakeWordEnrolled -> onSetUpWakeWord()
                else -> handsFree.resolve(requirement)
            }
        },
        modifier = modifier,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onOpenSession: (String) -> Unit,
    onPlanRoute: () -> Unit,
    modifier: Modifier = Modifier,
    listeningStatus: ListeningStatus = ListeningStatus(),
    wakeWord: WakeWord = WakeWord(phrase = ""),
    isAmberAlertActive: Boolean = false,
    amberRationale: String = "",
    onDismissAmber: () -> Unit = {},
    onArm: () -> Unit = {},
    onDisarm: () -> Unit = {},
    onRecordNow: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    setup: GuardianSetup = GuardianSetup(GuardianCapability.entries.toSet()),
    onFixCapability: (GuardianCapability) -> Unit = {},
    onFixBlocker: (ListeningRequirement) -> Unit = {},
) {
    when (state) {
        HomeUiState.Loading -> LoadingState(modifier)
        is HomeUiState.Ready -> ReadyState(
            snapshot = state.snapshot,
            listeningStatus = listeningStatus,
            wakeWord = wakeWord,
            isAmberAlertActive = isAmberAlertActive,
            amberRationale = amberRationale,
            onDismissAmber = onDismissAmber,
            onAction = onAction,
            onOpenSession = onOpenSession,
            onPlanRoute = onPlanRoute,
            onArm = onArm,
            onDisarm = onDisarm,
            onRecordNow = onRecordNow,
            onStopRecording = onStopRecording,
            setup = setup,
            onFixCapability = onFixCapability,
            onFixBlocker = onFixBlocker,
            modifier = modifier,
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GuardianTheme.colors.canvas),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = GuardianTheme.materialColors.primary)
            Spacer(Modifier.height(GuardianTheme.spacing.md))
            Text(
                text = "Waking Angel…",
                style = GuardianTheme.type.bodyMd,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadyState(
    snapshot: GuardianSnapshot,
    listeningStatus: ListeningStatus,
    wakeWord: WakeWord,
    isAmberAlertActive: Boolean,
    amberRationale: String,
    onDismissAmber: () -> Unit,
    onAction: (HomeAction) -> Unit,
    onOpenSession: (String) -> Unit,
    onPlanRoute: () -> Unit,
    onArm: () -> Unit,
    onDisarm: () -> Unit,
    onRecordNow: () -> Unit,
    onStopRecording: () -> Unit,
    setup: GuardianSetup,
    onFixCapability: (GuardianCapability) -> Unit,
    onFixBlocker: (ListeningRequirement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRecording = snapshot.mode == GuardianMode.Recording
    val atHaven = snapshot.isAtSafeHaven
    val mood = AngelMood.fromScore(
        score = snapshot.safetyScore.score,
        atSafeHaven = atHaven,
        isArmed = snapshot.mode != GuardianMode.Standby,
        inDuress = isRecording,
    )

    GuardianTabScaffold(
        modifier = modifier,
        topBar = {
            GuardianTopBar(
                mode = snapshot.mode,
                subtitle = snapshot.journey?.let { "Guarding ${it.corridorLabel}" }
                    ?: snapshot.safeHavenLabel?.let { "Resting at $it" },
                onToggleRecording = if (isRecording) onStopRecording else onRecordNow,
            )
        },
    ) {
        if (isAmberAlertActive) {
            AmberAlertBanner(
                rationale = amberRationale,
                onDismiss = onDismissAmber,
                onEmergencyCall = { onAction(HomeAction.DispatchAlert(CodewordTier.Emergency)) },
            )
        }

        // --- Recording takes over at the top ---------------------------------------
        val liveSession = snapshot.activeSession ?: if (isRecording) {
            com.example.guardianangel.domain.model.RecordingSession(
                id = "live-session",
                startedAtEpochMillis = System.currentTimeMillis(),
                triggeredBy = null,
                transcriptPreview = emptyList(),
            )
        } else null

        AnimatedVisibility(
            visible = isRecording && liveSession != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            liveSession?.let { session ->
                ActiveRecordingPanel(
                    session = session,
                    contactsNotifiedLabel = notifiedLabel(snapshot),
                    onStop = {
                        onAction(HomeAction.StopRecording)
                        onDisarm()
                    },
                )
            }
        }

        AngelHeroCard(
            mood = mood,
            message = angelMessage(snapshot, mood),
            badge = if (atHaven) "Standby" else snapshot.journey?.let { "Live" },
            badgeIcon = if (atHaven) GuardianIcons.Moon else GuardianIcons.Waveform,
        )

        // The safety score sits directly under Angel, before anything optional.
        //
        // It used to live inside the sanctuary/journey bodies, below the setup and
        // hands-free cards — which on a phone put it entirely below the fold on a
        // not-yet-configured account. The one number the user opens the app to see
        // required scrolling past three cards telling her what she had not set up.
        SafetyGaugeCard(
            score = snapshot.safetyScore,
            title = if (atHaven) "Sanctuary zone" else "Transit safety",
            statusChip = if (atHaven) "Shielded" else "Live",
            body = if (atHaven) {
                "Your score stays at 100% inside your home geofence. I'll start " +
                    "recalibrating the moment you leave."
            } else {
                snapshot.safetyScore.rationale
            },
            footer = if (atHaven) null else journeyMetrics(snapshot),
        )

        // Below the score: a gap in setup is the reason hands-free might not work, so it
        // reads in the right order.
        if (!setup.isComplete) {
            SetupNeededCard(setup = setup, onFix = onFixCapability)
        }

        HandsFreeCard(
            status = listeningStatus,
            wakeWord = wakeWord,
            onArm = onArm,
            onDisarm = onDisarm,
            onFixBlocker = onFixBlocker,
        )

        if (atHaven) {
            SanctuaryBody(snapshot, onAction, onPlanRoute, onRecordNow)
        } else {
            JourneyBody(snapshot, onAction, isRecording, onRecordNow)
        }

        GuardiansStrip(contacts = snapshot.contacts)

        SectionHeader(title = "Recent activity", icon = GuardianIcons.Clock) {
            Text(
                text = "Yesterday",
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
        }
        // Capped at two on Home: the full log lives in the Activities tab, and a long
        // list here would bury the controls that matter in the moment.
        ActivitySamples.sessions.take(2).forEach { session ->
            RecentActivityCard(session = session, onClick = { onOpenSession(session.id) })
        }
    }
}

@Composable
private fun AmberAlertBanner(
    rationale: String,
    onDismiss: () -> Unit,
    onEmergencyCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = GuardianTheme.shapes.lg
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GuardianTheme.materialColors.errorContainer)
            .padding(GuardianTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
        ) {
            Icon(
                imageVector = GuardianIcons.CrisisAlert,
                contentDescription = null,
                tint = GuardianTheme.materialColors.onErrorContainer,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = "CRITICAL SAFETY ESCALATION",
                style = GuardianTheme.type.labelMd,
                color = GuardianTheme.materialColors.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Dismiss",
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.materialColors.onErrorContainer,
                modifier = Modifier
                    .clip(GuardianTheme.shapes.pill)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = GuardianTheme.spacing.xs, vertical = 2.dp),
            )
        }
        Text(
            text = rationale,
            style = GuardianTheme.type.bodyMd,
            color = GuardianTheme.materialColors.onErrorContainer,
        )
        GuardianPrimaryButton(
            text = "Call 911 / Alert Circle",
            onClick = onEmergencyCall,
            leadingIcon = GuardianIcons.Phone,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}


/** Sanctuary: one clear way out the door, two quiet secondary actions. */
@Composable
private fun SanctuaryBody(
    snapshot: GuardianSnapshot,
    onAction: (HomeAction) -> Unit,
    onPlanRoute: () -> Unit,
    onRecordNow: () -> Unit,
) {
    GuardianPrimaryButton(
        text = "Start guarded walk",
        onClick = onPlanRoute,
        leadingIcon = GuardianIcons.Walk,
        trailingIcon = GuardianIcons.ArrowRight,
        modifier = Modifier.fillMaxWidth(),
    )

    QuickActionRow(
        leftLabel = "Record quietly",
        leftIcon = GuardianIcons.Mic,
        onLeft = onRecordNow,
        rightLabel = "Hold SOS",
        rightIcon = GuardianIcons.CrisisAlert,
        onRight = { onAction(HomeAction.DispatchAlert(CodewordTier.Danger)) },
        rightIsDuress = true,
    )
}

/** Out & about: the gauge goes live and the emergency controls come forward. */
@Composable
private fun JourneyBody(
    snapshot: GuardianSnapshot,
    onAction: (HomeAction) -> Unit,
    isRecording: Boolean,
    onRecordNow: () -> Unit,
) {
    if (!isRecording) {
        DuressTriggerCard(
            onFire = { tier -> onAction(HomeAction.DispatchAlert(tier)) },
            notifyingLabel = snapshot.contacts
                .takeIf { it.isNotEmpty() }
                ?.joinToString(" and ") { it.name.substringBefore(' ') }
                ?.let { "Sends a silent alert with your live location to $it." },
        )
    }

    GuardianPrimaryButton(
        text = "Safe arrival · end walk",
        onClick = { onAction(HomeAction.DisarmGuardian) },
        leadingIcon = GuardianIcons.ShieldCheck,
        modifier = Modifier.fillMaxWidth(),
    )

    if (!isRecording) {
        // Mid-walk is where the wake word is most likely to be drowned out by traffic,
        // so the manual path has to be reachable here too — not only from the sanctuary
        // screen the user sees before setting off.
        GuardianOutlinedButton(
            text = "Start recording now",
            onClick = onRecordNow,
            leadingIcon = GuardianIcons.Mic,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Lighting and arrival tiles under the score, while a walk is live.
 *
 * Returns null at a safe haven, where neither has anything to report and two empty tiles
 * read as missing data rather than as "nothing to worry about".
 */
private fun journeyMetrics(snapshot: GuardianSnapshot): (@Composable ColumnScope.() -> Unit)? {
    val journey = snapshot.journey ?: return null
    return {
        Row(horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm)) {
            MetricTile(
                label = "Illumination",
                value = "${journey.illuminationPercent}% lit",
                icon = GuardianIcons.Sun,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "Arrival",
                value = journey.delayMinutes
                    .takeIf { it > 0 }
                    ?.let { "+$it min" }
                    ?: "On time",
                icon = GuardianIcons.Clock,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** What Angel says, in her own voice, for the current situation. */
private fun angelMessage(snapshot: GuardianSnapshot, mood: AngelMood): String {
    val name = snapshot.userFirstName
    val journey = snapshot.journey
    return when (mood) {
        AngelMood.Critical ->
            "I've got you, $name. Your circle has been told and I'm sharing where you are."
        AngelMood.Warning ->
            "This stretch is darker than I'd like. I'm listening closely — say the word " +
                "and I'll call your circle."
        AngelMood.Cautious ->
            journey?.let {
                "Walking with you along ${it.corridorLabel}. Lighting is " +
                    "${it.illuminationPercent}% and your circle knows where you are."
            } ?: "I'm listening for your codewords."
        AngelMood.Sanctuary, AngelMood.Resting ->
            "Safe and sound at ${snapshot.safeHavenLabel ?: "home"}, $name. " +
                "I'm resting my listening ear, ready whenever you need me."
    }
}

/** Human-readable summary of who the active session has already alerted. */
@Composable
private fun notifiedLabel(snapshot: GuardianSnapshot): String? {
    val session = snapshot.activeSession ?: return null
    val names = snapshot.contacts
        .filter { it.id in session.contactsNotified }
        .map { it.name.substringBefore(' ') }
    return when (names.size) {
        0 -> null
        1 -> "${names.first()} has been alerted"
        else -> "${names.dropLast(1).joinToString(", ")} and ${names.last()} have been alerted"
    }
}

// --- Previews ------------------------------------------------------------------------

@Preview(name = "Home · sanctuary", showBackground = true, device = "id:pixel_8", heightDp = 1800)
@Composable
private fun HomeSanctuaryPreview() {
    GuardianAngelTheme {
        HomeScreen(
            state = HomeUiState.Ready(GuardianSamples.snapshot(GuardianMode.Standby)),
            onAction = {}, onOpenSession = {}, onPlanRoute = {},
        )
    }
}

@Preview(name = "Home · out & about", showBackground = true, device = "id:pixel_8", heightDp = 1800)
@Composable
private fun HomeJourneyPreview() {
    GuardianAngelTheme {
        HomeScreen(
            state = HomeUiState.Ready(GuardianSamples.snapshot(GuardianMode.Listening)),
            onAction = {}, onOpenSession = {}, onPlanRoute = {},
        )
    }
}

@Preview(name = "Home · recording", showBackground = true, device = "id:pixel_8", heightDp = 1800)
@Composable
private fun HomeRecordingPreview() {
    GuardianAngelTheme {
        HomeScreen(
            state = HomeUiState.Ready(GuardianSamples.snapshot(GuardianMode.Recording)),
            onAction = {}, onOpenSession = {}, onPlanRoute = {},
        )
    }
}
