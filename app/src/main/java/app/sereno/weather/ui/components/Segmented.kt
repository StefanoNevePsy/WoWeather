package app.sereno.weather.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.sereno.weather.design.Emphasis
import app.sereno.weather.design.Pressable
import app.sereno.weather.design.Radius
import app.sereno.weather.design.SText
import app.sereno.weather.design.Sereno
import app.sereno.weather.design.Stroke

/**
 * A segmented control.
 *
 * Square-cornered, hairline-bordered, with a sliding plate behind the active
 * segment. Deliberately not a row of pills: the pill shape is the single most
 * recognisable Material You component and using it anywhere would undo the rest
 * of the design system.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 34.dp,
) {
    val atmosphere = Sereno.atmosphere
    val motion = Sereno.motion
    if (options.isEmpty()) return

    var containerWidth by remember { mutableStateOf(0) }
    val index = options.indexOf(selected).coerceAtLeast(0)
    val position by animateFloatAsState(
        targetValue = index.toFloat(),
        animationSpec = motion.settle(),
        label = "segmentIndicator",
    )

    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.chip))
            .background(atmosphere.ink(0.045f))
            .border(Stroke.hairline, atmosphere.ink(Emphasis.hairline), RoundedCornerShape(Radius.chip))
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .onSizeChanged { containerWidth = it.width },
        ) {
            val density = LocalDensity.current
            val slot = with(density) { (containerWidth / options.size.toFloat()).toDp() }
            if (containerWidth > 0) {
                Box(
                    Modifier
                        .offset(x = slot * position)
                        .width(slot)
                        .height(height)
                        .clip(RoundedCornerShape(Radius.hair))
                        .background(atmosphere.ink(0.10f)),
                )
            }
            Row(Modifier.fillMaxWidth()) {
                options.forEach { option ->
                    val active = option == selected
                    val text = label(option)
                    Pressable(
                        onClick = { onSelect(option) },
                        modifier = Modifier
                            .weight(1f)
                            .height(height)
                            .semantics { contentDescription = text },
                        contentAlignment = Alignment.Center,
                        pressScale = 1f,
                    ) {
                        SText(
                            text = text,
                            style = Sereno.type.dataSmall,
                            emphasis = if (active) Emphasis.primary else Emphasis.tertiary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A boolean control.
 *
 * A bordered square that gains a check, rather than a sliding track. The track
 * switch is so strongly associated with Material and iOS that using one would
 * import another platform's voice into a design that has its own; a check in a
 * square reads as unambiguously on or off and belongs to the same drawn-stroke
 * family as the rest of the iconography.
 */
@Composable
fun SCheck(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val atmosphere = Sereno.atmosphere
    val motion = Sereno.motion
    val fill by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = motion.fade(180),
        label = "checkFill",
    )

    Pressable(
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        modifier = modifier.size(app.sereno.weather.design.Touch.compact),
        contentAlignment = Alignment.Center,
        onClickLabel = contentDescription,
    ) {
        Box(
            Modifier
                .size(21.dp)
                .clip(RoundedCornerShape(Radius.hair))
                .background(atmosphere.ink(0.05f + 0.10f * fill))
                .border(
                    Stroke.rule,
                    atmosphere.ink(Emphasis.hairline + 0.14f * fill),
                    RoundedCornerShape(Radius.hair),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (fill > 0.01f) {
                app.sereno.weather.design.SGlyph(
                    glyph = app.sereno.weather.design.Glyph.Check,
                    size = 14.dp,
                    emphasis = fill,
                )
            }
        }
    }
}

/** A settings row carrying a boolean. */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = app.sereno.weather.design.Touch.min)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            SText(label, style = Sereno.type.body)
            if (detail != null) {
                SText(detail, style = Sereno.type.caption, emphasis = Emphasis.tertiary)
            }
        }
        SCheck(checked, onCheckedChange, contentDescription = label)
    }
}
