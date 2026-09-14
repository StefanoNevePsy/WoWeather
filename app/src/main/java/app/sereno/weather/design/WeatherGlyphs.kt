package app.sereno.weather.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sereno.weather.domain.model.Condition
import app.sereno.weather.domain.model.WeatherCodes
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sereno's weather icons, drawn rather than imported.
 *
 * They are built on a 24-unit grid from open strokes of a single weight, with a
 * filled accent reserved for exactly two things: the sun's disc and a lightning
 * bolt. That restraint is the identity — nine glyphs that are unmistakably one
 * family, and that read correctly at 20dp in a list and at 96dp in the hero.
 *
 * Being vector-drawn rather than font- or asset-based also means they inherit
 * the atmosphere's ink colour automatically and can animate individual parts
 * (only the falling strokes move, never the whole icon).
 */
@Composable
fun WeatherGlyph(
    code: Int?,
    isDay: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    tint: Color = Color.Unspecified,
    accent: Color = Color.Unspecified,
    animated: Boolean = false,
    contentDescription: String? = null,
) {
    val atmosphere = Sereno.atmosphere
    val motion = Sereno.motion
    val inspecting = LocalInspectionMode.current
    val ink = if (tint != Color.Unspecified) tint else atmosphere.ink
    val highlight = if (accent != Color.Unspecified) accent else atmosphere.accent

    val shouldAnimate = animated && motion.ambientEnabled && !inspecting
    val transition = rememberInfiniteTransition(label = "glyph")
    val phase by if (shouldAnimate) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
            label = "glyphPhase",
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    val condition = WeatherCodes.condition(code)
    val intensity = WeatherCodes.intensity(code)

    Canvas(
        modifier
            .size(size)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier.clearAndSetSemantics { }
                },
            ),
    ) {
        val unit = this.size.minDimension / 24f
        withTransform({ scale(unit, unit, pivot = Offset.Zero) }) {
            drawCondition(condition, intensity, isDay, ink, highlight, phase)
        }
    }
}

private fun DrawScope.drawCondition(
    condition: Condition,
    intensity: Int,
    isDay: Boolean,
    ink: Color,
    accent: Color,
    phase: Float,
) {
    when (condition) {
        Condition.Clear -> if (isDay) sun(accent, ink, 12f, 12f, 1f) else moon(ink)
        Condition.MainlyClear -> if (isDay) {
            sun(accent, ink, 9.5f, 9.5f, 0.86f); cloud(ink, dx = 2.5f, dy = 3.5f, scale = 0.82f)
        } else {
            moon(ink, scale = 0.86f, dx = -1.5f, dy = -1f); cloud(ink, dx = 2.5f, dy = 3.5f, scale = 0.82f)
        }
        Condition.PartlyCloudy -> if (isDay) {
            sun(accent, ink, 8.5f, 8.5f, 0.78f); cloud(ink, dx = 1.5f, dy = 3f, scale = 0.94f)
        } else {
            moon(ink, scale = 0.8f, dx = -2f, dy = -1.5f); cloud(ink, dx = 1.5f, dy = 3f, scale = 0.94f)
        }
        Condition.Overcast -> {
            cloud(ink.copy(alpha = ink.alpha * 0.45f), dx = -2f, dy = -2f, scale = 0.76f)
            cloud(ink, dx = 1f, dy = 1.5f, scale = 0.96f)
        }
        Condition.Fog, Condition.RimeFog -> {
            cloud(ink, dy = -2.5f, scale = 0.92f)
            fogLines(ink)
        }
        Condition.Drizzle, Condition.FreezingDrizzle -> {
            cloud(ink, dy = -2f)
            fallingStrokes(ink, count = 3, length = 1.6f, phase = phase, alpha = 0.75f)
        }
        Condition.Rain, Condition.FreezingRain -> {
            cloud(ink, dy = -2f)
            fallingStrokes(ink, count = if (intensity >= 2) 4 else 3, length = if (intensity >= 2) 4f else 3f, phase = phase)
        }
        Condition.RainShowers -> {
            cloud(ink, dy = -2f, dx = -0.6f)
            fallingStrokes(ink, count = 3, length = 3.2f, phase = phase, slant = 1.4f)
        }
        Condition.Snow, Condition.SnowGrains, Condition.SnowShowers -> {
            cloud(ink, dy = -2f)
            flakes(ink, phase)
        }
        Condition.Thunderstorm, Condition.ThunderstormHail -> {
            cloud(ink, dy = -2.5f)
            bolt(accent)
            if (condition == Condition.ThunderstormHail) hail(ink, phase)
        }
        Condition.Unknown -> cloud(ink.copy(alpha = ink.alpha * 0.5f))
    }
}

// ---------------------------------------------------------------------------
// Parts
// ---------------------------------------------------------------------------

private fun DrawScope.strokeStyle(width: Float = 1.7f) =
    Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

/**
 * The cloud. Three lobes over a flat base, as one closed path so the join
 * between the lobes and the base is a single continuous contour.
 */
private fun DrawScope.cloud(
    color: Color,
    dx: Float = 0f,
    dy: Float = 0f,
    scale: Float = 1f,
) {
    val path = Path().apply {
        moveTo(5.6f, 17f)
        cubicTo(3.1f, 17f, 2.0f, 14.4f, 3.6f, 12.7f)
        cubicTo(4.4f, 11.8f, 5.5f, 11.5f, 6.6f, 11.9f)
        cubicTo(7.0f, 8.9f, 9.9f, 7.0f, 12.8f, 7.9f)
        cubicTo(14.9f, 8.6f, 16.3f, 10.4f, 16.4f, 12.5f)
        cubicTo(18.9f, 12.1f, 21.0f, 14.2f, 20.4f, 16.0f)
        cubicTo(20.1f, 16.7f, 19.4f, 17f, 18.6f, 17f)
        close()
    }
    withTransform({
        translate(dx, dy)
        scale(scale, scale, pivot = Offset(12f, 12f))
    }) {
        drawPath(path, color, style = strokeStyle())
    }
}

/** The sun: a filled disc with detached rays, which reads warmer than an outline. */
private fun DrawScope.sun(accent: Color, ink: Color, cx: Float, cy: Float, scale: Float) {
    val radius = 4.1f * scale
    drawCircle(accent, radius = radius, center = Offset(cx, cy))
    val rayInner = radius + 1.9f
    val rayOuter = radius + 3.5f
    repeat(8) { index ->
        val angle = (index * 45f) * PI.toFloat() / 180f
        drawLine(
            color = accent,
            start = Offset(cx + cos(angle) * rayInner, cy + sin(angle) * rayInner),
            end = Offset(cx + cos(angle) * rayOuter, cy + sin(angle) * rayOuter),
            strokeWidth = 1.6f * scale,
            cap = StrokeCap.Round,
        )
    }
}

/** The moon, as a crescent outline rather than a circle with a bite taken out. */
private fun DrawScope.moon(ink: Color, scale: Float = 1f, dx: Float = 0f, dy: Float = 0f) {
    val path = Path().apply {
        moveTo(16.4f, 4.4f)
        cubicTo(9.7f, 6.1f, 6.0f, 13.0f, 8.7f, 18.4f)
        cubicTo(10.2f, 21.2f, 13.1f, 22.5f, 16.0f, 21.8f)
        cubicTo(10.5f, 18.6f, 9.7f, 11.1f, 16.4f, 4.4f)
        close()
    }
    withTransform({
        translate(dx, dy)
        scale(scale, scale, pivot = Offset(12f, 12f))
    }) {
        drawPath(path, ink, style = strokeStyle())
    }
}

/**
 * Falling precipitation.
 *
 * [phase] slides the strokes down through one period and fades them at both
 * ends, so an animated icon loops without a visible jump. At rest ([phase] 0)
 * the strokes sit in their neutral position and the icon is a still drawing.
 */
private fun DrawScope.fallingStrokes(
    ink: Color,
    count: Int,
    length: Float,
    phase: Float,
    slant: Float = 0.8f,
    alpha: Float = 1f,
) {
    val spacing = 3.8f
    val startX = 12f - (count - 1) * spacing / 2f
    repeat(count) { index ->
        val travel = if (phase == 0f) 0f else ((phase + index * 0.27f) % 1f) * 3.2f
        val fade = if (phase == 0f) 1f else {
            val p = (phase + index * 0.27f) % 1f
            (1f - kotlin.math.abs(p - 0.45f) * 1.6f).coerceIn(0.25f, 1f)
        }
        val x = startX + index * spacing
        val y = 18.2f + travel
        drawLine(
            color = ink.copy(alpha = ink.alpha * alpha * fade),
            start = Offset(x, y),
            end = Offset(x - slant, y + length),
            strokeWidth = 1.7f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.flakes(ink: Color, phase: Float) {
    val positions = listOf(7.4f to 19.4f, 12f to 20.6f, 16.6f to 19.4f)
    positions.forEachIndexed { index, (x, baseY) ->
        val drift = if (phase == 0f) 0f else sin((phase + index * 0.33f) * 2 * PI.toFloat()) * 0.6f
        val y = baseY + (if (phase == 0f) 0f else ((phase + index * 0.3f) % 1f) * 1.4f)
        repeat(3) { arm ->
            val angle = (arm * 60f) * PI.toFloat() / 180f
            val r = 1.35f
            drawLine(
                color = ink,
                start = Offset(x + drift - cos(angle) * r, y - sin(angle) * r),
                end = Offset(x + drift + cos(angle) * r, y + sin(angle) * r),
                strokeWidth = 1.3f,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.bolt(accent: Color) {
    val path = Path().apply {
        moveTo(13.4f, 15.2f)
        lineTo(9.4f, 20.4f)
        lineTo(11.9f, 20.4f)
        lineTo(10.6f, 23.6f)
        lineTo(14.8f, 18.3f)
        lineTo(12.2f, 18.3f)
        close()
    }
    drawPath(path, accent)
}

private fun DrawScope.hail(ink: Color, phase: Float) {
    listOf(7.0f to 20.2f, 17.2f to 20.2f).forEachIndexed { index, (x, baseY) ->
        val fall = if (phase == 0f) 0f else ((phase + index * 0.5f) % 1f) * 1.6f
        drawCircle(ink, radius = 1.0f, center = Offset(x, baseY + fall))
    }
}

private fun DrawScope.fogLines(ink: Color) {
    val rows = listOf(
        Triple(4.6f, 18.6f, 14.8f),
        Triple(6.8f, 21.2f, 12.6f),
    )
    rows.forEach { (startX, y, width) ->
        drawLine(
            color = ink.copy(alpha = ink.alpha * 0.8f),
            start = Offset(startX, y),
            end = Offset(startX + width, y),
            strokeWidth = 1.7f,
            cap = StrokeCap.Round,
        )
    }
}

// ---------------------------------------------------------------------------
// Interface glyphs
// ---------------------------------------------------------------------------

enum class Glyph { ChevronRight, ChevronDown, ChevronLeft, Search, Pin, Plus, Close, Gear, Play, Pause, Layers, Drag, Sun, Droplet, Wind, Eye, Gauge, Check, Info, Warning }

/**
 * The non-weather icons, drawn on the same 24-unit grid and the same stroke
 * weight so a chevron next to a rain glyph looks like it came from the same set.
 */
@Composable
fun SGlyph(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = Color.Unspecified,
    emphasis: Float = Emphasis.secondary,
    contentDescription: String? = null,
) {
    val atmosphere = Sereno.atmosphere
    val base = if (tint != Color.Unspecified) tint else atmosphere.ink
    val color = base.copy(alpha = base.alpha * emphasis)

    Canvas(
        modifier
            .size(size)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else Modifier.clearAndSetSemantics { },
            ),
    ) {
        val unit = this.size.minDimension / 24f
        withTransform({ scale(unit, unit, pivot = Offset.Zero) }) {
            drawGlyph(glyph, color)
        }
    }
}

private fun DrawScope.drawGlyph(glyph: Glyph, color: Color) {
    val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (glyph) {
        Glyph.ChevronRight -> drawPath(
            Path().apply { moveTo(9.5f, 5.5f); lineTo(16f, 12f); lineTo(9.5f, 18.5f) }, color, style = stroke,
        )
        Glyph.ChevronLeft -> drawPath(
            Path().apply { moveTo(14.5f, 5.5f); lineTo(8f, 12f); lineTo(14.5f, 18.5f) }, color, style = stroke,
        )
        Glyph.ChevronDown -> drawPath(
            Path().apply { moveTo(5.5f, 9.5f); lineTo(12f, 16f); lineTo(18.5f, 9.5f) }, color, style = stroke,
        )
        Glyph.Search -> {
            drawCircle(color, radius = 6.4f, center = Offset(10.6f, 10.6f), style = stroke)
            drawLine(color, Offset(15.4f, 15.4f), Offset(20f, 20f), strokeWidth = 1.8f, cap = StrokeCap.Round)
        }
        Glyph.Pin -> {
            drawPath(
                Path().apply {
                    moveTo(12f, 21.5f)
                    cubicTo(12f, 21.5f, 4.8f, 15.2f, 4.8f, 10.2f)
                    cubicTo(4.8f, 6.2f, 8.0f, 3.0f, 12f, 3.0f)
                    cubicTo(16f, 3.0f, 19.2f, 6.2f, 19.2f, 10.2f)
                    cubicTo(19.2f, 15.2f, 12f, 21.5f, 12f, 21.5f)
                    close()
                },
                color, style = stroke,
            )
            drawCircle(color, radius = 2.4f, center = Offset(12f, 10.1f), style = Stroke(1.8f))
        }
        Glyph.Plus -> {
            drawLine(color, Offset(12f, 5.5f), Offset(12f, 18.5f), strokeWidth = 1.8f, cap = StrokeCap.Round)
            drawLine(color, Offset(5.5f, 12f), Offset(18.5f, 12f), strokeWidth = 1.8f, cap = StrokeCap.Round)
        }
        Glyph.Close -> {
            drawLine(color, Offset(6.5f, 6.5f), Offset(17.5f, 17.5f), strokeWidth = 1.8f, cap = StrokeCap.Round)
            drawLine(color, Offset(17.5f, 6.5f), Offset(6.5f, 17.5f), strokeWidth = 1.8f, cap = StrokeCap.Round)
        }
        Glyph.Gear -> {
            drawCircle(color, radius = 3.1f, center = Offset(12f, 12f), style = stroke)
            repeat(8) { index ->
                val angle = index * 45f * PI.toFloat() / 180f
                drawLine(
                    color,
                    Offset(12f + cos(angle) * 5.4f, 12f + sin(angle) * 5.4f),
                    Offset(12f + cos(angle) * 7.6f, 12f + sin(angle) * 7.6f),
                    strokeWidth = 1.8f, cap = StrokeCap.Round,
                )
            }
        }
        Glyph.Play -> drawPath(
            Path().apply { moveTo(8.5f, 5.5f); lineTo(18.5f, 12f); lineTo(8.5f, 18.5f); close() }, color,
        )
        Glyph.Pause -> {
            drawLine(color, Offset(9f, 5.5f), Offset(9f, 18.5f), strokeWidth = 2.6f, cap = StrokeCap.Round)
            drawLine(color, Offset(15f, 5.5f), Offset(15f, 18.5f), strokeWidth = 2.6f, cap = StrokeCap.Round)
        }
        Glyph.Layers -> {
            drawPath(
                Path().apply {
                    moveTo(12f, 3.4f); lineTo(21f, 8.2f); lineTo(12f, 13f); lineTo(3f, 8.2f); close()
                },
                color, style = stroke,
            )
            drawPath(
                Path().apply { moveTo(3.6f, 13.4f); lineTo(12f, 18f); lineTo(20.4f, 13.4f) },
                color.copy(alpha = color.alpha * 0.55f), style = stroke,
            )
        }
        Glyph.Drag -> {
            listOf(9.5f, 14.5f).forEach { y ->
                drawLine(color, Offset(7f, y), Offset(17f, y), strokeWidth = 1.8f, cap = StrokeCap.Round)
            }
        }
        Glyph.Sun -> {
            drawCircle(color, radius = 4.1f, center = Offset(12f, 12f), style = Stroke(1.8f))
            repeat(8) { index ->
                val angle = index * 45f * PI.toFloat() / 180f
                drawLine(
                    color,
                    Offset(12f + cos(angle) * 6.2f, 12f + sin(angle) * 6.2f),
                    Offset(12f + cos(angle) * 8.1f, 12f + sin(angle) * 8.1f),
                    strokeWidth = 1.7f, cap = StrokeCap.Round,
                )
            }
        }
        Glyph.Droplet -> drawPath(
            Path().apply {
                moveTo(12f, 3.6f)
                cubicTo(12f, 3.6f, 5.4f, 11.2f, 5.4f, 15.2f)
                cubicTo(5.4f, 18.9f, 8.4f, 21.4f, 12f, 21.4f)
                cubicTo(15.6f, 21.4f, 18.6f, 18.9f, 18.6f, 15.2f)
                cubicTo(18.6f, 11.2f, 12f, 3.6f, 12f, 3.6f)
                close()
            },
            color, style = stroke,
        )
        Glyph.Wind -> {
            drawPath(
                Path().apply {
                    moveTo(3.2f, 9f); lineTo(13f, 9f)
                    cubicTo(15.2f, 9f, 15.9f, 5.6f, 13.9f, 4.8f)
                },
                color, style = stroke,
            )
            drawPath(
                Path().apply {
                    moveTo(3.2f, 14.4f); lineTo(16.4f, 14.4f)
                    cubicTo(19f, 14.4f, 19.6f, 18.6f, 17.2f, 19.4f)
                },
                color, style = stroke,
            )
        }
        Glyph.Eye -> {
            drawPath(
                Path().apply {
                    moveTo(2.6f, 12f)
                    cubicTo(6f, 6.6f, 18f, 6.6f, 21.4f, 12f)
                    cubicTo(18f, 17.4f, 6f, 17.4f, 2.6f, 12f)
                    close()
                },
                color, style = stroke,
            )
            drawCircle(color, radius = 2.6f, center = Offset(12f, 12f), style = Stroke(1.8f))
        }
        Glyph.Gauge -> {
            drawArc(
                color = color, startAngle = 160f, sweepAngle = 220f, useCenter = false,
                topLeft = Offset(4f, 4f), size = androidx.compose.ui.geometry.Size(16f, 16f), style = stroke,
            )
            drawLine(color, Offset(12f, 12f), Offset(15.6f, 8.6f), strokeWidth = 1.8f, cap = StrokeCap.Round)
        }
        Glyph.Check -> drawPath(
            Path().apply { moveTo(5.5f, 12.6f); lineTo(10f, 17f); lineTo(18.5f, 7.4f) }, color, style = stroke,
        )
        Glyph.Info -> {
            drawCircle(color, radius = 8.6f, center = Offset(12f, 12f), style = stroke)
            drawLine(color, Offset(12f, 11f), Offset(12f, 16.4f), strokeWidth = 1.8f, cap = StrokeCap.Round)
            drawCircle(color, radius = 1.05f, center = Offset(12f, 7.8f))
        }
        Glyph.Warning -> {
            drawPath(
                Path().apply {
                    moveTo(12f, 3.6f); lineTo(21.4f, 20f); lineTo(2.6f, 20f); close()
                },
                color, style = stroke,
            )
            drawLine(color, Offset(12f, 10f), Offset(12f, 15f), strokeWidth = 1.8f, cap = StrokeCap.Round)
            drawCircle(color, radius = 1.0f, center = Offset(12f, 17.6f))
        }
    }
}


/**
 * Draws a weather glyph straight into an existing [DrawScope].
 *
 * Charts need the icons inline — condition marks along the hourly ribbon, for
 * instance — where a composable cannot go. This shares the exact drawing code
 * with [WeatherGlyph] so the two can never drift apart.
 */
fun DrawScope.drawWeatherGlyph(
    code: Int?,
    isDay: Boolean,
    center: Offset,
    sizePx: Float,
    ink: Color,
    accent: Color,
) {
    val unit = sizePx / 24f
    withTransform({
        translate(center.x - sizePx / 2f, center.y - sizePx / 2f)
        scale(unit, unit, pivot = Offset.Zero)
    }) {
        drawCondition(
            condition = WeatherCodes.condition(code),
            intensity = WeatherCodes.intensity(code),
            isDay = isDay,
            ink = ink,
            accent = accent,
            phase = 0f,
        )
    }
}
