package com.example.guardianangel.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.guardianangel.domain.model.ListeningRequirement
import com.example.guardianangel.domain.model.ListeningState
import com.example.guardianangel.domain.model.ListeningStatus
import com.example.guardianangel.domain.model.WakeWord
import com.example.guardianangel.ui.components.GuardianCard
import com.example.guardianangel.ui.components.GuardianOutlinedButton
import com.example.guardianangel.ui.components.GuardianPrimaryButton
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianTheme
import com.example.guardianangel.ui.theme.SafetyLevel
import com.example.guardianangel.ui.theme.safetyColorsFor

/**
 * Hands-free activation, on the home screen.
 *
 * This is where the platform's constraints become the user's problem, so the card does
 * three jobs rather than one:
 *
 *  - **Arms listening.** Android will not let a microphone service start from the
 *    background, so this tap — made with the app open — is the only moment listening can
 *    legally begin. That makes it a first-class control rather than a settings toggle.
 *  - **States what will happen.** While armed it names the wake word, so there is never
 *    doubt about which phrase to say under pressure.
 *  - **Reports what is missing.** A listening feature that silently does nothing because
 *    a permission was declined is worse than no feature; every blocker is listed with
 *    the one action that fixes it.
 */
@Composable
fun HandsFreeCard(
    status: ListeningStatus,
    wakeWord: WakeWord,
    onArm: () -> Unit,
    onDisarm: () -> Unit,
    onFixBlocker: (ListeningRequirement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listening = status.state == ListeningState.Listening ||
        status.state == ListeningState.Triggered
    val palette = safetyColorsFor(
        if (listening) SafetyLevel.Secure else SafetyLevel.Guarded
    )

    GuardianCard(modifier = modifier, contentPadding = GuardianTheme.spacing.lg) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(GuardianTheme.shapes.md)
                    .background(
                        if (listening) {
                            GuardianTheme.colors.accentSoft
                        } else {
                            GuardianTheme.materialColors.surfaceContainerHigh
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (listening) GuardianIcons.Waveform else GuardianIcons.MicOff,
                    contentDescription = null,
                    tint = if (listening) {
                        GuardianTheme.colors.onAccentSoft
                    } else {
                        GuardianTheme.materialColors.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.size(GuardianTheme.spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Hands-free",
                    style = GuardianTheme.type.labelLg,
                    color = GuardianTheme.materialColors.onSurface,
                )
                Text(
                    text = when {
                        listening -> "Listening for “${wakeWord.phrase}”"
                        status.blockedBy.any { it != ListeningRequirement.BatteryExemption } ->
                            "Needs a moment of setup"
                        !status.detectorReady -> "Unavailable in this build"
                        else -> "Ready — not listening yet"
                    },
                    style = GuardianTheme.type.labelSm,
                    color = GuardianTheme.materialColors.onSurfaceVariant,
                )
            }
            if (listening) {
                PulsingDot(color = palette.accent, size = 10.dp)
            }
        }

        Spacer(Modifier.height(GuardianTheme.spacing.md))

        // Blockers first — there is no point offering Arm when it cannot succeed.
        val hardBlockers = status.blockedBy.filter {
            it != ListeningRequirement.BatteryExemption
        }

        if (hardBlockers.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm)) {
                hardBlockers.forEach { requirement ->
                    BlockerRow(requirement = requirement, onFix = { onFixBlocker(requirement) })
                }
            }
        } else if (!status.detectorReady) {
            // Honest about a build with no model rather than arming into silence.
            Text(
                text = "Hands-free activation isn't available in this build — no wake-word " +
                    "model is installed. Everything else works; use the SOS trigger to " +
                    "start a recording by hand.",
                style = GuardianTheme.type.bodySm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
        } else if (listening) {
            Text(
                text = "Say your wake word and I'll start recording. You can lock your " +
                    "phone and put it away.",
                style = GuardianTheme.type.bodySm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
            Spacer(Modifier.height(GuardianTheme.spacing.md))
            GuardianOutlinedButton(
                text = "Stop listening",
                onClick = onDisarm,
                leadingIcon = GuardianIcons.MicOff,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = "Arm me before you set off. Android only lets me start listening " +
                    "while the app is open — after that I keep going in your pocket.",
                style = GuardianTheme.type.bodySm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
            Spacer(Modifier.height(GuardianTheme.spacing.md))
            GuardianPrimaryButton(
                text = "Arm hands-free listening",
                onClick = onArm,
                leadingIcon = GuardianIcons.Mic,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Battery is advisory, so it sits below whatever the main state is.
        if (ListeningRequirement.BatteryExemption in status.blockedBy && listening) {
            Spacer(Modifier.height(GuardianTheme.spacing.sm))
            Text(
                text = "Tip: exempt Angel from battery optimisation so long walks aren't " +
                    "cut short.",
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.colors.iconMuted,
            )
        }
    }
}

@Composable
private fun BlockerRow(
    requirement: ListeningRequirement,
    onFix: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (label, action) = when (requirement) {
        ListeningRequirement.MicrophonePermission ->
            "Microphone access is off" to "Allow"
        ListeningRequirement.NotificationPermission ->
            "Notifications are off — Android needs one to listen" to "Allow"
        ListeningRequirement.WakeWordEnrolled ->
            "No wake word recorded yet" to "Record"
        ListeningRequirement.VoiceProfile ->
            "Your voiceprint isn't set up" to "Set up"
        ListeningRequirement.BatteryExemption ->
            "Battery optimisation may stop long sessions" to "Fix"
    }
    val palette = safetyColorsFor(SafetyLevel.Elevated)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(GuardianTheme.shapes.md)
            .background(palette.container.copy(alpha = 0.45f))
            .padding(GuardianTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = GuardianIcons.Warning,
            contentDescription = null,
            tint = palette.accent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(GuardianTheme.spacing.sm))
        Text(
            text = label,
            style = GuardianTheme.type.bodySm,
            color = GuardianTheme.materialColors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = action,
            style = GuardianTheme.type.labelMd,
            color = GuardianTheme.materialColors.primary,
            modifier = Modifier
                .clip(GuardianTheme.shapes.sm)
                .clickable(onClick = onFix)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
