package com.ella.music.ui.player

import com.ella.music.data.RawWaveformCache
import com.ella.music.data.SettingsManager
import kotlin.math.max

/**
 * Pure geometry helpers for the user-tunable waveform/segment progress bars (issue #674).
 *
 * Density and peak height are percentages where 100 reproduces the original renderer exactly.
 * Density only changes how the already-decoded PCM envelope is resampled at draw time; it never
 * changes the analysis sample count, so no audio re-scan is triggered.
 */
internal object WaveformProgressTuning {
    /** Visible bar count of the whole-track waveform before the density multiplier. */
    const val BASE_WAVEFORM_BAR_COUNT = 160

    /** Original seconds-timeline pitch (one bar per second) and bar width, in dp. */
    const val BASE_SECOND_BAR_STEP_DP = 7.45f
    const val BASE_SECOND_BAR_WIDTH_DP = 4.9f

    /** Original cap for the whole-track waveform bar half height, as a fraction of canvas height. */
    const val WAVEFORM_HALF_HEIGHT_CAP = 0.49f

    /** Taller peaks may grow up to, but not past, the drag needle (1.36x canvas height). */
    const val WAVEFORM_MAX_HALF_HEIGHT_CAP = 0.66f

    /** Original cap for the seconds-timeline bar height, as a fraction of canvas height. */
    const val SECOND_HEIGHT_CAP = 0.88f
    const val SECOND_LIVE_HEIGHT_CAP = 0.96f

    /** Stays inside the seconds-timeline needle (1.34x canvas height). */
    const val SECOND_MAX_HEIGHT_CAP = 1.30f

    fun normalizedDensity(raw: Int): Int = SettingsManager.normalizePlayerWaveformDensity(raw)

    fun peakHeightScale(rawPercent: Int): Float =
        SettingsManager.normalizePlayerWaveformPeakHeight(rawPercent) / 100f

    /** Density percent -> visible bars for the whole-track waveform. 100% keeps [baseBarCount]. */
    fun waveformVisibleBarCount(baseBarCount: Int, densityPercent: Int): Int {
        val percent = normalizedDensity(densityPercent)
        val scaled = (baseBarCount.toLong() * percent + 50L) / 100L
        return scaled.toInt().coerceIn(32, RawWaveformCache.MAX_SAMPLE_COUNT)
    }

    /** Density percent -> seconds-timeline bar pitch in dp. Denser means a tighter pitch. */
    fun secondBarStepDp(densityPercent: Int): Float =
        BASE_SECOND_BAR_STEP_DP / (normalizedDensity(densityPercent) / 100f)

    /**
     * Density percent -> seconds-timeline bar width in dp. Sparser timelines keep the original
     * bar width (only the gaps widen); denser ones shrink the bar with the pitch so bars never
     * overlap.
     */
    fun secondBarWidthDp(densityPercent: Int): Float {
        val percent = normalizedDensity(densityPercent)
        return if (percent <= 100) BASE_SECOND_BAR_WIDTH_DP else BASE_SECOND_BAR_WIDTH_DP / (percent / 100f)
    }

    /**
     * Height cap fraction for a given peak height scale. Scales at or below 1 keep [baseCap]
     * (quiet bars shrink proportionally anyway); taller peaks may exceed it up to [maxCap].
     */
    fun peakCap(baseCap: Float, heightScale: Float, maxCap: Float): Float =
        (baseCap * max(1f, heightScale)).coerceAtMost(max(baseCap, maxCap))

    /** Half height of one whole-track waveform bar; heightScale == 1 matches the original. */
    fun waveformBarHalfHeight(
        minHalfHeight: Float,
        maxHalfHeight: Float,
        shaped: Float,
        heightScale: Float,
        canvasHeight: Float,
    ): Float {
        val cap = canvasHeight * peakCap(WAVEFORM_HALF_HEIGHT_CAP, heightScale, WAVEFORM_MAX_HALF_HEIGHT_CAP)
        return (minHalfHeight + maxHalfHeight * heightScale * shaped).coerceAtMost(cap)
    }

    /** Pause scale target: disabling the scale animation keeps the timeline at full size. */
    fun pauseScaleTarget(scaleAnimationEnabled: Boolean, playing: Boolean, dragging: Boolean): Float =
        if (!scaleAnimationEnabled || playing || dragging) 1f else PAUSED_SCALE

    const val PAUSED_SCALE = 0.95f

    const val DENSITY_SLIDER_STEP = 10
    const val PEAK_HEIGHT_SLIDER_STEP = 5

    /** Intermediate stop count for a Miuix slider covering [range] in [step] increments. */
    fun sliderSteps(range: IntRange, step: Int): Int =
        ((range.last - range.first) / step - 1).coerceAtLeast(0)

    /** Undo float truncation of stepped slider values (e.g. 69.9999 -> 69) by snapping to [step]. */
    fun snapToStep(value: Int, step: Int): Int = ((value + step / 2) / step) * step
}
