package app.sereno.weather.ui

import app.sereno.weather.data.prefs.SerenoSettings
import app.sereno.weather.i18n.Copy
import app.sereno.weather.i18n.Lang
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Every number the user sees is formatted here.
 *
 * Centralising it is what keeps "18°" from appearing as "18.0°" three screens
 * later, and it is the only place that knows about unit conversion — the domain
 * layer works exclusively in Celsius, km/h, mm and hPa.
 */
class Formatter(
    val settings: SerenoSettings,
    val copy: Copy,
    val utcOffsetSeconds: Int,
) {
    private val locale: Locale = if (copy.lang == Lang.It) Locale.ITALIAN else Locale.ENGLISH
    private val zone: ZoneOffset = ZoneOffset.ofTotalSeconds(utcOffsetSeconds)

    // --- temperature ---------------------------------------------------------

    /** Just the number: "18". Used wherever the degree sign would be noise. */
    fun degrees(celsius: Double?): String {
        if (celsius == null) return copy.noData
        return settings.temperatureUnit.fromCelsius(celsius).roundToInt().toString()
    }

    /** "18°" */
    fun temperature(celsius: Double?): String =
        if (celsius == null) copy.noData else "${degrees(celsius)}°"

    /** A difference, which must never be converted as if it were a reading. */
    fun temperatureDelta(celsiusDelta: Double?): String {
        if (celsiusDelta == null) return copy.noData
        val converted = celsiusDelta * settings.temperatureUnit.deltaPerCelsius
        return "%.1f°".format(locale, converted)
    }

    // --- other quantities -----------------------------------------------------

    fun speed(kmh: Double?, withUnit: Boolean = true): String {
        if (kmh == null) return copy.noData
        val value = settings.speedUnit.fromKmh(kmh)
        val number = if (value < 10) "%.1f".format(locale, value) else value.roundToInt().toString()
        return if (withUnit) "$number ${settings.speedUnit.symbol}" else number
    }

    fun precipitation(mm: Double?, withUnit: Boolean = true): String {
        if (mm == null) return copy.noData
        val value = settings.precipUnit.fromMm(mm)
        val number = when {
            value <= 0.0 -> "0"
            value < 1.0 -> "%.1f".format(locale, value)
            value < 10.0 -> "%.1f".format(locale, value)
            else -> value.roundToInt().toString()
        }
        return if (withUnit) "$number ${settings.precipUnit.symbol}" else number
    }

    fun pressure(hpa: Double?): String {
        if (hpa == null) return copy.noData
        val value = settings.pressureUnit.fromHpa(hpa)
        val number = if (settings.pressureUnit.symbol == "inHg") "%.2f".format(locale, value)
        else value.roundToInt().toString()
        return "$number ${settings.pressureUnit.symbol}"
    }

    fun percent(value: Double?): String =
        if (value == null) copy.noData else "${value.roundToInt()}%"

    fun visibility(metres: Double?): String {
        if (metres == null) return copy.noData
        return if (metres >= 1000) "${(metres / 1000).roundToInt()} km" else "${metres.roundToInt()} m"
    }

    fun uv(value: Double?): String =
        if (value == null) copy.noData else value.roundToInt().toString()

    fun uvDescription(value: Double?): String? = when {
        value == null -> null
        value < 3 -> copy.t("Basso", "Low")
        value < 6 -> copy.t("Moderato", "Moderate")
        value < 8 -> copy.t("Alto", "High")
        value < 11 -> copy.t("Molto alto", "Very high")
        else -> copy.t("Estremo", "Extreme")
    }

    fun airQualityDescription(aqi: Int?): String? = when {
        aqi == null -> null
        aqi <= 20 -> copy.t("Ottima", "Very good")
        aqi <= 40 -> copy.t("Buona", "Good")
        aqi <= 60 -> copy.t("Discreta", "Fair")
        aqi <= 80 -> copy.t("Scarsa", "Poor")
        aqi <= 100 -> copy.t("Molto scarsa", "Very poor")
        else -> copy.t("Pessima", "Extremely poor")
    }

    /** 8-point compass, in the reader's language. */
    fun windDirection(degrees: Double?): String {
        if (degrees == null) return copy.noData
        val sectors = if (copy.lang == Lang.It) {
            listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
        } else {
            listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        }
        val index = (((degrees % 360 + 360) % 360 + 22.5) / 45).toInt() % 8
        return sectors[index]
    }

    // --- time ------------------------------------------------------------------

    fun hourOfDay(epochSeconds: Long): Int =
        Instant.ofEpochSecond(epochSeconds).atOffset(zone).hour

    /** "14" — the axis label form. */
    fun hourShort(epochSeconds: Long): String =
        "%d".format(hourOfDay(epochSeconds))

    /** "14:30" */
    fun time(epochSeconds: Long): String {
        val moment = Instant.ofEpochSecond(epochSeconds).atOffset(zone)
        return "%02d:%02d".format(moment.hour, moment.minute)
    }

    /** "mer" / "Wed" */
    fun weekdayShort(epochSeconds: Long): String {
        val date = Instant.ofEpochSecond(epochSeconds).atOffset(zone).dayOfWeek
        return date.getDisplayName(TextStyle.SHORT, locale).replace(".", "")
            .replaceFirstChar { it.uppercase(locale) }
    }

    fun weekdayLong(epochSeconds: Long): String {
        val date = Instant.ofEpochSecond(epochSeconds).atOffset(zone).dayOfWeek
        return date.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase(locale) }
    }

    /** "14 set" */
    fun dayMonth(epochSeconds: Long): String {
        val moment = Instant.ofEpochSecond(epochSeconds).atOffset(zone)
        val month = moment.month.getDisplayName(TextStyle.SHORT, locale).replace(".", "")
        return "${moment.dayOfMonth} $month"
    }

    fun isToday(epochSeconds: Long, nowEpoch: Long): Boolean =
        localDate(epochSeconds) == localDate(nowEpoch)

    fun isTomorrow(epochSeconds: Long, nowEpoch: Long): Boolean =
        localDate(epochSeconds) == localDate(nowEpoch).plusDays(1)

    private fun localDate(epochSeconds: Long): LocalDate =
        Instant.ofEpochSecond(epochSeconds).atOffset(zone).toLocalDate()

    /** The day label used by the daily list: "Oggi", "Domani", then weekdays. */
    fun dayLabel(epochSeconds: Long, nowEpoch: Long): String = when {
        isToday(epochSeconds, nowEpoch) -> copy.today
        isTomorrow(epochSeconds, nowEpoch) -> copy.tomorrow
        else -> weekdayLong(epochSeconds)
    }

    fun relativeAge(ageMinutes: Long): String = copy.minutesAgo(abs(ageMinutes))
}
