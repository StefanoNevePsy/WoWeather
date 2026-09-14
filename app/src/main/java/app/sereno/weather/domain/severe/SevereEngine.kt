package app.sereno.weather.domain.severe

import app.sereno.weather.domain.model.AlertKind
import app.sereno.weather.domain.model.AlertSeverity
import app.sereno.weather.domain.model.BlendedHour
import app.sereno.weather.domain.model.WeatherAlert
import app.sereno.weather.domain.model.WeatherCodes
import app.sereno.weather.i18n.Copy
import kotlin.math.roundToInt

/**
 * Derives weather warnings from the blended forecast.
 *
 * These are *not* official warnings and never pretend to be: every alert
 * produced here carries `derived = true` and is attributed to Sereno, so the UI
 * can present it differently from a civil-protection bulletin. When an official
 * alert provider is wired in, its alerts take precedence and these fill the gaps.
 *
 * The design constraint that shaped the thresholds: **do not cry wolf.** An app
 * that shows a red banner every time it might drizzle trains people to ignore
 * the banner on the day it matters. So the lowest tier is deliberately quiet,
 * and anything above Advisory requires the models to broadly agree.
 */
object SevereEngine {

    private const val WINDOW_HOURS = 48

    fun derive(hours: List<BlendedHour>, nowEpoch: Long, copy: Copy, utcOffsetSeconds: Int): List<WeatherAlert> {
        val window = hours.filter { it.epochSeconds in nowEpoch..(nowEpoch + WINDOW_HOURS * 3600) }
        if (window.isEmpty()) return emptyList()

        val alerts = mutableListOf<WeatherAlert>()

        thunder(window, copy, utcOffsetSeconds)?.let { alerts += it }
        wind(window, copy, utcOffsetSeconds)?.let { alerts += it }
        rain(window, copy, utcOffsetSeconds)?.let { alerts += it }
        snow(window, copy, utcOffsetSeconds)?.let { alerts += it }
        ice(window, copy, utcOffsetSeconds)?.let { alerts += it }
        heat(window, copy, utcOffsetSeconds)?.let { alerts += it }
        cold(window, copy, utcOffsetSeconds)?.let { alerts += it }

        return alerts.sortedByDescending { it.severity.ordinal }
    }

    private fun thunder(hours: List<BlendedHour>, copy: Copy, offset: Int): WeatherAlert? {
        // The soonest contiguous episode, not every stormy hour in two days.
        // Taking first..last across the whole window produced "from 11:00 to
        // 11:00" — two separate afternoons collapsed into one nonsensical span.
        val stormy = firstRun(hours) { WeatherCodes.condition(it.weatherCode).isThunder }
        if (stormy.isEmpty()) return null
        val hail = stormy.any { it.weatherCode == 96 || it.weatherCode == 99 }
        val agreement = stormy.map { it.confidence.score }.average()

        val severity = when {
            hail && agreement >= 0.6 -> AlertSeverity.Severe
            hail -> AlertSeverity.Warning
            agreement >= 0.65 -> AlertSeverity.Warning
            agreement >= 0.45 -> AlertSeverity.Watch
            else -> AlertSeverity.Advisory
        }
        val from = stormy.first().epochSeconds
        val to = stormy.last().epochSeconds + 3600

        return WeatherAlert(
            id = "derived-thunder-$from",
            kind = if (hail) AlertKind.Hail else AlertKind.Thunderstorm,
            severity = severity,
            headline = if (hail) copy.alertKind(AlertKind.Hail) else copy.alertKind(AlertKind.Thunderstorm),
            detail = copy.t(
                "Attività temporalesca prevista ${window(from, to, offset, copy)}. ${confidenceNote(agreement, copy)}",
                "Thunderstorm activity expected ${window(from, to, offset, copy)}. ${confidenceNote(agreement, copy)}",
            ),
            startEpoch = from, endEpoch = to,
            issuer = "Sereno", derived = true,
        )
    }

    private fun wind(hours: List<BlendedHour>, copy: Copy, offset: Int): WeatherAlert? {
        val peak = hours.maxByOrNull { it.windGust ?: 0.0 } ?: return null
        val gust = peak.windGust ?: return null
        if (gust < 60) return null

        val severity = when {
            gust >= 100 -> AlertSeverity.Severe
            gust >= 80 -> AlertSeverity.Warning
            gust >= 70 -> AlertSeverity.Watch
            else -> AlertSeverity.Advisory
        }
        val affected = firstRun(hours) { (it.windGust ?: 0.0) >= 55 }.ifEmpty { listOf(peak) }
        return WeatherAlert(
            id = "derived-wind-${affected.first().epochSeconds}",
            kind = AlertKind.Wind,
            severity = severity,
            headline = copy.alertKind(AlertKind.Wind),
            detail = copy.t(
                "Raffiche fino a ${gust.roundToInt()} km/h intorno alle ${hh(peak.epochSeconds, offset)}.",
                "Gusts to ${gust.roundToInt()} km/h around ${hh(peak.epochSeconds, offset)}.",
            ),
            startEpoch = affected.first().epochSeconds,
            endEpoch = affected.last().epochSeconds + 3600,
            issuer = "Sereno", derived = true,
        )
    }

    private fun rain(hours: List<BlendedHour>, copy: Copy, offset: Int): WeatherAlert? {
        // Rolling accumulations, because the damaging quantity is rain per unit
        // time, not rain per calendar hour.
        val sorted = hours.sortedBy { it.epochSeconds }
        var worst: Triple<Double, Int, BlendedHour>? = null
        listOf(1, 3, 6).forEach { span ->
            sorted.windowed(span, 1, partialWindows = false).forEach { run ->
                val total = run.sumOf { it.precipitation ?: 0.0 }
                val threshold = when (span) { 1 -> 15.0; 3 -> 30.0; else -> 50.0 }
                if (total >= threshold) {
                    val score = total / threshold
                    if (worst == null || score > worst!!.first) worst = Triple(score, span, run.first())
                }
            }
        }
        val hit = worst ?: return null
        val severity = when {
            hit.first >= 1.6 -> AlertSeverity.Severe
            hit.first >= 1.2 -> AlertSeverity.Warning
            else -> AlertSeverity.Watch
        }
        return WeatherAlert(
            id = "derived-rain-${hit.third.epochSeconds}",
            kind = AlertKind.Rain,
            severity = severity,
            headline = copy.alertKind(AlertKind.Rain),
            detail = copy.t(
                "Accumuli significativi previsti in ${hit.second} ${if (hit.second == 1) "ora" else "ore"} dalle ${hh(hit.third.epochSeconds, offset)}.",
                "Significant accumulation expected over ${hit.second} ${if (hit.second == 1) "hour" else "hours"} from ${hh(hit.third.epochSeconds, offset)}.",
            ),
            startEpoch = hit.third.epochSeconds,
            endEpoch = hit.third.epochSeconds + hit.second * 3600L,
            issuer = "Sereno", derived = true,
        )
    }

    private fun snow(hours: List<BlendedHour>, copy: Copy, offset: Int): WeatherAlert? {
        val total = hours.sumOf { it.snowfall ?: 0.0 }
        if (total < 2.0) return null
        val snowy = firstRun(hours) { (it.snowfall ?: 0.0) > 0.05 }
        if (snowy.isEmpty()) return null
        val severity = when {
            total >= 25 -> AlertSeverity.Severe
            total >= 10 -> AlertSeverity.Warning
            total >= 5 -> AlertSeverity.Watch
            else -> AlertSeverity.Advisory
        }
        return WeatherAlert(
            id = "derived-snow-${snowy.first().epochSeconds}",
            kind = AlertKind.Snow,
            severity = severity,
            headline = copy.alertKind(AlertKind.Snow),
            detail = copy.t(
                "Circa ${total.roundToInt()} cm previsti dalle ${hh(snowy.first().epochSeconds, offset)}.",
                "Around ${total.roundToInt()} cm expected from ${hh(snowy.first().epochSeconds, offset)}.",
            ),
            startEpoch = snowy.first().epochSeconds,
            endEpoch = snowy.last().epochSeconds + 3600,
            issuer = "Sereno", derived = true,
        )
    }

    private fun ice(hours: List<BlendedHour>, copy: Copy, offset: Int): WeatherAlert? {
        // Freezing rain is the dangerous case; plain sub-zero air is not news.
        val freezing = firstRun(hours) {
            val code = it.weatherCode
            code == 56 || code == 57 || code == 66 || code == 67 ||
                ((it.temperature ?: 99.0) <= 0.5 && (it.precipitation ?: 0.0) >= 0.2)
        }
        if (freezing.isEmpty()) return null
        return WeatherAlert(
            id = "derived-ice-${freezing.first().epochSeconds}",
            kind = AlertKind.Ice,
            severity = if (freezing.size >= 3) AlertSeverity.Warning else AlertSeverity.Watch,
            headline = copy.alertKind(AlertKind.Ice),
            detail = copy.t(
                "Possibile formazione di ghiaccio dalle ${hh(freezing.first().epochSeconds, offset)}.",
                "Ice may form from ${hh(freezing.first().epochSeconds, offset)}.",
            ),
            startEpoch = freezing.first().epochSeconds,
            endEpoch = freezing.last().epochSeconds + 3600,
            issuer = "Sereno", derived = true,
        )
    }

    private fun heat(hours: List<BlendedHour>, copy: Copy, offset: Int): WeatherAlert? {
        val peak = hours.maxByOrNull { it.apparentTemperature ?: it.temperature ?: -99.0 } ?: return null
        val value = peak.apparentTemperature ?: peak.temperature ?: return null
        if (value < 32) return null
        val severity = when {
            value >= 40 -> AlertSeverity.Severe
            value >= 36 -> AlertSeverity.Warning
            value >= 34 -> AlertSeverity.Watch
            else -> AlertSeverity.Advisory
        }
        return WeatherAlert(
            id = "derived-heat-${peak.epochSeconds}",
            kind = AlertKind.Heat,
            severity = severity,
            headline = copy.alertKind(AlertKind.Heat),
            detail = copy.t(
                "Percepita fino a ${value.roundToInt()}° intorno alle ${hh(peak.epochSeconds, offset)}.",
                "Feels like ${value.roundToInt()}° around ${hh(peak.epochSeconds, offset)}.",
            ),
            startEpoch = peak.epochSeconds - 3600, endEpoch = peak.epochSeconds + 3600,
            issuer = "Sereno", derived = true,
        )
    }

    private fun cold(hours: List<BlendedHour>, copy: Copy, offset: Int): WeatherAlert? {
        val coldest = hours.minByOrNull { it.apparentTemperature ?: it.temperature ?: 99.0 } ?: return null
        val value = coldest.apparentTemperature ?: coldest.temperature ?: return null
        if (value > -6) return null
        return WeatherAlert(
            id = "derived-cold-${coldest.epochSeconds}",
            kind = AlertKind.Cold,
            severity = if (value <= -14) AlertSeverity.Warning else AlertSeverity.Watch,
            headline = copy.alertKind(AlertKind.Cold),
            detail = copy.t(
                "Percepita fino a ${value.roundToInt()}° intorno alle ${hh(coldest.epochSeconds, offset)}.",
                "Feels like ${value.roundToInt()}° around ${hh(coldest.epochSeconds, offset)}.",
            ),
            startEpoch = coldest.epochSeconds - 3600, endEpoch = coldest.epochSeconds + 3600,
            issuer = "Sereno", derived = true,
        )
    }

    private fun confidenceNote(agreement: Double, copy: Copy): String = when {
        agreement >= 0.7 -> copy.t("I modelli concordano.", "The models agree.")
        agreement >= 0.5 -> copy.t("Accordo parziale tra i modelli.", "The models partly agree.")
        else -> copy.t("I modelli sono in disaccordo: segnale da monitorare.", "The models disagree: one to keep an eye on.")
    }

    /**
     * The first contiguous run of hours matching [predicate].
     *
     * An alert describes one episode. Two separate storm afternoons are two
     * events, and merging them into a single span misstates both.
     */
    private fun firstRun(
        hours: List<BlendedHour>,
        predicate: (BlendedHour) -> Boolean,
    ): List<BlendedHour> {
        val sorted = hours.sortedBy { it.epochSeconds }
        val run = mutableListOf<BlendedHour>()
        for (hour in sorted) {
            if (predicate(hour)) {
                // A gap of more than one hour ends the episode.
                if (run.isNotEmpty() && hour.epochSeconds - run.last().epochSeconds > 3600) break
                run += hour
            } else if (run.isNotEmpty()) {
                break
            }
        }
        return run
    }

    /**
     * Phrases a time span, avoiding the "from 11:00 to 11:00" trap when a span
     * happens to be short, or a whole day long.
     */
    private fun window(from: Long, to: Long, offset: Int, copy: Copy): String {
        val hours = ((to - from) / 3600).toInt()
        return when {
            hours <= 1 -> copy.t("verso le ${hh(from, offset)}", "around ${hh(from, offset)}")
            hours >= 20 -> copy.t("per gran parte della giornata", "for much of the day")
            else -> copy.t(
                "dalle ${hh(from, offset)} alle ${hh(to, offset)}",
                "from ${hh(from, offset)} to ${hh(to, offset)}",
            )
        }
    }

    private fun hh(epoch: Long, offset: Int): String {
        val hour = (((epoch + offset) / 3600) % 24).toInt().let { if (it < 0) it + 24 else it }
        return "%02d:00".format(hour)
    }
}
