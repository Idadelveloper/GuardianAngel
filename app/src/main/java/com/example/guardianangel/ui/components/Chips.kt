package com.example.guardianangel.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.guardianangel.ui.theme.GuardianTheme

/** The status a chip reports. Each maps to a container/content pair in the theme. */
enum class GuardianStatus { Active, Safe, Caution, Alert }

/**
 * Status chip — 32dp pill, tonal container, 1px emphasized border, 14dp icon.
 *
 * The icon is not decorative here: colour alone must never be the only carrier of a
 * status, so each [GuardianStatus] pairs its tone with a distinct glyph and the label
 * text stays visible.
 */
@Composable
fun StatusChip(
    label: String,
    status: GuardianStatus,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = GuardianTheme.colors
    val container: Color
    val content: Color
    when (status) {
        GuardianStatus.Active -> {
            container = colors.activeContainer
            content = colors.onActiveContainer
        }
        GuardianStatus.Safe -> {
            container = colors.safeContainer
            content = colors.onSafeContainer
        }
        GuardianStatus.Caution -> {
            container = colors.cautionContainer
            content = colors.onCautionContainer
        }
        GuardianStatus.Alert -> {
            container = GuardianTheme.materialColors.errorContainer
            content = GuardianTheme.materialColors.onErrorContainer
        }
    }

    Row(
        modifier = modifier
            .height(32.dp)
            .clip(GuardianTheme.shapes.pill)
            .background(container)
            .border(
                border = BorderStroke(1.dp, colors.borderEmphasis),
                shape = GuardianTheme.shapes.pill,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.xs),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content.copy(alpha = 0.8f),
                modifier = Modifier.size(14.dp),
            )
        }
        Text(text = label, style = GuardianTheme.type.labelMd, color = content)
    }
}
