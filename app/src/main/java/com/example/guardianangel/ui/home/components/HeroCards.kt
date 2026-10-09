package com.example.guardianangel.ui.home.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.example.guardianangel.R
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.guardianangel.domain.model.GuardianTelemetry
import com.example.guardianangel.ui.components.GuardianPrimaryButton
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianTheme

/**
 * The hero card while the guardian is **listening**.
 *
 * A warm gradient sphere that reads as "something is awake and watching". Carries the
 * telemetry the user needs to trust that claim: battery, whether codewords are armed, and
 * GPS accuracy — because "it says it's protecting me" is only reassuring if the device
 * can actually follow through.
 */
@Composable
fun VigilanceOnlineCard(
    telemetry: GuardianTelemetry,
    onDisarm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GuardianTheme.colors
    val transition = rememberInfiniteTransition(label = "vigilance")
    val shimmer by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Reverse),
        label = "shimmer",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(GuardianTheme.shapes.xl)
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.butterCream.copy(alpha = shimmer),
                        colors.accentWarm,
                        colors.accentSoft,
                    )
                )
            )
            .border(1.dp, colors.borderEmphasis, GuardianTheme.shapes.xl)
            .padding(GuardianTheme.spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                TonalPill(
                    text = stringResource(R.string.vigilance_calibrated),
                    icon = GuardianIcons.ShieldCheck,
                    container = GuardianTheme.materialColors.surfaceContainerLowest,
                    content = GuardianTheme.materialColors.onSurface,
                )
                Spacer(Modifier.height(GuardianTheme.spacing.sm))
                Text(
                    text = stringResource(R.string.vigilance_title),
                    style = GuardianTheme.type.headlineMd,
                    color = colors.onAccentWarm,
                )
                Text(
                    text = stringResource(R.string.vigilance_subtitle),
                    style = GuardianTheme.type.bodySm,
                    color = colors.onAccentWarm.copy(alpha = 0.78f),
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(GuardianTheme.shapes.lg)
                    .background(GuardianTheme.materialColors.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = GuardianIcons.Waveform,
                    contentDescription = null,
                    tint = GuardianTheme.materialColors.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.height(GuardianTheme.spacing.md))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GuardianTheme.shapes.lg)
                .background(GuardianTheme.materialColors.surfaceContainerLowest.copy(alpha = 0.72f))
                .padding(GuardianTheme.spacing.md),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TelemetryReadout(
                icon = GuardianIcons.Battery,
                value = stringResource(R.string.safety_score_percent, telemetry.batteryPercent),
                label = stringResource(R.string.telemetry_battery),
            )
            TelemetryReadout(
                icon = GuardianIcons.Mic,
                value = stringResource(R.string.telemetry_codewords_armed),
                label = stringResource(R.string.telemetry_codewords),
            )
            TelemetryReadout(
                icon = GuardianIcons.MapPin,
                value = telemetry.gpsAccuracyMeters
                    ?.let { stringResource(R.string.telemetry_gps_accuracy, it.toString()) }
                    ?: stringResource(R.string.telemetry_unavailable),
                label = stringResource(R.string.telemetry_gps),
            )
        }

        Spacer(Modifier.height(GuardianTheme.spacing.md))

        GuardianPrimaryButton(
            text = stringResource(R.string.action_stand_down),
            onClick = onDisarm,
            leadingIcon = GuardianIcons.Power,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The hero card while the guardian is in **standby**.
 *
 * Intentionally quieter than its listening counterpart: muted surface, dormant-mic
 * language, and a single obvious way forward. Standby is the resting state, so it should
 * not look like an alarm.
 */
@Composable
fun GuardianShieldCard(
    telemetry: GuardianTelemetry,
    safeHavenLabel: String?,
    onArm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GuardianTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(GuardianTheme.shapes.xl)
            .background(GuardianTheme.materialColors.surfaceContainerHigh)
            .border(1.dp, colors.borderEmphasis, GuardianTheme.shapes.xl)
            .padding(GuardianTheme.spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.shield_title),
                    style = GuardianTheme.type.headlineMd,
                    color = GuardianTheme.materialColors.onSurface,
                )
                Spacer(Modifier.height(GuardianTheme.spacing.xs))
                Text(
                    text = safeHavenLabel
                        ?.let { stringResource(R.string.shield_body_at_haven, it) }
                        ?: stringResource(R.string.shield_body_idle),
                    style = GuardianTheme.type.bodyMd,
                    color = GuardianTheme.materialColors.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(GuardianTheme.shapes.lg)
                    .background(GuardianTheme.materialColors.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = GuardianIcons.Moon,
                    contentDescription = null,
                    tint = GuardianTheme.materialColors.secondary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.height(GuardianTheme.spacing.md))

        // Status chips wrap onto a second line on narrow screens rather than truncating.
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
        ) {
            TonalPill(
                text = stringResource(R.string.chip_battery, telemetry.batteryPercent),
                icon = GuardianIcons.Battery,
                container = GuardianTheme.materialColors.surfaceContainerLowest,
                content = GuardianTheme.materialColors.onSurface,
            )
            if (telemetry.isGeofenceActive) {
                TonalPill(
                    text = stringResource(R.string.chip_geofence),
                    icon = GuardianIcons.MapPin,
                    container = GuardianTheme.materialColors.surfaceContainerLowest,
                    content = GuardianTheme.materialColors.onSurface,
                )
            }
            TonalPill(
                text = stringResource(
                    if (telemetry.isEncrypted) R.string.chip_mic_dormant_encrypted
                    else R.string.chip_mic_dormant
                ),
                icon = GuardianIcons.MicOff,
                container = GuardianTheme.materialColors.surfaceContainerLowest,
                content = GuardianTheme.materialColors.onSurface,
            )
        }

        Spacer(Modifier.height(GuardianTheme.spacing.md))

        GuardianPrimaryButton(
            text = stringResource(R.string.action_activate_shield),
            onClick = onArm,
            leadingIcon = GuardianIcons.Shield,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TelemetryReadout(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GuardianTheme.materialColors.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = value,
                style = GuardianTheme.type.labelMd,
                color = GuardianTheme.materialColors.onSurface,
            )
        }
        Text(
            text = label,
            style = GuardianTheme.type.labelSm,
            color = GuardianTheme.materialColors.onSurfaceVariant,
        )
    }
}
