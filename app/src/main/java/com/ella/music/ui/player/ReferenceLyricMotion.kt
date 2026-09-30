package com.ella.music.ui.player

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.*

internal val LocalReferenceLyricMotion = staticCompositionLocalOf { false }
internal val ReferenceLyricEasing = CubicBezierEasing(.4f, .1f, 0f, 1f)
private val AppleEmphasisEasing = CubicBezierEasing(.25f, .1f, .25f, 1f)

/** Closed-form spring, adapted from Flamingo t7.B0/A0. Carries velocity across retargets. */
internal class ReferenceLyricSpring(
    private val start: Float, private val initialVelocity: Float, private val target: Float,
    mass: Float = .9f, damping: Float = 15f, stiffness: Float = 90f
) {
    private val omega = sqrt(stiffness / mass)
    private val decay = -damping / (2f * mass)
    private val frequency = sqrt(max(0f, stiffness / mass - decay * decay))
    private val delta = target - start
    fun position(seconds: Float): Float {
        if (seconds <= 0f) return start
        if (frequency < .0001f) return target - (delta + (omega * delta - initialVelocity) * seconds) * exp(-omega * seconds)
        val b = (-decay * delta - initialVelocity) / frequency
        return target - (delta * cos(frequency * seconds) + b * sin(frequency * seconds)) * exp(decay * seconds)
    }
    fun velocity(seconds: Float): Float {
        val t = seconds.coerceAtLeast(0f)
        if (frequency < .0001f) {
            val b = omega * delta - initialVelocity
            return (omega * (delta + b * t) - b) * exp(-omega * t)
        }
        val b = (-decay * delta - initialVelocity) / frequency
        val c = cos(frequency * t); val s = sin(frequency * t)
        return (delta * frequency * s - b * frequency * c - decay * (delta * c + b * s)) * exp(decay * t)
    }
}

/** Apple Music player.C.j0: 2dp lift, damping ratio .93 and stiffness 25. */
internal fun appleReferenceWordLift(elapsedMs: Long): Float {
    if (elapsedMs <= 0L) return 0f
    val t = elapsedMs.coerceAtMost(10_000L) / 1000f
    return ReferenceLyricSpring(0f, 0f, 1f, 1f, 2f * .93f * sqrt(25f), 25f).position(t)
}

/**
 * Duration-dependent emphasis from Apple player.C / C3722i. The 500 ms release is Flamingo's
 * trailing wave ([flamingoEmphasisTail]) instead of a plain ease-out.
 */
internal fun appleReferenceEmphasis(elapsedMs: Long, durationMs: Long): Float {
    if (elapsedMs <= 0L || durationMs < 1000L) return 0f
    val attack = durationMs.coerceAtMost(3000L).toFloat()
    val releaseStart = durationMs.toFloat()
    return if (elapsedMs <= durationMs) AppleEmphasisEasing.transform((elapsedMs / attack).coerceIn(0f, 1f))
    else flamingoEmphasisTail((elapsedMs - releaseStart) / 500f)
}

// Flamingo t7.AbstractC2048d.n: each letter swells over 68% of the word; starts spread over the other 32%.
private const val FlamingoLetterWindow = .68f
private const val FlamingoLetterSpread = 1f - FlamingoLetterWindow

/**
 * Flamingo's per-letter emphasis (drives scale 1 + (1.06..1.12 - 1) * e and glow alpha (.42..0.62) * e):
 * letter [index] of [count] runs a sin² bell, staggered so the last letter settles exactly at the
 * word end. No spring and no overshoot: the "tail" is the swell travelling off the last letters.
 */
internal fun flamingoLetterEmphasis(elapsedMs: Long, durationMs: Long, index: Int, count: Int): Float {
    if (durationMs < 1000L || count <= 0) return 0f
    val window = durationMs * FlamingoLetterWindow
    val stagger = if (count > 1) index.coerceIn(0, count - 1).toFloat() / (count - 1) else .5f
    val n = ((elapsedMs - stagger * durationMs * FlamingoLetterSpread) / window).coerceIn(0f, 1f)
    val s = sin(PI.toFloat() * n)
    return s * s
}

/**
 * Whole-word projection of Flamingo's trailing wave for [progress] 0..1 of the release: the mean of
 * the falling halves (peak to rest, 34% of the word each) of letters whose starts are spread over
 * 32% of the word. Leaves 1 and settles to 0 with zero slope at both ends and a long soft tail.
 */
internal fun flamingoEmphasisTail(progress: Float): Float {
    if (progress.isNaN() || progress >= 1f) return 0f
    if (progress <= 0f) return 1f
    val fall = FlamingoLetterWindow / 2f
    val decay = fall / (fall + FlamingoLetterSpread)
    val spread = 1f - decay
    // Integral of one letter's falling half cos²(πv/2), held at 1 before it starts.
    fun area(v: Float): Float = when {
        v <= 0f -> v
        v >= 1f -> .5f
        else -> v / 2f + sin(PI.toFloat() * v) / (2f * PI.toFloat())
    }
    return (decay / spread * (area(progress / decay) - area((progress - spread) / decay))).coerceIn(0f, 1f)
}

/**
 * The most recent automatic list jump. Rows that enter the viewport because of that jump have no
 * previous on-screen position; they start from where the jump moved them from (target + delta),
 * so they spring in with the rest instead of popping in at their final spot.
 */
internal class LyricAutoScrollShift {
    private var deltaPx = 0f
    private var atNanos = 0L
    fun record(delta: Float) { deltaPx = delta; atNanos = System.nanoTime() }
    /** Delta of a jump made within the last [windowMs]; 0 otherwise (manual scroll, restore). */
    fun recent(windowMs: Long = 250L): Float =
        if (atNanos != 0L && System.nanoTime() - atNanos <= windowMs * 1_000_000L) deltaPx else 0f
}

/** Per-row follower: read layout offsets without feeding the animated transform into measurement. */
internal fun Modifier.referenceLyricRowMotion(
    targetY: () -> Float?, enabled: Boolean, distance: Int, maxTravelPx: Float,
    enterShift: LyricAutoScrollShift? = null
): Modifier = composed {
    var position by remember { mutableFloatStateOf(Float.NaN) }
    var velocity by remember { mutableFloatStateOf(0f) }
    val latestTarget by rememberUpdatedState(targetY)
    val latestDistance by rememberUpdatedState(distance)
    val latestMaxTravel by rememberUpdatedState(maxTravelPx)
    LaunchedEffect(enabled) {
        snapshotFlow { latestTarget() }.collectLatest { target ->
            if (target == null) return@collectLatest
            if (enabled && !position.isFinite()) {
                // Newly visible row: begin from its pre-jump position if an auto-scroll just moved it in.
                val shift = enterShift?.recent() ?: 0f
                if (shift != 0f && abs(shift) <= latestMaxTravel) {
                    position = target + shift; velocity = 0f
                }
            }
            if (!enabled || !position.isFinite() || abs(target - position) > latestMaxTravel) {
                position = target; velocity = 0f; return@collectLatest
            }
            if (abs(target - position) < .1f && abs(velocity) < .1f) return@collectLatest
            // Subsequent rows follow the focus line in a wave; manual scroll skips this entirely.
            delay(latestDistance.coerceIn(0, 6) * 30L)
            val motion = ReferenceLyricSpring(position, velocity, target)
            val start = withFrameNanos { it }
            do {
                val elapsed = withFrameNanos { (it - start) / 1_000_000_000f }
                position = motion.position(elapsed)
                velocity = motion.velocity(elapsed)
            } while (abs(position - target) > .1f || abs(velocity) > .1f)
            position = target; velocity = 0f
        }
    }
    graphicsLayer {
        val target = latestTarget()
        alpha = if (target == null) 0f else 1f
        translationY = if (enabled && target != null && position.isFinite() && abs(position - target) <= maxTravelPx)
            position - target else 0f
    }
}
