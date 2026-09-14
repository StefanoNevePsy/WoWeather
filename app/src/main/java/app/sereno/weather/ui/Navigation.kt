package app.sereno.weather.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import app.sereno.weather.design.Space
import app.sereno.weather.design.Stroke
import app.sereno.weather.i18n.Copy
import kotlin.math.roundToInt

/** The four places the rail can take you. */
enum class Tab { Today, Days, Models, Map;

    fun label(copy: Copy): String = when (this) {
        Today -> copy.today
        Days -> copy.days
        Models -> copy.models
        Map -> copy.map
    }
}

/** Screens that arrive over the top of a tab rather than replacing it. */
sealed interface Overlay {
    data class DayDetail(val dayEpoch: Long) : Overlay
    data object Places : Overlay
    data object Settings : Overlay
    data object Debug : Overlay
}

class Navigator {
    var tab by mutableStateOf(Tab.Today)
        private set

    val overlays = mutableStateListOf<Overlay>()

    val current: Overlay? get() = overlays.lastOrNull()

    fun select(tab: Tab) {
        if (this.tab == tab) return
        overlays.clear()
        this.tab = tab
    }

    fun push(overlay: Overlay) {
        if (overlays.lastOrNull() == overlay) return
        overlays.add(overlay)
    }

    fun pop(): Boolean {
        if (overlays.isEmpty()) return false
        overlays.removeAt(overlays.lastIndex)
        return true
    }
}

@Composable
fun rememberNavigator(): Navigator = remember { Navigator() }

/**
 * The navigation rail.
 *
 * Text only, in the app's label style — no icons, no pill, no elevation, and
 * not the full width of the screen. It reads as a control the interface owns
 * rather than a system component bolted underneath it, and leaving out icons
 * means four words can be set at a size that is genuinely comfortable to read.
 *
 * The active state is carried by a low-contrast plate that slides between
 * positions, which gives the switch a sense of continuity that a cross-fade
 * between two highlighted labels does not.
 */
@Composable
fun SerenoRail(
    selected: Tab,
    copy: Copy,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val atmosphere = Sereno.atmosphere
    val motion = Sereno.motion
    val tabs = Tab.entries

    var railWidth by remember { mutableStateOf(0) }
    val indicatorPosition by animateFloatAsState(
        targetValue = tabs.indexOf(selected).toFloat(),
        animationSpec = motion.settle(),
        label = "railIndicator",
    )

    Box(
        modifier = modifier
            .wrapContentWidth()
            .clip(RoundedCornerShape(Radius.chip))
            .background(atmosphere.ink(0.055f))
            .border(Stroke.hairline, atmosphere.ink(Emphasis.hairline), RoundedCornerShape(Radius.chip))
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .onSizeChanged { railWidth = it.width },
        ) {
            val density = LocalDensity.current
            val slotWidth = with(density) { (railWidth / tabs.size.toFloat()).toDp() }
            if (railWidth > 0) {
                Box(
                    Modifier
                        .offset(x = slotWidth * indicatorPosition)
                        .width(slotWidth)
                        .height(36.dp)
                        .clip(RoundedCornerShape(Radius.hair))
                        .background(atmosphere.ink(0.10f)),
                )
            }

            Row(Modifier.fillMaxWidth()) {
                tabs.forEach { tab ->
                    val active = tab == selected
                    val label = tab.label(copy)
                    Pressable(
                        onClick = { onSelect(tab) },
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                        pressScale = 1f,
                    ) {
                        SText(
                            text = label.uppercase(),
                            style = Sereno.type.label,
                            emphasis = if (active) Emphasis.primary else Emphasis.tertiary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tab content switching.
 *
 * A short fade with a few pixels of vertical travel. Screens in Sereno are
 * siblings, not a hierarchy, so there is no left/right slide implying depth
 * that does not exist.
 */
@Composable
fun TabHost(
    tab: Tab,
    modifier: Modifier = Modifier,
    content: @Composable (Tab) -> Unit,
) {
    val motion = Sereno.motion
    AnimatedContent(
        targetState = tab,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            val travel = motion.enterTravel.roundToInt() * 2
            (fadeIn(motion.fade(300)) + slideInVertically(motion.settle()) { travel }) togetherWith
                fadeOut(motion.fade(160))
        },
        label = "tabHost",
    ) { current -> content(current) }
}

/**
 * Overlay presentation: a sheet rising over the current tab.
 *
 * Overlays *are* hierarchical — a day detail is inside the day list — so here
 * the vertical movement is meaningful and is kept.
 */
@Composable
fun OverlayHost(
    overlay: Overlay?,
    modifier: Modifier = Modifier,
    content: @Composable (Overlay) -> Unit,
) {
    val motion = Sereno.motion
    AnimatedVisibility(
        visible = overlay != null,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(motion.fade(220)) + slideInVertically(motion.settle()) { it / 10 },
        exit = fadeOut(motion.fade(180)) + slideOutVertically(motion.page()) { it / 12 },
    ) {
        // Keep rendering the last overlay through the exit animation so it
        // fades out showing its own content rather than blank.
        var last by remember { mutableStateOf(overlay) }
        if (overlay != null) last = overlay
        last?.let { content(it) }
    }
}

/** A full-bleed scrim used behind overlays. */
@Composable
fun OverlayScrim(modifier: Modifier = Modifier) {
    val atmosphere = Sereno.atmosphere
    Box(
        modifier
            .fillMaxSize()
            .background(
                if (atmosphere.dark) Color.Black.copy(alpha = 0.34f)
                else Color.Black.copy(alpha = 0.16f),
            ),
    )
}
