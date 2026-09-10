package com.ella.music.ui.player

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.util.Base64
import android.view.View
import androidx.annotation.RequiresApi
import android.view.animation.DecelerateInterpolator
import kotlin.math.max
import kotlin.math.roundToInt

class GlowGlowProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class ShaderMode {
        HIGH_END,
        MIDDLE
    }

    var shaderMode: ShaderMode = ShaderMode.HIGH_END
        set(value) {
            if (field != value) {
                field = value
                configureShader()
                invalidate()
            }
        }

    var progressFraction: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var headGlowAlpha: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var trackHeightPx: Float = dp(6f)
        set(value) {
            field = max(1f, value)
            invalidate()
        }

    var trackHorizontalPaddingPx: Float = dp(12f)
        set(value) {
            field = max(0f, value)
            invalidate()
        }

    var trackColor: Int = Color.argb(51, 255, 255, 255)
        set(value) {
            field = value
            invalidate()
        }

    var fallbackProgressColor: Int = Color.argb(153, 255, 255, 255)
        set(value) {
            field = value
            invalidate()
        }

    /** Tints the glow shader output; set to a dark color to invert the bar on a light background. */
    var glowColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    /**
     * When true, applies Lyricon-style ~1.5× SDR luminance/bloom boost to the progress glow
     * (same preference as Settings → Lyrics → lyric HDR highlight). Off keeps the default look.
     */
    var hdrBoostEnabled: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val hdrBoostRatio: Float
        get() = if (hdrBoostEnabled) HDR_BRIGHTNESS_RATIO else 1f

    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var runtimeShader: RuntimeShader? = null
    private var headBitmapShader: BitmapShader? = null
    private var headWidth = 75f
    private var headHeight = 38f
    private var glowAnimator: ValueAnimator? = null

    init {
        configureShader()
    }

    fun setProgress(progress: Int, max: Int) {
        progressFraction = if (max <= 0) 0f else progress.toFloat() / max.toFloat()
    }

    fun animateHeadGlow(targetAlpha: Float, durationMillis: Long = 220L) {
        glowAnimator?.cancel()
        glowAnimator = ValueAnimator.ofFloat(headGlowAlpha, targetAlpha.coerceIn(0f, 1f)).apply {
            duration = durationMillis
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                headGlowAlpha = animator.animatedValue as Float
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = dp(272f).roundToInt()
        val desiredHeight = dp(38f).roundToInt()
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val widthPx = width.toFloat()
        val heightPx = height.toFloat()
        if (widthPx <= 0f || heightPx <= 0f) return

        val shader = runtimeShader
        if (shader == null) {
            drawFallbackProgress(canvas, widthPx, heightPx)
            return
        }

        if (shaderMode == ShaderMode.MIDDLE) {
            drawFallbackProgress(canvas, widthPx, heightPx)
        }

        val trackHeight = trackHeightPx.coerceAtMost(heightPx)
        val trackWidth = max(0f, widthPx - trackHorizontalPaddingPx * 2f)
        val trackPosition = floatArrayOf(trackHorizontalPaddingPx, heightPx / 2f)
        val trackSize = floatArrayOf(trackWidth, trackHeight)
        val canvasSize = floatArrayOf(widthPx, heightPx)
        val headSize = floatArrayOf(headWidth, headHeight)

        updateRuntimeShaderUniforms(
            shader = shader,
            canvasSize = canvasSize,
            trackPosition = trackPosition,
            trackSize = trackSize,
            headSize = headSize
        )

        canvas.drawRect(0f, 0f, widthPx, heightPx, shaderPaint)
    }

    override fun onDetachedFromWindow() {
        glowAnimator?.cancel()
        glowAnimator = null
        super.onDetachedFromWindow()
    }

    private fun configureShader() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            runtimeShader = null
            shaderPaint.shader = null
            return
        }

        val shaderText = when (shaderMode) {
            ShaderMode.HIGH_END -> HIGH_END_SHADER
            ShaderMode.MIDDLE -> MIDDLE_SHADER
        }
        val shader = RuntimeShader(shaderText)
        val inputShader = headBitmapShader ?: createHeadBitmapShader().also { headBitmapShader = it }
        shader.setInputShader("uTex", inputShader)
        runtimeShader = shader
        shaderPaint.shader = shader
    }

    private fun createHeadBitmapShader(): BitmapShader {
        val bytes = Base64.decode(HEAD_PNG_BASE64, Base64.DEFAULT)
        val originalBitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        val w = originalBitmap.width
        val h = originalBitmap.height

        // Clear outer border pixels to ensure transparent edges, preventing any clamping bleed
        val cleanBitmap = originalBitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(w * h)
        cleanBitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        for (y in 0 until h) {
            for (x in 0 until 3) {
                pixels[y * w + x] = 0
            }
            pixels[y * w + (w - 1)] = 0
        }
        for (x in 0 until w) {
            pixels[x] = 0
            pixels[(h - 1) * w + x] = 0
        }
        cleanBitmap.setPixels(pixels, 0, w, 0, 0, w, h)

        headWidth = w.toFloat()
        headHeight = h.toFloat()
        val tileMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Shader.TileMode.DECAL
        } else {
            Shader.TileMode.CLAMP
        }
        return BitmapShader(cleanBitmap, tileMode, tileMode)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun updateRuntimeShaderUniforms(
        shader: RuntimeShader,
        canvasSize: FloatArray,
        trackPosition: FloatArray,
        trackSize: FloatArray,
        headSize: FloatArray
    ) {
        shader.setFloatUniform("uResolution", canvasSize)
        shader.setFloatUniform("uTrackCanvasSize", canvasSize)
        shader.setFloatUniform("uTrackPosition", trackPosition)
        shader.setFloatUniform("uTrackSize", trackSize)
        shader.setFloatUniform("uHeadSize", headSize)
        shader.setFloatUniform("uTrackProgress", progressFraction)
        shader.setFloatUniform("uHeadGlowAlpha", headGlowAlpha)
        shader.setFloatUniform("uHdrBoost", hdrBoostRatio)
        shader.setIntUniform("uIsRtl", if (layoutDirection == LAYOUT_DIRECTION_RTL) 1 else 0)
        val boosted = hdrBoostedRgb(glowColor)
        shader.setFloatUniform(
            "uContentColor",
            boosted[0],
            boosted[1],
            boosted[2]
        )
    }

    private fun drawFallbackProgress(canvas: Canvas, widthPx: Float, heightPx: Float) {
        val trackHeight = trackHeightPx.coerceAtMost(heightPx)
        val left = trackHorizontalPaddingPx
        val right = max(left, widthPx - trackHorizontalPaddingPx)
        val top = (heightPx - trackHeight) / 2f
        val bottom = top + trackHeight
        val radius = trackHeight / 2f

        fallbackPaint.color = trackColor
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, fallbackPaint)

        val progressWidth = (right - left) * progressFraction
        if (progressWidth <= 0f) return

        fallbackPaint.color = hdrBoostedArgb(fallbackProgressColor)
        if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            canvas.drawRoundRect(right - progressWidth, top, right, bottom, radius, radius, fallbackPaint)
        } else {
            canvas.drawRoundRect(left, top, left + progressWidth, bottom, radius, radius, fallbackPaint)
        }
    }


    /** Mix channel toward 1.0 by (1 - 1/ratio), matching Color.withHdrHighlightBoost. */
    private fun hdrBoostedRgb(color: Int): FloatArray {
        val ratio = hdrBoostRatio
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        if (ratio <= 1f) return floatArrayOf(r, g, b)
        val t = (1f - 1f / ratio).coerceIn(0f, 1f)
        return floatArrayOf(
            (r + (1f - r) * t).coerceIn(0f, 1f),
            (g + (1f - g) * t).coerceIn(0f, 1f),
            (b + (1f - b) * t).coerceIn(0f, 1f)
        )
    }

    private fun hdrBoostedArgb(color: Int): Int {
        val rgb = hdrBoostedRgb(color)
        val a = Color.alpha(color)
        // Slightly raise opacity so the SDR bloom reads brighter, capped.
        val boostedA = if (hdrBoostEnabled) {
            (a * HDR_BRIGHTNESS_RATIO).roundToInt().coerceAtMost(255)
        } else {
            a
        }
        return Color.argb(
            boostedA,
            (rgb[0] * 255f).roundToInt().coerceIn(0, 255),
            (rgb[1] * 255f).roundToInt().coerceIn(0, 255),
            (rgb[2] * 255f).roundToInt().coerceIn(0, 255)
        )
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        /** Lyricon-style HDR highlight brightness ratio (BasicStyle hdrBrightnessRatio default). */
        const val HDR_BRIGHTNESS_RATIO: Float = 1.5f

        private const val HEAD_PNG_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAEsAAAAmCAQAAAAmLa7lAAAD9UlEQVRYw+2YvY7dNhBGz1CU7rpNkyJd3v8l7JcIkARxkSBO5WKdhe31ipOCI2r4p12nCBDAurgSRVHi0TfD4VDwbfuPNpW+XB9VfI2Kv+Nqk38PcvFMrY59A81PEv0KLBXRy+4F0HJn7jzXjV9TG0A9kWdg8gIlZPoaHm3eSu1/nKmHHYPJBKo/azUSe7BUeDQq4vZaALW6NkCLF/qcGkj51wAygJHOkKmYMaEFCQ82BFAZIM330p0diNJA1jplqFRqnGatXnGoEhcI/b8vhWLcZEDZARKJUOmlY72i02qmTVv2GL5WuutKAJREAhJiaI0Bh75VhYORMnNNapizlNU6/UkIBhYA2MtzFAG9MKJMwKQpn2gey/9CFd8CyUwpCLtBK8mekUZmjJXD9+NMOnPVMBSUFu70noSS2FGUgAKB3bS0ENzqFQfu3YLkt/JqBDsPpRxYSimwNIgZ7JEPPPCZZEYV03Lg9lG9DocaR2dL6Th3u1jNSrSajZWFhciNyMLCSuSOaPdEghlTeeB3fuJPPpVxKsX9OyOK63KxR8XSxVbOVzY7rtxYWdiI3NjK8Y7VSq9YWVkJBKKNQeUjv/Ga1/xhY1K7GaEy4kJkJbKxWrcrWzluhpQBcruboRzl3G6zFhlwYyMgxaOEL3zPzjvec1+0Or2sU0uKIrE8+EBaXWd1/Vp0OtocLxaLsRcLFHm78R0/8gN3fCjjfWLE4JKPNvWQKharywRSGbWhuRMLoPl6KnMik1xDxxlftMEr5poHQiKx80Qi8cTGbjUbSmIDdvZm+lWHFoAFWFyu8MR73vKOjxVUmmHtQGLns8l/7LPHLY3Db9xKKRsze9idtX9lnhe5YytYAeFvfuUNv/DJAqzOU5soqgk1vagiUg4QUoa62HEpNZsLChsLwUZibnNjNZcXFoR73vIzf/HFQeWYJiqjcKrWIE8OMgioZ1ANLqjm7gJi0MECjQCL+a2apyUeueeBR8snzrwCXpAGamV5sf05saZqPsRhSjM5qfPUw1t3K2mXdeEVi91i4Myz68UDnaNKidPB2oaCSYnt5xDK++Rqq0Dqzeiz0+cyTumm8HkWcQSDFmw3rL261rl8nTT3YEwzriPm9flWndhkJIpax1maQ5VU5tkM9Tpvl2HCSAm9HuwEPf1rrNY052Ky3JJhukiVy3u1ai9TB+uWsd7lx+tEmayDdIpWZ+/iBk+9RtQSHs4sS6dGHK6qZbh+brG1Wyd6z6KDa5awz6yqLxb8Mvz4IdU6+/wcQjXF+2vJxibXK+qLLzYZ7/iy0qyOZh9Jrr4AHZFe6rj+gk8jX/cxqQVtzqvUzl/LIO0cOKv7tv2Pt38A9nPaiTc6xcoAAAAASUVORK5CYII="

        private val HIGH_END_SHADER = """
            uniform vec2 uResolution;
            uniform shader uTex;
            uniform float uTrackProgress;
            uniform vec2 uTrackSize;
            uniform vec2 uTrackPosition;
            uniform vec2 uTrackCanvasSize;
            uniform vec2 uHeadSize;
            uniform float uHeadGlowAlpha;
            uniform float uHdrBoost;
            uniform int uIsRtl;
            uniform vec3 uContentColor;

            vec4 alphaBlend(vec4 src, vec4 dst) {
                vec3 color = src.rgb + (1.0 - src.a) * dst.rgb;
                float alpha = src.a + dst.a * (1.0 - src.a);
                return vec4(color, alpha);
            }

            float rbx(in vec2 p, in vec2 b, in vec4 r) {
                r.xy = (p.x > 0.0) ? r.xy : r.zw;
                r.x = (p.y > 0.0) ? r.x : r.y;
                vec2 q = abs(p) - b + r.x;
                return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
            }

            float capsule(in vec2 uv, in vec2 pos, in vec2 size) {
                vec2 p = uv - vec2(0.5);
                p.x *= uResolution.x / uResolution.y;
                vec2 b = size / uResolution.y / 2.0;
                p.y += (uResolution.y / 2.0 - pos.y - size.y / 2.0) / uResolution.y;
                p.x -= pos.x / uResolution.y + b.x - uResolution.x / uResolution.y / 2.0;
                vec4 r = vec4(min(size.x, size.y) / uResolution.y / 2.0);
                return rbx(p, b, r);
            }

            vec4 draw_track(in vec2 uv) {
                float d = capsule(uv, vec2(uTrackPosition.x, uResolution.y - uTrackPosition.y - uTrackSize.y / 2.0), uTrackSize);
                float a = smoothstep(1.0 / uResolution.y, -1.0 / uResolution.y, d);
                return vec4(0.2 * a);
            }

            vec4 draw_track_progress(in vec2 uv, in float progress) {
                float m = min(uTrackSize.x, uTrackSize.y);
                vec2 size = uTrackSize;
                size.x = mix(m, uTrackSize.x, progress);
                float d = capsule(uv, vec2(uTrackPosition.x, uResolution.y - uTrackPosition.y - uTrackSize.y / 2.0), size);
                // HDR on: scale fill opacity by ~1.5× (capped) for Lyricon-like bloom.
                float fillGain = clamp(uHdrBoost, 1.0, 1.65);
                float a = smoothstep(1.0 / uResolution.y, -1.0 / uResolution.y, d) * 0.6 * fillGain;

                vec2 hsize = vec2(75.0, 38.0) * 2.7551020408;
                float thight = 6.0 * 2.7551020408;
                float xoffset = 52.0 * 2.7551020408;
                vec2 st = uv;
                st.x = (st.x - uTrackPosition.x / uResolution.x) / (uTrackSize.x / uResolution.x);
                st.y -= (uResolution.y - uTrackPosition.y - uTrackSize.y / 2.0) / uResolution.y;
                st.y *= uResolution.y / uTrackSize.y;
                float startFade = smoothstep(0.0, uTrackSize.y / uTrackSize.x * (hsize.x / hsize.y) * 1.5, st.x);
                st.x -= mix(uTrackSize.y / 2.0, uTrackSize.x - uTrackSize.y / 2.0, progress) / uTrackSize.x;
                st.y -= 0.5;
                float headScale = clamp(uTrackSize.y / thight, 0.75, 1.15);
                st.x /= headScale * (hsize.x / uTrackSize.x);
                st.y /= headScale * (hsize.y / uTrackSize.y);
                st.y += 0.5;
                st.x -= -xoffset / hsize.x;
                vec2 clampedSt = clamp(st, 0.0, 1.0);
                float inBounds = step(0.0, st.x) * step(st.x, 1.0) * step(0.0, st.y) * step(st.y, 1.0);
                vec4 head = uTex.eval(clampedSt * uHeadSize) * (startFade * uHeadGlowAlpha * inBounds * fillGain);
                vec4 blended = alphaBlend(head, vec4(a));
                // Toward-white luminance boost on the progress/head (track stays untouched).
                float t = clamp(1.0 - 1.0 / max(uHdrBoost, 1.0), 0.0, 1.0);
                blended.rgb = mix(blended.rgb, vec3(1.0) * blended.a + blended.rgb * (1.0 - blended.a), t);
                blended.rgb = min(blended.rgb, vec3(1.0));
                blended.a = min(blended.a, 1.0);
                return blended;
            }

            vec4 main(vec2 fragCoord) {
                vec2 vUv = fragCoord / uResolution;
                if (uIsRtl == 1) {
                    vUv.x = 1.0 - vUv.x;
                }
                vec4 color = vec4(0.0);
                color = alphaBlend(draw_track(vUv), color);
                color = alphaBlend(draw_track_progress(vUv, uTrackProgress), color);
                // Tint the (premultiplied) output so the bar can invert to dark on a light player.
                return vec4(color.rgb * uContentColor, color.a);
            }
        """.trimIndent()

        private val MIDDLE_SHADER = HIGH_END_SHADER
    }
}
