package com.example.guardianangel.ui.onboarding

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private const val TAG = "ContactPicker"

/** A contact as pulled from the phone book. */
data class PickedContact(
    val name: String,
    val phoneNumber: String,
    /** Stable across syncs and restores, unlike the row id. */
    val lookupKey: String?,
)

/**
 * Picks a guardian from the phone's contacts.
 *
 * Uses `ActivityResultContracts.PickContact`, which hands back a single contact the user
 * chose in the system picker. The important property is that **it needs no permission**:
 * `READ_CONTACTS` would let the app read the entire address book, where the picker
 * returns exactly the one person the user selected, and the choosing happens in the
 * system UI rather than ours.
 *
 * For an app whose whole pitch is that it does not take more than it needs, asking for
 * the full contacts permission to add three guardians would be hard to justify — and it
 * is one more permission prompt standing between someone and a working panic button.
 */
@Composable
fun rememberContactPicker(onPicked: (PickedContact) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        uri?.let { readContact(context, it)?.let(onPicked) }
    }
    return remember(launcher) { { launcher.launch(null) } }
}

/**
 * Reads the name and first phone number behind a picked contact URI.
 *
 * The picker grants a one-shot read on just this contact, so the query works without
 * `READ_CONTACTS` — but only for this URI, and only now.
 */
private fun readContact(context: Context, uri: Uri): PickedContact? = try {
    context.contentResolver.query(
        uri,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
        ),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            PickedContact(
                name = cursor.getString(0).orEmpty(),
                // Strips the formatting carriers and locales add, keeping a leading +.
                phoneNumber = cursor.getString(1).orEmpty().normalisePhoneNumber(),
                lookupKey = cursor.getString(2),
            )
        } else {
            null
        }
    }
} catch (e: SecurityException) {
    // The one-shot grant can lapse if the picker result is used late.
    Log.w(TAG, "Lost read access to the picked contact", e)
    null
} catch (e: Exception) {
    Log.e(TAG, "Could not read the picked contact", e)
    null
}

/** Keeps digits and a leading +, so stored numbers compare and dial consistently. */
internal fun String.normalisePhoneNumber(): String {
    val trimmed = trim()
    val hasPlus = trimmed.startsWith("+")
    val digits = trimmed.filter(Char::isDigit)
    return if (hasPlus) "+$digits" else digits
}
