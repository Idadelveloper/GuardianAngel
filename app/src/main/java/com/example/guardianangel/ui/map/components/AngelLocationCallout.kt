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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.map.AngelWhereAmI
import com.example.guardianangel.ui.theme.GuardianTheme

/**
 * Angel's answer to "where am I?", shown when the user taps her marker.
 *
 * Deliberately a card at the top of the map rather than a map info window. An info window
 * is drawn into a bitmap above the marker, which means it is unreadable at small zoom,
 * cannot be dismissed by anything but another tap, and disappears off-screen whenever the
 * marker is near the top edge — exactly where it ends up while the camera follows the
 * user. This sits in the overlay, in the same column as Angel's status bubble, so it
 * never fights the bottom navigation or the marker detail sheet for space.
 */
@Composable
fun AngelLocationCallout(
    callout: AngelWhereAmI.Callout?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = remember {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp)
    }

    // Held so the card keeps its text through the exit animation rather than collapsing
    // to an empty rectangle on the frame the callout clears.
    val shown = remember { Holder<AngelWhereAmI.Callout>() }
    callout?.let { shown.value = it }

    AnimatedVisibility(
        visible = callout != null,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
        modifier = modifier,
    ) {
        val content = shown.value ?: return@AnimatedVisibility
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 8.dp, shape = shape)
                .clip(shape)
                .background(GuardianTheme.materialColors.surfaceContainerLowest)
                .border(
                    width = 1.dp,
                    color = if (content.isSanctuary) {
                        GuardianTheme.colors.accentSoft
                    } else {
                        GuardianTheme.colors.borderEmphasis
                    },
                    shape = shape,
                )
                .clickable { onDismiss() }
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .semantics {
                    contentDescription = "${content.headline}. ${content.detail}"
                },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (content.isSanctuary) GuardianIcons.Moon else GuardianIcons.MapPin,
                    contentDescription = null,
                    tint = GuardianTheme.materialColors.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = content.headline,
                    style = GuardianTheme.type.labelMd,
                    color = GuardianTheme.materialColors.onSurface,
                )
            }
            Text(
                text = content.detail,
                style = GuardianTheme.type.bodySm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}

/** A plain holder; the value is read inside the same composition that wrote it. */
private class Holder<T> {
    var value: T? = null
}
