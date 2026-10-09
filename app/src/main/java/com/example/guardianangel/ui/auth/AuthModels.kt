package com.example.guardianangel.ui.auth

/**
 * Shared state for the sign-up and log-in screens.
 *
 * One type for both because they differ only in which fields they collect and which
 * repository call they make. A failed sign-in and a failed sign-up need identical
 * handling, and splitting them produced two slightly different error treatments.
 */
data class AuthFormState(
    val isSubmitting: Boolean = false,
    /** Written for a person to read. Never Firebase's own message — see `AuthResult`. */
    val error: String? = null,
)

/** Whether an email address is plausible enough to attempt. */
internal fun looksLikeEmail(value: String): Boolean {
    val trimmed = value.trim()
    val at = trimmed.indexOf('@')
    val dot = trimmed.lastIndexOf('.')
    return at > 0 && dot > at + 1 && dot < trimmed.length - 1 && !trimmed.contains(' ')
}

/**
 * The minimum password length the app will accept.
 *
 * Eight rather than something longer because this gates a safety app: a user who cannot
 * remember her password cannot get to her guardians. Length is encouraged by the strength
 * meter rather than enforced.
 */
internal const val MIN_PASSWORD_LENGTH = 8
