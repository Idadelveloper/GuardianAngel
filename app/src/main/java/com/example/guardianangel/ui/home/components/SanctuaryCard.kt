package com.example.guardianangel.ui.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.guardianangel.domain.model.SafetyScore
import com.example.guardianangel.ui.components.GuardianCard
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianTheme
import com.example.guardianangel.ui.theme.SafetyLevel
import com.example.guardianangel.ui.theme.safetyColorsFor

/**
 * The safety gauge, in two registers.
 *
 * At a safe haven it reads as reassurance — a full ring and a sentence saying why the
 * score will not move. On the street it becomes a live readout with the factors that are
 * pulling it down. Same component, because the user should recognise the same gauge in
 * both places rather than learn two.
 */
@Composable
fun SafetyGaugeCard(
    score: SafetyScore,
    title: String,
    statusChip: String,
    body: String,
    modifier: Modifier = Modifier,
    footer: @Composable (ColumnScope.() -> Unit)? = null,
) {
    val level = SafetyLevel.fromScore(score.score)
    val palette = safetyColorsFor(level)

    GuardianCard(modifier = modifier, contentPadding = GuardianTheme.spacing.lg) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = GuardianIcons.Shield,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(GuardianTheme.spacing.sm))
            Text(
                text = title,
                style = GuardianTheme.type.headlineMd,
                color = GuardianTheme.materialColors.onSurface,
                modifier = Modifier.weight(1f),
            )
            TonalPill(
                text = statusChip,
                container = palette.container,
                content = palette.onContainer,
            )
        }

        Spacer(Modifier.height(GuardianTheme.spacing.md))

        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(
                score = score.score,
                accent = palette.accent,
                caption = level.captionFor(score.score),
            )
            Spacer(Modifier.size(GuardianTheme.spacing.md))
            Text(
                text = body,
                style = GuardianTheme.type.bodyMd,
                color = GuardianTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }

        if (footer != null) {
            Spacer(Modifier.height(GuardianTheme.spacing.md))
            footer()
        }
    }
}

@Composable
private fun SafetyLevel.captionFor(score: Int): String = when {
    score >= 100 -> "OPTIMAL"
    else -> "SCORE"
}

/**
 * The circular gauge.
 *
 * Padded well inside its own bounds so the ring never touches the card edge, which the
 * design brief called out specifically.
 */
@Composable
fun ScoreRing(
    score: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    caption: String = "SCORE",
    diameter: Dp = 88.dp,
) {
    val sweep by animateFloatAsState(
        targetValue = score.coerceIn(0, 100) / 100f,
        animationSpec = tween(700),
        label = "ringSweep",
    )
    val track = GuardianTheme.materialColors.surfaceContainerHigh
    val description = "Safety score $score out of 100"

    Box(
        modifier = modifier
            .size(diameter)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = 9.dp.toPx()
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
                text = "$score%",
                style = GuardianTheme.type.headlineMd,
                color = GuardianTheme.materialColors.onSurface,
            )
            Text(
                text = caption,
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}

/** Two quick actions sitting side by side under the main CTA. */
@Composable
fun QuickActionRow(
    leftLabel: String,
    leftIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onLeft: () -> Unit,
    rightLabel: String,
    rightIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onRight: () -> Unit,
    modifier: Modifier = Modifier,
    rightIsDuress: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
    ) {
        QuickActionTile(
            label = leftLabel,
            icon = leftIcon,
            onClick = onLeft,
            container = GuardianTheme.materialColors.surfaceContainerLow,
            content = GuardianTheme.materialColors.onSurface,
            modifier = Modifier.weight(1f),
        )
        QuickActionTile(
            label = rightLabel,
            icon = rightIcon,
            onClick = onRight,
            container = if (rightIsDuress) {
                GuardianTheme.colors.accentSoft
            } else {
                GuardianTheme.materialColors.surfaceContainerLow
            },
            content = if (rightIsDuress) {
                GuardianTheme.colors.onAccentSoft
            } else {
                GuardianTheme.materialColors.onSurface
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickActionTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(GuardianTheme.shapes.lg)
            .background(container)
            .clickable(onClick = onClick)
            .padding(GuardianTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(20.dp),
        )
        Text(text = label, style = GuardianTheme.type.labelMd, color = content)
    }
}
