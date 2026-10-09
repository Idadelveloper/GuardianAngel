package com.example.guardianangel.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * The user's voiceprint: a fixed-length embedding of how she sounds.
 *
 * Separate from [ListeningRepository] because it holds a biometric and is governed by
 * different rules — it is encrypted at rest, never uploaded, and is the one piece of
 * stored data that becomes useless rather than merely stale if the Keystore key is
 * invalidated. Keeping it behind its own interface means those rules live in one place
 * instead of being a comment on a column.
 *
 * It stores the *embedding only*. The audio the embedding came from is discarded as soon
 * as the vector exists, so there is no recording of the user's voice anywhere on disk.
 */
interface VoiceProfileRepository {

    fun observe(): Flow<VoiceProfile?>

    /** The stored voiceprint, or null if absent or no longer decryptable. */
    suspend fun load(): FloatArray?

    /**
     * Replaces the voiceprint.
     *
     * @param embedding the averaged embedding across takes.
     * @param clarityPercent how consistent the takes were with each other, 0-100. Low
     *   values mean the takes disagreed — noise, or a different speaker — and the match
     *   check will be unreliable.
     * @param sampleCount how many takes went into it.
     */
    suspend fun save(embedding: FloatArray, clarityPercent: Int, sampleCount: Int)

    /** Forgets the voiceprint. Used when the user re-enrols or clears her data. */
    suspend fun clear()
}

/** What the UI needs to know about the stored voiceprint, without the biometric itself. */
data class VoiceProfile(
    val clarityPercent: Int,
    val sampleCount: Int,
    val calibratedAt: Long?,
) {
    /** Enough, and consistent enough, for the wake word to be gated on it. */
    val isUsable: Boolean get() = sampleCount > 0 && clarityPercent >= MIN_CLARITY

    companion object {
        /**
         * Below this the takes disagreed too much to gate on.
         *
         * Gating on a bad voiceprint is worse than not gating: it means the wake word
         * stops working for the person it belongs to.
         */
        const val MIN_CLARITY = 55
    }
}
