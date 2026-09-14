package app.sereno.weather.design

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sereno's spacing scale.
 *
 * It is a 4pt grid, but it deliberately skips values as it grows so that the
 * large gaps between editorial blocks stay visibly different from the small
 * gaps inside them. Using an unbroken 4/8/12/16/20/24/28/32 ramp is what makes
 * layouts read as "generic app"; the jumps here are what make a page read as
 * composed.
 */
object Space {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp

    /** Gap between related rows inside one block. */
    val rowGap: Dp = 14.dp

    /** Gap between blocks that belong to the same section. */
    val blockGap: Dp = 28.dp

    /** Gap between top-level sections of a page. */
    val sectionGap: Dp = 48.dp

    /** The one horizontal margin used by every screen. */
    val pageMargin: Dp = 22.dp

    /** Breathing room under a fixed header / above a fixed rail. */
    val headerDrop: Dp = 8.dp
    val railClearance: Dp = 96.dp
}

/**
 * Corner radii.
 *
 * Sereno is a square-cornered design. Large rounded rectangles are the single
 * strongest "this is a Material app" signal, so the only things allowed to be
 * round here are things that are genuinely circular (dots, pucks, the location
 * indicator) and a handful of very small interactive chips.
 */
object Radius {
    val none: Dp = 0.dp
    val hair: Dp = 2.dp
    val chip: Dp = 3.dp
    val sheet: Dp = 18.dp
    val full: Dp = 999.dp
}

/** Hairline weights. Sereno separates with rules, never with elevation. */
object Stroke {
    val hairline: Dp = 0.7.dp
    val rule: Dp = 1.dp
    val emphasis: Dp = 1.6.dp
    val chart: Dp = 2.dp
    val chartBold: Dp = 2.6.dp
}

/**
 * Opacity hierarchy.
 *
 * Every foreground element in the app picks its emphasis from this ladder
 * rather than inventing an alpha, which is what keeps six different screens
 * feeling like one product.
 */
object Emphasis {
    const val primary = 1.0f
    const val secondary = 0.66f
    const val tertiary = 0.42f
    const val quaternary = 0.26f
    const val hairline = 0.14f
    const val veil = 0.07f
    const val whisper = 0.04f
}

/** Minimum interactive sizes, per accessibility guidance. */
object Touch {
    val min: Dp = 48.dp
    val compact: Dp = 44.dp
}
