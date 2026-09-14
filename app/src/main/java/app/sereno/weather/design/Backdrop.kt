package app.sereno.weather.design

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalInspectionMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * The sky the whole app sits on.
 *
 * This is the component that carries the art direction. It is one `Canvas`,
 * drawn in four passes — gradient, light source, haze, texture — plus an
 * optional weather layer. Everything is a gradient or a handful of primitives,
 * so it costs nothing to redraw and never needs a bitmap the size of the screen.
 *
 * Three details do most of the work:
 *
 *  - **The light has a position.** Every atmosphere puts its glow somewhere
 *    specific (high right for a clear morning, straight overhead and diffuse
 *    for overcast), so the screen reads as lit rather than tinted.
 *  - **Grain.** A 96px tiled noise texture at 3–6% alpha. Large smooth
 *    gradients band badly on 8-bit displays, and the grain both hides that and
 *    gives the flat colour a physical, printed quality.
 *  - **Parallax.** The backdrop moves at a fraction of the content's scroll
 *    speed, which is what makes the content feel like it is floating in front
 *    of a sky rather than painted onto it.
 */
@Composable
fun AtmosphericBackdrop(
    modifier: Modifier = Modifier,
    parallax: Float = 0f,
) {
    val atmosphere = Sereno.atmosphere
    val motion = Sereno.motion
    val inspecting = LocalInspectionMode.current
    val animate = motion.ambientEnabled && !inspecting

    val noise = rememberNoiseTexture()

    // One slow cycle drives every ambient movement, so nothing beats against
    // anything else. 48 seconds is below the threshold at which the eye reads
    // it as motion at all — it is felt rather than seen.
    val transition = rememberInfiniteTransition(label = "atmosphere")
    val drift by if (animate) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(48_000, easing = LinearEasing), RepeatMode.Restart),
            label = "drift",
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Canvas(modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        val shift = parallax * height * 0.12f

        // 1. The sky itself.
        drawRect(
            brush = Brush.verticalGradient(
                0.0f to atmosphere.skyTop,
                0.45f to atmosphere.skyMid,
                1.0f to atmosphere.skyLow,
                startY = -shift,
                endY = height * 1.15f - shift,
            ),
        )

        // 2. The light source.
        val breathe = sin(drift * 2 * PI.toFloat()) * 0.035f
        val glowCentre = Offset(
            x = width * atmosphere.glowCenter.x,
            y = height * atmosphere.glowCenter.y - shift * 1.4f,
        )
        val glowRadius = maxOf(width, height) * atmosphere.glowRadius * (1f + breathe)
        drawCircle(
            brush = Brush.radialGradient(
                0.0f to atmosphere.glow.copy(alpha = 0.55f),
                0.35f to atmosphere.glow.copy(alpha = 0.26f),
                0.7f to atmosphere.glow.copy(alpha = 0.07f),
                1.0f to Color.Transparent,
                center = glowCentre,
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = glowCentre,
        )

        // 3. Atmospheric haze rising from the horizon. This is what separates
        //    fog from a merely grey palette: depth collapses towards the bottom.
        if (atmosphere.haze > 0.02f) {
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.55f to atmosphere.skyLow.copy(alpha = atmosphere.haze * 0.5f),
                    1.0f to atmosphere.skyLow.copy(alpha = atmosphere.haze),
                    startY = height * 0.35f - shift,
                    endY = height * 1.05f - shift,
                ),
            )
        }

        // 4. Weather-specific detail, deliberately sparse.
        when (atmosphere.mood) {
            Mood.ClearNight, Mood.PartlyNight -> drawStars(drift, atmosphere, animate)
            Mood.Rain -> drawRainfall(drift, atmosphere, animate, density = 46)
            Mood.Thunder -> drawRainfall(drift, atmosphere, animate, density = 70)
            Mood.Snow -> drawSnowfall(drift, atmosphere, animate)
            else -> Unit
        }

        // 5. Grain, last, over everything.
        if (noise != null && atmosphere.grain > 0f) {
            drawRect(
                brush = ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated)),
                alpha = atmosphere.grain,
            )
        }
    }
}

/**
 * Stars, as a detail rather than a feature.
 *
 * Forty of them, placed deterministically, concentrated in the upper half where
 * the sky is darkest, each twinkling on its own phase. A starfield that fills
 * the screen looks like a screensaver; this reads as night.
 */
private fun DrawScope.drawStars(phase: Float, atmosphere: Atmosphere, animate: Boolean) {
    val random = Random(20260914)
    repeat(42) {
        val x = random.nextFloat() * size.width
        // Bias upward: stars fade into the haze near the horizon.
        val y = random.nextFloat() * random.nextFloat() * size.height * 0.72f
        val baseAlpha = 0.18f + random.nextFloat() * 0.5f
        val starPhase = random.nextFloat()
        val twinkle = if (animate) 0.72f + 0.28f * sin((phase + starPhase) * 2 * PI.toFloat() * 3f) else 1f
        val radius = 0.6f + random.nextFloat() * 1.1f
        drawCircle(
            color = atmosphere.ink.copy(alpha = (baseAlpha * twinkle).coerceIn(0f, 1f)),
            radius = radius,
            center = Offset(x, y),
        )
    }
}

/**
 * Rain, as texture rather than as particles.
 *
 * Thin, near-transparent streaks falling at a slight angle. The brief asked for
 * rain that never distracts, so these sit at 10% alpha or below and are the
 * first thing dropped under reduce-motion.
 */
private fun DrawScope.drawRainfall(phase: Float, atmosphere: Atmosphere, animate: Boolean, density: Int) {
    if (!animate) return
    val random = Random(4711)
    val slant = size.width * 0.03f
    repeat(density) {
        val x = random.nextFloat() * (size.width + slant * 2) - slant
        val speed = 0.6f + random.nextFloat() * 0.9f
        val length = size.height * (0.03f + random.nextFloat() * 0.05f)
        val offset = ((phase * speed * 14f) + random.nextFloat()) % 1f
        val y = offset * (size.height + length) - length
        val alpha = 0.04f + random.nextFloat() * 0.06f
        drawLine(
            color = atmosphere.ink.copy(alpha = alpha),
            start = Offset(x, y),
            end = Offset(x + slant, y + length),
            strokeWidth = 1.1f,
            cap = StrokeCap.Round,
        )
    }
}

/** Snow: slower, rounder, and drifting sideways on a sine. */
private fun DrawScope.drawSnowfall(phase: Float, atmosphere: Atmosphere, animate: Boolean) {
    if (!animate) return
    val random = Random(90210)
    repeat(38) {
        val seedX = random.nextFloat()
        val speed = 0.25f + random.nextFloat() * 0.35f
        val sway = random.nextFloat() * 2 * PI.toFloat()
        val offset = ((phase * speed * 6f) + random.nextFloat()) % 1f
        val y = offset * size.height
        val x = seedX * size.width + sin(phase * 2 * PI.toFloat() * 1.5f + sway) * size.width * 0.03f
        val radius = 1.0f + random.nextFloat() * 1.8f
        drawCircle(
            color = atmosphere.ink.copy(alpha = 0.10f + random.nextFloat() * 0.14f),
            radius = radius,
            center = Offset(x, y),
        )
    }
}

/**
 * A small tiling noise texture, built once per process.
 *
 * 96x96 monochrome noise in the alpha channel. Tiled with a repeating shader it
 * covers any screen for the cost of one 36 KB bitmap.
 */
@Composable
private fun rememberNoiseTexture(): ImageBitmap? = remember {
    runCatching {
        val side = 96
        val random = Random(1312)
        val pixels = IntArray(side * side) {
            // Triangular distribution: two uniform samples averaged. Flat noise
            // looks like static; this looks like paper.
            val value = ((random.nextInt(256) + random.nextInt(256)) / 2)
            val alpha = (abs(value - 128) * 2).coerceIn(0, 255)
            (alpha shl 24) or (value shl 16) or (value shl 8) or value
        }
        Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888).asImageBitmap()
    }.getOrNull()
}
