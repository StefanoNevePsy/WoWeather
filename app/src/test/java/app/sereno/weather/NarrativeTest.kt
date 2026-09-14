package app.sereno.weather

import app.sereno.weather.data.prefs.SerenoSettings
import app.sereno.weather.domain.model.BlendedHour
import app.sereno.weather.domain.model.Confidence
import app.sereno.weather.domain.model.PrecipUnit
import app.sereno.weather.domain.model.Provenance
import app.sereno.weather.domain.model.SpeedUnit
import app.sereno.weather.domain.model.TemperatureUnit
import app.sereno.weather.domain.story.Narrator
import app.sereno.weather.i18n.Copy
import app.sereno.weather.i18n.Lang
import app.sereno.weather.ui.Formatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The narrative and formatting layers are the two places where the app talks to
 * a person in their own language, so both are checked in Italian and English.
 */
class NarrativeTest {

    private val italian = Copy(Lang.It)
    private val english = Copy(Lang.En)

    /** Local midnight for a UTC+2 zone. */
    private val midnight = 1_757_800_800L
    private val offset = 7200

    private fun hour(
        localHour: Int,
        temperature: Double = 18.0,
        precipitation: Double = 0.0,
        cloud: Double = 10.0,
        gust: Double = 12.0,
        code: Int = 0,
    ) = BlendedHour(
        epochSeconds = midnight + localHour * 3600L,
        temperature = temperature,
        temperatureSpread = 0.4,
        apparentTemperature = temperature,
        precipitation = precipitation,
        precipitationSpread = 0.1,
        precipitationProbability = null,
        probabilityProvenance = Provenance.Missing,
        snowfall = 0.0,
        windSpeed = gust / 1.8,
        windSpeedSpread = 1.0,
        windGust = gust,
        windDirection = 220.0,
        cloudCover = cloud,
        humidity = 60.0,
        pressure = 1015.0,
        visibility = 20000.0,
        uvIndex = 3.0,
        dewPoint = 10.0,
        weatherCode = code,
        isDay = localHour in 7..19,
        confidence = Confidence(0.8f, Confidence.bandFor(0.8f), 4),
        contributors = emptyList(),
    )

    @Test
    fun `a clear day produces a sentence in both languages`() {
        val hours = (6..21).map { hour(it, cloud = 8.0) }
        val it = Narrator.dayNarrative(hours, offset, italian)
        val en = Narrator.dayNarrative(hours, offset, english)

        assertNotNull(it)
        assertNotNull(en)
        assertTrue("was: $it", it!!.startsWith("Mattina"))
        assertTrue("should end as a sentence", it.endsWith("."))
        assertTrue("was: $en", en!!.contains("orning"))
        assertTrue(it != en)
    }

    @Test
    fun `clouds building in the afternoon is called out`() {
        val hours = (6..11).map { hour(it, cloud = 5.0) } +
            (12..20).map { hour(it, cloud = 80.0) }
        val sentence = Narrator.dayNarrative(hours, offset, italian)
        assertNotNull(sentence)
        assertTrue("was: $sentence", sentence!!.contains("nuvole in aumento"))
    }

    @Test
    fun `a rain window is named when it is short enough to be useful`() {
        val hours = (6..11).map { hour(it, cloud = 40.0) } +
            (12..14).map { hour(it, precipitation = 2.0, cloud = 95.0, code = 61) } +
            (15..20).map { hour(it, cloud = 60.0) }
        val sentence = Narrator.dayNarrative(hours, offset, italian)
        assertNotNull(sentence)
        assertTrue("was: $sentence", sentence!!.contains("pioggia"))
    }

    @Test
    fun `all-day rain is described without pretending to name a window`() {
        val hours = (6..21).map { hour(it, precipitation = 1.5, cloud = 95.0, code = 61) }
        val sentence = Narrator.dayNarrative(hours, offset, italian)
        assertNotNull(sentence)
        assertTrue("was: $sentence", sentence!!.contains("gran parte della giornata"))
    }

    @Test
    fun `strong gusts are mentioned, ordinary wind is not`() {
        val calm = (6..20).map { hour(it, gust = 14.0) }
        val windy = (6..20).map { hour(it, gust = 62.0) }

        assertTrue(Narrator.dayNarrative(calm, offset, italian)?.contains("raffiche") != true)
        assertTrue(Narrator.dayNarrative(windy, offset, italian)!!.contains("raffiche"))
    }

    @Test
    fun `no hours means no sentence rather than an empty one`() {
        assertNull(Narrator.dayNarrative(emptyList(), offset, italian))
    }

    // -----------------------------------------------------------------------
    // Formatting
    // -----------------------------------------------------------------------

    private fun formatter(
        temperature: TemperatureUnit = TemperatureUnit.Celsius,
        speed: SpeedUnit = SpeedUnit.KmH,
        precip: PrecipUnit = PrecipUnit.Mm,
        copy: Copy = italian,
    ) = Formatter(
        settings = SerenoSettings(temperatureUnit = temperature, speedUnit = speed, precipUnit = precip),
        copy = copy,
        utcOffsetSeconds = offset,
    )

    @Test
    fun `temperature converts and rounds`() {
        assertEquals("18°", formatter().temperature(18.4))
        assertEquals("64°", formatter(temperature = TemperatureUnit.Fahrenheit).temperature(17.8))
    }

    @Test
    fun `a temperature difference is not converted as if it were a reading`() {
        // 5 C of warming is 9 F of warming, not 41.
        val fahrenheit = formatter(temperature = TemperatureUnit.Fahrenheit)
        assertTrue(fahrenheit.temperatureDelta(5.0).startsWith("9"))
    }

    @Test
    fun `missing values render as a dash, never as zero`() {
        val f = formatter()
        assertEquals("—", f.temperature(null))
        assertEquals("—", f.speed(null))
        assertEquals("—", f.precipitation(null))
        assertEquals("—", f.percent(null))
        assertEquals("—", f.uv(null))
    }

    @Test
    fun `speed converts between units`() {
        assertEquals("36 km/h", formatter().speed(36.0))
        assertTrue(formatter(speed = SpeedUnit.Ms).speed(36.0).startsWith("10"))
    }

    @Test
    fun `wind direction is localised`() {
        // West is O for ovest in Italian, W in English.
        assertEquals("O", formatter().windDirection(270.0))
        assertEquals("W", formatter(copy = english).windDirection(270.0))
    }

    @Test
    fun `local time respects the forecast offset`() {
        assertEquals("00:00", formatter().time(midnight))
        assertEquals(14, formatter().hourOfDay(midnight + 14 * 3600))
    }
}
