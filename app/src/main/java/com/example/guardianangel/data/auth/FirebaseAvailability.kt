package com.example.guardianangel.data.auth

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp

private const val TAG = "FirebaseAvailability"

/**
 * Whether a Firebase project is actually configured in this build.
 *
 * The Firebase SDKs are always compiled in so the code type-checks, but without a
 * `google-services.json` there is no project to talk to and `FirebaseApp` never
 * initialises. Checking once here lets the app pick a local-only implementation and
 * carry on, rather than throwing on first use or — worse — appearing to sign someone in
 * and then losing their data.
 */
object FirebaseAvailability {

    @Volatile
    private var cached: Boolean? = null

    fun isConfigured(context: Context): Boolean = cached ?: synchronized(this) {
        cached ?: detect(context).also { cached = it }
    }

    private fun detect(context: Context): Boolean = try {
        // initializeApp returns null rather than throwing when no config is present.
        val configured = FirebaseApp.initializeApp(context) != null
        Log.i(TAG, if (configured) "Firebase configured" else "No Firebase config — local accounts only")
        configured
    } catch (e: Exception) {
        Log.i(TAG, "Firebase unavailable: ${e.message}")
        false
    }
}
