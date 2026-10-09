package com.example.guardianangel

import android.app.Application
import android.content.Context
import android.app.Activity
import android.os.Bundle
import com.example.guardianangel.di.AppContainer
import com.example.guardianangel.di.DatabaseAppContainer

/**
 * Owns the object graph for the whole process.
 *
 * It previously lived in a `remember { }` inside the root composable, which was fine
 * while only screens needed it. It no longer is: [com.example.guardianangel.service
 * .GuardianListeningService] runs without any activity and still has to reach the same
 * repositories, so a wake word detected with the app closed updates the same state the
 * UI will show when it is reopened. Two containers would mean the service recording into
 * one and the screen reading from the other.
 */
class GuardianAngelApplication : Application() {

    lateinit var container: AppContainer
        private set

    /**
     * The Activity on top, if any.
     *
     * Firebase phone verification has to present a Play Integrity or reCAPTCHA challenge
     * in a real window, so it needs an Activity. Tracking it here rather than passing
     * one around keeps every other layer free of Activity references.
     */
    private var foregroundActivity: Activity? = null

    override fun onCreate() {
        super.onCreate()
        val databaseContainer = DatabaseAppContainer(
            context = this,
            activityProvider = { foregroundActivity },
        )
        container = databaseContainer

        // Signs in anonymously and seeds a new account's codewords. Runs off the main
        // thread; nothing in the UI waits on it, because an app that cannot be opened
        // until a network call returns is useless at the moment it is needed.
        databaseContainer.bootstrap()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                foregroundActivity = activity
            }

            override fun onActivityPaused(activity: Activity) {
                if (foregroundActivity === activity) foregroundActivity = null
            }

            override fun onActivityCreated(activity: Activity, bundle: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}

/** The process-wide container. Safe from any `Context`. */
val Context.appContainer: AppContainer
    get() = (applicationContext as GuardianAngelApplication).container
