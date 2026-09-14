package app.sereno.weather.domain.model

import app.sereno.weather.design.Mood

/**
 * WMO 4677 present-weather codes, reduced to the set Open-Meteo actually emits
 * and grouped into the conditions Sereno draws.
 */
enum class Condition {
    Clear, MainlyClear, PartlyCloudy, Overcast,
    Fog, RimeFog,
    Drizzle, FreezingDrizzle,
    Rain, FreezingRain, RainShowers,
    Snow, SnowGrains, SnowShowers,
    Thunderstorm, ThunderstormHail,
    Unknown;

    val isPrecipitating: Boolean
        get() = this in setOf(
            Drizzle, FreezingDrizzle, Rain, FreezingRain, RainShowers,
            Snow, SnowGrains, SnowShowers, Thunderstorm, ThunderstormHail,
        )

    val isFrozen: Boolean get() = this in setOf(Snow, SnowGrains, SnowShowers)

    val isThunder: Boolean get() = this == Thunderstorm || this == ThunderstormHail
}

object WeatherCodes {

    fun condition(code: Int?): Condition = when (code) {
        0 -> Condition.Clear
        1 -> Condition.MainlyClear
        2 -> Condition.PartlyCloudy
        3 -> Condition.Overcast
        45 -> Condition.Fog
        48 -> Condition.RimeFog
        51, 53, 55 -> Condition.Drizzle
        56, 57 -> Condition.FreezingDrizzle
        61, 63, 65 -> Condition.Rain
        66, 67 -> Condition.FreezingRain
        71, 73, 75 -> Condition.Snow
        77 -> Condition.SnowGrains
        80, 81, 82 -> Condition.RainShowers
        85, 86 -> Condition.SnowShowers
        95 -> Condition.Thunderstorm
        96, 99 -> Condition.ThunderstormHail
        else -> Condition.Unknown
    }

    /** 0 = light, 1 = moderate, 2 = heavy. Used for icon weight and copy. */
    fun intensity(code: Int?): Int = when (code) {
        51, 56, 61, 66, 71, 80, 85 -> 0
        53, 63, 73, 81 -> 1
        55, 57, 65, 67, 75, 77, 82, 86, 99 -> 2
        95 -> 1
        96 -> 2
        else -> 0
    }

    fun mood(code: Int?, isDay: Boolean): Mood = when (condition(code)) {
        Condition.Clear, Condition.MainlyClear -> if (isDay) Mood.ClearDay else Mood.ClearNight
        Condition.PartlyCloudy -> if (isDay) Mood.PartlyDay else Mood.PartlyNight
        Condition.Overcast -> Mood.Overcast
        Condition.Fog, Condition.RimeFog -> Mood.Fog
        Condition.Snow, Condition.SnowGrains, Condition.SnowShowers -> Mood.Snow
        Condition.Thunderstorm, Condition.ThunderstormHail -> Mood.Thunder
        Condition.Unknown -> if (isDay) Mood.PartlyDay else Mood.PartlyNight
        else -> Mood.Rain
    }

    /**
     * Reconciles a model's categorical code with the blended numbers.
     *
     * Blending can leave the two disagreeing — four models saying "light rain"
     * and one saying "clear" average to 0.06 mm, which is dry, yet the modal
     * code still says rain. Showing a rain glyph above a dry number is exactly
     * the kind of small incoherence that makes an app feel untrustworthy, so
     * the numbers win and the code is pulled back to match.
     */
    fun reconcile(code: Int?, precipitationMm: Double?, snowfallCm: Double?, cloudCoverPct: Double?): Int? {
        val cond = condition(code)
        val precip = precipitationMm ?: 0.0
        val snow = snowfallCm ?: 0.0

        // Thunder and fog are categorical: never derive them away from a code
        // that asserts them, and never invent them from numbers either.
        if (cond.isThunder || cond == Condition.Fog || cond == Condition.RimeFog) return code

        if (cond.isPrecipitating && precip < BlendedHour.WET_THRESHOLD_MM && snow < 0.1) {
            return cloudCode(cloudCoverPct) ?: code
        }
        if (!cond.isPrecipitating && precip >= 0.4) {
            return if (snow >= 0.2) 71 else if (precip >= 2.0) 63 else 61
        }
        return code
    }

    private fun cloudCode(cloudCoverPct: Double?): Int? = when {
        cloudCoverPct == null -> null
        cloudCoverPct < 15 -> 0
        cloudCoverPct < 45 -> 1
        cloudCoverPct < 80 -> 2
        else -> 3
    }

    /** Severity ordering used when picking the code that represents a whole day. */
    fun rank(code: Int?): Int = when (condition(code)) {
        Condition.Clear -> 0
        Condition.MainlyClear -> 1
        Condition.PartlyCloudy -> 2
        Condition.Overcast -> 3
        Condition.Fog, Condition.RimeFog -> 4
        Condition.Drizzle -> 5
        Condition.SnowGrains -> 6
        Condition.Rain -> 7
        Condition.RainShowers -> 8
        Condition.FreezingDrizzle -> 9
        Condition.Snow, Condition.SnowShowers -> 10
        Condition.FreezingRain -> 11
        Condition.Thunderstorm -> 12
        Condition.ThunderstormHail -> 13
        Condition.Unknown -> -1
    }
}
