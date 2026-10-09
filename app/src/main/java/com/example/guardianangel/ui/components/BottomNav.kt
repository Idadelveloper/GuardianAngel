package com.example.guardianangel.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.guardianangel.ui.theme.GuardianTheme
import com.example.guardianangel.ui.theme.guardianFloatingElevation

/** One destination in the floating bar. */
data class GuardianNavItem(
    val label: String,
    val icon: ImageVector,
)

/**
 * The floating bottom navigation bar.
 *
 * Detached from the bottom edge by 16dp on all sides, `rounded-xl`, canvas surface with
 * the emphasized border and the level-2 ambient warm shadow. The active destination gets
 * a soft apricot pill behind its icon.
 *
 * The caller is responsible for the navigation-bar window inset — pass it in through
 * [modifier] (e.g. `Modifier.navigationBarsPadding()`) so the bar floats above the system
 * gesture area rather than under it.
 */
@Composable
fun GuardianBottomNav(
    items: List<GuardianNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = GuardianTheme.shapes.xl
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(GuardianTheme.spacing.md)
            .guardianFloatingElevation(
                shape = shape,
                shadowColor = GuardianTheme.colors.floatingShadow,
                borderColor = GuardianTheme.colors.borderEmphasis,
            )
            .clip(shape)
            .background(GuardianTheme.colors.canvas)
            .padding(vertical = GuardianTheme.spacing.sm),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            GuardianNavButton(
                item = item,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun GuardianNavButton(
    item: GuardianNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Inactive items use the accessible muted tint rather than the design document's
    // espresso-at-45%, which measured 2.70:1 against the canvas — under the 3:1 floor for
    // an icon that carries meaning.
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            GuardianTheme.materialColors.onSecondaryContainer
        } else {
            GuardianTheme.colors.iconMuted
        },
        animationSpec = tween(200),
        label = "navContent",
    )
    val pillColor by animateColorAsState(
        targetValue = if (selected) GuardianTheme.colors.activeContainer else Color.Transparent,
        animationSpec = tween(200),
        label = "navPill",
    )

    Column(
        modifier = Modifier
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = GuardianTheme.spacing.sm, vertical = GuardianTheme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .width(56.dp)
                .clip(GuardianTheme.shapes.pill)
                .background(pillColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = item.label,
            style = GuardianTheme.type.labelSm,
            color = contentColor,
        )
    }
}
