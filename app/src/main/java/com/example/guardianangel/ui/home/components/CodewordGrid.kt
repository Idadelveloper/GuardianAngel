package com.example.guardianangel.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.guardianangel.domain.model.Codeword
import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianTheme
import com.example.guardianangel.ui.theme.SafetyLevel
import com.example.guardianangel.ui.theme.safetyColorsFor

/** Tier presentation: the label, glyph and tone each escalation level carries. */
private data class TierStyle(
    val label: String,
    val icon: ImageVector,
    val description: String,
    val container: Color,
    val onContainer: Color,
)

@Composable
private fun styleFor(tier: CodewordTier): TierStyle = when (tier) {
    CodewordTier.Safe -> TierStyle(
        label = stringResource(R.string.tier_safe),
        icon = GuardianIcons.ShieldCheck,
        description = stringResource(R.string.tier_safe_desc),
        container = safetyColorsFor(SafetyLevel.Secure).container,
        onContainer = safetyColorsFor(SafetyLevel.Secure).onContainer,
    )
    CodewordTier.Caution -> TierStyle(
        label = stringResource(R.string.tier_caution),
        icon = GuardianIcons.Hourglass,
        description = stringResource(R.string.tier_caution_desc),
        container = safetyColorsFor(SafetyLevel.Guarded).container,
        onContainer = safetyColorsFor(SafetyLevel.Guarded).onContainer,
    )
    CodewordTier.Danger -> TierStyle(
        label = stringResource(R.string.tier_danger),
        icon = GuardianIcons.Warning,
        description = stringResource(R.string.tier_danger_desc),
        container = safetyColorsFor(SafetyLevel.Elevated).container,
        onContainer = safetyColorsFor(SafetyLevel.Elevated).onContainer,
    )
    CodewordTier.Emergency -> TierStyle(
        label = stringResource(R.string.tier_emergency),
        icon = GuardianIcons.CrisisAlert,
        description = stringResource(R.string.tier_emergency_desc),
        container = safetyColorsFor(SafetyLevel.High).container,
        onContainer = safetyColorsFor(SafetyLevel.High).onContainer,
    )
}

/**
 * The four codewords, as a two-column grid.
 *
 * Each tile names its tier in words and carries a distinct glyph as well as a tone, so
 * the escalation order survives both colour-blindness and a glance in the dark.
 */
@Composable
fun CodewordGrid(
    codewords: List<Codeword>,
    onCustomize: () -> Unit,
    modifier: Modifier = Modifier,
    isArmed: Boolean = true,
) {
    Column(modifier = modifier) {
        SectionHeader(
            title = stringResource(R.string.codewords_title),
            icon = GuardianIcons.Mic,
        ) {
            Text(
                text = stringResource(R.string.action_customize),
                style = GuardianTheme.type.labelMd,
                color = GuardianTheme.materialColors.primary,
                modifier = Modifier
                    .clip(GuardianTheme.shapes.sm)
                    .clickable(onClick = onCustomize)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        Spacer(Modifier.height(GuardianTheme.spacing.sm))

        // Ordered by escalation so the grid always reads safe -> emergency, regardless of
        // the order the data layer happens to return.
        val ordered = CodewordTier.entries.mapNotNull { tier ->
            codewords.firstOrNull { it.tier == tier }
        }

        ordered.chunked(2).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = GuardianTheme.spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
            ) {
                row.forEach { codeword ->
                    CodewordTile(
                        codeword = codeword,
                        isArmed = isArmed,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CodewordTile(
    codeword: Codeword,
    isArmed: Boolean,
    modifier: Modifier = Modifier,
) {
    val style = styleFor(codeword.tier)
    Column(
        modifier = modifier
            .clip(GuardianTheme.shapes.lg)
            .background(GuardianTheme.materialColors.surfaceContainer)
            .border(1.dp, GuardianTheme.colors.borderDefault, GuardianTheme.shapes.lg)
            .padding(GuardianTheme.spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TonalPill(
                text = style.label,
                container = style.container,
                content = style.onContainer,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = if (isArmed) style.icon else GuardianIcons.Lock,
                contentDescription = if (isArmed) null else stringResource(R.string.codeword_not_armed),
                tint = if (isArmed) {
                    GuardianTheme.materialColors.primary
                } else {
                    GuardianTheme.colors.iconMuted
                },
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.height(GuardianTheme.spacing.sm))
        Text(
            text = stringResource(R.string.codeword_quoted, codeword.phrase),
            style = GuardianTheme.type.labelMd,
            color = GuardianTheme.materialColors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = style.description,
            style = GuardianTheme.type.labelSm,
            color = GuardianTheme.materialColors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
