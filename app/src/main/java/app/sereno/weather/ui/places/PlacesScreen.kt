package app.sereno.weather.ui.places

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.sereno.weather.design.Emphasis
import app.sereno.weather.design.GapSection
import app.sereno.weather.design.Glyph
import app.sereno.weather.design.Pressable
import app.sereno.weather.design.Radius
import app.sereno.weather.design.SGlyph
import app.sereno.weather.design.SText
import app.sereno.weather.design.SectionLabel
import app.sereno.weather.design.Sereno
import app.sereno.weather.design.Skeleton
import app.sereno.weather.design.Space
import app.sereno.weather.design.Stroke
import app.sereno.weather.design.Touch
import app.sereno.weather.design.WeatherGlyph
import app.sereno.weather.domain.model.Place
import app.sereno.weather.i18n.Copy
import app.sereno.weather.ui.AppState
import app.sereno.weather.ui.Formatter
import app.sereno.weather.ui.LocationPermissionState
import app.sereno.weather.ui.components.IconAction
import app.sereno.weather.ui.components.MessageState
import app.sereno.weather.ui.components.ScreenHeader
import app.sereno.weather.ui.components.SectionRule

/**
 * Places: search, saved list, and the GPS entry.
 *
 * Search results replace the saved list rather than appearing beneath it, so
 * the screen is only ever doing one thing. The saved rows carry each place's
 * last *cached* temperature — opening this screen must never fire a forecast
 * request per city.
 */
@Composable
fun PlacesScreen(
    state: AppState,
    formatter: Formatter,
    onClose: () -> Unit,
    onSearch: (String) -> Unit,
    onSelect: (String) -> Unit,
    onAdd: (Place) -> Unit,
    onRemove: (String) -> Unit,
    onRequestLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = state.copy
    val scrollState = rememberScrollState()
    val keyboard = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = Space.pageMargin),
    ) {
        Spacer(Modifier.height(Space.sm))
        ScreenHeader(
            title = copy.places,
            actions = { IconAction(Glyph.Close, copy.close, onClose) },
        )

        Spacer(Modifier.height(Space.xl))
        SearchField(
            query = state.searchQuery,
            placeholder = copy.searchPlaceholder,
            onQueryChange = onSearch,
            onSubmit = { keyboard?.hide() },
        )

        if (state.searchQuery.isNotBlank()) {
            Spacer(Modifier.height(Space.xl))
            SearchResults(state, copy, onAdd)
        } else {
            Spacer(Modifier.height(Space.sectionGap))
            SavedPlaces(state, formatter, copy, onSelect, onRemove, onRequestLocation)
        }

        Spacer(Modifier.height(Space.railClearance))
    }
}

/**
 * The search field.
 *
 * A hairline-bordered rectangle with a leading glyph — no filled container, no
 * floating label, and a square corner, so it belongs to this design rather than
 * to the platform's.
 */
@Composable
private fun SearchField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val atmosphere = Sereno.atmosphere
    val type = Sereno.type

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Touch.min)
            .clip(RoundedCornerShape(Radius.chip))
            .background(atmosphere.ink(0.05f))
            .border(Stroke.hairline, atmosphere.ink(Emphasis.hairline), RoundedCornerShape(Radius.chip))
            .padding(horizontal = Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SGlyph(Glyph.Search, size = 17.dp, emphasis = Emphasis.tertiary)
        Spacer(Modifier.width(Space.md))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                SText(placeholder, style = type.body, emphasis = Emphasis.tertiary)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = type.body.copy(color = atmosphere.ink),
                singleLine = true,
                cursorBrush = SolidColor(atmosphere.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = placeholder },
            )
        }
        if (query.isNotEmpty()) {
            IconAction(Glyph.Close, "clear", { onQueryChange("") }, emphasis = Emphasis.tertiary)
        }
    }
}

@Composable
private fun SearchResults(state: AppState, copy: Copy, onAdd: (Place) -> Unit) {
    if (state.searching && state.searchResults.isEmpty()) {
        repeat(4) {
            Skeleton(Modifier.fillMaxWidth().height(44.dp))
            Spacer(Modifier.height(Space.md))
        }
        return
    }
    if (state.searchResults.isEmpty()) {
        MessageState(
            title = copy.noResults,
            body = copy.t(
                "Prova con un nome diverso, o controlla l'ortografia.",
                "Try a different name, or check the spelling.",
            ),
            glyph = Glyph.Search,
        )
        return
    }

    val alreadySaved = state.places.map { it.id }.toSet()
    SectionLabel(copy.t("Risultati", "Results"))
    Spacer(Modifier.height(Space.sm))

    state.searchResults.forEachIndexed { index, place ->
        if (index > 0) SectionRule()
        val saved = place.id in alreadySaved
        Pressable(
            onClick = { if (!saved) onAdd(place) },
            enabled = !saved,
            modifier = Modifier.fillMaxWidth(),
            pressScale = 0.995f,
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = Touch.min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    SText(place.name, style = Sereno.type.body, maxLines = 1)
                    place.subtitle?.let {
                        SText(it, style = Sereno.type.caption, emphasis = Emphasis.tertiary, maxLines = 1)
                    }
                }
                SGlyph(
                    glyph = if (saved) Glyph.Check else Glyph.Plus,
                    size = 17.dp,
                    emphasis = if (saved) Emphasis.quaternary else Emphasis.secondary,
                    contentDescription = if (saved) copy.done else copy.add,
                )
            }
        }
    }
}

@Composable
private fun SavedPlaces(
    state: AppState,
    formatter: Formatter,
    copy: Copy,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRequestLocation: () -> Unit,
) {
    val current = state.currentLocationPlace

    if (current != null) {
        SectionLabel(copy.t("Posizione", "Location"))
        Spacer(Modifier.height(Space.sm))
        PlaceRow(
            place = current,
            state = state,
            formatter = formatter,
            copy = copy,
            selected = state.selectedPlaceId == current.id,
            onSelect = onSelect,
            onRemove = null,
        )
        GapSection()
    } else if (state.locationState == LocationPermissionState.Denied ||
        state.locationState == LocationPermissionState.Disabled
    ) {
        MessageState(
            title = copy.locationDeniedTitle,
            body = copy.locationDeniedBody,
            glyph = Glyph.Pin,
            actionLabel = copy.enableLocation,
            onAction = onRequestLocation,
        )
        GapSection()
    }

    SectionLabel(copy.t("Salvati", "Saved"))
    Spacer(Modifier.height(Space.sm))

    if (state.places.isEmpty()) {
        MessageState(
            title = copy.noPlacesTitle,
            body = copy.noPlacesBody,
            glyph = Glyph.Search,
        )
        return
    }

    state.places.forEachIndexed { index, place ->
        if (index > 0) SectionRule()
        PlaceRow(
            place = place,
            state = state,
            formatter = formatter,
            copy = copy,
            selected = state.selectedPlaceId == place.id,
            onSelect = onSelect,
            onRemove = onRemove,
        )
    }
}

@Composable
private fun PlaceRow(
    place: Place,
    state: AppState,
    formatter: Formatter,
    copy: Copy,
    selected: Boolean,
    onSelect: (String) -> Unit,
    onRemove: ((String) -> Unit)?,
) {
    val summary = state.placeSummaries[place.id]
    val current = summary?.current
    val hour = summary?.hours?.firstOrNull()
    val temperature = current?.temperature ?: hour?.temperature
    val code = current?.weatherCode ?: hour?.weatherCode

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { contentDescription = place.name },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pressable(
            onClick = { onSelect(place.id) },
            modifier = Modifier.weight(1f),
            pressScale = 0.995f,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (place.isCurrentLocation) {
                    SGlyph(Glyph.Pin, size = 14.dp, emphasis = Emphasis.tertiary)
                    Spacer(Modifier.width(Space.sm))
                }
                Column(Modifier.weight(1f)) {
                    SText(
                        text = place.name,
                        style = if (selected) Sereno.type.bodyStrong else Sereno.type.body,
                        maxLines = 1,
                    )
                    place.subtitle?.let {
                        SText(it, style = Sereno.type.caption, emphasis = Emphasis.tertiary, maxLines = 1)
                    }
                }
                if (code != null) {
                    WeatherGlyph(code = code, isDay = current?.isDay ?: true, size = 21.dp)
                    Spacer(Modifier.width(Space.md))
                }
                SText(
                    text = formatter.temperature(temperature),
                    style = Sereno.type.data,
                    emphasis = if (temperature == null) Emphasis.quaternary else Emphasis.primary,
                )
            }
        }
        if (onRemove != null) {
            Spacer(Modifier.width(Space.sm))
            IconAction(
                glyph = Glyph.Close,
                contentDescription = "${copy.remove} ${place.name}",
                onClick = { onRemove(place.id) },
                emphasis = Emphasis.quaternary,
            )
        }
    }
}
