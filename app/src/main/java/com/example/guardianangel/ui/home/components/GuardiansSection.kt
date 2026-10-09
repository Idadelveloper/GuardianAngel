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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.guardianangel.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.guardianangel.domain.model.ContactPresence
import com.example.guardianangel.domain.model.EmergencyContact
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianTheme
import com.example.guardianangel.ui.theme.SafetyLevel
import com.example.guardianangel.ui.theme.safetyColorsFor

/**
 * The trusted circle, ordered by the priority the user set during onboarding.
 *
 * Presence matters here in a way it would not in an ordinary contacts list: knowing that
 * the top-priority guardian is offline *before* an incident is the point, so it is shown
 * on the row rather than hidden behind a tap.
 */
@Composable
fun GuardiansSection(
    contacts: List<EmergencyContact>,
    onPing: (EmergencyContact) -> Unit,
    onCall: (EmergencyContact) -> Unit,
    modifier: Modifier = Modifier,
) {
    val online = contacts.count { it.presence == ContactPresence.Online }

    Column(modifier = modifier) {
        SectionHeader(title = stringResource(R.string.guardians_title), icon = GuardianIcons.Users) {
            TonalPill(
                text = pluralStringResource(R.plurals.guardians_online, online, online),
                container = GuardianTheme.materialColors.surfaceContainer,
                content = GuardianTheme.materialColors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(GuardianTheme.spacing.sm))

        contacts.sortedBy { it.priority }.forEach { contact ->
            GuardianRow(
                contact = contact,
                onPing = { onPing(contact) },
                onCall = { onCall(contact) },
                modifier = Modifier.padding(top = GuardianTheme.spacing.sm),
            )
        }
    }
}

@Composable
private fun GuardianRow(
    contact: EmergencyContact,
    onPing: () -> Unit,
    onCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(GuardianTheme.shapes.lg)
            .background(GuardianTheme.materialColors.surfaceContainerLowest)
            .border(1.dp, GuardianTheme.colors.borderDefault, GuardianTheme.shapes.lg)
            .padding(GuardianTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
    ) {
        Box {
            InitialsAvatar(initials = contact.initials)
            // Presence dot, ringed against the card so it reads on any avatar.
            val presenceColor = when (contact.presence) {
                ContactPresence.Online -> safetyColorsFor(SafetyLevel.Secure).accent
                ContactPresence.Offline -> GuardianTheme.colors.iconMuted
                ContactPresence.Unknown -> GuardianTheme.materialColors.outline
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(GuardianTheme.materialColors.surfaceContainerLowest)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(presenceColor),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.xs),
            ) {
                Text(
                    text = contact.name,
                    style = GuardianTheme.type.labelMd,
                    color = GuardianTheme.materialColors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (contact.priority == 1) {
                    TonalPill(text = stringResource(R.string.guardians_top_priority))
                }
            }
            Text(
                text = run {
                    val presence = stringResource(
                        when (contact.presence) {
                            ContactPresence.Online -> R.string.presence_online
                            ContactPresence.Offline -> R.string.presence_offline
                            ContactPresence.Unknown -> R.string.presence_unknown
                        }
                    )
                    contact.locationLabel
                        ?.let {
                            stringResource(
                                R.string.guardian_meta_located,
                                contact.relationship,
                                presence,
                                it,
                            )
                        }
                        ?: stringResource(
                            R.string.guardian_meta,
                            contact.relationship,
                            presence,
                        )
                },
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(
            onClick = onPing,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = GuardianTheme.materialColors.surfaceContainer,
                contentColor = GuardianTheme.materialColors.onSurfaceVariant,
            ),
        ) {
            Icon(
                imageVector = GuardianIcons.Broadcast,
                contentDescription = stringResource(R.string.action_ping_guardian, contact.name),
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(
            onClick = onCall,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = GuardianTheme.colors.accentSoft,
                contentColor = GuardianTheme.colors.onAccentSoft,
            ),
        ) {
            Icon(
                imageVector = GuardianIcons.Phone,
                contentDescription = stringResource(R.string.action_call_guardian, contact.name),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
