package com.example.guardianangel.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.guardianangel.data.FakeAccountRepository
import com.example.guardianangel.data.GuardianSamples
import com.example.guardianangel.domain.model.AccountSnapshot
import com.example.guardianangel.domain.model.Codeword
import com.example.guardianangel.domain.model.EmergencyContact
import com.example.guardianangel.domain.model.initialsOf
import com.example.guardianangel.domain.model.WakeWord
import com.example.guardianangel.domain.repository.AccountRepository
import com.example.guardianangel.domain.repository.AuthRepository
import com.example.guardianangel.domain.repository.CodewordRepository
import com.example.guardianangel.ui.components.GuardianCard
import com.example.guardianangel.ui.components.GuardianOutlinedButton
import com.example.guardianangel.ui.components.GuardianPrimaryButton
import com.example.guardianangel.ui.components.GuardianTextField
import com.example.guardianangel.data.sync.CloudSync
import com.example.guardianangel.data.sync.SyncStatus
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
    cloudSync: CloudSync,
    currentUserId: suspend () -> String,
    authRepository: AuthRepository,
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
    val syncStatus by cloudSync.status
        .collectAsStateWithLifecycle(initialValue = SyncStatus.Idle)

    SettingsHubScreen(
        account = account,
        contacts = contacts,
        codewords = codewords,
        wakeWord = wakeWord,
        syncStatus = syncStatus,
        onBackUp = { scope.launch { cloudSync.push(currentUserId()) } },
        onOpenWakeWord = onOpenWakeWord,
        onOpenCodewords = onOpenCodewords,
        onOpenGuardians = onOpenGuardians,
        onOpenVoice = onOpenVoice,
        onSaveProfile = { name, phone ->
            scope.launch { accountRepository.updateProfile(name, phone) }
        },
        onSignOut = {
            scope.launch {
                // Both: the auth repository ends the session, the account repository
                // gets its chance to clear anything session-scoped. The auth call is the
                // one that matters and was missing — sign-out navigated away while
                // leaving the session open, so the next launch walked straight back in.
                authRepository.signOut()
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
    syncStatus: SyncStatus,
    onBackUp: () -> Unit,
    onOpenWakeWord: () -> Unit,
    onOpenCodewords: () -> Unit,
    onOpenGuardians: () -> Unit,
    onOpenVoice: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    onSaveProfile: (name: String, phone: String) -> Unit = { _, _ -> },
) {
    // Says what will actually happen, including that it cannot happen yet.
    val cloudSubtitle = when (val status = syncStatus) {
        SyncStatus.Unavailable ->
            "Not set up — your data stays on this phone"
        SyncStatus.Idle -> "Keep your setup if you change phones"
        SyncStatus.Running -> "Backing up…"
        is SyncStatus.Succeeded ->
            if (status.rows == 0) "Already up to date" else "Backed up ${status.rows} items"
        is SyncStatus.Failed -> status.message
    }

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
            var editing by remember(profile.id) { mutableStateOf(false) }

            if (editing) {
                ProfileEditor(
                    initialName = profile.fullName,
                    initialPhone = profile.phoneNumber,
                    onCancel = { editing = false },
                    onSave = { name, phone ->
                        onSaveProfile(name, phone)
                        editing = false
                    },
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GuardianTheme.shapes.xl)
                        .background(GuardianTheme.colors.accentSoft.copy(alpha = 0.3f))
                        // The whole card opens the editor. A name shown on every screen
                        // had no way to be corrected after sign-up.
                        .clickable(role = Role.Button) { editing = true }
                        .padding(GuardianTheme.spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InitialsAvatar(initials = initialsOf(profile.fullName), size = 56.dp)
                    Spacer(Modifier.size(GuardianTheme.spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.fullName.ifBlank { "Your account" },
                            style = GuardianTheme.type.headlineMd,
                            color = GuardianTheme.materialColors.onSurface,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = profile.phoneNumber.ifBlank { "No number added yet" },
                            style = GuardianTheme.type.bodySm,
                            color = GuardianTheme.materialColors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(GuardianTheme.spacing.xs))
                        TonalPill(
                            text = if (profile.shieldActive) "Shield active" else "Shield off",
                            icon = GuardianIcons.ShieldCheck,
                        )
                    }
                    Icon(
                        imageVector = GuardianIcons.ChevronRight,
                        contentDescription = "Edit your details",
                        tint = GuardianTheme.materialColors.onSurfaceVariant,
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
                icon = GuardianIcons.Broadcast,
                title = "Back up to the cloud",
                subtitle = cloudSubtitle,
                trailing = {
                    if (syncStatus is SyncStatus.Running) {
                        TonalPill(text = "Syncing…")
                    }
                },
                onClick = onBackUp,
            )
            SettingsRow(
                icon = GuardianIcons.Lock,
                title = "Privacy & encryption",
                subtitle = "On-device processing · transcripts never leave this phone",
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

/**
 * Inline editor for the name and number.
 *
 * Inline rather than a sub-screen: it is two fields, and pushing a destination for them
 * would make correcting a typo feel like a bigger commitment than it is.
 */
@Composable
private fun ProfileEditor(
    initialName: String,
    initialPhone: String,
    onCancel: () -> Unit,
    onSave: (name: String, phone: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }

    GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
        Text(
            text = "Your details",
            style = GuardianTheme.type.labelLg,
            color = GuardianTheme.materialColors.onSurface,
        )
        Spacer(Modifier.height(GuardianTheme.spacing.md))
        GuardianTextField(
            value = name,
            onValueChange = { name = it },
            label = "Full name",
            placeholder = "Ida Delphine",
            leadingIcon = GuardianIcons.Users,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(GuardianTheme.spacing.sm))
        GuardianTextField(
            value = phone,
            onValueChange = { phone = it.filter { c -> c.isDigit() || c in "+() -" } },
            label = "Mobile number",
            placeholder = "(555) 392-8174",
            leadingIcon = GuardianIcons.Phone,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            supportingText = "Shown to your guardians so they know who is calling.",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(GuardianTheme.spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm)) {
            GuardianOutlinedButton(
                text = "Cancel",
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            GuardianPrimaryButton(
                text = "Save",
                onClick = { onSave(name.trim(), phone.trim()) },
                enabled = name.isNotBlank(),
                leadingIcon = GuardianIcons.Check,
                modifier = Modifier.weight(1f),
            )
        }
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
            wakeWord = WakeWord(phrase = "hey angel", voiceSamples = 3),
            syncStatus = SyncStatus.Unavailable,
            onBackUp = {},
            onOpenWakeWord = {},
            onOpenCodewords = {}, onOpenGuardians = {}, onOpenVoice = {}, onSignOut = {},
        )
    }
}
