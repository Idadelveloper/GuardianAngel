package com.example.guardianangel.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.guardianangel.data.GuardianSamples
import com.example.guardianangel.domain.model.AccountSnapshot
import com.example.guardianangel.domain.model.Codeword
import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.model.ContactPresence
import com.example.guardianangel.domain.model.EmergencyContact
import com.example.guardianangel.domain.model.ListeningSensitivity
import com.example.guardianangel.domain.repository.AccountRepository
import com.example.guardianangel.domain.repository.CodewordRepository
import com.example.guardianangel.domain.repository.ContactsRepository
import com.example.guardianangel.ui.activities.SegmentedToggle
import com.example.guardianangel.ui.components.GuardianCard
import com.example.guardianangel.ui.components.GuardianOutlinedButton
import com.example.guardianangel.ui.components.GuardianPrimaryButton
import com.example.guardianangel.ui.components.GuardianStackScaffold
import com.example.guardianangel.ui.components.GuardianTextField
import com.example.guardianangel.ui.home.components.InitialsAvatar
import com.example.guardianangel.ui.home.components.SectionHeader
import com.example.guardianangel.ui.home.components.TonalPill
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.onboarding.AssuranceCard
import com.example.guardianangel.ui.onboarding.ChipRow
import com.example.guardianangel.ui.onboarding.rememberContactPicker
import com.example.guardianangel.ui.onboarding.GuardianSwitch
import com.example.guardianangel.ui.onboarding.TIER_COPY
import com.example.guardianangel.ui.onboarding.levelFor
import com.example.guardianangel.ui.theme.GuardianAngelTheme
import com.example.guardianangel.ui.theme.GuardianTheme
import com.example.guardianangel.ui.theme.safetyColorsFor
import kotlinx.coroutines.launch

// =====================================================================================
// Codewords
// =====================================================================================

/**
 * Manage the four codeword tiers and the disarm PIN.
 *
 * Edits are held locally and committed on save, so a half-typed phrase never becomes a
 * live trigger. The emergency tier can be re-worded but not removed — losing the 911 tier
 * by accident is not a recoverable mistake.
 */
@Composable
fun SettingsCodewordsRoute(
    codewordRepository: CodewordRepository,
    accountRepository: AccountRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val codewords by codewordRepository.observeCodewords()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val account by accountRepository.observeAccount()
        .collectAsStateWithLifecycle(initialValue = null)

    SettingsCodewordsScreen(
        codewords = codewords,
        account = account,
        onBack = onBack,
        onSave = { edits ->
            scope.launch {
                edits.forEach { (id, phrase) ->
                    codewords.firstOrNull { it.id == id }?.let {
                        codewordRepository.updateCodeword(it.copy(phrase = phrase))
                    }
                }
                onBack()
            }
        },
        onRehearse = { scope.launch { codewordRepository.rehearse(it) } },
        modifier = modifier,
    )
}

@Composable
fun SettingsCodewordsScreen(
    codewords: List<Codeword>,
    account: AccountSnapshot?,
    onBack: () -> Unit,
    onSave: (Map<String, String>) -> Unit,
    onRehearse: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val edits = remember(codewords) {
        mutableStateMapOf<String, String>().apply {
            codewords.forEach { put(it.id, it.phrase) }
        }
    }

    GuardianStackScaffold(
        title = "Codewords",
        onBack = onBack,
        modifier = modifier,
        bottomBar = {
            GuardianPrimaryButton(
                text = "Save codewords",
                onClick = { onSave(edits.toMap()) },
                leadingIcon = GuardianIcons.Check,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        AssuranceCard(
            icon = GuardianIcons.Lock,
            title = "Matched on this phone",
            body = "Your codewords are compared against audio locally. They are never " +
                "sent anywhere.",
        )

        CodewordTier.entries.forEach { tier ->
            val codeword = codewords.firstOrNull { it.tier == tier } ?: return@forEach
            val copy = TIER_COPY.first { it.tier == tier }
            val palette = safetyColorsFor(levelFor(tier))

            GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TonalPill(
                        text = copy.name,
                        container = palette.container,
                        content = palette.onContainer,
                    )
                    Spacer(Modifier.weight(1f))
                    if (tier == CodewordTier.Emergency) {
                        // Protected: re-wordable, never deletable.
                        TonalPill(
                            text = "Protected",
                            icon = GuardianIcons.Lock,
                            container = GuardianTheme.materialColors.surfaceContainerLow,
                            content = GuardianTheme.materialColors.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(GuardianTheme.spacing.xs))
                Text(
                    text = copy.purpose,
                    style = GuardianTheme.type.bodySm,
                    color = GuardianTheme.materialColors.onSurfaceVariant,
                )
                Spacer(Modifier.height(GuardianTheme.spacing.md))
                GuardianTextField(
                    value = edits[codeword.id].orEmpty(),
                    onValueChange = { edits[codeword.id] = it },
                    label = "Phrase",
                    leadingIcon = GuardianIcons.Mic,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(GuardianTheme.spacing.sm))
                GuardianOutlinedButton(
                    text = "Rehearse this word",
                    onClick = { onRehearse(codeword.id) },
                    leadingIcon = GuardianIcons.Waveform,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
            SectionHeader(title = "Disarm PIN", icon = GuardianIcons.Lock)
            Spacer(Modifier.height(GuardianTheme.spacing.sm))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
            ) {
                repeat(account?.disarmPin?.length ?: 4) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (account?.disarmPin?.isSet == true) {
                                    GuardianTheme.materialColors.onSurface
                                } else {
                                    GuardianTheme.materialColors.surfaceContainerHigh
                                }
                            ),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Change",
                    style = GuardianTheme.type.labelMd,
                    color = GuardianTheme.materialColors.primary,
                    modifier = Modifier
                        .clip(GuardianTheme.shapes.sm)
                        .clickable { /* PIN dialog is not part of this build. */ }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(GuardianTheme.spacing.md))
            Text(
                text = "Reverse it to fake a disarm",
                style = GuardianTheme.type.labelMd,
                color = GuardianTheme.materialColors.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Typing your PIN backwards looks like you stood the alert down, " +
                    "but quietly keeps your circle alerted instead.",
                style = GuardianTheme.type.bodySm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}

// =====================================================================================
// Guardians
// =====================================================================================

/** Manage the trusted circle — up to five, ranked by priority. */
@Composable
fun SettingsGuardiansRoute(
    repository: ContactsRepository,
    accountRepository: AccountRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val contacts by repository.observeContacts()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val account by accountRepository.observeAccount()
        .collectAsStateWithLifecycle(initialValue = null)

    // Asked for here rather than at the moment of an alert.
    //
    // A permission dialog is the last thing that should appear between a duress hold
    // and a message going out, and this is the screen where the reason for it is
    // self-evident: she is adding the people it will text.
    val context = LocalContext.current
    var canSendSilently by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> canSendSilently = granted }

    // Calling needs its own permission, asked for only when she turns the setting on.
    // Requesting it up front, next to the microphone, would read as an app asking to
    // make phone calls for no stated reason.
    var canPlaceCalls by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val callLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        canPlaceCalls = granted
        // The setting is saved either way. Refused, the alert still posts a one-tap call
        // notification, which is weaker but not nothing — and the copy says which it is.
        scope.launch { accountRepository.setCallGuardianOnEmergency(true) }
    }

    SettingsGuardiansScreen(
        contacts = contacts,
        canSendSilently = canSendSilently,
        onEnableSilentAlerts = { smsLauncher.launch(Manifest.permission.SEND_SMS) },
        callOnEmergency = account?.profile?.callGuardianOnEmergency ?: true,
        canPlaceCalls = canPlaceCalls,
        onCallOnEmergencyChange = { enabled ->
            if (enabled && !canPlaceCalls) {
                callLauncher.launch(Manifest.permission.CALL_PHONE)
            } else {
                scope.launch { accountRepository.setCallGuardianOnEmergency(enabled) }
            }
        },
        onBack = onBack,
        onAdd = { name, phone, relationship ->
            scope.launch {
                repository.addContact(
                    EmergencyContact(
                        id = "g-${System.currentTimeMillis()}",
                        name = name,
                        relationship = relationship,
                        phoneNumber = phone,
                        priority = contacts.size + 1,
                        presence = ContactPresence.Unknown,
                    )
                )
            }
        },
        onRemove = { scope.launch { repository.removeContact(it) } },
        onTestPing = { scope.launch { repository.sendTestPing(it) } },
        modifier = modifier,
    )
}

private const val MAX_GUARDIANS = 5
private val RELATIONSHIPS = listOf("Partner", "Parent", "Sibling", "Friend", "Roommate")

@Composable
fun SettingsGuardiansScreen(
    contacts: List<EmergencyContact>,
    onBack: () -> Unit,
    onAdd: (name: String, phone: String, relationship: String) -> Unit,
    onRemove: (String) -> Unit,
    onTestPing: (String) -> Unit,
    modifier: Modifier = Modifier,
    canSendSilently: Boolean = true,
    onEnableSilentAlerts: () -> Unit = {},
    callOnEmergency: Boolean = true,
    canPlaceCalls: Boolean = false,
    onCallOnEmergencyChange: (Boolean) -> Unit = {},
) {
    var showForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf(RELATIONSHIPS.first()) }

    val pickContact = rememberContactPicker { picked ->
        name = picked.name
        phone = picked.phoneNumber
        showForm = true
    }

    GuardianStackScaffold(title = "Your guardians", onBack = onBack, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Trusted circle",
                style = GuardianTheme.type.labelLg,
                color = GuardianTheme.materialColors.onSurface,
                modifier = Modifier.weight(1f),
            )
            TonalPill(text = "${contacts.size} of $MAX_GUARDIANS")
        }

        contacts.sortedBy { it.priority }.forEach { contact ->
            GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InitialsAvatar(initials = contact.initials)
                    Spacer(Modifier.size(GuardianTheme.spacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.xs),
                        ) {
                            Text(
                                text = contact.name,
                                style = GuardianTheme.type.labelLg,
                                color = GuardianTheme.materialColors.onSurface,
                            )
                            if (contact.priority == 1) TonalPill(text = "First call")
                        }
                        Text(
                            text = "${contact.relationship} · ${contact.phoneNumber}",
                            style = GuardianTheme.type.labelSm,
                            color = GuardianTheme.materialColors.onSurfaceVariant,
                        )
                    }
                    if (contact.priority != 1) {
                        IconButton(onClick = { onRemove(contact.id) }) {
                            Icon(
                                imageVector = GuardianIcons.Close,
                                contentDescription = "Remove ${contact.name}",
                                tint = GuardianTheme.colors.iconMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(GuardianTheme.spacing.md))
                GuardianOutlinedButton(
                    text = "Send a test ping",
                    onClick = { onTestPing(contact.id) },
                    leadingIcon = GuardianIcons.Broadcast,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (showForm && contacts.size < MAX_GUARDIANS) {
            GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
                SectionHeader(title = "Add a guardian", icon = GuardianIcons.Plus)
                Spacer(Modifier.height(GuardianTheme.spacing.md))
                GuardianOutlinedButton(
                    text = "Choose from contacts",
                    onClick = pickContact,
                    leadingIcon = GuardianIcons.Users,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(GuardianTheme.spacing.md))
                GuardianTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Name",
                    leadingIcon = GuardianIcons.Users,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(GuardianTheme.spacing.md))
                GuardianTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { c -> c.isDigit() || c in "+() -" } },
                    label = "Mobile number",
                    leadingIcon = GuardianIcons.Phone,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(GuardianTheme.spacing.md))
                ChipRow(
                    options = RELATIONSHIPS,
                    selected = relationship,
                    onSelect = { relationship = it },
                )
                Spacer(Modifier.height(GuardianTheme.spacing.md))
                GuardianPrimaryButton(
                    text = "Save guardian",
                    onClick = {
                        onAdd(name.trim(), phone.trim(), relationship)
                        name = ""; phone = ""; showForm = false
                    },
                    enabled = name.isNotBlank() && phone.length >= 7,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else if (contacts.size < MAX_GUARDIANS) {
            GuardianOutlinedButton(
                text = "Add a guardian",
                onClick = { showForm = true },
                leadingIcon = GuardianIcons.Plus,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
            SectionHeader(title = "How an alert reaches them", icon = GuardianIcons.Broadcast)
            Spacer(Modifier.height(GuardianTheme.spacing.md))

            Text(
                text = if (canSendSilently) {
                    "Every alert texts your guardians your location straight away, " +
                        "without opening anything on your screen."
                } else {
                    "Right now an alert opens your messaging app with the text ready, " +
                        "which needs one tap from you. Allow sending texts and it goes " +
                        "out on its own."
                },
                style = GuardianTheme.type.bodySm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )

            if (!canSendSilently) {
                Spacer(Modifier.height(GuardianTheme.spacing.md))
                GuardianOutlinedButton(
                    text = "Allow sending texts",
                    onClick = onEnableSilentAlerts,
                    leadingIcon = GuardianIcons.Broadcast,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(GuardianTheme.spacing.md))
            SwitchRow(
                title = "Also ring my first guardian",
                description = if (!callOnEmergency) {
                    "Your emergency codeword and a held SOS will text, but nobody's " +
                        "phone will ring."
                } else if (canPlaceCalls) {
                    "On your emergency codeword or a held SOS only. " +
                        "${contacts.minByOrNull { it.priority }?.name ?: "Your first guardian"} " +
                        "is called right after the text goes out."
                } else {
                    "On your emergency codeword or a held SOS only. Without permission to " +
                        "place calls I'll put a one-tap Call button on your lock screen " +
                        "instead of dialling."
                },
                checked = callOnEmergency,
                onCheckedChange = onCallOnEmergencyChange,
            )
        }

        AssuranceCard(
            icon = GuardianIcons.Shield,
            title = "They can't see you unless you need them",
            body = "Guardians only receive your location while an alert is live. Nothing " +
                "here calls emergency services, and nothing in this app will ever tell " +
                "you it has.",
        )
    }
}

// =====================================================================================
// Voice & listening
// =====================================================================================

/** Acoustic sensitivity, whisper detection and recalibration. */
@Composable
fun SettingsVoiceRoute(
    repository: AccountRepository,
    onBack: () -> Unit,
    onRecalibrate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val account by repository.observeAccount()
        .collectAsStateWithLifecycle(initialValue = null)

    SettingsVoiceScreen(
        account = account,
        onBack = onBack,
        onRecalibrate = onRecalibrate,
        onSensitivity = { scope.launch { repository.setSensitivity(it) } },
        onWhisper = { scope.launch { repository.setWhisperDetection(it) } },
        onNoiseCancellation = { scope.launch { repository.setNoiseCancellation(it) } },
        modifier = modifier,
    )
}

@Composable
fun SettingsVoiceScreen(
    account: AccountSnapshot?,
    onBack: () -> Unit,
    onRecalibrate: () -> Unit,
    onSensitivity: (ListeningSensitivity) -> Unit,
    onWhisper: (Boolean) -> Unit,
    onNoiseCancellation: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = account?.voiceProfile

    GuardianStackScaffold(title = "Voice & listening", onBack = onBack, modifier = modifier) {
        GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Your voiceprint",
                        style = GuardianTheme.type.headlineMd,
                        color = GuardianTheme.materialColors.onSurface,
                    )
                    Text(
                        text = if (voice?.isCalibrated == true) {
                            "Calibrated · ${voice.clarityPercent}% clarity"
                        } else {
                            "Not calibrated yet"
                        },
                        style = GuardianTheme.type.bodySm,
                        color = GuardianTheme.materialColors.onSurfaceVariant,
                    )
                }
                TonalPill(
                    text = if (voice?.isCalibrated == true) "Active" else "Needed",
                    icon = GuardianIcons.Check,
                )
            }
            Spacer(Modifier.height(GuardianTheme.spacing.md))
            GuardianOutlinedButton(
                text = "Recalibrate (1 min)",
                onClick = onRecalibrate,
                leadingIcon = GuardianIcons.Refresh,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
            SectionHeader(title = "How closely I listen", icon = GuardianIcons.Waveform)
            Spacer(Modifier.height(GuardianTheme.spacing.sm))
            Text(
                text = "Whisper catches quiet codewords but reacts more often. Balanced " +
                    "suits most situations.",
                style = GuardianTheme.type.bodySm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
            Spacer(Modifier.height(GuardianTheme.spacing.md))
            SegmentedToggle(
                options = listOf("Low", "Balanced", "Whisper"),
                selectedIndex = (voice?.sensitivity ?: ListeningSensitivity.Balanced).ordinal,
                onSelect = { onSensitivity(ListeningSensitivity.entries[it]) },
            )
        }

        GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
            SwitchRow(
                title = "Whisper detection",
                description = "Picks up codewords said under your breath.",
                checked = voice?.whisperDetection ?: true,
                onCheckedChange = onWhisper,
            )
            Spacer(Modifier.height(GuardianTheme.spacing.md))
            SwitchRow(
                title = "Filter background noise",
                description = "Cuts traffic and rain so your voice stands out.",
                checked = voice?.noiseCancellation ?: true,
                onCheckedChange = onNoiseCancellation,
            )
        }

        AssuranceCard(
            icon = GuardianIcons.Lock,
            title = "Offline processing, always on",
            body = "Keyword matching runs in the phone's secure enclave and cannot be " +
                "turned off or routed to a server.",
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = GuardianTheme.type.labelLg,
                color = GuardianTheme.materialColors.onSurface,
            )
            Text(
                text = description,
                style = GuardianTheme.type.bodySm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(GuardianTheme.spacing.sm))
        GuardianSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Preview(showBackground = true, device = "id:pixel_8", heightDp = 1600)
@Composable
private fun CodewordsPreview() {
    GuardianAngelTheme {
        SettingsCodewordsScreen(
            codewords = GuardianSamples.codewords,
            account = null,
            onBack = {}, onSave = {}, onRehearse = {},
        )
    }
}
