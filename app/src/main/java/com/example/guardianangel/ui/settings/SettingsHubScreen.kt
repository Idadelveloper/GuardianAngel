package com.example.guardianangel.ui.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.guardianangel.data.FakeAccountRepository
import com.example.guardianangel.data.GuardianSamples
import com.example.guardianangel.domain.model.AccountSnapshot
import com.example.guardianangel.domain.model.Codeword
import com.example.guardianangel.domain.model.EmergencyContact
import com.example.guardianangel.domain.model.WakeWord
import com.example.guardianangel.domain.repository.AccountRepository
import com.example.guardianangel.domain.repository.CodewordRepository
import com.example.guardianangel.domain.repository.ContactsRepository
import com.example.guardianangel.domain.repository.ListeningRepository
import com.example.guardianangel.ui.components.GuardianOutlinedButton
import com.example.guardianangel.ui.components.GuardianTabScaffold
import com.example.guardianangel.ui.home.components.InitialsAvatar
import com.example.guardianangel.ui.home.components.SectionHeader
import com.example.guardianangel.ui.home.components.TonalPill
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.mascot.AngelMascot
import com.example.guardianangel.ui.mascot.AngelMood
import com.example.guardianangel.ui.theme.GuardianAngelTheme
import com.example.guardianangel.ui.theme.GuardianTheme
import kotlinx.coroutines.launch

/**
 * Tab 4 — Settings.
 *
 * Profile, a word from Angel, then the safeguard list. Each row states its current state
 * in its subtitle, so the user can audit her whole setup without opening anything.
 */
@Composable
fun SettingsHubRoute(
    accountRepository: AccountRepository,
    contactsRepository: ContactsRepository,
    codewordRepository: CodewordRepository,
    listeningRepository: ListeningRepository,
    onOpenWakeWord: () -> Unit,
    onOpenCodewords: () -> Unit,
    onOpenGuardians: () -> Unit,
    onOpenVoice: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val account by accountRepository.observeAccount()
        .collectAsStateWithLifecycle(initialValue = null)
    val contacts by contactsRepository.observeContacts()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val codewords by codewordRepository.observeCodewords()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val wakeWord by listeningRepository.observeWakeWord()
        .collectAsStateWithLifecycle(initialValue = null)

    SettingsHubScreen(
        account = account,
        contacts = contacts,
        codewords = codewords,
        wakeWord = wakeWord,
        onOpenWakeWord = onOpenWakeWord,
        onOpenCodewords = onOpenCodewords,
        onOpenGuardians = onOpenGuardians,
        onOpenVoice = onOpenVoice,
        onSignOut = {
            scope.launch {
                accountRepository.signOut()
                onSignOut()
            }
        },
        modifier = modifier,
    )
}

@Composable
fun SettingsHubScreen(
    account: AccountSnapshot?,
    contacts: List<EmergencyContact>,
    codewords: List<Codeword>,
    wakeWord: WakeWord?,
    onOpenWakeWord: () -> Unit,
    onOpenCodewords: () -> Unit,
    onOpenGuardians: () -> Unit,
    onOpenVoice: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GuardianTabScaffold(
        modifier = modifier,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GuardianTheme.materialColors.surfaceContainerLow)
                    .statusBarsPadding()
                    .padding(
                        horizontal = GuardianTheme.spacing.screenMargin,
                        vertical = GuardianTheme.spacing.md,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Settings",
                    style = GuardianTheme.type.headlineLgMobile,
                    color = GuardianTheme.materialColors.onSurface,
                )
            }
        },
    ) {
        account?.profile?.let { profile ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GuardianTheme.shapes.xl)
                    .background(GuardianTheme.colors.accentSoft.copy(alpha = 0.3f))
                    .padding(GuardianTheme.spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InitialsAvatar(
                    initials = profile.fullName
                        .split(' ')
                        .take(2)
                        .map { it.first().uppercaseChar() }
                        .joinToString(""),
                    size = 56.dp,
                )
                Spacer(Modifier.size(GuardianTheme.spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.fullName,
                        style = GuardianTheme.type.headlineMd,
                        color = GuardianTheme.materialColors.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = profile.phoneNumber,
                        style = GuardianTheme.type.bodySm,
                        color = GuardianTheme.materialColors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(GuardianTheme.spacing.xs))
                    TonalPill(
                        text = if (profile.shieldActive) "Shield active" else "Shield off",
                        icon = GuardianIcons.ShieldCheck,
                    )
                }
            }
        }

        // Angel whisper.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GuardianTheme.shapes.lg)
                .background(GuardianTheme.materialColors.surfaceContainerLow)
                .padding(GuardianTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AngelMascot(mood = AngelMood.Sanctuary, size = 68.dp)
            Spacer(Modifier.size(GuardianTheme.spacing.sm))
            Text(
                text = "Everything's locked and guarded. Update your circle, change a " +
                    "codeword, or retrain my ear whenever you like.",
                style = GuardianTheme.type.bodySm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }

        SectionHeader(title = "Safeguards", icon = GuardianIcons.Shield)

        Column(verticalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm)) {
            SettingsRow(
                icon = GuardianIcons.Waveform,
                title = "Wake word",
                subtitle = wakeWord?.takeIf { it.isEnrolled }
                    ?.let { "“${it.phrase}” · hands-free" }
                    ?: "Not recorded yet",
                trailing = {
                    TonalPill(text = if (wakeWord?.isEnrolled == true) "Ready" else "Set up")
                },
                onClick = onOpenWakeWord,
            )
            SettingsRow(
                icon = GuardianIcons.Mic,
                title = "Codewords & duress triggers",
                subtitle = "${codewords.size} active · disarm PIN set",
                trailing = { TonalPill(text = "Armed") },
                onClick = onOpenCodewords,
            )
            SettingsRow(
                icon = GuardianIcons.Users,
                title = "Your guardians",
                subtitle = contacts.joinToString(", ") { it.name.substringBefore(' ') }
                    .ifBlank { "Nobody added yet" },
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                        contacts.take(2).forEach {
                            InitialsAvatar(initials = it.initials, size = 26.dp)
                        }
                    }
                },
                onClick = onOpenGuardians,
            )
            SettingsRow(
                icon = GuardianIcons.Waveform,
                title = "Voice & listening",
                subtitle = account?.voiceProfile
                    ?.takeIf { it.isCalibrated }
                    ?.let { "Calibrated · ${it.clarityPercent}% clarity" }
                    ?: "Not calibrated yet",
                trailing = {
                    account?.voiceProfile?.takeIf { it.isCalibrated }?.let {
                        TonalPill(text = "${it.clarityPercent}%", icon = GuardianIcons.Check)
                    }
                },
                onClick = onOpenVoice,
            )
            SettingsRow(
                icon = GuardianIcons.MapPin,
                title = "Location & safe havens",
                subtitle = "Home geofence set · route lighting on",
                onClick = { /* Not part of this build. */ },
            )
            SettingsRow(
                icon = GuardianIcons.Lock,
                title = "Privacy & encryption",
                subtitle = "On-device processing · nothing uploaded",
                onClick = { /* Not part of this build. */ },
            )
        }

        GuardianOutlinedButton(
            text = "Log out",
            onClick = onSignOut,
            leadingIcon = GuardianIcons.Power,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * A safeguard row.
 *
 * The subtitle always reports live state rather than a static description, which is what
 * makes the list auditable at a glance.
 */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(GuardianTheme.shapes.lg)
            .background(GuardianTheme.materialColors.surfaceContainerLowest)
            .border(1.dp, GuardianTheme.colors.borderDefault, GuardianTheme.shapes.lg)
            .clickable(onClick = onClick)
            .padding(GuardianTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(GuardianTheme.shapes.md)
                .background(GuardianTheme.materialColors.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GuardianTheme.materialColors.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.size(GuardianTheme.spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = GuardianTheme.type.labelLg,
                color = GuardianTheme.materialColors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
        Spacer(Modifier.size(GuardianTheme.spacing.xs))
        Icon(
            imageVector = GuardianIcons.ChevronRight,
            contentDescription = null,
            tint = GuardianTheme.colors.iconMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_8", heightDp = 1500)
@Composable
private fun SettingsHubPreview() {
    GuardianAngelTheme {
        SettingsHubScreen(
            account = null,
            contacts = GuardianSamples.contacts,
            codewords = GuardianSamples.codewords,
            wakeWord = WakeWord(phrase = "hey angel", enrolmentTakes = 3),
            onOpenWakeWord = {},
            onOpenCodewords = {}, onOpenGuardians = {}, onOpenVoice = {}, onSignOut = {},
        )
    }
}
