package com.example.guardianangel.ui.home.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.example.guardianangel.R
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.ui.components.GuardianCard
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianTheme
import kotlinx.coroutines.delay

private const val HOLD_MILLIS = 3_000

/**
 * Hold-to-fire duress trigger.
 *
 * Deliberately a long press rather than a tap. This dispatches a silent alert to the
 * user's contacts and, at the emergency tier, calls the police — a control that
 * consequential should not be reachable by a pocket brush or a mis-tap, and the three
 * second hold doubles as a cancel window.
 *
 * Progress is shown as a filling bar plus a counting label, so the user can tell how much
 * longer to hold without relying on the animation alone. Releasing early fully resets.
 */
@Composable
fun DuressTriggerCard(
    onFire: (CodewordTier) -> Unit,
    modifier: Modifier = Modifier,
    tier: CodewordTier = CodewordTier.Danger,
    notifyingLabel: String? = null,
) {
    var isHolding by remember { mutableStateOf(false) }
    var hasFired by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val holdDescription = stringResource(R.string.duress_a11y)
    val dispatchLabel = stringResource(R.string.duress_a11y_action)

    // Drives the fill. Animating to 1f over exactly the hold duration keeps the bar and
    // the timer honest about when the alert will actually dispatch.
    val progress by animateFloatAsState(
        targetValue = if (isHolding) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isHolding) HOLD_MILLIS else 220,
            easing = LinearEasing,
        ),
        label = "duressProgress",
    )

    LaunchedEffect(isHolding) {
        if (!isHolding) {
            hasFired = false
            return@LaunchedEffect
        }
        delay(HOLD_MILLIS.toLong())
        hasFired = true
        // Confirms dispatch without a sound or a screen flash, which is the whole
        // point of a discreet trigger.
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onFire(tier)
    }

    GuardianCard(modifier = modifier, contentPadding = GuardianTheme.spacing.lg) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = GuardianIcons.CrisisAlert,
                contentDescription = null,
                tint = GuardianTheme.materialColors.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(GuardianTheme.spacing.sm))
            Text(
                text = stringResource(R.string.duress_title),
                style = GuardianTheme.type.labelLg,
                color = GuardianTheme.materialColors.onSurface,
                modifier = Modifier.weight(1f),
            )
            TonalPill(text = stringResource(R.string.duress_haptic))
        }

        Spacer(Modifier.height(GuardianTheme.spacing.md))

        val label = stringResource(
            when {
                hasFired -> R.string.duress_fired
                isHolding -> R.string.duress_holding
                else -> R.string.duress_idle
            }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(GuardianTheme.shapes.pill)
                .background(GuardianTheme.colors.accentSoft)
                .pointerInput(tier) {
                    detectTapGestures(
                        onPress = {
                            isHolding = true
                            // Resets on release *and* on cancel (finger slides off),
                            // so a partial hold can never leak into a dispatch.
                            tryAwaitRelease()
                            isHolding = false
                        },
                    )
                }
                .semantics {
                    contentDescription = holdDescription
                    onLongClick(label = dispatchLabel) {
                        onFire(tier)
                        true
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // Fill sweeps left to right behind the label.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(
                        if (hasFired) {
                            GuardianTheme.materialColors.error
                        } else {
                            GuardianTheme.colors.accentWarm
                        }
                    )
                    .align(Alignment.CenterStart),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
            ) {
                Icon(
                    imageVector = GuardianIcons.CrisisAlert,
                    contentDescription = null,
                    tint = if (hasFired) {
                        GuardianTheme.materialColors.onError
                    } else {
                        GuardianTheme.colors.onAccentSoft
                    },
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = label,
                    style = GuardianTheme.type.labelLg,
                    color = if (hasFired) {
                        GuardianTheme.materialColors.onError
                    } else {
                        GuardianTheme.colors.onAccentSoft
                    },
                )
            }
        }

        Spacer(Modifier.height(GuardianTheme.spacing.sm))

        Text(
            text = notifyingLabel ?: stringResource(R.string.duress_hint),
            style = GuardianTheme.type.bodySm,
            color = GuardianTheme.materialColors.onSurfaceVariant,
        )
    }
}
