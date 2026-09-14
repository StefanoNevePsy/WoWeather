package app.sereno.weather

import app.sereno.weather.domain.model.BlendedHour
import app.sereno.weather.domain.model.ConfidenceBand
import app.sereno.weather.domain.model.HourPoint
import app.sereno.weather.domain.model.ModelSeries
import app.sereno.weather.domain.model.Provenance
import app.sereno.weather.domain.model.WeatherCodes
import app.sereno.weather.domain.model.WeatherModel
import app.sereno.weather.domain.nowcast.Nowcaster
import app.sereno.weather.domain.model.NowcastKind
import app.sereno.weather.domain.synthesis.ModelWeights
import app.sereno.weather.domain.synthesis.Synthesizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The forecast engine is pure Kotlin with no Android dependencies, which is
 * exactly so that the part of the app most likely to be quietly wrong can be
 * tested directly.
 */
class SynthesisTest {

    private val now = 1_757_840_400L

    private fun hours(
        count: Int,
        temperature: (Int) -> Double? = { 20.0 },
        precipitation: (Int) -> Double? = { 0.0 },
        wind: (Int) -> Double? = { 10.0 },
        code: (Int) -> Int? = { 0 },
    ): List<HourPoint> = (0 until count).map { i ->
        HourPoint(
            epochSeconds = now + i * 3600L,
            temperature = temperature(i),
            precipitation = precipitation(i),
            windSpeed = wind(i),
            weatherCode = code(i),
            isDay = true,
        )
    }

    private fun series(vararg entries: Pair<WeatherModel, List<HourPoint>>) =
        entries.associate { (model, points) -> model to ModelSeries(model, hourly = points) }

    // -----------------------------------------------------------------------
    // Weighting
    // -----------------------------------------------------------------------

    @Test
    fun `regional model outweighs global models in the short range`() {
        val regional = ModelWeights.weight(WeatherModel.Icon2I, leadHours = 12)
        val global = ModelWeights.weight(WeatherModel.EcmwfIfs, leadHours = 12)
        assertTrue("ICON-2I should dominate at +12h", regional > global)
    }

    @Test
    fun `ECMWF overtakes the regional models in the medium range`() {
        val regional = ModelWeights.weight(WeatherModel.Icon2I, leadHours = 96)
        val ecmwf = ModelWeights.weight(WeatherModel.EcmwfIfs, leadHours = 96)
        assertEquals("ICON-2I does not run to +96h", 0.0, regional, 1e-9)
        assertTrue(ecmwf > 0.8)
    }

    @Test
    fun `no model contributes beyond its published horizon`() {
        WeatherModel.entries.forEach { model ->
            val beyond = ModelWeights.weight(model, model.horizonHours + 1)
            assertEquals("${model.displayName} should stop at its horizon", 0.0, beyond, 1e-9)
        }
    }

    // -----------------------------------------------------------------------
    // Blending
    // -----------------------------------------------------------------------

    @Test
    fun `blend is weighted towards the higher-weighted model`() {
        val result = Synthesizer.synthesize(
            series(
                WeatherModel.Icon2I to hours(6, temperature = { 20.0 }),
                WeatherModel.Gfs to hours(6, temperature = { 10.0 }),
            ),
            now,
        )
        val first = result.hours.first()
        // ICON-2I carries roughly four times GFS's weight at +0h, so the blend
        // must sit much nearer 20 than the arithmetic mean of 15.
        assertTrue("blend was ${first.temperature}", (first.temperature ?: 0.0) > 17.0)
        assertTrue((first.temperature ?: 0.0) < 20.0)
    }

    @Test
    fun `a field no model supplies stays null rather than becoming zero`() {
        val result = Synthesizer.synthesize(
            series(WeatherModel.EcmwfIfs to hours(4)),
            now,
        )
        assertNull("humidity was never supplied", result.hours.first().humidity)
        assertNull(result.hours.first().uvIndex)
    }

    @Test
    fun `models that return nothing for a location do not drag the blend down`() {
        val absent = hours(6, temperature = { null }, precipitation = { null }, wind = { null })
        val result = Synthesizer.synthesize(
            series(
                WeatherModel.EcmwfIfs to hours(6, temperature = { 18.0 }),
                WeatherModel.Icon2I to absent,
            ),
            now,
        )
        assertEquals(18.0, result.hours.first().temperature!!, 0.01)
        assertEquals(listOf(WeatherModel.EcmwfIfs), result.hours.first().contributors)
    }

    @Test
    fun `spread reflects disagreement`() {
        val agreeing = Synthesizer.synthesize(
            series(
                WeatherModel.EcmwfIfs to hours(3, temperature = { 20.0 }),
                WeatherModel.IconEu to hours(3, temperature = { 20.1 }),
            ),
            now,
        ).hours.first()

        val disagreeing = Synthesizer.synthesize(
            series(
                WeatherModel.EcmwfIfs to hours(3, temperature = { 20.0 }),
                WeatherModel.IconEu to hours(3, temperature = { 28.0 }),
            ),
            now,
        ).hours.first()

        assertTrue(agreeing.temperatureSpread < 0.2)
        assertTrue(disagreeing.temperatureSpread > 2.0)
    }

    // -----------------------------------------------------------------------
    // Confidence
    // -----------------------------------------------------------------------

    @Test
    fun `unanimous models score higher than split ones`() {
        val unanimous = Synthesizer.synthesize(
            series(
                WeatherModel.Icon2I to hours(3, precipitation = { 2.0 }),
                WeatherModel.IconEu to hours(3, precipitation = { 2.1 }),
                WeatherModel.EcmwfIfs to hours(3, precipitation = { 1.9 }),
                WeatherModel.Gfs to hours(3, precipitation = { 2.0 }),
            ),
            now,
        ).hours.first().confidence

        val split = Synthesizer.synthesize(
            series(
                WeatherModel.Icon2I to hours(3, precipitation = { 6.0 }),
                WeatherModel.IconEu to hours(3, precipitation = { 0.0 }),
                WeatherModel.EcmwfIfs to hours(3, precipitation = { 0.0 }),
                WeatherModel.Gfs to hours(3, precipitation = { 5.0 }),
            ),
            now,
        ).hours.first().confidence

        assertTrue("unanimous=${unanimous.percent} split=${split.percent}", unanimous.score > split.score)
        assertTrue(unanimous.band == ConfidenceBand.VeryHigh || unanimous.band == ConfidenceBand.High)
    }

    @Test
    fun `a single model can never reach high confidence`() {
        val confidence = Synthesizer.synthesize(
            series(WeatherModel.EcmwfIfs to hours(3)),
            now,
        ).hours.first().confidence
        assertEquals(1, confidence.modelCount)
        assertTrue("score was ${confidence.percent}", confidence.score < 0.86f)
    }

    @Test
    fun `confidence decays with lead time even when models agree perfectly`() {
        val identical = hours(24 * 14, temperature = { 20.0 }, precipitation = { 0.0 })
        val result = Synthesizer.synthesize(
            series(
                WeatherModel.EcmwfIfs to identical,
                WeatherModel.EcmwfAifs to identical,
                WeatherModel.IconGlobal to identical,
                WeatherModel.Gfs to identical,
            ),
            now,
        )
        val early = result.hours.first().confidence.score
        val late = result.hours.last().confidence.score
        assertTrue("early=$early late=$late", late < early)
        assertTrue("day 14 should not read as near-certain", late < 0.75f)
    }

    // -----------------------------------------------------------------------
    // Derived probability and code reconciliation
    // -----------------------------------------------------------------------

    @Test
    fun `probability is derived from model agreement and labelled as such`() {
        val result = Synthesizer.synthesize(
            series(
                WeatherModel.EcmwfIfs to hours(3, precipitation = { 1.0 }),
                WeatherModel.IconEu to hours(3, precipitation = { 1.0 }),
                WeatherModel.IconGlobal to hours(3, precipitation = { 0.0 }),
            ),
            now,
        ).hours.first()

        assertEquals(Provenance.Derived, result.probabilityProvenance)
        val probability = result.precipitationProbability!!
        assertTrue("probability was $probability", probability in 40.0..90.0)
    }

    @Test
    fun `a rain code is pulled back when the blended amount is dry`() {
        // Four models say clear, one says light rain: the mean is below the wet
        // threshold, so the icon must not still show rain.
        val reconciled = WeatherCodes.reconcile(
            code = 61,
            precipitationMm = 0.04,
            snowfallCm = 0.0,
            cloudCoverPct = 70.0,
        )
        assertEquals(2, reconciled)
    }

    @Test
    fun `thunder is never reconciled away`() {
        assertEquals(95, WeatherCodes.reconcile(95, 0.0, 0.0, 10.0))
    }

    // -----------------------------------------------------------------------
    // Nowcast
    // -----------------------------------------------------------------------

    @Test
    fun `a dry window reports dry`() {
        val blended = Synthesizer.synthesize(
            series(
                WeatherModel.Icon2I to hours(6, precipitation = { 0.0 }),
                WeatherModel.EcmwfIfs to hours(6, precipitation = { 0.0 }),
            ),
            now,
        ).hours
        assertEquals(NowcastKind.Dry, Nowcaster.compute(blended, now).kind)
    }

    @Test
    fun `rain arriving later is reported as a range, not a single minute`() {
        val blended = Synthesizer.synthesize(
            series(
                WeatherModel.Icon2I to hours(6, precipitation = { if (it >= 2) 2.0 else 0.0 }),
                WeatherModel.EcmwfIfs to hours(6, precipitation = { if (it >= 2) 2.0 else 0.0 }),
                WeatherModel.IconEu to hours(6, precipitation = { if (it >= 2) 2.0 else 0.0 }),
            ),
            now,
        ).hours

        val nowcast = Nowcaster.compute(blended, now)
        assertEquals(NowcastKind.StartingSoon, nowcast.kind)
        assertNotNull(nowcast.startMinutesLow)
        assertNotNull(nowcast.startMinutesHigh)
        assertTrue(
            "range should be a genuine interval",
            nowcast.startMinutesHigh!! > nowcast.startMinutesLow!!,
        )
    }

    @Test
    fun `rain already falling reports ongoing`() {
        val blended = Synthesizer.synthesize(
            series(
                WeatherModel.Icon2I to hours(6, precipitation = { 3.0 }),
                WeatherModel.EcmwfIfs to hours(6, precipitation = { 3.0 }),
            ),
            now,
        ).hours
        val kind = Nowcaster.compute(blended, now).kind
        assertTrue(kind == NowcastKind.Ongoing || kind == NowcastKind.Stopping)
    }

    @Test
    fun `wet threshold is respected`() {
        assertTrue(BlendedHour.WET_THRESHOLD_MM > 0.0)
    }
}
