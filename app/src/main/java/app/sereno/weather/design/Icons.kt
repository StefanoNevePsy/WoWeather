package app.sereno.weather.design

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sereno.weather.R
import app.sereno.weather.domain.model.Condition
import app.sereno.weather.domain.model.WeatherCodes

/**
 * Sereno's iconography.
 *
 * Earlier versions drew every glyph by hand with Bezier curves. On a real
 * device that showed: at small sizes the hand-tuned control points drifted out
 * of alignment, the crescent moon read as a comma, and the layered sun-behind-
 * cloud marks overlapped badly. Hand-drawing 30 icons well is a job for a type
 * designer with a lot of time, not for a few hundred lines of path data.
 *
 * So the set is now Lucide (ISC licence) — a single 24-unit grid, a single 2px
 * stroke weight, round caps and joins throughout. It covers the weather set and
 * the interface set, which means the chevron next to a rain glyph genuinely is
 * from the same family rather than merely trying to look like it.
 *
 * Every asset ships with a placeholder stroke colour and is tinted at the call
 * site, so one drawable serves ink, accent and every emphasis level.
 */

/** Interface icons. */
enum class Glyph(val res: Int) {
    ChevronRight(R.drawable.ic_chevron_right),
    ChevronLeft(R.drawable.ic_chevron_left),
    ChevronDown(R.drawable.ic_chevron_down),
    Search(R.drawable.ic_search),
    Pin(R.drawable.ic_map_pin),
    Locate(R.drawable.ic_locate_fixed),
    Plus(R.drawable.ic_plus),
    Close(R.drawable.ic_x),
    Gear(R.drawable.ic_sliders_horizontal),
    Play(R.drawable.ic_play),
    Pause(R.drawable.ic_pause),
    Layers(R.drawable.ic_layers),
    Drag(R.drawable.ic_grip_horizontal),
    Sun(R.drawable.ic_sun),
    Droplet(R.drawable.ic_droplet),
    Wind(R.drawable.ic_wind),
    Eye(R.drawable.ic_eye),
    Gauge(R.drawable.ic_gauge),
    Check(R.drawable.ic_check),
    Info(R.drawable.ic_info),
    Warning(R.drawable.ic_triangle_alert),
}

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
    val painter = rememberVectorPainter(ImageVector.vectorResource(glyph.res))

    Image(
        painter = painter,
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(base.copy(alpha = base.alpha * emphasis)),
        modifier = modifier
            .size(size)
            .then(if (contentDescription == null) Modifier.clearAndSetSemantics { } else Modifier),
    )
}

// ---------------------------------------------------------------------------
// Weather
// ---------------------------------------------------------------------------

/**
 * Maps a WMO code onto a glyph.
 *
 * Intensity is folded in where the set supports it — heavy rain gets the
 * wind-blown mark, light rain the drizzle one — and left alone where it does
 * not, rather than inventing a distinction the icon cannot carry.
 */
fun weatherIconRes(code: Int?, isDay: Boolean): Int {
    val condition = WeatherCodes.condition(code)
    val intensity = WeatherCodes.intensity(code)
    return when (condition) {
        Condition.Clear -> if (isDay) R.drawable.ic_sun else R.drawable.ic_moon
        Condition.MainlyClear,
        Condition.PartlyCloudy,
        -> if (isDay) R.drawable.ic_cloud_sun else R.drawable.ic_cloud_moon
        Condition.Overcast -> R.drawable.ic_cloudy
        Condition.Fog, Condition.RimeFog -> R.drawable.ic_cloud_fog
        Condition.Drizzle, Condition.FreezingDrizzle -> R.drawable.ic_cloud_drizzle
        Condition.Rain, Condition.FreezingRain ->
            if (intensity >= 2) R.drawable.ic_cloud_rain_wind else R.drawable.ic_cloud_rain
        Condition.RainShowers -> R.drawable.ic_cloud_rain_wind
        Condition.Snow, Condition.SnowShowers -> R.drawable.ic_cloud_snow
        Condition.SnowGrains -> R.drawable.ic_snowflake
        Condition.Thunderstorm -> R.drawable.ic_cloud_lightning
        Condition.ThunderstormHail -> R.drawable.ic_cloud_hail
        Condition.Unknown -> R.drawable.ic_cloud
    }
}

/**
 * The tint a condition deserves.
 *
 * Only genuinely sunny conditions take the warm accent. A cloud-and-sun mark
 * tinted orange turns the cloud orange too, which is worse than a neutral one,
 * so the rule stops at fully clear skies.
 */
@Composable
fun weatherTint(code: Int?, isDay: Boolean): Color {
    val atmosphere = Sereno.atmosphere
    val condition = WeatherCodes.condition(code)
    return if (isDay && condition == Condition.Clear) atmosphere.accent else atmosphere.ink
}

@Composable
fun WeatherGlyph(
    code: Int?,
    isDay: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    tint: Color = Color.Unspecified,
    emphasis: Float = Emphasis.primary,
    animated: Boolean = false,
    contentDescription: String? = null,
) {
    val motion = Sereno.motion
    val inspecting = LocalInspectionMode.current
    val base = if (tint != Color.Unspecified) tint else weatherTint(code, isDay)
    val painter = rememberVectorPainter(ImageVector.vectorResource(weatherIconRes(code, isDay)))

    // The hero mark breathes very slightly. It is under two percent of scale
    // over six seconds — felt rather than seen, and the first thing dropped
    // under reduce-motion.
    val shouldBreathe = animated && motion.ambientEnabled && !inspecting
    val transition = rememberInfiniteTransition(label = "glyphBreath")
    val breath by if (shouldBreathe) {
        transition.animateFloat(
            initialValue = 0.986f,
            targetValue = 1.014f,
            animationSpec = infiniteRepeatable(tween(6_000, easing = motion.easeInOut), RepeatMode.Reverse),
            label = "breath",
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    }

    Image(
        painter = painter,
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(base.copy(alpha = base.alpha * emphasis)),
        modifier = modifier
            .size(size)
            .scale(breath)
            .then(if (contentDescription == null) Modifier.clearAndSetSemantics { } else Modifier),
    )
}

/**
 * A painter for use inside a `Canvas`.
 *
 * Charts mark conditions along their axes, and a `DrawScope` cannot host a
 * composable — so the painter is resolved during composition and drawn later.
 */
@Composable
fun rememberWeatherPainter(code: Int?, isDay: Boolean): VectorPainter =
    rememberVectorPainter(ImageVector.vectorResource(weatherIconRes(code, isDay)))
