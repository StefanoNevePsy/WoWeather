package app.sereno.weather.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * The nine atmospheres Sereno can be in.
 *
 * The app does not tint itself by "weather code"; it resolves the weather to
 * one of these moods and then dresses the entire interface in it. Keeping the
 * set small is what stops the app from looking like nine unrelated themes.
 */
enum class Mood {
    ClearDay, ClearNight, PartlyDay, PartlyNight, Overcast, Fog, Rain, Thunder, Snow;

    val isNight: Boolean get() = this == ClearNight || this == PartlyNight
}

/**
 * A fully resolved visual environment.
 *
 * [skyTop]/[skyMid]/[skyLow] drive the backdrop gradient, [glow] is a single
 * soft light source placed at [glowCenter], and the ink ramp is pre-resolved
 * against that backdrop so no screen ever has to guess a contrast value.
 */
@Immutable
data class Atmosphere(
    val mood: Mood,
    val dark: Boolean,
    val skyTop: Color,
    val skyMid: Color,
    val skyLow: Color,
    val glow: Color,
    val glowCenter: Offset,
    val glowRadius: Float,
    val ink: Color,
    val accent: Color,
    val accentSoft: Color,
    /** How much atmospheric haze sits between the viewer and the content. */
    val haze: Float,
    /** Film grain strength; keeps large flat gradients from banding. */
    val grain: Float,
) {
    fun ink(alpha: Float): Color = ink.copy(alpha = alpha)

    val inkSecondary: Color get() = ink(Emphasis.secondary)
    val inkTertiary: Color get() = ink(Emphasis.tertiary)
    val inkQuaternary: Color get() = ink(Emphasis.quaternary)
    val hairline: Color get() = ink(Emphasis.hairline)
    val veil: Color get() = ink(Emphasis.veil)
}

/**
 * Data colours for charts and semantic states.
 *
 * These are intentionally *not* derived from the atmosphere: a rain trace has
 * to mean "rain" on a clear day and in a thunderstorm alike. They were picked
 * to stay distinguishable under deuteranopia and protanopia, which is why the
 * precipitation/wind pair separates on blue-vs-teal *and* on lightness rather
 * than on hue alone.
 */
@Immutable
data class DataColors(
    val warm: Color,
    val hot: Color,
    val mild: Color,
    val cool: Color,
    val cold: Color,
    val precip: Color,
    val precipSoft: Color,
    val probability: Color,
    val wind: Color,
    val gust: Color,
    val uv: Color,
    val cloud: Color,
    val positive: Color,
    val caution: Color,
    val warning: Color,
    val severe: Color,
) {
    /** Maps a temperature in °C onto the warm/cold ramp used by every chart. */
    fun forTemperature(celsius: Double): Color {
        val stops = listOf(
            -20.0 to cold, 0.0 to cool, 10.0 to mild, 20.0 to warm, 32.0 to hot,
        )
        if (celsius <= stops.first().first) return stops.first().second
        if (celsius >= stops.last().first) return stops.last().second
        for (i in 0 until stops.size - 1) {
            val (t0, c0) = stops[i]
            val (t1, c1) = stops[i + 1]
            if (celsius in t0..t1) {
                val f = ((celsius - t0) / (t1 - t0)).toFloat()
                return lerpColor(c0, c1, f)
            }
        }
        return mild
    }
}

fun lerpColor(a: Color, b: Color, f: Float): Color {
    val t = f.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha + (b.alpha - a.alpha) * t,
    )
}

val LightData = DataColors(
    warm = Color(0xFFE08A3C),
    hot = Color(0xFFD4552F),
    mild = Color(0xFFC9A227),
    cool = Color(0xFF3F87C4),
    cold = Color(0xFF5C6FC9),
    precip = Color(0xFF2E7BB8),
    precipSoft = Color(0xFF8FC0E0),
    probability = Color(0xFF6FA8CE),
    wind = Color(0xFF2F8F86),
    gust = Color(0xFF63B6AC),
    uv = Color(0xFFD08A1F),
    cloud = Color(0xFF8D97A6),
    positive = Color(0xFF2F8055),
    caution = Color(0xFFC58A1E),
    warning = Color(0xFFC96A21),
    severe = Color(0xFFB53A2E),
)

val DarkData = DataColors(
    warm = Color(0xFFF2A45C),
    hot = Color(0xFFEE7350),
    mild = Color(0xFFE2C158),
    cool = Color(0xFF6FB4E8),
    cold = Color(0xFF8E9CEA),
    precip = Color(0xFF6BB6EC),
    precipSoft = Color(0xFF3A6C94),
    probability = Color(0xFF8FC6E8),
    wind = Color(0xFF5FC6BA),
    gust = Color(0xFF8FDCD2),
    uv = Color(0xFFECBB55),
    cloud = Color(0xFFA6B0BF),
    positive = Color(0xFF63C08C),
    caution = Color(0xFFE6BC63),
    warning = Color(0xFFEE9A5A),
    severe = Color(0xFFEE6E5E),
)
