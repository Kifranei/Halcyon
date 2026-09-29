package com.ella.music.ui.player

import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import com.ella.music.data.SettingsManager
import kotlin.math.*
import kotlin.random.Random

/** Motion reconstructed from the supplied ConePlayer 1.3.0 renderer le.u / frame provider cf.c.
 * Uses its 64 bands, 0.3 gap ratio, 0.6 gravity, 0.02 drag, and coupled-string coefficients.
 * Simulation runs at a fixed 60 Hz regardless of the display refresh rate.
 */
internal class ConeVisualizerMotion(private val random: Random = Random.Default) {
    private data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float,
        val radius: Float, var life: Float, val initialLife: Float)
    private val particles = ArrayList<Particle>()
    private val displacement = FloatArray(64)
    private val velocity = FloatArray(64)
    private val force = FloatArray(64)
    private val current = FloatArray(64)
    private val bursts = FloatArray(64)
    private var pendingSeconds = 0f

    fun reset() {
        particles.clear(); displacement.fill(0f); velocity.fill(0f); force.fill(0f)
        current.fill(0f); bursts.fill(0f); pendingSeconds = 0f
    }

    fun advance(targets: List<Float>, width: Float, height: Float, seconds: Float, style: Int) {
        if (width <= 0f || height <= 0f) return
        pendingSeconds += seconds.coerceIn(0f, .1f)
        while (pendingSeconds >= 1f / 60f) {
            pendingSeconds -= 1f / 60f
            for (i in current.indices) {
                val target = (targets.getOrNull(i) ?: 0f).coerceIn(0f, 1f)
                val delta = target - current[i]
                bursts[i] = if (delta > .02f) delta else 0f
                current[i] = if (abs(delta * height) > .5f) current[i] + .1f * delta else target
            }
            when (style) {
                SettingsManager.AUDIO_VISUALIZER_STYLE_PARTICLES -> {
                    for (i in bursts.indices) {
                        val burst = bursts[i]
                        if (burst <= .02f || random.nextFloat() >= min(burst * 3f + .2f, .8f)) continue
                        val strength = (burst / .4f).coerceIn(.05f, 1f)
                        val launch = sqrt(.6f * 2f * .85f * height * strength)
                        repeat((6f * strength).toInt().coerceIn(1, 4)) {
                            if (particles.size >= 4096) return@repeat
                            val life = launch * 2f / .6f + 20f + random.nextInt(15)
                            particles += Particle((i + .5f) * width / 64f, height,
                                (random.nextFloat() - .5f) * 8f * strength,
                                -(random.nextFloat() * .4f + .8f) * launch,
                                strength * 2f + random.nextFloat() * 2f + 1.5f, life, life)
                        }
                    }
                    val iterator = particles.iterator()
                    while (iterator.hasNext()) {
                        val p = iterator.next()
                        p.x += p.vx; p.y += p.vy
                        p.vx *= .98f; p.vy = (p.vy + .6f) * .98f; p.life -= 1f
                        if (p.life <= 0f || p.y > height + p.radius) iterator.remove()
                    }
                }
                SettingsManager.AUDIO_VISUALIZER_STYLE_STRINGS -> {
                    for (i in velocity.indices) if (bursts[i] > .1f) {
                        velocity[i] += bursts[i] * height * .5f * if (random.nextBoolean()) 1f else -1f
                    }
                    for (i in force.indices) {
                        force[i] = -.15f * displacement[i] +
                            (if (i > 0) (displacement[i - 1] - displacement[i]) * .48f else 0f) +
                            (if (i < 63) (displacement[i + 1] - displacement[i]) * .48f else 0f)
                    }
                    for (i in velocity.indices) {
                        velocity[i] = (velocity[i] + force[i]) * .9f
                        displacement[i] += velocity[i]
                    }
                }
            }
        }
    }

    fun draw(scope: DrawScope, style: Int, accent: Color) = with(scope) {
        val color = accent.copy(alpha = accent.alpha * 180f / 255f)
        val step = size.width / 64f
        val barWidth = step / 1.3f
        when (style) {
            SettingsManager.AUDIO_VISUALIZER_STYLE_PARTICLES -> particles.forEach { p ->
                var alpha = (p.life / p.initialLife).coerceIn(0f, 1f)
                if (p.y < size.height * .15f) alpha *= (p.y / (size.height * .15f)).coerceIn(0f, 1f)
                drawCircle(accent.copy(alpha = accent.alpha * alpha), p.radius, Offset(p.x, p.y))
            }
            SettingsManager.AUDIO_VISUALIZER_STYLE_STRINGS -> current.indices.forEach { i ->
                // Coupled-string overshoot may exceed half the box; keep it inside the height.
                val y = coneStringY(size.height, displacement[i], 2f)
                drawRoundRect(color, Offset(i * step + (step - barWidth) / 2f, y - 2f),
                    Size(barWidth, 4f), CornerRadius(10f))
            }
            SettingsManager.AUDIO_VISUALIZER_STYLE_CLASSIC_BARS -> current.indices.forEach { i ->
                val height = current[i] * size.height
                drawRoundRect(color, Offset(i * step + (step - barWidth) / 2f, size.height - height),
                    Size(barWidth, height), CornerRadius(10f))
            }
            SettingsManager.AUDIO_VISUALIZER_STYLE_WATER_RIPPLE -> {
                val path = Path()
                // Inset by half the 5 px stroke so a full-scale crest is not cut at the top.
                val inset = 2.5f
                val span = (size.height - inset * 2f).coerceAtLeast(0f)
                var previous = Offset(0f, inset + span * (1f - current[0]))
                path.moveTo(previous.x, previous.y)
                for (i in 1..63) {
                    val point = Offset(i * size.width / 63f, inset + span * (1f - current[i]))
                    val midpoint = (point + previous) / 2f
                    if (i == 1) path.lineTo(midpoint.x, midpoint.y)
                    else path.quadraticBezierTo(previous.x, previous.y, midpoint.x, midpoint.y)
                    previous = point
                }
                path.lineTo(previous.x, previous.y)
                drawPath(path, color, style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** Vertical centre of one resonance-string segment, clamped so its [halfThickness] stays visible. */
internal fun coneStringY(height: Float, displacement: Float, halfThickness: Float): Float {
    if (height <= halfThickness * 2f) return height * .5f
    return (height * .5f + displacement).coerceIn(halfThickness, height - halfThickness)
}

/** ConePlayer's log-bin peak mapping and normalized five-tap smoothing kernel. */
internal fun coneSpectrumTargets(fft: ByteArray): List<Float> {
    val bins = fft.size / 2
    if (bins < 2) return List(64) { 0f }
    val raw = FloatArray(64)
    var start = 1
    val logMax = ln((bins - 1).toDouble())
    for (i in raw.indices) {
        val end = exp((i + 1.0) / 64 * logMax).toInt().coerceAtLeast(start).coerceAtMost(bins - 1)
        var peak = 0f
        for (bin in start..end) {
            peak = max(peak, hypot(fft[bin * 2].toFloat(), fft[bin * 2 + 1].toFloat()))
        }
        raw[i] = (peak / 180f).coerceIn(0f, 1f)
        start = end + 1
    }
    val kernel = floatArrayOf(.1f, .2f, .4f, .2f, .1f)
    return List(64) { i ->
        var sum = 0f; var weight = 0f
        for (j in kernel.indices) {
            val k = i + j - 2
            if (k in raw.indices) { sum += raw[k] * kernel[j]; weight += kernel[j] }
        }
        if (weight > 0f) sum / weight else 0f
    }
}
