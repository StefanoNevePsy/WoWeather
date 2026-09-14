package app.sereno.weather.design

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection

/** User-selectable appearance. */
enum class ThemeMode { System, Light, Dark }

val LocalAtmosphere: ProvidableCompositionLocal<Atmosphere> =
    staticCompositionLocalOf { Atmospheres.resolve(Mood.ClearDay, dark = false) }

val LocalTypography: ProvidableCompositionLocal<SerenoTypography> =
    staticCompositionLocalOf { SerenoTypography() }

val LocalDataColors: ProvidableCompositionLocal<DataColors> =
    staticCompositionLocalOf { LightData }

val LocalMotion: ProvidableCompositionLocal<Motion> =
    staticCompositionLocalOf { Motion(reduced = false) }

/** Ambient text style, so `SText` can inherit like a real typography system. */
val LocalTextStyle: ProvidableCompositionLocal<TextStyle> =
    staticCompositionLocalOf { SerenoTypography().body }

/**
 * The design system entry point. Deliberately not `MaterialTheme`: nothing in
 * the app reads a Material colour scheme, shape scheme or typography.
 */
@Composable
fun SerenoTheme(
    mood: Mood,
    themeMode: ThemeMode = ThemeMode.System,
    reduceMotionOverride: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    // Night moods are dark regardless of the user's light/dark preference:
    // a clear night sky rendered in a light palette is simply wrong.
    val dark = when {
        mood.isNight -> true
        themeMode == ThemeMode.Light -> false
        themeMode == ThemeMode.Dark -> true
        else -> systemDark
    }

    val atmosphere = remember(mood, dark) { Atmospheres.resolve(mood, dark) }
    val typography = remember { SerenoTypography() }
    val data = if (dark) DarkData else LightData

    val reduceMotion = reduceMotionOverride ?: rememberSystemReduceMotion()
    val motion = remember(reduceMotion) { Motion(reduceMotion) }

    CompositionLocalProvider(
        LocalAtmosphere provides atmosphere,
        LocalTypography provides typography,
        LocalDataColors provides data,
        LocalMotion provides motion,
        LocalTextStyle provides typography.body,
        content = content,
    )
}

/**
 * Honours both the platform "remove animations" accessibility setting and
 * battery saver, which is the point at which ambient motion stops being
 * delightful and starts being rude.
 */
@Composable
private fun rememberSystemReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val animatorScale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        val powerSave = runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
        }.getOrDefault(false)
        val lowRam = runCatching {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
        }.getOrDefault(false)
        animatorScale == 0f || powerSave || lowRam
    }
}

object Sereno {
    val atmosphere: Atmosphere
        @Composable @ReadOnlyComposable get() = LocalAtmosphere.current
    val type: SerenoTypography
        @Composable @ReadOnlyComposable get() = LocalTypography.current
    val data: DataColors
        @Composable @ReadOnlyComposable get() = LocalDataColors.current
    val motion: Motion
        @Composable @ReadOnlyComposable get() = LocalMotion.current
}

internal fun TextStyle.forLayout(direction: LayoutDirection): TextStyle =
    if (textAlign == TextAlign.Unspecified) {
        copy(textDirection = if (direction == LayoutDirection.Rtl) TextDirection.Rtl else TextDirection.Ltr)
    } else this
