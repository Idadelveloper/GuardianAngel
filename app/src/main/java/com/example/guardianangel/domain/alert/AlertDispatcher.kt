package com.example.guardianangel.domain.alert

import android.util.Log
import com.example.guardianangel.agent.GuardianNotificationAgent
import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.model.DiarizedEntry
import com.example.guardianangel.domain.repository.ContactsRepository
import com.example.guardianangel.domain.repository.GuardianRepository
import com.example.guardianangel.domain.repository.SessionRecorder
import kotlinx.coroutines.flow.first

private const val TAG = "AlertDispatcher"

/**
 * The one path an alert takes, however it was triggered.
 *
 * The SOS hold and a spoken codeword are the same event from the app's point of view,
 * and they were reaching different code — the button only changed in-memory UI state
 * while the codeword path called the repository. Routing both through here means a
 * guardian receives the same message either way, the session records it the same way,
 * and there is exactly one place where the behaviour can be read or changed.
 *
 * Everything it needs is gathered at dispatch time rather than passed in: the caller is
 * usually a button handler or an audio callback, and neither should be assembling a
 * guardian list and a location fix.
 */
class AlertDispatcher(
    private val notificationAgent: GuardianNotificationAgent,
    private val contacts: ContactsRepository,
    private val guardianRepository: GuardianRepository,
    private val recorder: SessionRecorder,
    private val profileName: suspend () -> String,
    private val currentLocation: suspend () -> LocationFix?,
    private val sessionEntries: suspend () -> List<DiarizedEntry>,
    /**
     * Rings a guardian on the top tier. Null on a build or device that cannot call, in
     * which case the alert is text-only and says so rather than failing.
     */
    private val caller: GuardianCaller? = null,
    /** The user's "ring a guardian" setting, read at dispatch time. */
    private val callingEnabled: suspend () -> Boolean = { false },
) {
    data class LocationFix(
        val latitude: Double,
        val longitude: Double,
        val placeLabel: String?,
    )

    /**
     * Sends the alert and records what happened.
     *
     * Never throws. This is called from a duress hold and from the audio pipeline, and
     * an exception on either path would turn a failed text into a crashed app at the
     * moment the user is relying on it.
     */
    /**
     * Forgets what was already sent, so the next session can alert at the same tier.
     *
     * Called when a recording ends. Without it, a Danger alert in the morning would
     * silently suppress a Danger alert that evening.
     */
    fun resetForNewSession() {
        notificationAgent.reset()
        calledThisSession = false
    }

    /**
     * Whether this session has already rung someone.
     *
     * Kept here rather than in the policy so the policy stays pure. Without it a user who
     * says her emergency codeword twice — which is exactly what someone does when she is
     * frightened and unsure it worked — would have the second utterance tear down the
     * call the first one placed.
     */
    private var calledThisSession = false

    suspend fun dispatch(tier: CodewordTier): GuardianNotificationAgent.Dispatch? = try {
        val guardians = contacts.observeContacts().first()
        val fix = runCatching { currentLocation() }.getOrNull()
        val entries = runCatching { sessionEntries() }.getOrDefault(emptyList())

        val result = notificationAgent.dispatch(
            tier = tier,
            userName = runCatching { profileName() }.getOrDefault(""),
            guardians = guardians,
            entries = entries,
            latitude = fix?.latitude,
            longitude = fix?.longitude,
            placeLabel = fix?.placeLabel,
        )

        // Written into the session before the UI state changes, so the record exists
        // even if the process dies in the next second.
        if (!result.suppressed) {
            recorder.noteGuardiansNotified(result.outcome.reached)
            recorder.addAudioEvent(
                label = result.summaryLine,
                decibels = null,
                confidence = 1f,
                atMillis = System.currentTimeMillis(),
                isDanger = tier.ordinal >= CodewordTier.Danger.ordinal,
            )
        }

        // After the text, never before. The text carries the location link and survives
        // an unanswered phone; if the dial were first, a guardian could pick up with no
        // address and the process could die before the SMS ever went out.
        //
        // Deliberately *not* gated on `result.suppressed`. A suppressed duplicate means
        // the same-tier text already went out — it says nothing about whether a phone
        // ever rang. Someone who says her emergency codeword a second time because
        // nothing visibly happened is exactly the person who needs another attempt, and
        // `calledThisSession` below is what stops a live call being torn down.
        placeCallIfWarranted(tier)

        guardianRepository.dispatchAlert(tier)
        Log.i(TAG, result.summaryLine)
        result
    } catch (e: Exception) {
        Log.e(TAG, "Alert dispatch failed for $tier", e)
        runCatching { guardianRepository.dispatchAlert(tier) }
        null
    }

    /**
     * Rings the first guardian, if this tier and this user's settings call for it.
     *
     * Failures are recorded, not thrown and not hidden. A call that could not be placed
     * is something the user needs to be able to see afterwards, because she may well have
     * believed someone was already on the phone.
     */
    private suspend fun placeCallIfWarranted(tier: CodewordTier) {
        val dialler = caller ?: return
        val decision = EmergencyCallPolicy.decide(
            tier = tier,
            guardians = runCatching { contacts.observeContacts().first() }.getOrDefault(emptyList()),
            enabled = runCatching { callingEnabled() }.getOrDefault(false),
            alreadyCalledInSession = calledThisSession,
        )
        if (decision !is EmergencyCallPolicy.Decision.Call) {
            (decision as? EmergencyCallPolicy.Decision.Skip)?.let { Log.i(TAG, "No call: ${it.reason}") }
            return
        }

        val outcome = runCatching { dialler.call(decision.contact) }.getOrElse {
            Log.e(TAG, "Calling ${decision.contact.name} failed", it)
            GuardianCaller.Outcome.Failed("Couldn't start a call to ${decision.contact.name}")
        }

        // Only a call that actually started counts as "already called". An untapped
        // notification must not stop the next emergency from trying again.
        if (outcome is GuardianCaller.Outcome.Dialling) calledThisSession = true

        val note = when (outcome) {
            is GuardianCaller.Outcome.Dialling -> "Calling ${outcome.contact.name}"
            is GuardianCaller.Outcome.AwaitingTap ->
                "Call to ${outcome.contact.name} is ready — tap the notification to connect"
            is GuardianCaller.Outcome.Failed -> outcome.reason
        }
        runCatching {
            recorder.addAudioEvent(
                label = note,
                decibels = null,
                confidence = 1f,
                atMillis = System.currentTimeMillis(),
                isDanger = false,
            )
        }
        Log.i(TAG, note)
    }
}
