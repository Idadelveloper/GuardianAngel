package com.example.guardianangel.ui.onboarding

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
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
 * Needs **no permission**: the system picker does the choosing, and the result grants a
 * one-shot read on exactly the row the user selected. `READ_CONTACTS` would hand the app
 * the entire address book, which for adding three guardians is impossible to justify —
 * and it is one more prompt between someone and a working panic button.
 *
 * ## Why not `ActivityResultContracts.PickContact`
 *
 * That contract returns a **contact** URI, and a contact has any number of phone
 * numbers — so `CommonDataKinds.Phone.NUMBER` is not a column on it. Querying it with
 * phone projections threw, the failure was swallowed, and the name and number fields
 * simply never filled in.
 *
 * Picking with `Phone.CONTENT_TYPE` instead returns the *phone row* the user chose. Both
 * the name and the number are columns on it, it still needs no permission, and when a
 * contact has several numbers the user picks which one rather than the app guessing.
 */
@Composable
fun rememberContactPicker(onPicked: (PickedContact) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(PickPhoneNumber) { uri ->
        uri?.let { readContact(context, it)?.let(onPicked) }
    }
    return remember(launcher) { { launcher.launch(Unit) } }
}

/** Opens the system picker filtered to phone numbers, returning the chosen row. */
private object PickPhoneNumber : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK).setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
}

/**
 * Reads the name and number from the picked phone row.
 *
 * The picker grants a one-shot read on just this row, so the query works without
 * `READ_CONTACTS` — but only for this URI, and only now, which is why the result is
 * copied out immediately rather than the URI being stored.
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
