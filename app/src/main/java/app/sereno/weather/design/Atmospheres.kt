package app.sereno.weather.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * The colour of every sky Sereno can draw.
 *
 * Two decisions run through the whole table:
 *
 *  - **No pure black and no pure white.** The darkest value here is #090C12
 *    (thunder, night) and the lightest horizon is #F4F0EA. Pure black kills the
 *    sense of atmosphere and makes OLED smearing obvious when the backdrop
 *    animates; pure white glares.
 *  - **Warm horizon, cool zenith.** Real skies get warmer towards the ground.
 *    Almost every daytime entry moves from a cool top to a warmer bottom, and
 *    that single gradient does more for the "atmospheric" feel than any effect.
 *
 * Thunder is the one mood that ignores the light/dark preference and always
 * goes dark. A storm rendered in a bright palette is not a storm.
 */
object Atmospheres {

    fun resolve(mood: Mood, dark: Boolean): Atmosphere =
        if (mood == Mood.Thunder) thunder(dark)
        else if (mood.isNight || dark) night(mood) else day(mood)

    private fun day(mood: Mood): Atmosphere = when (mood) {
        Mood.ClearDay -> Atmosphere(
            mood = mood, dark = false,
            skyTop = Color(0xFFA6C8EC), skyMid = Color(0xFFCCDFF0), skyLow = Color(0xFFF4F0EA),
            glow = Color(0xFFFFE6BE), glowCenter = Offset(0.80f, 0.10f), glowRadius = 0.95f,
            ink = Color(0xFF0F1B27), accent = Color(0xFFDE9748), accentSoft = Color(0xFFF3D8B4),
            haze = 0.09f, grain = 0.035f,
        )
        Mood.PartlyDay -> Atmosphere(
            mood = mood, dark = false,
            skyTop = Color(0xFFA8BFD6), skyMid = Color(0xFFCAD8E3), skyLow = Color(0xFFEEEAE4),
            glow = Color(0xFFFFEAD2), glowCenter = Offset(0.72f, 0.14f), glowRadius = 1.0f,
            ink = Color(0xFF121C26), accent = Color(0xFFD4924F), accentSoft = Color(0xFFEBD4B8),
            haze = 0.16f, grain = 0.04f,
        )
        Mood.Overcast -> Atmosphere(
            mood = mood, dark = false,
            skyTop = Color(0xFFB6BEC6), skyMid = Color(0xFFC9CED4), skyLow = Color(0xFFDEDFE0),
            glow = Color(0xFFE9ECEF), glowCenter = Offset(0.50f, 0.06f), glowRadius = 1.25f,
            ink = Color(0xFF151B21), accent = Color(0xFF6B7684), accentSoft = Color(0xFFC2C9D1),
            haze = 0.26f, grain = 0.045f,
        )
        Mood.Fog -> Atmosphere(
            mood = mood, dark = false,
            skyTop = Color(0xFFC7CACB), skyMid = Color(0xFFD5D6D5), skyLow = Color(0xFFE1E0DD),
            glow = Color(0xFFF0EFEB), glowCenter = Offset(0.50f, 0.34f), glowRadius = 1.4f,
            ink = Color(0xFF1A1E21), accent = Color(0xFF898F94), accentSoft = Color(0xFFCFD1D1),
            haze = 0.44f, grain = 0.055f,
        )
        Mood.Rain -> Atmosphere(
            mood = mood, dark = false,
            skyTop = Color(0xFF8DA2B7), skyMid = Color(0xFFACBDCC), skyLow = Color(0xFFC8D2DA),
            glow = Color(0xFFDDE8F1), glowCenter = Offset(0.50f, 0.04f), glowRadius = 1.05f,
            ink = Color(0xFF0B151E), accent = Color(0xFF3C80B3), accentSoft = Color(0xFFAFCBE0),
            haze = 0.24f, grain = 0.045f,
        )
        Mood.Snow -> Atmosphere(
            mood = mood, dark = false,
            skyTop = Color(0xFFC1CCD9), skyMid = Color(0xFFDBE2E9), skyLow = Color(0xFFEFF2F5),
            glow = Color(0xFFFFFFFF), glowCenter = Offset(0.50f, 0.10f), glowRadius = 1.2f,
            ink = Color(0xFF111921), accent = Color(0xFF7D9EBE), accentSoft = Color(0xFFCBDCEB),
            haze = 0.30f, grain = 0.05f,
        )
        else -> day(Mood.ClearDay)
    }

    private fun night(mood: Mood): Atmosphere = when (mood) {
        Mood.ClearNight -> Atmosphere(
            mood = mood, dark = true,
            skyTop = Color(0xFF0A0F1E), skyMid = Color(0xFF121A2C), skyLow = Color(0xFF1B2236),
            glow = Color(0xFF35486E), glowCenter = Offset(0.76f, 0.09f), glowRadius = 0.9f,
            ink = Color(0xFFEBF0F7), accent = Color(0xFFA8BDE0), accentSoft = Color(0xFF43557A),
            haze = 0.12f, grain = 0.042f,
        )
        Mood.PartlyNight -> Atmosphere(
            mood = mood, dark = true,
            skyTop = Color(0xFF0C111F), skyMid = Color(0xFF151D2B), skyLow = Color(0xFF1F2734),
            glow = Color(0xFF2B3648), glowCenter = Offset(0.70f, 0.12f), glowRadius = 1.0f,
            ink = Color(0xFFE7ECF3), accent = Color(0xFF92A5BF), accentSoft = Color(0xFF3A465A),
            haze = 0.20f, grain = 0.045f,
        )
        Mood.ClearDay -> Atmosphere(
            mood = mood, dark = true,
            skyTop = Color(0xFF0B1627), skyMid = Color(0xFF132237), skyLow = Color(0xFF1C2D44),
            glow = Color(0xFF3A5A82), glowCenter = Offset(0.79f, 0.11f), glowRadius = 0.95f,
            ink = Color(0xFFEDF2F8), accent = Color(0xFFE6A65E), accentSoft = Color(0xFF4C5F7C),
            haze = 0.10f, grain = 0.04f,
        )
        Mood.PartlyDay -> Atmosphere(
            mood = mood, dark = true,
            skyTop = Color(0xFF0F1927), skyMid = Color(0xFF172433), skyLow = Color(0xFF202D3B),
            glow = Color(0xFF32465D), glowCenter = Offset(0.72f, 0.14f), glowRadius = 1.05f,
            ink = Color(0xFFE9EEF5), accent = Color(0xFFD7A270), accentSoft = Color(0xFF44546A),
            haze = 0.18f, grain = 0.042f,
        )
        Mood.Overcast -> Atmosphere(
            mood = mood, dark = true,
            skyTop = Color(0xFF13171C), skyMid = Color(0xFF1B2026), skyLow = Color(0xFF252A30),
            glow = Color(0xFF323942), glowCenter = Offset(0.50f, 0.07f), glowRadius = 1.3f,
            ink = Color(0xFFE4E8EE), accent = Color(0xFF8B97A5), accentSoft = Color(0xFF3A424C),
            haze = 0.28f, grain = 0.05f,
        )
        Mood.Fog -> Atmosphere(
            mood = mood, dark = true,
            skyTop = Color(0xFF17191B), skyMid = Color(0xFF1F2224), skyLow = Color(0xFF292C2E),
            glow = Color(0xFF34383B), glowCenter = Offset(0.50f, 0.36f), glowRadius = 1.45f,
            ink = Color(0xFFDFE2E5), accent = Color(0xFF8F979F), accentSoft = Color(0xFF3A3E42),
            haze = 0.46f, grain = 0.06f,
        )
        Mood.Rain -> Atmosphere(
            mood = mood, dark = true,
            skyTop = Color(0xFF091018), skyMid = Color(0xFF101A25), skyLow = Color(0xFF192431),
            glow = Color(0xFF23374B), glowCenter = Offset(0.50f, 0.05f), glowRadius = 1.1f,
            ink = Color(0xFFE6EDF5), accent = Color(0xFF59A2D7), accentSoft = Color(0xFF2C4458),
            haze = 0.26f, grain = 0.048f,
        )
        Mood.Snow -> Atmosphere(
            mood = mood, dark = true,
            skyTop = Color(0xFF0D131B), skyMid = Color(0xFF161E28), skyLow = Color(0xFF202A35),
            glow = Color(0xFF334458), glowCenter = Offset(0.50f, 0.11f), glowRadius = 1.25f,
            ink = Color(0xFFEAF0F7), accent = Color(0xFF9EBFDF), accentSoft = Color(0xFF3B4B5E),
            haze = 0.30f, grain = 0.05f,
        )
        else -> night(Mood.ClearNight)
    }

    /**
     * Storms are always dark. The light variant is a touch less deep so that
     * the transition from a bright day does not feel like the screen broke.
     */
    private fun thunder(dark: Boolean): Atmosphere = Atmosphere(
        mood = Mood.Thunder,
        dark = true,
        skyTop = if (dark) Color(0xFF080B11) else Color(0xFF232B35),
        skyMid = if (dark) Color(0xFF111620) else Color(0xFF323B46),
        skyLow = if (dark) Color(0xFF1A2029) else Color(0xFF454E59),
        glow = if (dark) Color(0xFF2A3040) else Color(0xFF5A6472),
        glowCenter = Offset(0.60f, 0.08f),
        glowRadius = 1.15f,
        ink = Color(0xFFF1F4F8),
        accent = Color(0xFFEFCC6B),
        accentSoft = Color(0xFF575E44),
        haze = 0.20f,
        grain = 0.05f,
    )
}
