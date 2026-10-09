package com.example.guardianangel.data

import com.example.guardianangel.domain.repository.VoiceProfile
import com.example.guardianangel.domain.repository.VoiceProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory voiceprint.
 *
 * Holds a real [FloatArray] rather than a flag, so a preview or test exercises the same
 * "is there a voiceprint to gate on" branch the device does.
 */
class FakeVoiceProfileRepository(enrolled: Boolean = true) : VoiceProfileRepository {

    private var embedding: FloatArray? = if (enrolled) FloatArray(512) { 0.04f } else null

    private val profile = MutableStateFlow(
        if (enrolled) {
            VoiceProfile(clarityPercent = 82, sampleCount = 3, calibratedAt = 0L)
        } else {
            null
        }
    )

    override fun observe(): Flow<VoiceProfile?> = profile.asStateFlow()

    override suspend fun load(): FloatArray? = embedding

    override suspend fun save(embedding: FloatArray, clarityPercent: Int, sampleCount: Int) {
        this.embedding = embedding
        profile.value = VoiceProfile(
            clarityPercent = clarityPercent,
            sampleCount = sampleCount,
            calibratedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun clear() {
        embedding = null
        profile.value = null
    }
}
