package app.sereno.weather.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.sereno.weather.R

/**
 * Inter, cut for user-interface sizes (optical size 14).
 */
val InterText = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/**
 * Inter, cut for display sizes (optical size 32).
 *
 * This is a genuinely different set of outlines, not the text cut scaled up:
 * tighter apertures, thinner joins, less spacing built into the sidebearings.
 * It is the reason the 104sp temperature reads as typeset rather than zoomed.
 */
val InterDisplay = FontFamily(
    Font(R.font.inter_display_light, FontWeight.Light),
    Font(R.font.inter_display_regular, FontWeight.Normal),
    Font(R.font.inter_display_medium, FontWeight.Medium),
)

/**
 * Instrument Serif. Used in exactly one place — the narrative line — where a
 * human sentence about the weather sits among all the numbers.
 */
val SerenoSerif = FontFamily(
    Font(R.font.serif_regular, FontWeight.Normal),
    Font(R.font.serif_italic, FontWeight.Normal, FontStyle.Italic),
)

@OptIn(ExperimentalTextApi::class)
private val noPadding = PlatformTextStyle(includeFontPadding = false)

/** Lining tabular figures: columns of numbers never jitter as values change. */
private const val TABULAR = "tnum"

@Immutable
data class SerenoTypography(
    /** The hero temperature. One per screen, never two. */
    val display: TextStyle = TextStyle(
        fontFamily = InterDisplay,
        fontWeight = FontWeight.Light,
        fontSize = 104.sp,
        lineHeight = 100.sp,
        letterSpacing = (-0.045).em,
        fontFeatureSettings = TABULAR,
        platformStyle = noPadding,
    ),
    /** Secondary large number: day detail highs, model readouts. */
    val displaySmall: TextStyle = TextStyle(
        fontFamily = InterDisplay,
        fontWeight = FontWeight.Light,
        fontSize = 56.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.04).em,
        fontFeatureSettings = TABULAR,
        platformStyle = noPadding,
    ),
    /** Screen titles. */
    val title: TextStyle = TextStyle(
        fontFamily = InterDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 27.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.022).em,
        platformStyle = noPadding,
    ),
    val headline: TextStyle = TextStyle(
        fontFamily = InterText,
        fontWeight = FontWeight.Medium,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.015).em,
        platformStyle = noPadding,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = InterText,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.006).em,
        platformStyle = noPadding,
    ),
    val bodyStrong: TextStyle = TextStyle(
        fontFamily = InterText,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.006).em,
        platformStyle = noPadding,
    ),
    val caption: TextStyle = TextStyle(
        fontFamily = InterText,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.002).em,
        platformStyle = noPadding,
    ),
    /**
     * Section labels. Uppercase, widely tracked, small — the editorial device
     * that replaces card titles throughout the app.
     */
    val label: TextStyle = TextStyle(
        fontFamily = InterText,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.15.em,
        platformStyle = noPadding,
    ),
    /** Any number that sits in a column with other numbers. */
    val data: TextStyle = TextStyle(
        fontFamily = InterText,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 19.sp,
        letterSpacing = (-0.01).em,
        fontFeatureSettings = TABULAR,
        platformStyle = noPadding,
    ),
    val dataLarge: TextStyle = TextStyle(
        fontFamily = InterDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 25.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.03).em,
        fontFeatureSettings = TABULAR,
        platformStyle = noPadding,
    ),
    val dataSmall: TextStyle = TextStyle(
        fontFamily = InterText,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        fontFeatureSettings = TABULAR,
        platformStyle = noPadding,
    ),
    /** Chart axis ticks: quiet, tabular, never competing with the data. */
    val tick: TextStyle = TextStyle(
        fontFamily = InterText,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.02.em,
        fontFeatureSettings = TABULAR,
        platformStyle = noPadding,
    ),
    /** The one serif in the app. */
    val narrative: TextStyle = TextStyle(
        fontFamily = SerenoSerif,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.002.em,
        textAlign = TextAlign.Start,
        platformStyle = noPadding,
    ),
)
