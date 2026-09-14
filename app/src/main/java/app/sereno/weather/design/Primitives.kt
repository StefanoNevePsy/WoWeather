package app.sereno.weather.design

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Text, resolved against the current atmosphere.
 *
 * The app has no `MaterialTheme`, so this is the only text component: it pulls
 * its style from [LocalTextStyle] and its colour from the live atmosphere, which
 * is what lets the entire interface re-ink itself when the weather changes
 * without a single call site knowing about it.
 */
@Composable
fun SText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    emphasis: Float = Emphasis.primary,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    textAlign: TextAlign? = null,
) {
    val atmosphere = Sereno.atmosphere
    val resolved = if (color.isSpecified) color else atmosphere.ink
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(
            color = resolved.copy(alpha = resolved.alpha * emphasis),
            textAlign = textAlign ?: style.textAlign,
        ),
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun SText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    emphasis: Float = Emphasis.primary,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val atmosphere = Sereno.atmosphere
    val resolved = if (color.isSpecified) color else atmosphere.ink
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = resolved.copy(alpha = resolved.alpha * emphasis)),
        maxLines = maxLines,
        overflow = overflow,
    )
}

private val Color.isSpecified: Boolean get() = this != Color.Unspecified

/**
 * A section label: small, uppercase, widely tracked.
 *
 * This is the device that replaces card headers throughout Sereno. A label plus
 * a hairline organises a page just as clearly as a container does, without
 * chopping it into boxes.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SText(
            text = text.uppercase(),
            style = Sereno.type.label,
            emphasis = Emphasis.tertiary,
        )
        Spacer(Modifier.width(Space.md))
        Box(
            Modifier
                .weight(1f)
                .height(Stroke.hairline)
                .background(Sereno.atmosphere.hairline),
        )
        if (trailing != null) {
            Spacer(Modifier.width(Space.md))
            trailing()
        }
    }
}

/** A hairline rule. Sereno's only separator: there are no dividers with weight. */
@Composable
fun Rule(
    modifier: Modifier = Modifier,
    emphasis: Float = Emphasis.hairline,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Stroke.hairline)
            .background(Sereno.atmosphere.ink(emphasis)),
    )
}

/**
 * A tappable region with no ripple.
 *
 * Material's ink ripple is one of the strongest "this is an Android app"
 * signals there is. Sereno acknowledges a press the way a physical control
 * would: the target dips very slightly and dims, and springs back.
 */
@Composable
fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pressScale: Float = 0.984f,
    pressAlpha: Float = 0.68f,
    contentAlignment: Alignment = Alignment.TopStart,
    onClickLabel: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = Sereno.motion

    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressScale else 1f,
        animationSpec = motion.track(),
        label = "pressScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed && enabled) pressAlpha else 1f,
        animationSpec = motion.fade(120),
        label = "pressAlpha",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .alpha(alpha)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClickLabel = onClickLabel,
                onClick = onClick,
            ),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/**
 * A very low-contrast surface.
 *
 * Used sparingly — for the few places that genuinely need to read as a distinct
 * plane, such as a sheet or a selected segment. It is a wash of ink at 4–7%
 * with a hairline, never a shadowed card.
 */
@Composable
fun Veil(
    modifier: Modifier = Modifier,
    corner: Dp = Radius.hair,
    strength: Float = Emphasis.veil,
    bordered: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val atmosphere = Sereno.atmosphere
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(atmosphere.ink(strength))
            .then(
                if (bordered) {
                    Modifier.background(
                        Brush.verticalGradient(
                            0f to atmosphere.ink(Emphasis.whisper),
                            1f to Color.Transparent,
                        ),
                    )
                } else Modifier,
            ),
        content = content,
    )
}

/**
 * The loading placeholder.
 *
 * A slow, low-amplitude luminance sweep rather than a spinner. Sereno never
 * shows a spinner: the shape of the content is always drawn first, so the page
 * does not jump when data lands.
 */
@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    corner: Dp = Radius.hair,
) {
    val atmosphere = Sereno.atmosphere
    val motion = Sereno.motion
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by if (motion.ambientEnabled) {
        transition.animateFloat(
            initialValue = 0.045f,
            targetValue = 0.11f,
            animationSpec = infiniteRepeatable(tween(1400, easing = motion.easeInOut), RepeatMode.Reverse),
            label = "pulse",
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0.07f) }
    }

    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(atmosphere.ink(pulse))
            .clearAndSetSemantics { },
    )
}

/** A labelled value, the workhorse row of the conditions section. */
@Composable
fun ValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    accent: Color? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 34.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) { leading() }
            Spacer(Modifier.width(Space.md))
        }
        SText(label, style = Sereno.type.body, emphasis = Emphasis.secondary)
        Spacer(Modifier.weight(1f))
        if (detail != null) {
            SText(detail, style = Sereno.type.caption, emphasis = Emphasis.tertiary)
            Spacer(Modifier.width(Space.sm))
        }
        SText(
            value,
            style = Sereno.type.data,
            color = accent ?: Color.Unspecified,
        )
    }
}

/** Vertical rhythm helpers, so screens never hand-roll a `Spacer(17.dp)`. */
@Composable fun GapRow() = Spacer(Modifier.height(Space.rowGap))
@Composable fun GapBlock() = Spacer(Modifier.height(Space.blockGap))
@Composable fun GapSection() = Spacer(Modifier.height(Space.sectionGap))

@Composable
fun HorizontalStack(
    modifier: Modifier = Modifier,
    spacing: Dp = Space.sm,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = verticalAlignment,
        content = content,
    )
}
