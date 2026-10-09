package com.example.guardianangel.ui.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.example.guardianangel.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.guardianangel.domain.model.FactorDirection
import com.example.guardianangel.domain.model.SafetyScore
import com.example.guardianangel.ui.components.GuardianCard
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianTheme
import com.example.guardianangel.ui.theme.SafetyLevel
import com.example.guardianangel.ui.theme.safetyColorsFor

/**
 * The location safety score: how likely the user is to run into trouble right now.
 *
 * The number is an estimate from public crime data, time of day, distance from a safe
 * base and street lighting, so the card leads with the band ([SafetyLevel.label]) and a
 * one-line rationale, with the percentage as supporting detail and every contributing
 * factor listed underneath. An unexplained score is not something anyone can act on.
 */
@Composable
fun SafetyScoreCard(
    score: SafetyScore,
    onRescan: () -> Unit,
    onNavigateHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val level = SafetyLevel.fromScore(score.score)
    val palette = safetyColorsFor(level)

    GuardianCard(modifier = modifier, contentPadding = GuardianTheme.spacing.lg) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.xs),
                ) {
                    Text(
                        text = stringResource(R.string.safety_score_title),
                        style = GuardianTheme.type.labelLg,
                        color = GuardianTheme.materialColors.onSurface,
                    )
                    if (score.isLiveScanning) {
                        PulsingDot(color = palette.accent, size = 8.dp)
                    }
                }
                Text(
                    text = stringResource(R.string.safety_score_subtitle),
                    style = GuardianTheme.type.bodySm,
                    color = GuardianTheme.materialColors.onSurfaceVariant,
                )
            }
            TonalPill(
                text = stringResource(
                    if (score.isLiveScanning) R.string.safety_score_scanning
                    else R.string.safety_score_live
                ),
                container = palette.container,
                content = palette.onContainer,
            )
        }

        Spacer(Modifier.height(GuardianTheme.spacing.md))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GuardianTheme.shapes.lg)
                .background(GuardianTheme.materialColors.surfaceContainerLowest)
                .padding(GuardianTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.md),
        ) {
            ScoreRing(score = score.score, accent = palette.accent)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.xs),
                ) {
                    Text(
                        text = stringResource(level.labelRes),
                        style = GuardianTheme.type.labelMd,
                        color = GuardianTheme.materialColors.onSurface,
                    )
                    if (score.deltaPercent != 0) {
                        DeltaBadge(score.deltaPercent)
                    }
                }
                Spacer(Modifier.height(GuardianTheme.spacing.xs))
                Text(
                    text = score.rationale,
                    style = GuardianTheme.type.bodySm,
                    color = GuardianTheme.materialColors.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(GuardianTheme.spacing.sm))

        // Factors in a two-column grid. Built from Rows rather than a LazyVerticalGrid
        // because this sits inside a vertically scrolling column, where nesting a lazy
        // grid of the same orientation is not allowed.
        score.factors.chunked(2).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = GuardianTheme.spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
            ) {
                row.forEach { factor ->
                    val lowers = factor.direction == FactorDirection.Lowers
                    MetricTile(
                        label = factor.label,
                        value = factor.value,
                        icon = factorIcon(factor.id, factor.direction),
                        iconTint = if (lowers) palette.accent else GuardianTheme.materialColors.secondary,
                        emphasis = if (lowers) palette.accent else null,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keep a lone trailing factor at half width instead of stretching it.
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(GuardianTheme.spacing.md))

        Row(horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm)) {
            SecondaryPillButton(
                text = stringResource(R.string.action_scan_area),
                icon = GuardianIcons.Refresh,
                onClick = onRescan,
                enabled = !score.isLiveScanning,
                modifier = Modifier.weight(1f),
            )
            PrimaryPillButton(
                text = stringResource(R.string.action_guide_home),
                icon = GuardianIcons.HomePin,
                onClick = onNavigateHome,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The circular score gauge. */
@Composable
private fun ScoreRing(
    score: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 80.dp,
) {
    val sweep by animateFloatAsState(
        targetValue = score.coerceIn(0, 100) / 100f,
        animationSpec = tween(700),
        label = "scoreSweep",
    )
    val track = GuardianTheme.materialColors.surfaceVariant
    val description = stringResource(R.string.safety_score_description, score)

    Box(
        modifier = modifier
            .size(diameter)
            // One description for the whole gauge; the arc and the digits are the same fact.
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clearAndSetSemantics { },
        ) {
            Text(
                text = stringResource(R.string.safety_score_percent, score),
                style = GuardianTheme.type.headlineMd,
                color = GuardianTheme.materialColors.onSurface,
            )
            Text(
                text = stringResource(R.string.safety_score_unit),
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeltaBadge(delta: Int) {
    val dropped = delta < 0
    val palette = safetyColorsFor(if (dropped) SafetyLevel.Elevated else SafetyLevel.Secure)
    Row(
        modifier = Modifier
            .clip(GuardianTheme.shapes.sm)
            .background(palette.container)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = if (dropped) GuardianIcons.TrendingDown else GuardianIcons.TrendingUp,
            contentDescription = null,
            tint = palette.onContainer,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = stringResource(
                if (dropped) R.string.safety_delta_down else R.string.safety_delta_up,
                delta,
            ),
            style = GuardianTheme.type.labelSm,
            color = palette.onContainer,
        )
    }
}

/** 44dp pill actions sized for the inside of a card, below the 52dp standard button. */
@Composable
internal fun PrimaryPillButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = GuardianTheme.shapes.pill,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = GuardianTheme.materialColors.primary,
            contentColor = GuardianTheme.materialColors.onPrimary,
        ),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(GuardianTheme.spacing.xs))
        Text(text, style = GuardianTheme.type.labelMd)
    }
}

@Composable
internal fun SecondaryPillButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = GuardianTheme.shapes.pill,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = GuardianTheme.materialColors.secondaryContainer,
            contentColor = GuardianTheme.materialColors.onSecondaryContainer,
            disabledContainerColor = GuardianTheme.materialColors.secondaryContainer.copy(alpha = 0.5f),
            disabledContentColor = GuardianTheme.materialColors.onSecondaryContainer.copy(alpha = 0.5f),
        ),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(GuardianTheme.spacing.xs))
        Text(text, style = GuardianTheme.type.labelMd)
    }
}

/**
 * Picks a glyph that describes what the factor actually measures.
 *
 * Keyed on the factor id rather than carried on the domain model, so the data layer stays
 * free of UI concerns. Anything unrecognised falls back to a trend arrow, which still
 * tells the user which way the factor is pushing the score.
 */
private fun factorIcon(
    id: String,
    direction: FactorDirection,
): androidx.compose.ui.graphics.vector.ImageVector = when (id) {
    "away" -> GuardianIcons.Clock
    "havens" -> GuardianIcons.Shield
    "light" -> GuardianIcons.Sun
    "decay" -> GuardianIcons.Hourglass
    "range" -> GuardianIcons.MapPin
    "perimeter" -> GuardianIcons.HomePin
    "check" -> GuardianIcons.Refresh
    else -> when (direction) {
        FactorDirection.Lowers -> GuardianIcons.TrendingDown
        FactorDirection.Raises -> GuardianIcons.TrendingUp
        FactorDirection.Neutral -> GuardianIcons.Clock
    }
}
