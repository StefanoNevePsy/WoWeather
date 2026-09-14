package app.sereno.weather.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import app.sereno.weather.design.weatherIconRes

/**
 * Renders Sereno's weather glyphs into bitmaps for the home-screen widget.
 *
 * `RemoteViews` cannot host Compose, but it does not need to: the icons are
 * vector drawables, so the widget tints and rasterises the very same asset the
 * app renders. One icon set, one source of truth, no parallel copy to drift.
 */
object WidgetGlyphs {

    fun render(
        context: Context,
        code: Int?,
        isDay: Boolean,
        sizePx: Int,
        tintColor: Int,
    ): Bitmap? {
        val side = sizePx.coerceAtLeast(1)
        val drawable = ContextCompat.getDrawable(context, weatherIconRes(code, isDay))
            ?.mutate() ?: return null
        DrawableCompat.setTint(drawable, tintColor)
        return runCatching { drawable.toBitmap(side, side) }.getOrNull()
    }
}
