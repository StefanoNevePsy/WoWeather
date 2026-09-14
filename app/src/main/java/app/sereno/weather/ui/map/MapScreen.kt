package app.sereno.weather.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.sereno.weather.data.net.Http
import app.sereno.weather.data.provider.RadarStatus
import app.sereno.weather.design.Emphasis
import app.sereno.weather.design.Glyph
import app.sereno.weather.design.Pressable
import app.sereno.weather.design.SGlyph
import app.sereno.weather.design.SText
import app.sereno.weather.design.Sereno
import app.sereno.weather.design.Space
import app.sereno.weather.domain.model.Coordinates
import app.sereno.weather.i18n.Copy
import app.sereno.weather.ui.AppState
import app.sereno.weather.ui.Formatter
import app.sereno.weather.ui.components.SegmentedControl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The map.
 *
 * An honest note about what this shows: it is a **forecast field**, sampled
 * from the models on a grid and interpolated, not a radar image. That trade is
 * deliberate. Radar is sharper about the present but stops there; a model field
 * runs 24 hours forward, which is what makes the timeline at the bottom worth
 * dragging. Where Italy's DPC radar is reachable its status is reported
 * separately rather than being quietly blended in and passed off as the same
 * thing.
 */
@Composable
fun MapScreen(
    state: AppState,
    formatter: Formatter,
    http: Http,
    nowEpoch: Long,
    modifier: Modifier = Modifier,
) {
    val copy = state.copy
    val atmosphere = Sereno.atmosphere
    val motion = Sereno.motion
    val scope = rememberCoroutineScope()

    val tileSource = remember(http) { TileSource(http, scope) }
    val fieldSource = remember(http) { WeatherFieldSource(http) }

    val place = state.selectedPlace
    var camera by remember(place?.id) {
        mutableStateOf(
            MapCamera(
                center = place?.coordinates ?: Coordinates(42.5, 12.5),
                zoom = 7.0,
            ),
        )
    }

    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var metric by remember { mutableStateOf(FieldMetric.Precipitation) }
    var field by remember { mutableStateOf<WeatherField?>(null) }
    var fieldBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var loadingField by remember { mutableStateOf(false) }
    var hourIndex by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var lastFetchedBounds by remember { mutableStateOf<GeoBounds?>(null) }

    // Refetch the field when the viewport has genuinely moved, debounced so a
    // pan costs one request at the end rather than one per frame.
    LaunchedEffect(viewport, metric) {
        snapshotFlow { camera }
            .debounce(500)
            .distinctUntilChanged()
            .collect { current ->
                if (viewport.width == 0 || viewport.height == 0) return@collect
                val bounds = current.bounds(viewport.width.toFloat(), viewport.height.toFloat())
                val previous = lastFetchedBounds
                if (previous != null && !bounds.differsMateriallyFrom(previous)) return@collect
                loadingField = true
                val fetched = fieldSource.fetch(bounds, metric)
                if (fetched != null) {
                    field = fetched
                    lastFetchedBounds = bounds
                    hourIndex = hourIndex.coerceIn(0, fetched.hourCount - 1)
                }
                loadingField = false
            }
    }

    // Re-render only when the hour or the field changes; this is the expensive
    // step, so it must not run on every pan frame.
    LaunchedEffect(field, hourIndex) {
        val current = field
        fieldBitmap = if (current == null) null else renderField(current, hourIndex)
    }

    LaunchedEffect(playing, field) {
        if (!playing) return@LaunchedEffect
        val count = field?.hourCount ?: return@LaunchedEffect
        while (true) {
            delay(if (motion.reduceMotion) 900 else 420)
            hourIndex = (hourIndex + 1) % count
        }
    }

    Box(modifier.fillMaxSize()) {

        // --- the map itself -------------------------------------------------
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it }
                .clearAndSetSemantics { }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        var next = camera.panBy(pan)
                        if (zoom != 1f) next = next.zoomBy(zoom)
                        camera = next
                    }
                },
        ) {
            drawRect(atmosphere.skyMid)

            val tiles = visibleTiles(camera, size.width, size.height)
            val tileSize = tileScreenSize(camera)
            tiles.forEach { (key, position) ->
                val bitmap = tileSource.tile(key, atmosphere.dark)
                if (bitmap != null) {
                    drawImage(
                        image = bitmap,
                        dstOffset = IntOffset(position.x.roundToInt(), position.y.roundToInt()),
                        dstSize = IntSize(tileSize.roundToInt() + 1, tileSize.roundToInt() + 1),
                        filterQuality = FilterQuality.Medium,
                    )
                }
            }

            // The forecast field, projected onto the box it was sampled over.
            val current = field
            val bitmap = fieldBitmap
            if (current != null && bitmap != null) {
                val topLeft = camera.project(
                    Coordinates(current.bounds.north, current.bounds.west),
                    size.width, size.height,
                )
                val bottomRight = camera.project(
                    Coordinates(current.bounds.south, current.bounds.east),
                    size.width, size.height,
                )
                val width = (bottomRight.x - topLeft.x).roundToInt()
                val height = (bottomRight.y - topLeft.y).roundToInt()
                if (width > 0 && height > 0) {
                    drawImage(
                        image = bitmap,
                        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                        dstSize = IntSize(width, height),
                        filterQuality = FilterQuality.High,
                        alpha = 0.92f,
                    )
                }
            }

            // The selected place.
            if (place != null) {
                val position = camera.project(place.coordinates, size.width, size.height)
                drawCircle(Color.White.copy(alpha = 0.9f), radius = 6.dp.toPx(), center = position)
                drawCircle(atmosphere.accent, radius = 4.dp.toPx(), center = position)
                drawCircle(
                    color = atmosphere.accent.copy(alpha = 0.35f),
                    radius = 11.dp.toPx(),
                    center = position,
                    style = Stroke(width = 1.2.dp.toPx()),
                )
            }
        }

        // --- top controls ----------------------------------------------------
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(atmosphere.skyTop.copy(alpha = 0.92f), Color.Transparent),
                    ),
                )
                .statusBarsPadding()
                .padding(horizontal = Space.pageMargin)
                .padding(top = Space.sm, bottom = Space.xl),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SText(copy.precipitationMap, style = Sereno.type.headline, maxLines = 1)
                    SText(
                        text = radarNote(state.radar, copy, formatter),
                        style = Sereno.type.caption,
                        emphasis = Emphasis.tertiary,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(Space.md))
                Pressable(
                    onClick = {
                        place?.let { camera = camera.copy(center = it.coordinates, zoom = 7.0) }
                    },
                    modifier = Modifier.size(app.sereno.weather.design.Touch.compact),
                    contentAlignment = Alignment.Center,
                    onClickLabel = copy.useCurrentLocation,
                ) {
                    SGlyph(Glyph.Pin, size = 19.dp, contentDescription = copy.useCurrentLocation)
                }
            }

            Spacer(Modifier.height(Space.md))
            SegmentedControl(
                options = FieldMetric.entries,
                selected = metric,
                label = { metricLabel(it, copy) },
                onSelect = { metric = it; lastFetchedBounds = null },
            )
        }

        // --- bottom controls -------------------------------------------------
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, atmosphere.skyLow.copy(alpha = 0.94f)),
                    ),
                )
                .padding(horizontal = Space.pageMargin)
                .padding(top = Space.sectionGap, bottom = Space.railClearance),
        ) {
            FieldLegend(metric, formatter, copy)

            Spacer(Modifier.height(Space.lg))

            val current = field
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Pressable(
                    onClick = { playing = !playing },
                    enabled = current != null,
                    modifier = Modifier.size(app.sereno.weather.design.Touch.compact),
                    contentAlignment = Alignment.Center,
                    onClickLabel = if (playing) copy.pause else copy.play,
                ) {
                    SGlyph(
                        glyph = if (playing) Glyph.Pause else Glyph.Play,
                        size = 18.dp,
                        emphasis = if (current == null) Emphasis.quaternary else Emphasis.primary,
                    )
                }
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    SText(
                        text = when {
                            loadingField && current == null -> copy.loading
                            current == null -> copy.t("Nessun dato per quest'area", "No data for this area")
                            else -> {
                                val epoch = current.startEpoch + hourIndex * 3600L
                                "${formatter.weekdayShort(epoch)} ${formatter.time(epoch)}"
                            }
                        },
                        style = Sereno.type.data,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(Space.sm))
                    Timeline(
                        hourCount = current?.hourCount ?: 0,
                        index = hourIndex,
                        nowIndex = current?.let {
                            ((nowEpoch - it.startEpoch) / 3600).toInt().coerceIn(0, it.hourCount - 1)
                        },
                        onSelect = { hourIndex = it; playing = false },
                    )
                }
            }

            Spacer(Modifier.height(Space.md))
            SText(
                text = "${TileSource.ATTRIBUTION} · ${copy.t("campo previsto da Open-Meteo", "forecast field by Open-Meteo")}",
                style = Sereno.type.caption,
                emphasis = Emphasis.quaternary,
                maxLines = 1,
            )
        }
    }
}

private fun metricLabel(metric: FieldMetric, copy: Copy): String = when (metric) {
    FieldMetric.Precipitation -> copy.t("Pioggia", "Rain")
    FieldMetric.Cloud -> copy.t("Nuvole", "Cloud")
    FieldMetric.Wind -> copy.t("Raffiche", "Gusts")
}

private fun radarNote(status: RadarStatus?, copy: Copy, formatter: Formatter): String = when (status) {
    is RadarStatus.Available -> copy.t(
        "Radar DPC disponibile · ${formatter.time(status.latestEpoch)}",
        "DPC radar available · ${formatter.time(status.latestEpoch)}",
    )
    is RadarStatus.OutOfCoverage -> copy.t(
        "Campo previsto dai modelli",
        "Model forecast field",
    )
    is RadarStatus.Unavailable, null -> copy.t(
        "Campo previsto dai modelli",
        "Model forecast field",
    )
}

/**
 * The timeline.
 *
 * A row of hour ticks with the current position marked and "now" called out.
 * Drawn rather than assembled from widgets because at 24 ticks it needs to be
 * one continuous object the finger can sweep along, not twenty-four targets.
 */
@Composable
private fun Timeline(
    hourCount: Int,
    index: Int,
    nowIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val atmosphere = Sereno.atmosphere
    if (hourCount <= 1) {
        Box(modifier.fillMaxWidth().height(26.dp))
        return
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(26.dp)
            .pointerInput(hourCount) {
                detectTapGestures { offset ->
                    val step = size.width / hourCount.toFloat()
                    onSelect((offset.x / step).toInt().coerceIn(0, hourCount - 1))
                }
            }
            .pointerInput(hourCount) {
                // Sweeping along the timeline scrubs it; this is the gesture
                // people reach for before they think to tap a specific tick.
                detectDragGestures { change, _ ->
                    val step = size.width / hourCount.toFloat()
                    onSelect((change.position.x / step).toInt().coerceIn(0, hourCount - 1))
                }
            },
    ) {
        val step = size.width / hourCount.toFloat()
        val midY = size.height / 2f

        repeat(hourCount) { hour ->
            val x = step * (hour + 0.5f)
            val isNow = hour == nowIndex
            val selected = hour == index
            val height = when {
                selected -> size.height * 0.72f
                isNow -> size.height * 0.5f
                hour % 6 == 0 -> size.height * 0.42f
                else -> size.height * 0.24f
            }
            drawLine(
                color = when {
                    selected -> atmosphere.accent
                    isNow -> atmosphere.ink(Emphasis.secondary)
                    else -> atmosphere.ink(Emphasis.quaternary)
                },
                start = Offset(x, midY - height / 2f),
                end = Offset(x, midY + height / 2f),
                strokeWidth = if (selected) 2.4.dp.toPx() else 1.4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

/** The colour scale, with its real units. */
@Composable
private fun FieldLegend(metric: FieldMetric, formatter: Formatter, copy: Copy) {
    val stops = remember(metric) { FieldPalette.legend(metric) }
    val unit = when (metric) {
        FieldMetric.Precipitation -> "mm/h"
        FieldMetric.Cloud -> "%"
        FieldMetric.Wind -> "km/h"
    }

    Column(Modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clearAndSetSemantics { },
        ) {
            val step = size.width / stops.size
            stops.forEachIndexed { index, (_, color) ->
                drawRect(
                    color = Color(color),
                    topLeft = Offset(step * index, 0f),
                    size = Size(step, size.height),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            stops.forEach { (value, _) ->
                Box(Modifier.weight(1f)) {
                    SText(
                        text = if (value < 1f) "%.1f".format(value) else value.roundToInt().toString(),
                        style = Sereno.type.tick,
                        emphasis = Emphasis.tertiary,
                    )
                }
            }
            SText(unit, style = Sereno.type.tick, emphasis = Emphasis.quaternary)
        }
    }
}
