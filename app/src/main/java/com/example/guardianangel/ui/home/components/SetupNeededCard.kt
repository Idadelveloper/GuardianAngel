package com.example.guardianangel.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.guardianangel.domain.model.GuardianCapability
import com.example.guardianangel.domain.model.GuardianSetup
import com.example.guardianangel.ui.components.GuardianCard
import com.example.guardianangel.ui.components.GuardianOutlinedButton
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianAngelTheme
import com.example.guardianangel.ui.theme.GuardianTheme

/**
 * What Angel still cannot do, and the one tap that fixes it.
 *
 * Shown only while something is missing, and showing **one** thing at a time by default.
 * Onboarding is skippable, so this is where a skipped step resurfaces — but five
 * simultaneous warnings on a safety app's home screen train the user to scroll past the
 * whole region, which is worse than saying nothing. One clear gap with a working button
 * gets acted on; the rest expand on request.
 *
 * Framed around what she loses rather than what the app wants. "Set codewords" is a
 * chore; "I record, and nothing else" is a reason.
 */
@Composable
fun SetupNeededCard(
    setup: GuardianSetup,
    onFix: (GuardianCapability) -> Unit,
    modifier: Modifier = Modifier,
) {
    val headline = setup.mostImportantMissing ?: return
    val others = setup.missing.filter { it != headline }
    var expanded by remember { mutableStateOf(false) }

    GuardianCard(modifier = modifier, contentPadding = GuardianTheme.spacing.lg) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(GuardianTheme.shapes.sm)
                    .background(GuardianTheme.colors.peachCream),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = GuardianIcons.Warning,
                    contentDescription = null,
                    tint = GuardianTheme.materialColors.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Not set up yet",
                    style = GuardianTheme.type.labelLg,
                    color = GuardianTheme.materialColors.onSurface,
                )
                Text(
                    text = headline.summary,
                    style = GuardianTheme.type.bodySm,
                    color = GuardianTheme.materialColors.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(GuardianTheme.spacing.sm))
        Text(
            text = headline.missingDetail,
            style = GuardianTheme.type.bodySm,
            color = GuardianTheme.materialColors.onSurfaceVariant,
        )
        Spacer(Modifier.height(GuardianTheme.spacing.md))
        GuardianOutlinedButton(
            text = headline.fixLabel,
            onClick = { onFix(headline) },
            leadingIcon = GuardianIcons.ArrowRight,
            modifier = Modifier.fillMaxWidth(),
        )

        if (others.isNotEmpty()) {
            Spacer(Modifier.height(GuardianTheme.spacing.sm))
            Text(
                text = if (expanded) {
                    "Hide the rest"
                } else {
                    "${others.size} more thing${if (others.size == 1) "" else "s"} to set up"
                },
                style = GuardianTheme.type.labelSm,
                color = GuardianTheme.materialColors.primary,
                modifier = Modifier
                    .clip(GuardianTheme.shapes.sm)
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            )

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm),
                ) {
                    others.forEach { capability ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(GuardianTheme.shapes.sm)
                                .clickable(role = Role.Button) { onFix(capability) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(
                                GuardianTheme.spacing.sm
                            ),
                        ) {
                            Text(
                                text = capability.summary,
                                style = GuardianTheme.type.bodySm,
                                color = GuardianTheme.materialColors.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = capability.fixLabel,
                                style = GuardianTheme.type.labelSm,
                                color = GuardianTheme.materialColors.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_8")
@Composable
private fun SetupNeededPreview() {
    GuardianAngelTheme {
        SetupNeededCard(
            setup = GuardianSetup(ready = setOf(GuardianCapability.HandsFree)),
            onFix = {},
            modifier = Modifier.padding(GuardianTheme.spacing.lg),
        )
    }
}
