package app.sereno.weather.domain.story

import app.sereno.weather.domain.model.BlendedHour
import app.sereno.weather.domain.model.ForecastBundle
import app.sereno.weather.domain.model.WeatherCodes
import app.sereno.weather.domain.model.WeatherModel
import app.sereno.weather.i18n.Copy
import app.sereno.weather.i18n.Lang
import kotlin.math.roundToInt

/**
 * Writes the sentences Sereno says out loud.
 *
 * Everything here is deterministic and offline — no language model is involved,
 * by design. The output has to be reproducible, auditable against the numbers,
 * and correct in two languages, and a remote LLM would give up all three for
 * prose nobody asked for.
 *
 * The approach is classic natural-language generation: segment the day, decide
 * which two or three facts are worth saying, then realise them with
 * language-specific templates. Italian and English are realised separately
 * rather than translated, because the natural phrasings do not map word for
 * word ("nuvole in aumento" is not "clouds increasing").
 */
object Narrator {

    private data class Segment(
        val name: SegmentName,
        val hours: List<BlendedHour>,
    ) {
        val cloud: Double? get() = hours.mapNotNull { it.cloudCover }.averageOrNull()
        val precip: Double get() = hours.sumOf { it.precipitation ?: 0.0 }
        val maxGust: Double? get() = hours.mapNotNull { it.windGust }.maxOrNull()
        val maxTemp: Double? get() = hours.mapNotNull { it.temperature }.maxOrNull()
        val minTemp: Double? get() = hours.mapNotNull { it.temperature }.minOrNull()
        val thundery: Boolean get() = hours.any { WeatherCodes.condition(it.weatherCode).isThunder }
        val snowy: Boolean get() = hours.any { (it.snowfall ?: 0.0) > 0.1 }
        val isEmpty: Boolean get() = hours.isEmpty()
    }

    private enum class SegmentName { Morning, Afternoon, Evening, Night }

    // -----------------------------------------------------------------------
    // The narrative line
    // -----------------------------------------------------------------------

    fun dayNarrative(
        hours: List<BlendedHour>,
        utcOffsetSeconds: Int,
        copy: Copy,
    ): String? {
        if (hours.isEmpty()) return null

        val segments = segment(hours, utcOffsetSeconds)
        val morning = segments[SegmentName.Morning]
        val afternoon = segments[SegmentName.Afternoon]
        val evening = segments[SegmentName.Evening]

        val clauses = mutableListOf<String>()

        // 1. The opening clause describes the first segment that still lies ahead.
        val opener = listOfNotNull(morning, afternoon, evening).firstOrNull { !it.isEmpty }
        if (opener != null) clauses += describeOpening(opener, copy)

        // 2. A second clause only if something actually *changes*.
        val change = findChange(opener, segments, copy)
        if (change != null) clauses += change

        // 3. Rain window, if one exists and has not already been said.
        val rainWindow = rainWindow(hours, utcOffsetSeconds, copy)
        if (rainWindow != null && clauses.none { it.contains(rainWindow.second) }) {
            clauses += rainWindow.first
        }

        // 4. Wind, only when it is genuinely notable.
        val gust = hours.mapNotNull { it.windGust }.maxOrNull()
        if (gust != null && gust >= 45) {
            clauses += copy.t(
                "raffiche fino a ${gust.roundToInt()} km/h",
                "gusts to ${gust.roundToInt()} km/h",
            )
        }

        if (clauses.isEmpty()) return null
        return assemble(clauses, copy.lang)
    }

    private fun describeOpening(segment: Segment, copy: Copy): String {
        val part = when (segment.name) {
            SegmentName.Morning -> copy.t("Mattina", "Morning")
            SegmentName.Afternoon -> copy.t("Pomeriggio", "Afternoon")
            SegmentName.Evening -> copy.t("Serata", "Evening")
            SegmentName.Night -> copy.t("Notte", "Night")
        }
        val sky = skyWord(segment, copy)
        val temperature = temperatureWord(segment, copy)

        return if (temperature != null) {
            copy.t("$part $temperature e $sky", "$temperature $part, $sky")
        } else {
            copy.t("$part $sky", "$part $sky")
        }
    }

    private fun skyWord(segment: Segment, copy: Copy): String {
        val cloud = segment.cloud
        return when {
            segment.thundery -> copy.t("temporalesca", "thundery")
            segment.snowy -> copy.t("con neve", "with snow")
            segment.precip >= 2.0 -> copy.t("piovosa", "wet")
            segment.precip >= 0.3 -> copy.t("con qualche pioggia", "with some rain")
            cloud == null -> copy.t("variabile", "changeable")
            cloud < 20 -> copy.t("serena", "clear")
            cloud < 50 -> copy.t("poco nuvolosa", "mostly clear")
            cloud < 80 -> copy.t("nuvolosa", "cloudy")
            else -> copy.t("coperta", "overcast")
        }
    }

    private fun temperatureWord(segment: Segment, copy: Copy): String? {
        val t = segment.maxTemp ?: return null
        return when {
            t >= 33 -> copy.t("molto calda", "very hot")
            t >= 28 -> copy.t("calda", "warm")
            t <= -2 -> copy.t("gelida", "freezing")
            t <= 6 -> copy.t("fredda", "cold")
            t <= 13 -> copy.t("fresca", "cool")
            else -> null
        }
    }

    /**
     * Says something only when the day genuinely turns. "Clear morning, clear
     * afternoon" is not worth a clause, and printing it anyway is what makes
     * generated copy feel robotic.
     */
    private fun findChange(from: Segment?, segments: Map<SegmentName, Segment>, copy: Copy): String? {
        if (from == null) return null
        val later = segments.values
            .filter { it.name.ordinal > from.name.ordinal && !it.isEmpty }
            .minByOrNull { it.name.ordinal } ?: return null

        val partWhen = when (later.name) {
            SegmentName.Morning -> copy.t("in mattinata", "in the morning")
            SegmentName.Afternoon -> copy.t("nel pomeriggio", "in the afternoon")
            SegmentName.Evening -> copy.t("in serata", "in the evening")
            SegmentName.Night -> copy.t("nella notte", "overnight")
        }

        val cloudFrom = from.cloud ?: return null
        val cloudTo = later.cloud ?: return null

        return when {
            later.thundery && !from.thundery ->
                copy.t("temporali $partWhen", "thunderstorms $partWhen")
            later.precip >= 0.5 && from.precip < 0.3 ->
                copy.t("piogge $partWhen", "rain $partWhen")
            from.precip >= 0.5 && later.precip < 0.2 ->
                copy.t("$partWhen più asciutto", "drier $partWhen")
            // Rain that merely eases still deserves a clause: "it keeps
            // raining but less" is genuinely different news from "it keeps
            // raining", and without this a wet day gets a one-clause sentence.
            from.precip >= 0.8 && later.precip <= from.precip * 0.45 ->
                copy.t("in attenuazione $partWhen", "easing $partWhen")
            later.precip >= 0.8 && later.precip >= from.precip * 2.0 ->
                copy.t("in intensificazione $partWhen", "turning wetter $partWhen")
            cloudTo - cloudFrom >= 28 ->
                copy.t("nuvole in aumento $partWhen", "clouds building $partWhen")
            cloudFrom - cloudTo >= 28 ->
                copy.t("schiarite $partWhen", "clearing $partWhen")
            else -> null
        }
    }

    /**
     * "rain between 17:00 and 20:00", but only when that is a genuinely useful
     * thing to say: a single tight window. All-day rain is already implied by
     * the opening clause, and repeating it reads as padding.
     *
     * Returns the clause plus the hour string it mentions, so the caller can
     * avoid saying the same time twice.
     */
    private fun rainWindow(hours: List<BlendedHour>, offset: Int, copy: Copy): Pair<String, String>? {
        fun localHour(epoch: Long): Int =
            (((epoch + offset) / 3600) % 24).toInt().let { if (it < 0) it + 24 else it }

        val wet = hours.filter { (it.precipitation ?: 0.0) >= 0.3 }
        if (wet.isEmpty()) return null

        val startEpoch = wet.first().epochSeconds
        val endEpoch = wet.last().epochSeconds + 3600
        val spanHours = ((endEpoch - startEpoch) / 3600).toInt()
        if (spanHours > 9) {
            // Too long to name a window, but saying nothing leaves the sentence
            // thin on exactly the days it matters most.
            val wetFraction = wet.size.toDouble() / hours.size.toDouble()
            return if (wetFraction >= 0.55) {
                copy.t("pioggia per gran parte della giornata", "rain for much of the day") to "~"
            } else {
                copy.t("pioggia a tratti", "showers on and off") to "~"
            }
        }

        val from = localHour(startEpoch)
        val to = localHour(endEpoch)
        val clause = if (spanHours <= 1) {
            copy.t("pioggia verso le $from", "rain around $from:00")
        } else {
            copy.t("pioggia tra le $from e le $to", "rain between $from:00 and $to:00")
        }
        return clause to from.toString()
    }

    private fun assemble(clauses: List<String>, lang: Lang): String {
        val joined = clauses.joinToString(", ")
        return joined.replaceFirstChar { it.uppercase() } + "."
    }

    private fun segment(hours: List<BlendedHour>, offset: Int): Map<SegmentName, Segment> {
        fun localHour(epoch: Long): Int = (((epoch + offset) / 3600) % 24).toInt().let { if (it < 0) it + 24 else it }
        return mapOf(
            SegmentName.Morning to Segment(SegmentName.Morning, hours.filter { localHour(it.epochSeconds) in 6..11 }),
            SegmentName.Afternoon to Segment(SegmentName.Afternoon, hours.filter { localHour(it.epochSeconds) in 12..17 }),
            SegmentName.Evening to Segment(SegmentName.Evening, hours.filter { localHour(it.epochSeconds) in 18..22 }),
            SegmentName.Night to Segment(SegmentName.Night, hours.filter { localHour(it.epochSeconds) in listOf(23, 0, 1, 2, 3, 4, 5) }),
        )
    }

    // -----------------------------------------------------------------------
    // Why the confidence is what it is
    // -----------------------------------------------------------------------

    /**
     * The sentence that makes the confidence score mean something.
     *
     * Rather than restating the score, it names the models and the specific
     * thing they do or do not agree about — which is the whole reason Sereno
     * fetches several models in the first place.
     */
    fun confidenceExplanation(bundle: ForecastBundle, copy: Copy): String? {
        val now = bundle.fetchedAtEpoch
        val horizon = now + 24 * 3600
        val upcoming = bundle.hours.filter { it.epochSeconds in now..horizon }
        if (upcoming.isEmpty()) return null

        val event = upcoming.firstOrNull { (it.precipitation ?: 0.0) >= 0.2 }

        val available = bundle.availableModels.filter { model ->
            bundle.seriesFor(model)?.hourly?.isNotEmpty() == true
        }
        if (available.size < 2) {
            return copy.t(
                "Un solo modello disponibile per questa località: previsione da usare con prudenza.",
                "Only one model is available for this location, so treat the forecast with care.",
            )
        }

        if (event == null) {
            // Everyone dry? Say who agrees, that is the reassuring part.
            val dryModels = available.filter { model ->
                val series = bundle.seriesFor(model) ?: return@filter false
                series.hourly.filter { it.epochSeconds in now..horizon }
                    .all { (it.precipitation ?: 0.0) < 0.2 }
            }
            if (dryModels.size >= 2 && dryModels.size >= available.size - 1) {
                return copy.t(
                    "${nameList(dryModels, copy)} concordano: nessuna precipitazione significativa nelle prossime 24 ore.",
                    "${nameList(dryModels, copy)} agree: no significant precipitation in the next 24 hours.",
                )
            }
            return null
        }

        // Which models see this event?
        val windowStart = event.epochSeconds
        val windowEnd = event.epochSeconds + 3 * 3600
        val wet = mutableListOf<WeatherModel>()
        val dry = mutableListOf<WeatherModel>()
        available.forEach { model ->
            val series = bundle.seriesFor(model) ?: return@forEach
            val inWindow = series.hourly.filter { it.epochSeconds in windowStart..windowEnd }
            if (inWindow.isEmpty()) return@forEach
            val total = inWindow.sumOf { it.precipitation ?: 0.0 }
            if (total >= 0.3) wet += model else dry += model
        }
        if (wet.isEmpty() && dry.isEmpty()) return null

        val from = formatHour(event.epochSeconds, bundle.utcOffsetSeconds)
        val to = formatHour(event.epochSeconds + 3600, bundle.utcOffsetSeconds)
        val thundery = WeatherCodes.condition(event.weatherCode).isThunder

        return when {
            dry.isEmpty() || wet.size >= dry.size * 3 -> copy.t(
                "${nameList(wet, copy)} concordano sull'arrivo della pioggia tra le $from e le $to.",
                "${nameList(wet, copy)} agree that rain arrives between $from and $to.",
            )
            wet.isEmpty() -> null
            else -> {
                val leader = wet.first()
                val what = if (thundery) copy.t("temporali", "thunderstorms") else copy.t("pioggia", "rain")
                copy.t(
                    "Previsione incerta: ${leader.displayName} indica $what verso le $from, ${nameList(dry, copy)} mantengono condizioni quasi asciutte.",
                    "Uncertain: ${leader.displayName} shows $what around $from, while ${nameList(dry, copy)} keep it mostly dry.",
                )
            }
        }
    }

    private fun nameList(models: List<WeatherModel>, copy: Copy): String {
        val names = models.take(3).map { it.displayName }
        return when (names.size) {
            0 -> ""
            1 -> names[0]
            2 -> copy.t("${names[0]} e ${names[1]}", "${names[0]} and ${names[1]}")
            else -> copy.t(
                "${names[0]}, ${names[1]} e ${names[2]}",
                "${names[0]}, ${names[1]} and ${names[2]}",
            )
        }
    }

    private fun formatHour(epoch: Long, offset: Int): String {
        val hour = (((epoch + offset) / 3600) % 24).toInt().let { if (it < 0) it + 24 else it }
        return "%02d:00".format(hour)
    }
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
