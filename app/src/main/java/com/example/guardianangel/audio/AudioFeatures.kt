package com.example.guardianangel.audio

import kotlin.math.sqrt

/**
 * Shared audio constants and maths.
 *
 * This file once held a full log-mel + FFT front end, written when the wake word ran on
 * a raw LiteRT embedding model that needed features computed by hand. Every model in the
 * stack now brings its own front end — sherpa-onnx computes fbank internally, and YAMNet
 * takes a raw waveform — so that code was deleted rather than left to rot as a second,
 * unused implementation of something subtle enough to get quietly wrong. It is in git
 * history if a future model ever needs it.
 */

/** Everything in the pipeline runs at 16 kHz mono. */
const val SAMPLE_RATE = 16_000

/**
 * Cosine similarity of two equal-length vectors, in -1f..1f.
 *
 * The comparison behind speaker verification and diarization: embeddings are compared by
 * angle rather than distance, so loudness does not change who the model thinks is
 * speaking.
 */
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
