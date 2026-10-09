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
import com.example.guardianangel.domain.model.GuardianMode
import com.example.guardianangel.domain.model.GuardianSnapshot
import com.example.guardianangel.domain.repository.GuardianRepository
import com.example.guardianangel.ui.components.GuardianCard
import com.example.guardianangel.ui.components.GuardianPrimaryButton
import com.example.guardianangel.ui.components.GuardianTabScaffold
import com.example.guardianangel.ui.home.components.ActiveRecordingPanel
import com.example.guardianangel.ui.home.components.AngelHeroCard
import com.example.guardianangel.ui.home.components.DuressTriggerCard
import com.example.guardianangel.ui.home.components.GuardianTopBar
import com.example.guardianangel.ui.home.components.GuardiansStrip
import com.example.guardianangel.ui.home.components.MetricTile
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
    onOpenSession: (String) -> Unit,
    onPlanRoute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(repository))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onAction = viewModel::onAction,
        onOpenSession = onOpenSession,
        onPlanRoute = onPlanRoute,
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
) {
    when (state) {
        HomeUiState.Loading -> LoadingState(modifier)
        is HomeUiState.Ready -> ReadyState(
            snapshot = state.snapshot,
            onAction = onAction,
            onOpenSession = onOpenSession,
            onPlanRoute = onPlanRoute,
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
    onAction: (HomeAction) -> Unit,
    onOpenSession: (String) -> Unit,
    onPlanRoute: () -> Unit,
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
                onQuickAlert = { onAction(HomeAction.DispatchAlert(CodewordTier.Danger)) },
            )
        },
    ) {
        AngelHeroCard(
            mood = mood,
            message = angelMessage(snapshot, mood),
            badge = if (atHaven) "Standby" else snapshot.journey?.let { "Live" },
            badgeIcon = if (atHaven) GuardianIcons.Moon else GuardianIcons.Waveform,
        )

        // --- Recording takes over --------------------------------------------------
        AnimatedVisibility(
            visible = isRecording && snapshot.activeSession != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            snapshot.activeSession?.let { session ->
                ActiveRecordingPanel(
                    session = session,
                    contactsNotifiedLabel = notifiedLabel(snapshot),
                    onStop = { onAction(HomeAction.StopRecording) },
                )
            }
        }

        if (atHaven) {
            SanctuaryBody(snapshot, onAction, onPlanRoute)
        } else {
            JourneyBody(snapshot, onAction, isRecording)
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

/** Sanctuary: one clear way out the door, two quiet secondary actions. */
@Composable
private fun SanctuaryBody(
    snapshot: GuardianSnapshot,
    onAction: (HomeAction) -> Unit,
    onPlanRoute: () -> Unit,
) {
    SafetyGaugeCard(
        score = snapshot.safetyScore,
        title = "Sanctuary zone",
        statusChip = "Shielded",
        body = "Your score stays at 100% inside your home geofence. I'll start " +
            "recalibrating the moment you leave.",
    )

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
        onLeft = { onAction(HomeAction.StartRecording(CodewordTier.Caution)) },
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
) {
    val journey = snapshot.journey

    SafetyGaugeCard(
        score = snapshot.safetyScore,
        title = "Transit safety",
        statusChip = "Live",
        body = snapshot.safetyScore.rationale,
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm)) {
                MetricTile(
                    label = "Illumination",
                    value = "${journey?.illuminationPercent ?: 0}% lit",
                    icon = GuardianIcons.Sun,
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = "Arrival",
                    value = journey?.delayMinutes
                        ?.takeIf { it > 0 }
                        ?.let { "+$it min" }
                        ?: "On time",
                    icon = GuardianIcons.Clock,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    )

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
