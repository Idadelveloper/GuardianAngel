package com.example.guardianangel.ui.activities

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.guardianangel.data.ActivitySamples
import com.example.guardianangel.domain.model.ActivityFilter
import com.example.guardianangel.domain.model.AnalyticsRange
import com.example.guardianangel.domain.model.MonitoredSession
import com.example.guardianangel.domain.model.TrailPoint
import com.example.guardianangel.domain.model.MovementAnalytics
import com.example.guardianangel.domain.repository.ActivityRepository
import com.example.guardianangel.ui.components.GuardianTabScaffold
import com.example.guardianangel.ui.home.components.RecentActivityCard
import com.example.guardianangel.ui.home.components.TonalPill
import com.example.guardianangel.ui.theme.GuardianAngelTheme
import com.example.guardianangel.ui.theme.GuardianTheme

/** The two things the Activities tab can show. */
private enum class ActivityView { Log, Insights }

/**
 * Tab 3 — Activities.
 *
 * A log of monitored sessions and a movement-intelligence rollup, behind one segmented
 * control. The design brief had them as separate screens; folding them into one tab means
 * the bottom bar keeps four entries and the user does not have to remember which tab
 * holds which half of the same subject.
 */
@Composable
fun ActivitiesRoute(
    repository: ActivityRepository,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf(ActivityFilter.All) }
    var range by remember { mutableStateOf(AnalyticsRange.Week) }

    val sessions by repository.observeSessions(filter)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val analytics by repository.observeAnalytics(range)
        .collectAsStateWithLifecycle(initialValue = ActivitySamples.analytics(range))
    // One query for every row's path, rather than one per row.
    val trails by repository.observeTrailPaths()
        .collectAsStateWithLifecycle(initialValue = emptyMap())

    ActivitiesScreen(
        sessions = sessions,
        analytics = analytics,
        trails = trails,
        filter = filter,
        range = range,
        onFilterChange = { filter = it },
        onRangeChange = { range = it },
        onOpenSession = onOpenSession,
        modifier = modifier,
    )
}

@Composable
fun ActivitiesScreen(
    sessions: List<MonitoredSession>,
    analytics: MovementAnalytics,
    trails: Map<String, List<TrailPoint>>,
    filter: ActivityFilter,
    range: AnalyticsRange,
    onFilterChange: (ActivityFilter) -> Unit,
    onRangeChange: (AnalyticsRange) -> Unit,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var view by remember { mutableStateOf(ActivityView.Log) }

    GuardianTabScaffold(
        modifier = modifier,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GuardianTheme.materialColors.surfaceContainerLow)
                    .statusBarsPadding()
                    .padding(
                        horizontal = GuardianTheme.spacing.screenMargin,
                        vertical = GuardianTheme.spacing.md,
                    ),
            ) {
                Text(
                    text = "Activity",
                    style = GuardianTheme.type.headlineLgMobile,
                    color = GuardianTheme.materialColors.onSurface,
                )
                Spacer(Modifier.height(GuardianTheme.spacing.sm))
                SegmentedToggle(
                    options = listOf("Log", "Insights"),
                    selectedIndex = view.ordinal,
                    onSelect = { view = ActivityView.entries[it] },
                )
            }
        },
    ) {
        AnimatedContent(
            targetState = view,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "activityView",
        ) { target ->
            Column(verticalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.lg)) {
                when (target) {
                    ActivityView.Log -> LogView(
                        sessions = sessions,
                        trails = trails,
                        filter = filter,
                        onFilterChange = onFilterChange,
                        onOpenSession = onOpenSession,
                    )
                    ActivityView.Insights -> InsightsView(
                        analytics = analytics,
                        range = range,
                        onRangeChange = onRangeChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun LogView(
    sessions: List<MonitoredSession>,
    trails: Map<String, List<TrailPoint>>,
    filter: ActivityFilter,
    onFilterChange: (ActivityFilter) -> Unit,
    onOpenSession: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(GuardianTheme.spacing.sm)) {
        ActivityFilter.entries.forEach { option ->
            val selected = option == filter
            TonalPill(
                text = when (option) {
                    ActivityFilter.All -> "All"
                    ActivityFilter.Incidents -> "Incidents"
                    ActivityFilter.Conversations -> "Conversations"
                },
                container = if (selected) {
                    GuardianTheme.materialColors.onSurface
                } else {
                    GuardianTheme.materialColors.surfaceContainerLow
                },
                content = if (selected) {
                    GuardianTheme.colors.canvas
                } else {
                    GuardianTheme.materialColors.onSurfaceVariant
                },
                modifier = Modifier
                    .clip(GuardianTheme.shapes.pill)
                    .clickable(role = Role.Tab) { onFilterChange(option) }
                    .padding(vertical = 6.dp),
            )
        }
    }

    Spacer(Modifier.height(GuardianTheme.spacing.xs))

    if (sessions.isEmpty()) {
        EmptyState(
            title = "Nothing logged yet",
            body = "Sessions appear here after Angel listens in. Nothing is recorded " +
                "until a codeword fires.",
        )
    } else {
        sessions.forEach { session ->
            RecentActivityCard(
                session = session,
                onClick = { onOpenSession(session.id) },
                trailPoints = trails[session.id].orEmpty(),
            )
        }
    }
}

/** A pill-track segmented control, used for Log/Insights and the analytics range. */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(GuardianTheme.shapes.pill)
            .background(GuardianTheme.materialColors.surfaceContainerHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(GuardianTheme.shapes.pill)
                    .background(
                        if (selected) {
                            GuardianTheme.materialColors.surfaceContainerLowest
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        }
                    )
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = GuardianTheme.type.labelMd,
                    color = if (selected) {
                        GuardianTheme.materialColors.onSurface
                    } else {
                        GuardianTheme.materialColors.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(GuardianTheme.shapes.lg)
            .background(GuardianTheme.materialColors.surfaceContainerLow)
            .padding(GuardianTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = GuardianTheme.type.labelLg,
            color = GuardianTheme.materialColors.onSurface,
        )
        Spacer(Modifier.height(GuardianTheme.spacing.xs))
        Text(
            text = body,
            style = GuardianTheme.type.bodySm,
            color = GuardianTheme.materialColors.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_8", heightDp = 1500)
@Composable
private fun ActivitiesPreview() {
    GuardianAngelTheme {
        ActivitiesScreen(
            sessions = ActivitySamples.sessions,
            analytics = ActivitySamples.analytics(AnalyticsRange.Week),
            trails = emptyMap(),
            filter = ActivityFilter.All,
            range = AnalyticsRange.Week,
            onFilterChange = {}, onRangeChange = {}, onOpenSession = {},
        )
    }
}
