package com.example.guardianangel.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.guardianangel.data.FakeListeningRepository
import com.example.guardianangel.data.GrantedPermissions
import com.example.guardianangel.ui.home.HandsFreeController
import com.example.guardianangel.ui.home.RecordAttempt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Recording needs the microphone *and* location, and asking is not starting.
 *
 * Pinned by a test because this gate was already weakened once: a version requested
 * location and then started capture anyway, so a single tap raised a permission dialog
 * and began recording behind it. That is worse than having no gate — the dialog becomes
 * a lie, and the user believes she declined.
 */
@RunWith(AndroidJUnit4::class)
class RecordingGateTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun withoutMicrophoneItAsksAndDoesNotRecord() {
        val probe = Probe()
        val attempt = controller(probe, microphone = false, location = true).recordNow()

        assertEquals(RecordAttempt.NeedsMicrophone, attempt)
        assertTrue("Should have asked for the microphone", probe.askedMicrophone)
        assertFalse("Started recording without the microphone", probe.startedService)
        assertFalse(attempt.isStarted)
    }

    @Test
    fun withoutLocationItAsksAndDoesNotRecord() {
        val probe = Probe()
        val attempt = controller(probe, microphone = true, location = false).recordNow()

        assertEquals(RecordAttempt.NeedsLocation, attempt)
        assertTrue("Should have asked for location", probe.askedLocation)
        assertFalse("Started recording without location", probe.startedService)
        assertFalse(attempt.isStarted)
    }

    @Test
    fun withBothItRecordsWithoutAsking() {
        val probe = Probe()
        val attempt = controller(probe, microphone = true, location = true).recordNow()

        assertEquals(RecordAttempt.Started, attempt)
        assertTrue(attempt.isStarted)
        assertTrue("The service should have been started", probe.startedService)
        assertFalse(probe.askedMicrophone)
        assertFalse(probe.askedLocation)
    }

    /**
     * A controller whose permission answers and service starts are both observable.
     *
     * [HandsFreeController] reads permissions from its `Context`, so the context is
     * wrapped. Intercepting the service start is what lets the test assert nothing ran,
     * rather than inferring it from the absence of a log line.
     */
    private fun controller(
        probe: Probe,
        microphone: Boolean,
        location: Boolean,
    ) = HandsFreeController(
        context = PermissionContext(context, probe, microphone, location),
        repository = FakeListeningRepository(
            permissions = GrantedPermissions(microphone = microphone, location = location)
        ),
        requestMicrophone = { probe.askedMicrophone = true },
        requestNotifications = {},
        requestLocationPermission = { probe.askedLocation = true },
        scope = CoroutineScope(SupervisorJob()),
    )

    private class Probe {
        var askedMicrophone = false
        var askedLocation = false
        var startedService = false
    }

    private class PermissionContext(
        base: Context,
        private val probe: Probe,
        private val microphone: Boolean,
        private val location: Boolean,
    ) : ContextWrapper(base) {

        override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
            answer(permission)

        override fun checkSelfPermission(permission: String): Int = answer(permission)

        override fun startForegroundService(service: Intent?) = null.also {
            probe.startedService = true
        }

        override fun startService(service: Intent?) = null.also {
            probe.startedService = true
        }

        private fun answer(permission: String): Int {
            val granted = when (permission) {
                android.Manifest.permission.RECORD_AUDIO -> microphone
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION -> location
                else -> true
            }
            return if (granted) PackageManager.PERMISSION_GRANTED
            else PackageManager.PERMISSION_DENIED
        }
    }
}
