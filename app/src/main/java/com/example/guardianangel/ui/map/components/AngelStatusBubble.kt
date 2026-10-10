package com.example.guardianangel.ui.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.guardianangel.domain.model.StatusCategory
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.mascot.AngelMood
import com.example.guardianangel.ui.theme.GuardianTheme

/**
 * Floating status bubble displaying discreet, calm reassurance from Angel.
 *
 * Invariants:
 * 1. Uses structured state only; never invents facts or exposes raw transcripts.
 * 2. Gentle animation, avoids flashing/strobing except critical emergency tier.
 * 3. Legible in both light and dark themes using Guardian design tokens.
 */
@Composable
fun AngelStatusBubble(
    message: String,
    category: StatusCategory,
    mood: AngelMood,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val icon = when (category) {
        StatusCategory.ListeningStatus -> GuardianIcons.Waveform
        StatusCategory.NavigationStatus -> GuardianIcons.ShieldCheck
        StatusCategory.LocationStatus -> GuardianIcons.MapPin
        StatusCategory.SafetyAssessmentStatus -> GuardianIcons.Activity
        StatusCategory.HomeStatus -> GuardianIcons.Moon
        StatusCategory.EmergencyStatus -> GuardianIcons.CrisisAlert
    }

    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomEnd = 16.dp,
        bottomStart = 4.dp
    )

    AnimatedVisibility(
        visible = visible && message.isNotBlank(),
        enter = fadeIn() + scaleIn(initialScale = 0.88f),
        exit = fadeOut() + scaleOut(targetScale = 0.88f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .shadow(elevation = 6.dp, shape = bubbleShape)
                .clip(bubbleShape)
                .background(GuardianTheme.materialColors.surfaceContainerLowest)
                .border(1.dp, GuardianTheme.colors.accentSoft, bubbleShape)
                .clickable { onDismiss() }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics {
                    contentDescription = "Angel status: $message"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (mood == AngelMood.Critical) {
                    GuardianTheme.materialColors.error
                } else {
                    GuardianTheme.materialColors.primary
                },
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = message,
                style = GuardianTheme.type.labelMd,
                color = GuardianTheme.materialColors.onSurface,
            )
        }
    }
}
