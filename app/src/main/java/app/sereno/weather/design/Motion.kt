package app.sereno.weather.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntOffset

/**
 * Sereno's motion language.
 *
 * Two rules govern everything here:
 *  1. Position and size are animated with springs, because physical things
 *     settle; opacity and colour are animated with eased tweens, because light
 *     does not overshoot.
 *  2. Nothing may delay a user's next interaction. Entrances are allowed to be
 *     slow, but anything on the critical path of a tap is under 200ms.
 */
@Immutable
class Motion(private val reduced: Boolean) {

    val reduceMotion: Boolean get() = reduced

    /** Standard easing: fast out, long gentle settle. */
    val easeOut: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val easeInOut: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

    /** Layout position/size. */
    fun <T> settle(): FiniteAnimationSpec<T> =
        if (reduced) tween(0)
        else spring(dampingRatio = 0.92f, stiffness = Spring.StiffnessMediumLow)

    fun offsetSettle(): FiniteAnimationSpec<IntOffset> =
        if (reduced) tween(0)
        else spring(
            dampingRatio = 0.92f,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = IntOffset.VisibilityThreshold,
        )

    /** A slightly livelier spring for things the finger is directly driving. */
    fun <T> track(): FiniteAnimationSpec<T> =
        if (reduced) tween(0)
        else spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)

    /** Opacity and colour. */
    fun <T> fade(durationMs: Int = 260): FiniteAnimationSpec<T> =
        tween(if (reduced) 0 else durationMs, easing = easeOut)

    /** Page-level transitions. */
    fun <T> page(): FiniteAnimationSpec<T> = tween(if (reduced) 0 else 380, easing = easeOut)

    /** The progressive draw-in used by every chart. Skipped under reduce-motion. */
    fun <T> draw(): FiniteAnimationSpec<T> = tween(if (reduced) 0 else 720, easing = easeOut)

    /** Ambient, never-ending atmosphere motion (drifting light, rain). */
    val ambientEnabled: Boolean get() = !reduced

    /** Vertical travel for entering content, in dp. */
    val enterTravel: Float get() = if (reduced) 0f else 14f
}
