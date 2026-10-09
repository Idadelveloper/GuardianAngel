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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.example.guardianangel.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.guardianangel.domain.model.GuardianMode
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianTheme

/**
 * The persistent app bar.
 *
 * Its job is to answer "is my guardian on?" from across the room, so the mode badge sits
 * next to the wordmark rather than buried in a settings screen.
 */
@Composable
fun GuardianTopBar(
    mode: GuardianMode,
    onQuickAlert: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    unreadCount: Int = 0,
    onOpenNotifications: () -> Unit = {},
) {
    val colors = GuardianTheme.colors
    val (badge, badgeContainer, badgeContent) = when (mode) {
        GuardianMode.Standby -> Triple(
            stringResource(R.string.mode_badge_standby),
            GuardianTheme.materialColors.surfaceContainerHigh,
            GuardianTheme.materialColors.onSurfaceVariant,
        )
        GuardianMode.Listening -> Triple(
            stringResource(R.string.mode_badge_active),
            colors.accentSoft,
            colors.onAccentSoft,
        )
        GuardianMode.Recording -> Triple(
            stringResource(R.string.mode_badge_recording),
            GuardianTheme.materialColors.errorContainer,
            GuardianTheme.materialColors.onErrorContainer,
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(GuardianTheme.materialColors.surfaceContainerLow)
            .statusBarsPadding()
            .padding(
                horizontal = GuardianTheme.spacing.md,
                vertical = GuardianTheme.spacing.sm,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GuardianTheme.materialColors.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = GuardianIcons.Shield,
                    contentDescription = null,
                    tint = GuardianTheme.materialColors.primary,
                    modifier = Modifier.size(22.dp),
                )
            }

            // Wordmark on its own line, state badge beside the subtitle. Keeping the
            // badge on the title line squeezed "Guardian Angel" into an ellipsis on
            // normal phone widths once the two action buttons were accounted for.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_wordmark),
                    style = GuardianTheme.type.headlineMd,
                    color = GuardianTheme.materialColors.onSurface,
                    maxLines = 1,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.xs),
                ) {
                    TonalPill(
                        text = badge,
                        container = badgeContainer,
                        content = badgeContent,
                    )
                    Text(
                        text = subtitle ?: when (mode) {
                            GuardianMode.Standby -> stringResource(R.string.mode_caption_standby)
                            GuardianMode.Listening -> stringResource(R.string.mode_caption_listening)
                            GuardianMode.Recording -> stringResource(R.string.mode_caption_recording)
                        },
                        style = GuardianTheme.type.labelSm,
                        color = GuardianTheme.materialColors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            IconButton(
                onClick = onQuickAlert,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = GuardianTheme.materialColors.primaryContainer,
                    contentColor = GuardianTheme.materialColors.onPrimaryContainer,
                ),
            ) {
                Icon(
                    imageVector = GuardianIcons.CrisisAlert,
                    contentDescription = stringResource(R.string.action_quick_alert),
                    modifier = Modifier.size(20.dp),
                )
            }
            // The design brief removes the profile avatar from Home — the account lives
            // in the Settings tab, and two routes to it invited a dead end here.
            Box {
                IconButton(
                    onClick = onOpenNotifications,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = GuardianTheme.materialColors.surfaceContainerLowest,
                        contentColor = GuardianTheme.materialColors.onSurface,
                    ),
                ) {
                    Icon(
                        imageVector = GuardianIcons.Bell,
                        contentDescription = "Notifications",
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(GuardianTheme.colors.duress),
                    )
                }
            }
        }
    }
}

/**
 * The one-line "what is happening right now" strip under the greeting.
 *
 * Restates the mode in plain language next to the hero card, because the badge in the app
 * bar is small and this is the sentence a user in a hurry will actually read.
 */
@Composable
fun GuardianStatusStrip(
    mode: GuardianMode,
    isEncrypted: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = GuardianTheme.colors
    val dotColor = when (mode) {
        GuardianMode.Standby -> GuardianTheme.colors.iconMuted
        GuardianMode.Listening -> GuardianTheme.materialColors.primary
        GuardianMode.Recording -> GuardianTheme.materialColors.error
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(GuardianTheme.shapes.lg)
            .background(GuardianTheme.materialColors.surfaceContainerLow)
            .border(1.dp, colors.borderDefault, GuardianTheme.shapes.lg)
            .padding(horizontal = GuardianTheme.spacing.md, vertical = GuardianTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
    ) {
        PulsingDot(color = dotColor, animate = mode != GuardianMode.Standby)
        Text(
            text = when (mode) {
                GuardianMode.Standby -> stringResource(R.string.status_standby)
                GuardianMode.Listening -> stringResource(R.string.status_listening)
                GuardianMode.Recording -> stringResource(R.string.status_recording)
            },
            style = GuardianTheme.type.labelMd,
            color = GuardianTheme.materialColors.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (isEncrypted) {
            TonalPill(text = stringResource(R.string.label_encrypted), icon = GuardianIcons.Lock)
        }
    }
}
