package com.example.guardianangel.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.guardianangel.domain.model.ContactPresence
import com.example.guardianangel.domain.model.EmergencyContact
import com.example.guardianangel.domain.model.TrailPoint
import com.example.guardianangel.domain.trail.TrailBuilder
import com.example.guardianangel.domain.model.MonitoredSession
import com.example.guardianangel.domain.model.SessionKind
import com.example.guardianangel.ui.components.GuardianCard
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.trail.TrailPreview
import com.example.guardianangel.ui.theme.GuardianTheme
import com.example.guardianangel.ui.theme.SafetyLevel
import com.example.guardianangel.ui.theme.safetyColorsFor

/**
 * A compact row of guardian avatars with a one-line status.
 *
 * Home shows presence at a glance and nothing more; managing the circle belongs in
 * Settings, so this row deliberately offers no edit affordance.
 */
@Composable
fun GuardiansStrip(
    contacts: List<EmergencyContact>,
    modifier: Modifier = Modifier,
) {
    if (contacts.isEmpty()) return
    val online = contacts.count { it.presence == ContactPresence.Online }
    val names = contacts.take(2).joinToString(", ") { it.name.substringBefore(' ') }
    val extra = (contacts.size - 2).coerceAtLeast(0)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(GuardianTheme.shapes.lg)
            .background(GuardianTheme.materialColors.surfaceContainerLow)
            .padding(GuardianTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Overlapped avatars read as "a group" in far less width than a list.
        Row {
            contacts.take(3).forEachIndexed { index, contact ->
                Box(modifier = Modifier.offset(x = (-10 * index).dp)) {
                    InitialsAvatar(initials = contact.initials, size = 36.dp)
                }
            }
            if (extra > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = (-10 * 3).dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(GuardianTheme.colors.accentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+$extra",
                        style = GuardianTheme.type.labelSm,
                        color = GuardianTheme.colors.onAccentSoft,
                    )
                }
            }
        }

        Spacer(Modifier.size(GuardianTheme.spacing.sm))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (extra > 0) "$names & $extra more" else names,
                style = GuardianTheme.type.labelMd,
                color = GuardianTheme.materialColors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (online > 0) "$online on standby for you" else "Nobody online right now",
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}

/**
 * A past session, summarised.
 *
 * Leads with Angel's plain-language summary rather than metadata, because what the user
 * wants from a log entry is "what happened", not a timestamp.
 */
@Composable
fun RecentActivityCard(
    session: MonitoredSession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Breadcrumbs for this session. Empty hides the preview rather than drawing a box. */
    trailPoints: List<TrailPoint> = emptyList(),
) {
    val level = when (session.kind) {
        SessionKind.Incident -> SafetyLevel.High
        SessionKind.SafeTransit -> SafetyLevel.Secure
        SessionKind.Conversation -> SafetyLevel.Guarded
    }
    val palette = safetyColorsFor(level)
    val kindLabel = when (session.kind) {
        SessionKind.Incident -> "Incident"
        SessionKind.SafeTransit -> "Safe transit"
        SessionKind.Conversation -> "Conversation"
    }

    GuardianCard(modifier = modifier, onClick = onClick, contentPadding = GuardianTheme.spacing.lg) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = session.title,
                style = GuardianTheme.type.labelLg,
                color = GuardianTheme.materialColors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(GuardianTheme.spacing.sm))
            TonalPill(
                text = kindLabel,
                container = palette.container,
                content = palette.onContainer,
            )
        }

        Spacer(Modifier.height(GuardianTheme.spacing.xs))
        Text(
            text = buildString {
                append(session.locationLabel)
                session.durationMinutes?.let { append(" · $it min") }
            },
            style = GuardianTheme.type.labelSm,
            color = GuardianTheme.materialColors.onSurfaceVariant,
        )

        // The path, when there is one. Tapping it opens the session, same as the card —
        // a preview that looked tappable but did something different would be worse
        // than one that is not tappable at all.
        if (trailPoints.isNotEmpty()) {
            Spacer(Modifier.height(GuardianTheme.spacing.sm))
            TrailPreview(
                trail = TrailBuilder.build(session.id, trailPoints, session.entries),
                onClick = onClick,
                height = 118.dp,
            )
        }

        Spacer(Modifier.height(GuardianTheme.spacing.sm))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GuardianTheme.shapes.md)
                .background(GuardianTheme.materialColors.surfaceContainerLow)
                .padding(GuardianTheme.spacing.md),
        ) {
            Text(
                text = session.summary,
                style = GuardianTheme.type.bodySm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(GuardianTheme.spacing.sm))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.xs),
        ) {
            Text(
                text = "View transcript & timeline",
                style = GuardianTheme.type.labelMd,
                color = GuardianTheme.materialColors.primary,
            )
            Icon(
                imageVector = GuardianIcons.ArrowRight,
                contentDescription = null,
                tint = GuardianTheme.materialColors.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
