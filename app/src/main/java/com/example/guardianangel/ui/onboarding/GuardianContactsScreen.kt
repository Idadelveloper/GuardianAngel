package com.example.guardianangel.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.example.guardianangel.domain.model.ContactPresence
import com.example.guardianangel.domain.model.EmergencyContact
import com.example.guardianangel.domain.repository.AccountRepository
import com.example.guardianangel.domain.repository.ContactsRepository
import com.example.guardianangel.ui.components.GuardianCard
import com.example.guardianangel.ui.components.GuardianCheckbox
import com.example.guardianangel.ui.components.GuardianTextField
import com.example.guardianangel.ui.components.GuardianWizardScaffold
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.mascot.AngelMood
import com.example.guardianangel.ui.theme.GuardianAngelTheme
import com.example.guardianangel.ui.theme.GuardianTheme
import kotlinx.coroutines.launch

private val RELATIONSHIPS = listOf("Sister", "Partner", "Parent", "Best friend", "Roommate")

/**
 * Step 3 — the first guardian.
 *
 * Only one is required. The reference showed three contact slots plus an import button up
 * front, which makes setup feel like data entry; asking for one person and offering to
 * add more later gets users through the flow and into a protected state sooner.
 */
@Composable
fun GuardianContactsRoute(
    contactsRepository: ContactsRepository,
    accountRepository: AccountRepository,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    GuardianContactsScreen(
        modifier = modifier,
        onBack = onBack,
        onSave = { name, phone, relationship, shareGps, shareAudio ->
            scope.launch {
                contactsRepository.addContact(
                    EmergencyContact(
                        id = "g-${System.currentTimeMillis()}",
                        name = name,
                        relationship = relationship,
                        phoneNumber = phone,
                        priority = 1,
                        presence = ContactPresence.Unknown,
                    )
                )
                onContinue()
            }
        },
    )
}

@Composable
fun GuardianContactsScreen(
    onBack: () -> Unit,
    onSave: (
        name: String,
        phone: String,
        relationship: String,
        shareGps: Boolean,
        shareAudio: Boolean,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf(RELATIONSHIPS.first()) }
    var shareGps by remember { mutableStateOf(true) }
    var shareAudio by remember { mutableStateOf(true) }

    val canSave = name.isNotBlank() && phone.length >= 7

    GuardianWizardScaffold(
        modifier = modifier,
        stepLabel = "Step 3 of 5 · Your circle",
        progress = 0.6f,
        onBack = onBack,
        ctaLabel = "Save guardian & continue",
        onCta = { onSave(name.trim(), phone.trim(), relationship, shareGps, shareAudio) },
        ctaEnabled = canSave,
    ) {
        AngelSays(
            message = "Who should I reach first? Pick someone who usually has their " +
                "phone nearby. You can add more later.",
            mood = AngelMood.Resting,
            mascotSize = 92.dp,
        )

        GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
            GuardianTextField(
                value = name,
                onValueChange = { name = it },
                label = "Their name",
                placeholder = "Sarah Miller",
                leadingIcon = GuardianIcons.Users,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(GuardianTheme.spacing.md))
            GuardianTextField(
                value = phone,
                onValueChange = { phone = it.filter { c -> c.isDigit() || c in "+() -" } },
                label = "Mobile number",
                placeholder = "(555) 018-2234",
                leadingIcon = GuardianIcons.Phone,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(GuardianTheme.spacing.md))
            Text(
                text = "How do you know them?",
                style = GuardianTheme.type.labelMd,
                color = GuardianTheme.materialColors.onSurface,
            )
            Spacer(Modifier.height(GuardianTheme.spacing.sm))
            ChipRow(
                options = RELATIONSHIPS,
                selected = relationship,
                onSelect = { relationship = it },
            )
        }

        GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
            Text(
                text = "When I alert them",
                style = GuardianTheme.type.labelLg,
                color = GuardianTheme.materialColors.onSurface,
            )
            Spacer(Modifier.height(GuardianTheme.spacing.md))
            GuardianCheckbox(
                checked = shareGps,
                onCheckedChange = { shareGps = it },
                label = "Share my live location",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(GuardianTheme.spacing.md))
            GuardianCheckbox(
                checked = shareAudio,
                onCheckedChange = { shareAudio = it },
                label = "Send surrounding audio",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AssuranceCard(
            icon = GuardianIcons.Shield,
            title = "They only hear from me if you need them",
            body = "Contacts are stored encrypted on this device and are never contacted " +
                "unless a codeword or the SOS trigger fires.",
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_8", heightDp = 1400)
@Composable
private fun GuardianContactsPreview() {
    GuardianAngelTheme {
        GuardianContactsScreen(onBack = {}, onSave = { _, _, _, _, _ -> })
    }
}
