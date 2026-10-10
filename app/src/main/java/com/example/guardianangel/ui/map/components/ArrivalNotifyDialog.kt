package com.example.guardianangel.ui.map.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.example.guardianangel.domain.model.EmergencyContact
import com.example.guardianangel.ui.components.GuardianOutlinedButton
import com.example.guardianangel.ui.components.GuardianPrimaryButton
import com.example.guardianangel.ui.components.GuardianTextField
import com.example.guardianangel.ui.home.components.InitialsAvatar
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianTheme

/**
 * "Shall I tell someone when you get there?"
 *
 * Asked once, at the moment she picks a destination and before she sets off, because
 * that is the only point where the question is obviously about *this* walk. As a
 * settings toggle it would be either always on — texting a guardian every time she walks
 * to the shop, which is how people get muted — or forgotten.
 *
 * Telling nobody is a first-class answer and is reachable in one tap, not hidden behind
 * a dismiss. The message field is optional and is sent as her own words.
 */
@Composable
fun ArrivalNotifyDialog(
    destinationName: String,
    guardians: List<EmergencyContact>,
    onDismiss: () -> Unit,
    onStart: (contactId: String?, message: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    val where = destinationName.ifBlank { "your destination" }

    Column(
        modifier = modifier
            .padding(GuardianTheme.spacing.lg)
            .shadow(16.dp, GuardianTheme.shapes.xl)
            .clip(GuardianTheme.shapes.xl)
            .background(GuardianTheme.materialColors.surfaceContainerLowest)
            .border(1.dp, GuardianTheme.colors.borderEmphasis, GuardianTheme.shapes.xl)
            .padding(GuardianTheme.spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = GuardianIcons.ShieldCheck,
                contentDescription = null,
                tint = GuardianTheme.materialColors.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.size(GuardianTheme.spacing.sm))
            Text(
                text = "Tell someone when you arrive?",
                style = GuardianTheme.type.headlineMd,
                color = GuardianTheme.materialColors.onSurface,
            )
        }

        Spacer(Modifier.height(GuardianTheme.spacing.sm))
        Text(
            text = if (guardians.isEmpty()) {
                "Add a guardian and I can text them the moment you reach $where."
            } else {
                "I'll text them the moment you reach $where, with the time you got there."
            },
            style = GuardianTheme.type.bodySm,
            color = GuardianTheme.materialColors.onSurfaceVariant,
        )

        if (guardians.isNotEmpty()) {
            Spacer(Modifier.height(GuardianTheme.spacing.md))
            guardians.sortedBy { it.priority }.forEach { guardian ->
                val selected = guardian.id == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GuardianTheme.shapes.lg)
                        .background(
                            if (selected) {
                                GuardianTheme.materialColors.primaryContainer
                            } else {
                                GuardianTheme.materialColors.surfaceContainerLow
                            }
                        )
                        // Tapping the selected guardian again clears it, so "actually,
                        // don't" does not require starting the dialog over.
                        .clickable { selectedId = if (selected) null else guardian.id }
                        .padding(GuardianTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
                ) {
                    InitialsAvatar(initials = guardian.initials)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = guardian.name,
                            style = GuardianTheme.type.labelMd,
                            color = GuardianTheme.materialColors.onSurface,
                        )
                        Text(
                            text = guardian.relationship,
                            style = GuardianTheme.type.labelSm,
                            color = GuardianTheme.materialColors.onSurfaceVariant,
                        )
                    }
                    if (selected) {
                        Icon(
                            imageVector = GuardianIcons.Check,
                            contentDescription = "Will be told",
                            tint = GuardianTheme.materialColors.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.height(GuardianTheme.spacing.xs))
            }

            if (selectedId != null) {
                Spacer(Modifier.height(GuardianTheme.spacing.sm))
                GuardianTextField(
                    value = message,
                    onValueChange = { message = it.take(MAX_MESSAGE_LENGTH) },
                    label = "Add a message (optional)",
                    leadingIcon = GuardianIcons.Broadcast,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(GuardianTheme.spacing.md))
        GuardianPrimaryButton(
            text = if (selectedId == null) "Start walking" else "Start walking & tell them",
            onClick = { onStart(selectedId, message.takeIf { selectedId != null }) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(GuardianTheme.spacing.xs))
        GuardianOutlinedButton(
            text = "Cancel",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One SMS part of headroom once the app's own sentence is appended. */
private const val MAX_MESSAGE_LENGTH = 120
