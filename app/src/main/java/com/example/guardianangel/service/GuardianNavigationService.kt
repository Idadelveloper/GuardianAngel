package com.example.guardianangel.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.guardianangel.MainActivity
import com.example.guardianangel.R
import com.example.guardianangel.appContainer
import com.example.guardianangel.domain.model.Trip
import com.example.guardianangel.domain.model.TripOutcome
import com.example.guardianangel.domain.route.WalkDirections
import com.example.guardianangel.domain.trip.ArrivalDetector
import com.example.guardianangel.domain.trip.ArrivalMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "NavigationService"

/**
 * Keeps a walk going after the user puts her phone away.
 *
 * ## Why this is a service and not screen state
 *
 * Navigation used to be a `remember`ed id inside the map composable. Switching to
 * another tab destroyed it, so Angel silently stopped walking with her and she had to
 * notice and tap again — the failure being invisible is what makes it serious. A
 * foreground service is the only thing Android lets keep a location stream alive with
 * the app in the background, and it is what the user is implicitly asking for when she
 * says "walk with me".
 *
 * It is a **separate** service from `GuardianListeningService` on purpose. That one
 * holds the microphone and a few hundred megabytes of models; this one needs neither,
 * and the two run independently so a walk survives standing the microphone down and a
 * recording survives arriving. Android allows both foreground types at once.
 *
 * ## What it owns
 *
 * - The location stream, at a tighter cadence than the map's.
 * - Arrival, via [ArrivalDetector] — close enough *and* still there.
 * - Closing the trip, so the journeys log records what actually happened.
 * - The arrival text, if she asked for one.
 *
 * The active trip lives in Room, not here, so a process death mid-walk is recoverable:
 * on restart the service reads the open trip and carries on.
 */
class GuardianNavigationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var trackingJob: Job? = null
    private var trip: Trip? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END) {
            scope.launch {
                trip?.let { closeTrip(it, TripOutcome.Ended, metersCovered = null) }
                stopEverything()
            }
            return START_NOT_STICKY
        }

        // Must happen within a few seconds of starting or Android kills the process,
        // so the placeholder goes up before Room is asked anything.
        startForegroundSafely(buildNotification("Walking with you", "Getting your route…"))
        if (trackingJob == null) trackingJob = scope.launch { track() }

        // START_STICKY: if the system reclaims the process mid-walk, bring it back. The
        // open trip in Room is what makes that recovery meaningful rather than a restart
        // into an empty state.
        return START_STICKY
    }

    private suspend fun track() {
        val container = appContainer
        val active = runCatching { container.tripRepository.observeActiveTrip().first() }.getOrNull()
        if (active == null) {
            Log.w(TAG, "No active trip; standing down")
            stopEverything()
            return
        }
        trip = active

        val tracker = container.locationTracker
        if (tracker == null) {
            Log.w(TAG, "No location tracker; cannot navigate")
            stopEverything()
            return
        }

        val detector = ArrivalDetector(active.destination)
        val routePath = runCatching {
            container.routeRepository.observeRoutePlan().first()
                .routes.firstOrNull { it.id == active.routeId }?.path
        }.getOrNull().orEmpty()
        val steps = WalkDirections.stepsFor(routePath)

        var metersCovered = 0.0
        var lastPoint: com.example.guardianangel.domain.model.GeoPoint? = null

        // The navigation stream, not the breadcrumb one: arrival is proved by standing
        // still, which a displacement-filtered stream stops reporting.
        tracker.observeNavigationLocation().collect { fix ->
            val point = fix.point
            lastPoint?.let { metersCovered += WalkDirections.distanceMeters(it, point) }
            lastPoint = point

            val state = detector.onLocation(
                point = point,
                atMillis = System.currentTimeMillis(),
                accuracyMeters = fix.accuracyMeters,
            )
            when (state) {
                is ArrivalDetector.State.Arrived -> {
                    onArrived(active, state.atMillis, metersCovered.toInt())
                    return@collect
                }

                is ArrivalDetector.State.Settling -> updateNotification(
                    title = "Almost there",
                    body = "Looks like you've reached ${active.destinationName}.",
                )

                is ArrivalDetector.State.Approaching -> {
                    val progress = WalkDirections.progress(routePath, steps, point)
                    updateNotification(
                        title = progress.nextStep?.instruction ?: "Walking to ${active.destinationName}",
                        body = if (routePath.isEmpty()) {
                            "${WalkDirections.formatDistance(state.metersAway)} away"
                        } else {
                            "${WalkDirections.formatDistance(progress.metersToNextStep)} · " +
                                "${progress.minutesRemaining} min to ${active.destinationName}"
                        },
                    )
                }
            }
        }
    }

    private suspend fun onArrived(trip: Trip, atMillis: Long, metersCovered: Int) {
        Log.i(TAG, "Arrived at ${trip.destinationName}")
        closeTrip(trip, TripOutcome.Arrived, metersCovered)
        notifyGuardianOfArrival(trip, atMillis)

        // Replaces the ongoing notification with a dismissible one, so she sees it
        // happened without having to open the app.
        updateNotification(
            title = "You've arrived",
            body = buildString {
                append("At ${trip.destinationName}.")
                trip.notifyContactName?.let { append(" $it has been told.") }
            },
            ongoing = false,
        )
        stopEverything(removeNotification = false)
    }

    private suspend fun closeTrip(trip: Trip, outcome: TripOutcome, metersCovered: Int?) {
        runCatching {
            appContainer.tripRepository.complete(trip.id, outcome, metersCovered)
        }.onFailure { Log.w(TAG, "Could not close the trip", it) }
    }

    /**
     * Sends the "I'm here" text, if she set one up.
     *
     * Goes through the same notifier as an alert, so it obeys the same permission
     * reality: with `SEND_SMS` it goes out silently; without it, the user is handed a
     * pre-filled composer, and the trip records that it was *not* sent rather than
     * pretending otherwise.
     */
    private suspend fun notifyGuardianOfArrival(trip: Trip, atMillis: Long) {
        val contactId = trip.notifyContactId ?: return
        val container = appContainer

        val outcome = runCatching {
            val contact = container.contactsRepository.observeContacts().first()
                .firstOrNull { it.id == contactId }
                ?: error("${trip.notifyContactName ?: "That guardian"} is no longer in your circle")

            val userName = container.accountRepository.observeAccount().first()
                .profile?.fullName.orEmpty()

            container.guardianNotifier.notify(
                guardians = listOf(contact),
                message = ArrivalMessage.compose(
                    userName = userName,
                    destinationName = trip.destinationName,
                    arrivedAtMillis = atMillis,
                    customMessage = trip.notifyMessage,
                ),
            )
        }

        outcome.fold(
            onSuccess = { result ->
                val delivered = result.attempted.isNotEmpty()
                container.tripRepository.noteArrivalNotified(
                    tripId = trip.id,
                    atMillis = atMillis.takeIf { delivered },
                    error = result.blockedReason.takeIf { !delivered },
                )
            },
            onFailure = { error ->
                Log.w(TAG, "Arrival text failed", error)
                container.tripRepository.noteArrivalNotified(
                    tripId = trip.id,
                    atMillis = null,
                    error = error.message ?: "The arrival text could not be sent",
                )
            },
        )
    }

    private fun stopEverything(removeNotification: Boolean = true) {
        trackingJob?.cancel()
        trackingJob = null
        ServiceCompat.stopForeground(
            this,
            if (removeNotification) {
                ServiceCompat.STOP_FOREGROUND_REMOVE
            } else {
                ServiceCompat.STOP_FOREGROUND_DETACH
            },
        )
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // -- Notification ------------------------------------------------------------------

    private fun startForegroundSafely(notification: android.app.Notification) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure {
            // Thrown when the start came from the background. Nothing to recover: the
            // walk is started from a visible screen, and failing loudly in the log beats
            // a service that looks alive and is not.
            Log.e(TAG, "Could not go foreground; navigation cannot run in the background", it)
            stopSelf()
        }
    }

    /**
     * Refreshes the ongoing notification.
     *
     * The permission is checked inline rather than in a helper so lint can see it: a
     * refused `POST_NOTIFICATIONS` makes `notify` a silent no-op on some versions and a
     * `SecurityException` on others. The *service* keeps running either way — the
     * notification is how she sees the walk, not what keeps it alive.
     */
    private fun updateNotification(title: String, body: String, ongoing: Boolean = true) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        runCatching {
            NotificationManagerCompat.from(this)
                .notify(NOTIFICATION_ID, buildNotification(title, body, ongoing))
        }
    }

    private fun buildNotification(
        title: String,
        body: String,
        ongoing: Boolean = true,
    ): android.app.Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val end = PendingIntent.getService(
            this,
            1,
            Intent(this, GuardianNavigationService::class.java).setAction(ACTION_END),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setOngoing(ongoing)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .apply {
                if (ongoing) addAction(0, "End walk", end) else setAutoCancel(true)
            }
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            // Low and silent: it is a progress indicator she glances at, and a safety
            // app that chimes at every turn is the opposite of discreet.
            NotificationManagerCompat.IMPORTANCE_LOW,
        )
            .setName(getString(R.string.navigation_channel_name))
            .setDescription(getString(R.string.navigation_channel_description))
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "guardian_navigation"
        private const val NOTIFICATION_ID = 1003
        private const val ACTION_END = "com.example.guardianangel.END_WALK"

        /** Only legal from a visible screen — see `startForegroundSafely`. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, GuardianNavigationService::class.java),
            )
        }

        fun end(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, GuardianNavigationService::class.java).setAction(ACTION_END)
                )
            }
        }
    }
}
