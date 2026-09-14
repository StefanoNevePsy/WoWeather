package app.sereno.weather.widget

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.sereno.weather.design.drawWeatherGlyph

/**
 * Renders Sereno's weather glyphs into bitmaps for the home-screen widget.
 *
 * `RemoteViews` cannot host Compose, so the obvious route would be a parallel
 * set of vector drawables — and a parallel set is a set that drifts. Driving
 * Compose's `CanvasDrawScope` onto a plain bitmap instead means the widget is
 * drawn by exactly the same code as the app, and an icon fixed in one place is
 * fixed in both.
 */
object WidgetGlyphs {

    fun render(
        code: Int?,
        isDay: Boolean,
        sizePx: Int,
        ink: Color,
        accent: Color,
    ): Bitmap {
        val side = sizePx.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(bitmap.asImageBitmap()),
            size = Size(side.toFloat(), side.toFloat()),
        ) {
            drawWeatherGlyph(
                code = code,
                isDay = isDay,
                center = Offset(side / 2f, side / 2f),
                sizePx = side.toFloat(),
                ink = ink,
                accent = accent,
            )
        }
        return bitmap
    }
}
