package app.sereno.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.sereno.weather.design.Emphasis
import app.sereno.weather.design.Glyph
import app.sereno.weather.design.Pressable
import app.sereno.weather.design.SGlyph
import app.sereno.weather.design.SText
import app.sereno.weather.design.Sereno
import app.sereno.weather.design.Skeleton
import app.sereno.weather.design.Space
import app.sereno.weather.design.Stroke
import app.sereno.weather.design.Touch
import app.sereno.weather.i18n.Copy

/**
 * The header every screen wears.
 *
 * A title, optional subtitle, and up to two actions — set large and left
 * aligned, with no bar, no background and no elevation. The page simply begins.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onTitleClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(Space.sm))
                }
                if (onTitleClick != null) {
                    Pressable(onClick = onTitleClick, pressScale = 0.99f) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SText(title, style = Sereno.type.title, maxLines = 1)
                            Spacer(Modifier.width(Space.xs))
                            SGlyph(Glyph.ChevronDown, size = 15.dp, emphasis = Emphasis.tertiary)
                        }
                    }
                } else {
                    SText(title, style = Sereno.type.title, maxLines = 1)
                }
            }
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                SText(subtitle, style = Sereno.type.caption, emphasis = Emphasis.tertiary, maxLines = 1)
            }
        }
        if (actions != null) {
            Spacer(Modifier.width(Space.md))
            actions()
        }
    }
}

/** A tappable icon with a correctly sized touch target and no ripple. */
@Composable
fun IconAction(
    glyph: Glyph,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: Float = Emphasis.secondary,
) {
    Pressable(
        onClick = onClick,
        modifier = modifier.size(Touch.compact),
        contentAlignment = Alignment.Center,
        onClickLabel = contentDescription,
    ) {
        SGlyph(glyph, size = 20.dp, emphasis = emphasis, contentDescription = contentDescription)
    }
}

/**
 * The empty / error / permission state.
 *
 * Sereno has several of these and they are all this one component, because the
 * moment they diverge is the moment one of them stops looking designed. The
 * glyph is quiet, the title carries the meaning, and the action is optional.
 */
@Composable
fun MessageState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.sectionGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (glyph != null) {
            SGlyph(glyph, size = 26.dp, emphasis = Emphasis.quaternary)
            Spacer(Modifier.height(Space.xl))
        }
        SText(title, style = Sereno.type.headline, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.sm))
        SText(
            body,
            style = Sereno.type.body,
            emphasis = Emphasis.tertiary,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.xl))
            TextButton(actionLabel, onAction)
        }
    }
}

/**
 * A text button.
 *
 * Underlined rather than boxed: a filled or outlined rectangle would read as a
 * Material button, and in a design this typographic a rule under a word is a
 * stronger affordance than a container anyway.
 */
@Composable
fun TextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: Float = Emphasis.primary,
) {
    Pressable(
        onClick = onClick,
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // IntrinsicSize.Max sizes the column to the text, so the rule
        // underneath is exactly as wide as the word it underlines.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .padding(vertical = Space.sm),
        ) {
            SText(label, style = Sereno.type.bodyStrong, emphasis = emphasis)
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(Stroke.hairline)
                    .background(Sereno.atmosphere.ink(Emphasis.quaternary)),
            )
        }
    }
}

/** The skeleton shown on a genuinely cold start, shaped like the Today screen. */
@Composable
fun TodaySkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Skeleton(Modifier.width(160.dp).height(26.dp))
        Spacer(Modifier.height(Space.blockGap))
        Skeleton(Modifier.width(180.dp).height(86.dp))
        Skeleton(Modifier.width(130.dp).height(20.dp))
        Spacer(Modifier.height(Space.blockGap))
        Skeleton(Modifier.fillMaxWidth().height(180.dp))
        Spacer(Modifier.height(Space.lg))
        repeat(4) {
            Skeleton(Modifier.fillMaxWidth().height(30.dp))
        }
    }
}

@Composable
fun FullScreenBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
