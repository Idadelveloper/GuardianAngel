package com.example.guardianangel.ui.home.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.example.guardianangel.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.guardianangel.domain.model.SafeWalk
import com.example.guardianangel.ui.components.GuardianPrimaryButton
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianTheme

/**
 * Safe Walk: a monitored journey with an expected arrival.
 *
 * If the user does not check in by the ETA, their guardians are notified automatically —
 * the "if a text isn't sent within x minutes" rule from the product brief. The route
 * itself is chosen by the Map tab's safest-path routing rather than by shortest distance.
 */
@Composable
fun SafeWalkCard(
    safeWalk: SafeWalk,
    onBegin: () -> Unit,
    onChangeDestination: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(GuardianTheme.shapes.xl)
            .background(GuardianTheme.materialColors.surfaceContainerLow)
            .border(1.dp, GuardianTheme.colors.borderEmphasis, GuardianTheme.shapes.xl)
            .padding(GuardianTheme.spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(GuardianTheme.shapes.lg)
                    .background(GuardianTheme.materialColors.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = GuardianIcons.Walk,
                    contentDescription = null,
                    tint = GuardianTheme.materialColors.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.size(GuardianTheme.spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (safeWalk.isActive) R.string.safewalk_title_active
                        else R.string.safewalk_title_idle
                    ),
                    style = GuardianTheme.type.labelLg,
                    color = GuardianTheme.materialColors.onSurface,
                )
                Text(
                    text = stringResource(R.string.safewalk_subtitle),
                    style = GuardianTheme.type.bodySm,
                    color = GuardianTheme.materialColors.onSurfaceVariant,
                )
            }
            TonalPill(
                text = stringResource(
                    R.string.safewalk_duration,
                    safeWalk.estimatedDuration.inWholeMinutes,
                ),
                container = GuardianTheme.colors.accentSoft,
                content = GuardianTheme.colors.onAccentSoft,
            )
        }

        Spacer(Modifier.height(GuardianTheme.spacing.md))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GuardianTheme.shapes.lg)
                .background(GuardianTheme.materialColors.surfaceContainerLowest)
                .border(1.dp, GuardianTheme.colors.borderDefault, GuardianTheme.shapes.lg)
                .padding(GuardianTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
        ) {
            Icon(
                imageVector = GuardianIcons.HomePin,
                contentDescription = null,
                tint = GuardianTheme.materialColors.secondary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = safeWalk.destinationLabel,
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.materialColors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.safewalk_eta, safeWalk.etaLabel),
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.materialColors.tertiary,
            )
        }

        Spacer(Modifier.height(GuardianTheme.spacing.md))

        GuardianPrimaryButton(
            text = stringResource(
                if (safeWalk.isActive) R.string.action_view_journey
                else R.string.action_begin_journey
            ),
            onClick = onBegin,
            leadingIcon = GuardianIcons.ArrowRight,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
