package com.example.guardianangel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.guardianangel.R
import com.example.guardianangel.ui.components.EmergencyTrigger
import com.example.guardianangel.ui.components.GuardianAccentButton
import com.example.guardianangel.ui.components.GuardianBottomNav
import com.example.guardianangel.ui.components.GuardianCard
import com.example.guardianangel.ui.components.GuardianCheckbox
import com.example.guardianangel.ui.components.GuardianHighlightCard
import com.example.guardianangel.ui.components.GuardianNavItem
import com.example.guardianangel.ui.components.GuardianOutlinedButton
import com.example.guardianangel.ui.components.GuardianStatus
import com.example.guardianangel.ui.components.GuardianTextField
import com.example.guardianangel.ui.components.StatusChip
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianAngelTheme
import com.example.guardianangel.ui.theme.GuardianTheme

/**
 * The Guardian Angel home screen.
 *
 * Doubles as the reference implementation of the design system: every surface, radius,
 * type token and component here comes from the theme, so there is a single place to look
 * when checking that a new screen is consistent with the rest of the app.
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val navItems = remember {
        listOf(
            GuardianNavItem("Home", GuardianIcons.Home),
            GuardianNavItem("Places", GuardianIcons.MapPin),
            GuardianNavItem("Circle", GuardianIcons.Users),
            GuardianNavItem("Settings", GuardianIcons.Sliders),
        )
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    var contact by remember { mutableStateOf("") }
    var shareLocation by remember { mutableStateOf(true) }
    var autoCheckIn by remember { mutableStateOf(false) }

    val spacing = GuardianTheme.spacing
    val sizeClass = GuardianTheme.windowSizeClass

    Box(
        modifier = modifier
            .fillMaxSize()
            // Level 0: the flat canvas everything else layers onto.
            .background(GuardianTheme.colors.canvas)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Inset first, scroll second: applied the other way round the padding
                // lives inside the scrolling viewport and slides away, letting the
                // header ride up under the status bar.
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Centre and cap the column on wide windows, per the grid architecture.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = sizeClass.maxContentWidth)
                    .padding(horizontal = spacing.screenMargin),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                Spacer(Modifier.height(spacing.md))

                GreetingHeader()

                ActiveGuardianCard()

                // The safety dial gets space-xl breathing room on either side.
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        Spacer(Modifier.height(spacing.md))
                        EmergencyTrigger(
                            onClick = { /* wire to the alert pipeline */ },
                            label = stringResource(R.string.sos_label),
                            contentDescription = stringResource(R.string.sos_description),
                        )
                        Text(
                            text = stringResource(R.string.sos_hint),
                            style = GuardianTheme.type.bodySm,
                            color = GuardianTheme.materialColors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(spacing.md))
                    }
                }

                QuickActions()

                CheckInCard(
                    shareLocation = shareLocation,
                    onShareLocationChange = { shareLocation = it },
                    autoCheckIn = autoCheckIn,
                    onAutoCheckInChange = { autoCheckIn = it },
                )

                TrustedContactCard(
                    value = contact,
                    onValueChange = { contact = it },
                )

                // Clearance for the floating nav bar.
                Spacer(Modifier.height(112.dp))
            }
        }

        GuardianBottomNav(
            items = navItems,
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 560.dp)
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun GreetingHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.greeting_eyebrow),
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.materialColors.onSurfaceVariant,
            )
            Spacer(Modifier.height(GuardianTheme.spacing.xs))
            Text(
                // Steps down to 28sp on compact widths, per the responsive scale.
                text = stringResource(R.string.greeting_title),
                style = GuardianTheme.headlineResponsive,
                color = GuardianTheme.materialColors.onSurface,
            )
        }
        StatusChip(
            label = stringResource(R.string.status_safe),
            status = GuardianStatus.Safe,
            icon = GuardianIcons.ShieldCheck,
        )
    }
}

@Composable
private fun ActiveGuardianCard() {
    GuardianHighlightCard(
        accent = GuardianTheme.colors.activeContainer,
        header = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
            ) {
                Icon(
                    imageVector = GuardianIcons.Beacon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(stringResource(R.string.guardian_active_header))
            }
        },
    ) {
        Text(
            text = stringResource(R.string.guardian_active_title),
            style = GuardianTheme.type.headlineMd,
            color = GuardianTheme.materialColors.onSurface,
        )
        Spacer(Modifier.height(GuardianTheme.spacing.sm))
        Text(
            text = stringResource(R.string.guardian_active_body),
            style = GuardianTheme.type.bodyMd,
            color = GuardianTheme.materialColors.onSurfaceVariant,
        )
        Spacer(Modifier.height(GuardianTheme.spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm)) {
            StatusChip(
                label = stringResource(R.string.status_sharing),
                status = GuardianStatus.Active,
                icon = GuardianIcons.MapPin,
            )
            StatusChip(
                label = stringResource(R.string.status_eta),
                status = GuardianStatus.Caution,
                icon = GuardianIcons.Clock,
            )
        }
    }
}

@Composable
private fun QuickActions() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
    ) {
        GuardianAccentButton(
            text = stringResource(R.string.action_check_in),
            onClick = { },
            leadingIcon = GuardianIcons.ShieldCheck,
            modifier = Modifier.weight(1f),
        )
        GuardianOutlinedButton(
            text = stringResource(R.string.action_call),
            onClick = { },
            leadingIcon = GuardianIcons.Phone,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CheckInCard(
    shareLocation: Boolean,
    onShareLocationChange: (Boolean) -> Unit,
    autoCheckIn: Boolean,
    onAutoCheckInChange: (Boolean) -> Unit,
) {
    GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
        Text(
            text = stringResource(R.string.preferences_title),
            style = GuardianTheme.type.headlineMd,
            color = GuardianTheme.materialColors.onSurface,
        )
        Spacer(Modifier.height(GuardianTheme.spacing.md))
        GuardianCheckbox(
            checked = shareLocation,
            onCheckedChange = onShareLocationChange,
            label = stringResource(R.string.preference_share_location),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(GuardianTheme.spacing.md))
        GuardianCheckbox(
            checked = autoCheckIn,
            onCheckedChange = onAutoCheckInChange,
            label = stringResource(R.string.preference_auto_check_in),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TrustedContactCard(value: String, onValueChange: (String) -> Unit) {
    GuardianCard(contentPadding = GuardianTheme.spacing.lg) {
        Text(
            text = stringResource(R.string.contacts_title),
            style = GuardianTheme.type.headlineMd,
            color = GuardianTheme.materialColors.onSurface,
        )
        Spacer(Modifier.height(GuardianTheme.spacing.xs))
        Text(
            text = stringResource(R.string.contacts_body),
            style = GuardianTheme.type.bodySm,
            color = GuardianTheme.materialColors.onSurfaceVariant,
        )
        Spacer(Modifier.height(GuardianTheme.spacing.md))
        GuardianTextField(
            value = value,
            onValueChange = onValueChange,
            label = stringResource(R.string.contacts_field_label),
            placeholder = stringResource(R.string.contacts_field_placeholder),
            leadingIcon = GuardianIcons.Users,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(GuardianTheme.spacing.md))
        GuardianOutlinedButton(
            text = stringResource(R.string.contacts_add),
            onClick = { },
            leadingIcon = GuardianIcons.Plus,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(name = "Home · light", showBackground = true, device = "id:pixel_8")
@Composable
private fun HomeScreenLightPreview() {
    GuardianAngelTheme(darkTheme = false) { HomeScreen() }
}

@Preview(name = "Home · dark", showBackground = true, device = "id:pixel_8")
@Composable
private fun HomeScreenDarkPreview() {
    GuardianAngelTheme(darkTheme = true) { HomeScreen() }
}
