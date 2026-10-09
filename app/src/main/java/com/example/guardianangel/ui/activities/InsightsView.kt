package com.example.guardianangel.ui.activities

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.guardianangel.domain.model.AnalyticsRange
import com.example.guardianangel.domain.model.Breakdown
import com.example.guardianangel.domain.model.MovementAnalytics
import com.example.guardianangel.ui.components.GuardianCard
import com.example.guardianangel.ui.home.components.MetricTile
import com.example.guardianangel.ui.home.components.ScoreRing
import com.example.guardianangel.ui.home.components.SectionHeader
import com.example.guardianangel.ui.home.components.TonalPill
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianTheme
import com.example.guardianangel.ui.theme.SafetyLevel
import com.example.guardianangel.ui.theme.safetyColorsFor

/**
 * Movement intelligence.
 *
 * Framed as "calm, private metrics" rather than a dashboard: one headline rating, three
 * supporting counts, and three breakdowns. The reference also carried a Knox
 * certification card and an export button on the same screen; the certification is a
 * single footer line here, because a wall of security badges on an analytics page is
 * reassurance theatre rather than information.
 */
@Composable
fun InsightsView(
    analytics: MovementAnalytics,
    range: AnalyticsRange,
    onRangeChange: (AnalyticsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = safetyColorsFor(SafetyLevel.fromScore(analytics.overallRating))

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.lg),
    ) {
        SegmentedToggle(
            options = listOf("Week", "Month", "All time"),
            selectedIndex = range.ordinal,
            onSelect = { onRangeChange(AnalyticsRange.entries[it]) },
        )

        GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(
                    score = analytics.overallRating,
                    accent = palette.accent,
                    caption = "OVERALL",
                )
                Spacer(Modifier.size(GuardianTheme.spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "You've been well looked after",
                        style = GuardianTheme.type.labelLg,
                        color = GuardianTheme.materialColors.onSurface,
                    )
                    Spacer(Modifier.height(GuardianTheme.spacing.xs))
                    Text(
                        text = "${analytics.guardedWalks} guarded walks · " +
                            "${analytics.safeArrivalsPercent}% safe arrivals",
                        style = GuardianTheme.type.bodySm,
                        color = GuardianTheme.materialColors.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(GuardianTheme.spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm)) {
                MetricTile(
                    label = "Guarded walks",
                    value = analytics.guardedWalks.toString(),
                    icon = GuardianIcons.Walk,
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = "Duress triggers",
                    value = analytics.duressTriggers.toString(),
                    icon = GuardianIcons.CrisisAlert,
                    emphasis = if (analytics.duressTriggers > 0) palette.accent else null,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (analytics.insight != null) {
            InsightCard(analytics.insight!!)
        }

        BreakdownCard(
            title = "Route lighting by time of day",
            icon = GuardianIcons.Sun,
            items = analytics.diurnalBreakdown,
        )

        BreakdownCard(
            title = "How loud your surroundings were",
            icon = GuardianIcons.Waveform,
            items = analytics.acousticBreakdown,
        )

        BreakdownCard(
            title = "Whose voices Angel heard",
            icon = GuardianIcons.Users,
            items = analytics.voiceBreakdown,
        )

        GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
            SectionHeader(title = "Where you spend safe time", icon = GuardianIcons.HomePin)
            Spacer(Modifier.height(GuardianTheme.spacing.sm))
            analytics.sanctuaries.forEach { sanctuary ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(GuardianTheme.colors.accentSoft),
                    )
                    Spacer(Modifier.size(GuardianTheme.spacing.sm))
                    Text(
                        text = sanctuary.name,
                        style = GuardianTheme.type.bodyMd,
                        color = GuardianTheme.materialColors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${sanctuary.hoursLogged} hrs",
                        style = GuardianTheme.type.labelMd,
                        color = GuardianTheme.materialColors.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = GuardianIcons.Lock,
                contentDescription = null,
                tint = GuardianTheme.colors.iconMuted,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "Computed on this device. Nothing here has been uploaded.",
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.colors.iconMuted,
            )
        }
    }
}

@Composable
private fun InsightCard(insight: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(GuardianTheme.shapes.lg)
            .background(GuardianTheme.colors.peachCream.copy(alpha = 0.5f))
            .padding(GuardianTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
    ) {
        Icon(
            imageVector = GuardianIcons.Beacon,
            contentDescription = null,
            tint = GuardianTheme.materialColors.onSurface,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = insight,
            style = GuardianTheme.type.bodySm,
            color = GuardianTheme.materialColors.onSurface,
        )
    }
}

/**
 * A labelled set of proportion bars.
 *
 * Bars rather than a pie: comparing three to four values is what bars are for, and they
 * stay readable at phone width without a legend.
 */
@Composable
private fun BreakdownCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    items: List<Breakdown>,
    modifier: Modifier = Modifier,
) {
    GuardianCard(modifier = modifier, contentPadding = GuardianTheme.spacing.lg) {
        SectionHeader(title = title, icon = icon)
        Spacer(Modifier.height(GuardianTheme.spacing.md))
        items.forEach { item ->
            BreakdownBar(item)
            Spacer(Modifier.height(GuardianTheme.spacing.sm))
        }
    }
}

@Composable
private fun BreakdownBar(item: Breakdown, modifier: Modifier = Modifier) {
    val palette = safetyColorsFor(
        if (item.isFlagged) SafetyLevel.Elevated else SafetyLevel.Secure
    )
    val fraction by animateFloatAsState(
        targetValue = item.percent / 100f,
        animationSpec = tween(600),
        label = "breakdownBar",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            // One description for the row; the bar is a redrawing of the same number.
            .semantics { contentDescription = "${item.label}: ${item.percent} percent" },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = item.label,
                style = GuardianTheme.type.bodySm,
                color = GuardianTheme.materialColors.onSurface,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (item.isFlagged) {
                    TonalPill(
                        text = "Watch",
                        container = palette.container,
                        content = palette.onContainer,
                    )
                }
                Text(
                    text = "${item.percent}%",
                    style = GuardianTheme.type.labelMd,
                    color = GuardianTheme.materialColors.onSurface,
                )
            }
        }
        Spacer(Modifier.height(GuardianTheme.spacing.xs))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(GuardianTheme.materialColors.surfaceContainerHigh)
                .clearAndSetSemantics { },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(palette.accent),
            )
        }
    }
}
