package com.example.guardianangel.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Log-mel spectrogram extraction.
 *
 * Every practical keyword-spotting model — openWakeWord, the Google `speech_embedding`
 * backbone, microWakeWord, Vosk's acoustic front end — takes log-mel frames rather than
 * raw samples, so this stage is required whichever model ends up behind
 * [KeywordSpotter]. Implemented here rather than pulled from a library because it is
 * ~150 lines, has no dependencies, and keeps the exact parameters visible: get the mel
 * scale or the frame hop wrong and the model silently scores noise.
 *
 * Defaults match the openWakeWord / `speech_embedding` front end: 16 kHz mono, 25 ms
 * windows, 10 ms hop, 32 mel bins.
 */
class MelSpectrogram(
    private val sampleRate: Int = SAMPLE_RATE,
    private val frameSizeSamples: Int = 400,   // 25 ms at 16 kHz
    private val hopSamples: Int = 160,         // 10 ms at 16 kHz
    private val melBins: Int = 32,
    private val fftSize: Int = 512,
    minFrequency: Float = 60f,
    maxFrequency: Float = 7600f,
) {
    private val window = FloatArray(frameSizeSamples) { i ->
        // Periodic Hann, matching how TF computes it.
        0.5f - 0.5f * cos(2.0 * PI * i / frameSizeSamples).toFloat()
    }

    private val melFilters: Array<FloatArray> =
        buildMelFilterBank(melBins, fftSize, sampleRate, minFrequency, maxFrequency)

    private val fft = RealFft(fftSize)

    /** Number of frames [extract] will produce for [sampleCount] samples. */
    fun frameCount(sampleCount: Int): Int =
        if (sampleCount < frameSizeSamples) 0
        else 1 + (sampleCount - frameSizeSamples) / hopSamples

    /**
     * Log-mel frames for [samples], which must be mono PCM in -1f..1f.
     *
     * @return `[frames][melBins]`, natural-log scaled and floored so silence cannot
     *   produce negative infinity.
     */
    fun extract(samples: FloatArray): Array<FloatArray> {
        val frames = frameCount(samples.size)
        if (frames <= 0) return emptyArray()

        val output = Array(frames) { FloatArray(melBins) }
        val windowed = FloatArray(fftSize)
        val power = FloatArray(fftSize / 2 + 1)

        for (frame in 0 until frames) {
            val offset = frame * hopSamples
            for (i in 0 until frameSizeSamples) {
                windowed[i] = samples[offset + i] * window[i]
            }
            // Zero-pad the tail up to the FFT size.
            java.util.Arrays.fill(windowed, frameSizeSamples, fftSize, 0f)

            fft.powerSpectrum(windowed, power)

            val melFrame = output[frame]
            for (bin in 0 until melBins) {
                val filter = melFilters[bin]
                var sum = 0f
                for (k in power.indices) {
                    val weight = filter[k]
                    if (weight != 0f) sum += weight * power[k]
                }
                melFrame[bin] = ln(max(sum, LOG_FLOOR))
            }
        }
        return output
    }

    private companion object {
        const val LOG_FLOOR = 1e-10f

        fun hzToMel(hz: Float): Float = 1127f * ln(1f + hz / 700f)
        fun melToHz(mel: Float): Float = 700f * (kotlin.math.exp(mel / 1127f) - 1f)

        /** Triangular filters, evenly spaced on the mel scale. */
        fun buildMelFilterBank(
            bins: Int,
            fftSize: Int,
            sampleRate: Int,
            minFrequency: Float,
            maxFrequency: Float,
        ): Array<FloatArray> {
            val spectrumSize = fftSize / 2 + 1
            val melMin = hzToMel(minFrequency)
            val melMax = hzToMel(min(maxFrequency, sampleRate / 2f))
            // bins + 2 points gives each triangle a left, centre and right edge.
            val points = FloatArray(bins + 2) { i ->
                melToHz(melMin + (melMax - melMin) * i / (bins + 1))
            }
            val binFor = { hz: Float -> hz * fftSize / sampleRate }

            return Array(bins) { b ->
                val left = binFor(points[b])
                val centre = binFor(points[b + 1])
                val right = binFor(points[b + 2])
                FloatArray(spectrumSize) { k ->
                    val x = k.toFloat()
                    when {
                        x <= left || x >= right -> 0f
                        x <= centre -> (x - left) / max(centre - left, 1e-6f)
                        else -> (right - x) / max(right - centre, 1e-6f)
                    }
                }
            }
        }
    }
}

/**
 * Minimal radix-2 real FFT.
 *
 * Only the power spectrum is ever needed, so this skips the inverse transform and the
 * complex output API entirely. [size] must be a power of two.
 */
internal class RealFft(private val size: Int) {

    init {
        require(size > 0 && size and (size - 1) == 0) { "FFT size must be a power of two" }
    }

    private val real = FloatArray(size)
    private val imag = FloatArray(size)
    private val cosTable = FloatArray(size / 2) { cos(2.0 * PI * it / size).toFloat() }
    private val sinTable = FloatArray(size / 2) { sin(2.0 * PI * it / size).toFloat() }

    /** Writes `|X(k)|²` for k in 0..size/2 into [out]. */
    fun powerSpectrum(input: FloatArray, out: FloatArray) {
        input.copyInto(real, 0, 0, size)
        java.util.Arrays.fill(imag, 0f)
        transform()
        for (k in out.indices) {
            out[k] = real[k] * real[k] + imag[k] * imag[k]
        }
    }

    private fun transform() {
        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                real[i] = real[j].also { real[j] = real[i] }
                imag[i] = imag[j].also { imag[j] = imag[i] }
            }
        }
        // Cooley–Tukey butterflies.
        var len = 2
        while (len <= size) {
            val step = size / len
            var i = 0
            while (i < size) {
                var k = 0
                for (x in i until i + len / 2) {
                    val y = x + len / 2
                    val wr = cosTable[k]
                    val wi = -sinTable[k]
                    val tr = real[y] * wr - imag[y] * wi
                    val ti = real[y] * wi + imag[y] * wr
                    real[y] = real[x] - tr
                    imag[y] = imag[x] - ti
                    real[x] += tr
                    imag[x] += ti
                    k += step
                }
                i += len
            }
            len = len shl 1
        }
    }
}

/** Audio constants shared by the capture, feature and model stages. */
const val SAMPLE_RATE = 16_000

/** Cosine similarity of two equal-length vectors, in -1f..1f. */
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    require(a.size == b.size) { "embeddings must match: ${a.size} vs ${b.size}" }
    var dot = 0f
    var normA = 0f
    var normB = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    val denom = sqrt(normA) * sqrt(normB)
    return if (denom <= 1e-8f) 0f else dot / denom
}
