// Adapted from RawS Music, Copyright 2024–2026 RawSMusic Contributors, Apache-2.0.
package com.ella.music.ui.player

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Beat-aware structural climax analysis for the immersive waveform.
 *
 * BPM is used as a timing grid, never as the climax decision by itself. The actual score is built
 * from sustained loudness, prominence over the rest of the track, lift from the preceding section,
 * transient density and beat-coherent attacks. This keeps a fast but quiet verse from being marked
 * as a climax while still letting a slow, sustained chorus win.
 */
internal data class ImmersiveClimaxAnalysis(
    val segments: List<ImmersiveClimaxSegment>,
    val resolvedBpm: Float,
    val tempoConfidence: Float,
)

private data class ClimaxTempo(
    val bpm: Float,
    val beatPeriodSamples: Float,
    val beatPhaseSamples: Float,
    val confidence: Float,
)

private data class ClimaxCandidate(
    val start: Int,
    val end: Int,
    val score: Float,
)

internal fun analyzeImmersiveClimax(
    peaks: FloatArray,
    durationMs: Long,
    preferredBpm: Int = 0,
): ImmersiveClimaxAnalysis {
    if (peaks.size < 64 || durationMs <= 0L) {
        return ImmersiveClimaxAnalysis(emptyList(), 0f, 0f)
    }

    val durationSeconds = durationMs / 1000f
    val sampleHz = peaks.size / durationSeconds.coerceAtLeast(1f)
    if (sampleHz < 2f) return ImmersiveClimaxAnalysis(emptyList(), 0f, 0f)

    val clamped = FloatArray(peaks.size) { peaks[it].coerceIn(0f, 1f) }
    val shortRadius = (sampleHz * 0.18f).roundToInt().coerceAtLeast(1)
    val envelope = movingAverage(clamped, shortRadius)

    val sortedEnvelope = envelope.copyOf().also(FloatArray::sort)
    val floor = percentile(sortedEnvelope, 0.38f)
    val ceiling = max(percentile(sortedEnvelope, 0.92f), floor + 0.015f)
    val dynamicSpan = ceiling - floor
    if (dynamicSpan < 0.035f) {
        return ImmersiveClimaxAnalysis(emptyList(), 0f, 0f)
    }

    val energy = FloatArray(envelope.size) { index ->
        ((envelope[index] - floor) / dynamicSpan).coerceIn(0f, 1f)
    }
    val sortedEnergy = energy.copyOf().also(FloatArray::sort)
    val globalMedianEnergy = percentile(sortedEnergy, 0.50f)
    val globalHighEnergy = percentile(sortedEnergy, 0.82f)

    // Positive spectral-flux style proxy from the *raw* waveform. Do not derive tempo from the
    // smoothed energy envelope: around 120 BPM one beat is ~500 ms, which is close enough to the
    // smoothing window to erase the very periodicity we are trying to detect.
    val onset = FloatArray(envelope.size)
    for (index in 1 until envelope.size) {
        onset[index] = (clamped[index] - clamped[index - 1]).coerceAtLeast(0f)
    }
    val sortedOnset = onset.copyOf().also(FloatArray::sort)
    val onsetScale = max(percentile(sortedOnset, 0.92f), 1.0e-4f)
    val onsetNormalized = FloatArray(onset.size) { index ->
        (onset[index] / onsetScale).coerceIn(0f, 1f)
    }

    val tempo = resolveClimaxTempo(
        onset = onset,
        sampleHz = sampleHz,
        preferredBpm = preferredBpm,
    )

    val windowSeconds = if (tempo.bpm > 0f) {
        // Eight 4/4 bars is a useful chorus-scale window. Clamp it so very slow/fast metadata does
        // not create absurdly large or tiny structural windows.
        (32f * 60f / tempo.bpm).coerceIn(10f, 24f)
    } else {
        (durationSeconds * 0.075f).coerceIn(10f, 24f)
    }
    val window = (windowSeconds * sampleHz).roundToInt().coerceIn(24, peaks.size)
    if (window >= peaks.size) return ImmersiveClimaxAnalysis(emptyList(), tempo.bpm, tempo.confidence)

    val firstAllowed = (peaks.size * 0.06f).roundToInt()
    val lastAllowed = (peaks.size * 0.92f).roundToInt() - window
    if (lastAllowed <= firstAllowed) {
        return ImmersiveClimaxAnalysis(emptyList(), tempo.bpm, tempo.confidence)
    }

    val candidateStep = if (tempo.beatPeriodSamples > 0f) {
        tempo.beatPeriodSamples.roundToInt().coerceAtLeast(1)
    } else {
        (window / 10).coerceAtLeast(1)
    }
    var candidateStart = if (tempo.beatPeriodSamples > 0f) {
        alignAtOrAfter(
            value = firstAllowed.toFloat(),
            phase = tempo.beatPhaseSamples,
            period = tempo.beatPeriodSamples,
        ).roundToInt()
    } else {
        firstAllowed
    }

    val candidates = ArrayList<ClimaxCandidate>()
    while (candidateStart <= lastAllowed) {
        val end = candidateStart + window
        val beforeStart = (candidateStart - window).coerceAtLeast(0)
        val sectionEnergy = average(energy, candidateStart, end)
        val previousEnergy = average(energy, beforeStart, candidateStart)
        val prominence = ((sectionEnergy - globalMedianEnergy) / 0.34f).coerceIn(0f, 1f)
        val lift = ((sectionEnergy - previousEnergy) / 0.34f).coerceIn(0f, 1f)

        var sustainedCount = 0
        for (index in candidateStart until end) {
            if (energy[index] >= max(0.62f, globalHighEnergy * 0.90f)) sustainedCount++
        }
        val sustained = sustainedCount.toFloat() / (end - candidateStart).coerceAtLeast(1)
        val transientDensity = average(onsetNormalized, candidateStart, end)
        val beatAttack = beatAttackDensity(
            onset = onsetNormalized,
            start = candidateStart,
            end = end,
            tempo = tempo,
        )

        val buildWindow = (window / 2).coerceAtLeast(4)
        val buildStart = (candidateStart - buildWindow).coerceAtLeast(0)
        val buildMid = buildStart + (candidateStart - buildStart) / 2
        val earlyBuild = average(energy, buildStart, buildMid)
        val lateBuild = average(energy, buildMid, candidateStart)
        val preBuild = ((lateBuild - earlyBuild) / 0.25f).coerceIn(0f, 1f)

        // Only a mild edge penalty remains. The old detector effectively assumed the climax lives
        // around 58% of the song; real songs can peak much earlier or later.
        val centerFraction = (candidateStart + window * 0.5f) / peaks.size.toFloat()
        val edgeWeight = when {
            centerFraction < 0.10f -> 0.86f
            centerFraction > 0.90f -> 0.88f
            centerFraction < 0.16f || centerFraction > 0.84f -> 0.94f
            else -> 1f
        }

        val score = (
            sectionEnergy * 0.24f +
                prominence * 0.23f +
                sustained * 0.16f +
                lift * 0.18f +
                transientDensity * 0.08f +
                beatAttack * 0.07f +
                preBuild * 0.04f
            ) * edgeWeight

        candidates += ClimaxCandidate(candidateStart, end, score.coerceIn(0f, 1f))
        candidateStart += candidateStep
    }

    if (candidates.isEmpty()) {
        return ImmersiveClimaxAnalysis(emptyList(), tempo.bpm, tempo.confidence)
    }
    val ranked = candidates.sortedByDescending(ClimaxCandidate::score)
    val best = ranked.first()
    val sortedScores = candidates.map(ClimaxCandidate::score).sorted()
    val medianScore = sortedScores[sortedScores.size / 2]
    val structuralSeparation = (best.score - medianScore).coerceAtLeast(0f)
    val confidence = (
        structuralSeparation * 2.15f +
            dynamicSpan.coerceIn(0f, 0.45f) * 0.72f +
            tempo.confidence.coerceIn(0f, 1f) * 0.12f
        ).coerceIn(0f, 1f)

    // A flat/compressed track can still have one numerically highest window. Require both an
    // absolute structural score and enough separation from the typical candidate before painting
    // a climax segment.
    if (best.score < 0.46f || confidence < 0.17f) {
        return ImmersiveClimaxAnalysis(emptyList(), tempo.bpm, tempo.confidence)
    }

    val beatPadding = if (tempo.beatPeriodSamples > 0f) {
        (tempo.beatPeriodSamples * 2f).roundToInt()
    } else {
        window / 10
    }
    val segmentStart = (best.start - beatPadding).coerceAtLeast(0)
    val segmentEnd = (best.end + beatPadding).coerceAtMost(peaks.size)
    val segment = ImmersiveClimaxSegment(
        startFraction = segmentStart / peaks.size.toFloat(),
        endFraction = segmentEnd / peaks.size.toFloat(),
        confidence = confidence,
    )
    return ImmersiveClimaxAnalysis(listOf(segment), tempo.bpm, tempo.confidence)
}

private fun resolveClimaxTempo(
    onset: FloatArray,
    sampleHz: Float,
    preferredBpm: Int,
): ClimaxTempo {
    val taggedBpm = preferredBpm.toFloat().takeIf { it in 40f..240f }
    if (taggedBpm != null) {
        val period = sampleHz * 60f / taggedBpm
        val roundedPeriod = period.roundToInt().coerceAtLeast(1)
        val confidence = autocorrelationAtLag(onset, roundedPeriod)
        return ClimaxTempo(
            bpm = taggedBpm,
            beatPeriodSamples = period,
            beatPhaseSamples = bestBeatPhase(onset, roundedPeriod).toFloat(),
            // Metadata is authoritative for spacing even when the waveform has soft attacks. Keep
            // confidence waveform-derived so it cannot artificially inflate climax confidence.
            confidence = confidence,
        )
    }

    val minLag = (sampleHz * 60f / 190f).roundToInt().coerceAtLeast(1)
    val maxLag = (sampleHz * 60f / 65f).roundToInt().coerceAtMost(onset.size / 2)
    if (maxLag <= minLag) return ClimaxTempo(0f, 0f, 0f, 0f)

    var bestLag = 0
    var bestScore = 0f
    for (lag in minLag..maxLag) {
        val score = autocorrelationAtLag(onset, lag)
        if (score > bestScore) {
            bestScore = score
            bestLag = lag
        }
    }
    if (bestLag <= 0 || bestScore < 0.075f) return ClimaxTempo(0f, 0f, 0f, 0f)

    val bpm = sampleHz * 60f / bestLag
    return ClimaxTempo(
        bpm = bpm.coerceIn(65f, 190f),
        beatPeriodSamples = bestLag.toFloat(),
        beatPhaseSamples = bestBeatPhase(onset, bestLag).toFloat(),
        confidence = bestScore.coerceIn(0f, 1f),
    )
}

private fun autocorrelationAtLag(values: FloatArray, lag: Int): Float {
    if (lag <= 0 || lag >= values.size) return 0f
    var xy = 0.0
    var xx = 0.0
    var yy = 0.0
    for (index in lag until values.size) {
        val a = values[index].toDouble()
        val b = values[index - lag].toDouble()
        xy += a * b
        xx += a * a
        yy += b * b
    }
    val denominator = sqrt(xx * yy)
    return if (denominator > 1.0e-9) (xy / denominator).toFloat().coerceIn(0f, 1f) else 0f
}

private fun bestBeatPhase(onset: FloatArray, lag: Int): Int {
    if (lag <= 0) return 0
    var bestPhase = 0
    var bestScore = -1f
    for (phase in 0 until lag) {
        var score = 0f
        var index = phase
        while (index < onset.size) {
            score += onset[index]
            index += lag
        }
        if (score > bestScore) {
            bestScore = score
            bestPhase = phase
        }
    }
    return bestPhase
}

private fun beatAttackDensity(
    onset: FloatArray,
    start: Int,
    end: Int,
    tempo: ClimaxTempo,
): Float {
    if (tempo.beatPeriodSamples <= 0f || end <= start) return 0f
    var beat = alignAtOrAfter(
        value = start.toFloat(),
        phase = tempo.beatPhaseSamples,
        period = tempo.beatPeriodSamples,
    )
    var sum = 0f
    var count = 0
    while (beat < end) {
        val center = beat.roundToInt().coerceIn(0, onset.lastIndex)
        var localPeak = onset[center]
        if (center > 0) localPeak = max(localPeak, onset[center - 1])
        if (center < onset.lastIndex) localPeak = max(localPeak, onset[center + 1])
        sum += localPeak
        count++
        beat += tempo.beatPeriodSamples
    }
    return if (count > 0) (sum / count).coerceIn(0f, 1f) else 0f
}

private fun alignAtOrAfter(value: Float, phase: Float, period: Float): Float {
    if (period <= 0f) return value
    var aligned = phase
    if (aligned < value) {
        val steps = ((value - aligned) / period).toInt()
        aligned += steps * period
        while (aligned < value) aligned += period
    }
    return aligned
}

private fun movingAverage(values: FloatArray, radius: Int): FloatArray {
    if (values.isEmpty() || radius <= 0) return values.copyOf()
    val prefix = FloatArray(values.size + 1)
    for (index in values.indices) prefix[index + 1] = prefix[index] + values[index]
    return FloatArray(values.size) { index ->
        val start = (index - radius).coerceAtLeast(0)
        val endExclusive = (index + radius + 1).coerceAtMost(values.size)
        (prefix[endExclusive] - prefix[start]) / (endExclusive - start).coerceAtLeast(1)
    }
}

private fun percentile(sorted: FloatArray, fraction: Float): Float {
    if (sorted.isEmpty()) return 0f
    val index = (sorted.lastIndex * fraction.coerceIn(0f, 1f)).roundToInt()
        .coerceIn(0, sorted.lastIndex)
    return sorted[index]
}

private fun average(values: FloatArray, start: Int, end: Int): Float {
    if (values.isEmpty()) return 0f
    val from = start.coerceIn(0, values.size)
    val until = end.coerceIn(from, values.size)
    if (until <= from) return 0f
    var sum = 0f
    for (index in from until until) sum += values[index]
    return sum / (until - from)
}
