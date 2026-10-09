package com.example.guardianangel.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.guardianangel.ui.icons.GuardianIcons
import com.example.guardianangel.ui.theme.GuardianTheme

/** Vertical room left under scrolling content so the floating nav never covers the last card. */
val BottomBarClearance: Dp = 112.dp

/**
 * The shell every primary tab uses.
 *
 * Owns the window insets, the content max-width and the clearance beneath the floating
 * navigation bar, so no individual screen has to remember any of it — which is what keeps
 * the four tabs feeling like one app rather than four.
 */
@Composable
fun GuardianTabScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable (() -> Unit)? = null,
    scrollable: Boolean = true,
    contentSpacing: Dp = GuardianTheme.spacing.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GuardianTheme.colors.canvas),
    ) {
        topBar?.invoke()

        val bodyModifier = Modifier
            .fillMaxSize()
            .then(if (topBar == null) Modifier.statusBarsPadding() else Modifier)
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)

        Column(
            modifier = bodyModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = GuardianTheme.windowSizeClass.maxContentWidth)
                    .padding(horizontal = GuardianTheme.spacing.screenMargin),
                verticalArrangement = Arrangement.spacedBy(contentSpacing),
            ) {
                Spacer(Modifier.height(GuardianTheme.spacing.sm))
                content()
                Spacer(Modifier.height(BottomBarClearance))
            }
        }
    }
}

/**
 * The shell for a stacked sub-screen: back arrow, title, optional overflow.
 *
 * No bottom bar — a sub-screen is somewhere you came *from* a tab, and the back arrow is
 * the only way out so it can never be ambiguous where "back" goes.
 */
@Composable
fun GuardianStackScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable (RowScope.() -> Unit)? = null,
    bottomBar: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GuardianTheme.colors.canvas),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(GuardianTheme.materialColors.surfaceContainerLow)
                .statusBarsPadding()
                .padding(
                    horizontal = GuardianTheme.spacing.sm,
                    vertical = GuardianTheme.spacing.sm,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = GuardianIcons.ChevronLeft,
                    contentDescription = "Back",
                    tint = GuardianTheme.materialColors.onSurface,
                )
            }
            Text(
                text = title,
                style = GuardianTheme.type.headlineMd,
                color = GuardianTheme.materialColors.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = GuardianTheme.spacing.xs),
            )
            actions?.invoke(this)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = GuardianTheme.windowSizeClass.maxContentWidth)
                    .padding(
                        horizontal = GuardianTheme.spacing.screenMargin,
                        vertical = GuardianTheme.spacing.md,
                    ),
                verticalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.lg),
                content = content,
            )
        }

        if (bottomBar != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GuardianTheme.materialColors.surfaceContainerLow)
                    .navigationBarsPadding()
                    .padding(GuardianTheme.spacing.md),
            ) {
                bottomBar()
            }
        }
    }
}

/**
 * The onboarding wizard shell: progress bar, back arrow, scrolling body, pinned CTA.
 *
 * The call to action is pinned rather than scrolled so the way forward is always visible
 * — during setup the user should never have to hunt for the next step.
 */
@Composable
fun GuardianWizardScaffold(
    stepLabel: String,
    progress: Float,
    onBack: (() -> Unit)?,
    ctaLabel: String,
    onCta: () -> Unit,
    modifier: Modifier = Modifier,
    ctaEnabled: Boolean = true,
    footer: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(420),
        label = "wizardProgress",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GuardianTheme.colors.canvas)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = GuardianTheme.spacing.sm,
                    vertical = GuardianTheme.spacing.xs,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = GuardianIcons.ChevronLeft,
                        contentDescription = "Back",
                        tint = GuardianTheme.materialColors.onSurface,
                    )
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
            Text(
                text = stepLabel,
                style = GuardianTheme.type.labelMd,
                color = GuardianTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }

        // Progress track.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GuardianTheme.spacing.screenMargin)
                .height(6.dp)
                .clip(CircleShape)
                .background(GuardianTheme.materialColors.surfaceContainerHigh),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(GuardianTheme.colors.accentSoft),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = GuardianTheme.windowSizeClass.maxContentWidth)
                    .padding(
                        horizontal = GuardianTheme.spacing.screenMargin,
                        vertical = GuardianTheme.spacing.lg,
                    ),
                verticalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.lg),
                content = content,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GuardianTheme.materialColors.surfaceContainerLow)
                .navigationBarsPadding()
                .padding(GuardianTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GuardianPrimaryButton(
                text = ctaLabel,
                onClick = onCta,
                enabled = ctaEnabled,
                trailingIcon = GuardianIcons.ArrowRight,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
            )
            if (footer != null) {
                Spacer(Modifier.height(GuardianTheme.spacing.sm))
                footer()
            }
        }
    }
}

/** Tappable footer line, e.g. "Already have an account? Log in". */
@Composable
fun GuardianTextLink(
    prefix: String,
    linkLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = prefix,
            style = GuardianTheme.type.bodySm,
            color = GuardianTheme.materialColors.onSurfaceVariant,
        )
        Text(
            text = linkLabel,
            style = GuardianTheme.type.labelMd,
            color = GuardianTheme.materialColors.primary,
            modifier = Modifier
                .clip(GuardianTheme.shapes.sm)
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}
