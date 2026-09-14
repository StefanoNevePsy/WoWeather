package app.sereno.weather.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.sereno.weather.core.Container
import app.sereno.weather.data.ForecastResource
import app.sereno.weather.data.location.LocationResult
import app.sereno.weather.data.prefs.SerenoSettings
import app.sereno.weather.data.provider.RadarStatus
import app.sereno.weather.design.Mood
import app.sereno.weather.design.ThemeMode
import app.sereno.weather.domain.mock.MockWeather
import app.sereno.weather.domain.model.ForecastBundle
import app.sereno.weather.domain.model.Place
import app.sereno.weather.domain.model.PrecipUnit
import app.sereno.weather.domain.model.PressureUnit
import app.sereno.weather.domain.model.SpeedUnit
import app.sereno.weather.domain.model.TemperatureUnit
import app.sereno.weather.domain.model.WeatherCodes
import app.sereno.weather.i18n.Copy
import app.sereno.weather.i18n.Lang
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LocationPermissionState { Unknown, Granted, Denied, Disabled, Unavailable }

data class AppState(
    val settings: SerenoSettings = SerenoSettings(),
    val copy: Copy = Copy(Lang.En),
    val places: List<Place> = emptyList(),
    val currentLocationPlace: Place? = null,
    val selectedPlaceId: String? = null,
    val forecast: ForecastResource = ForecastResource.Loading,
    val locationState: LocationPermissionState = LocationPermissionState.Unknown,
    val radar: RadarStatus? = null,
    val searchQuery: String = "",
    val searchResults: List<Place> = emptyList(),
    val searching: Boolean = false,
    /** Cached forecasts for the saved places, so the Places list shows real temperatures. */
    val placeSummaries: Map<String, ForecastBundle> = emptyMap(),
) {
    /** Every place the switcher offers: the GPS entry first, then saved ones. */
    val allPlaces: List<Place>
        get() = listOfNotNull(currentLocationPlace) + places

    val selectedPlace: Place?
        get() = allPlaces.firstOrNull { it.id == selectedPlaceId } ?: allPlaces.firstOrNull()

    val bundle: ForecastBundle?
        get() = when (val resource = forecast) {
            is ForecastResource.Data -> resource.bundle
            is ForecastResource.Failed -> resource.lastKnown
            ForecastResource.Loading -> null
        }

    val refreshing: Boolean
        get() = forecast is ForecastResource.Loading ||
            (forecast as? ForecastResource.Data)?.refreshing == true

    /**
     * The atmosphere the whole app wears.
     *
     * Taken from the current hour rather than from "current conditions", because
     * the two can disagree at dusk and the interface following the forecast
     * hour is what makes the sky change *before* the user notices it has.
     */
    val mood: Mood
        get() {
            val forced = MockWeather.State.fromKey(settings.mockState)
            if (forced != null) return forced.mood
            val current = bundle?.current
            val hour = bundle?.hours?.firstOrNull()
            val code = current?.weatherCode ?: hour?.weatherCode
            val isDay = current?.isDay ?: hour?.isDay ?: true
            return WeatherCodes.mood(code, isDay)
        }
}

/**
 * One view model for the whole app.
 *
 * Sereno's screens all read the same forecast for the same place; splitting
 * that across five view models would mean five copies of the same state and a
 * refresh that only updates one of them. Screen-local concerns (a scrub
 * position, an expanded row) stay in the screens as remembered state.
 */
class SerenoViewModel(private val container: Container, deviceLanguage: Lang) : ViewModel() {

    private val _state = MutableStateFlow(AppState(copy = Copy(deviceLanguage)))
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val deviceLang = deviceLanguage
    private var forecastJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            container.settings.settings.collect { settings ->
                val previous = _state.value
                _state.update {
                    it.copy(settings = settings, copy = Copy(settings.language ?: deviceLang))
                }
                // A change of mock state or language changes what must be shown,
                // so re-derive; a change of units does not need a refetch.
                if (previous.settings.mockState != settings.mockState ||
                    previous.settings.language != settings.language
                ) {
                    loadForecast()
                }
            }
        }
        viewModelScope.launch {
            container.places.places.collect { places ->
                _state.update { it.copy(places = places) }
                refreshPlaceSummaries()
                if (_state.value.selectedPlace == null) loadForecast()
            }
        }
        viewModelScope.launch {
            container.places.selectedPlaceId.collect { id ->
                if (id != null && id != _state.value.selectedPlaceId) {
                    _state.update { it.copy(selectedPlaceId = id) }
                    loadForecast()
                }
            }
        }
        resolveLocation()
        refreshPlaceSummaries()
    }

    /**
     * Reads each saved place's cached forecast so the Places list can show a
     * temperature next to every row. Cache only, never the network: opening a
     * list of ten cities must not fire ten forecast requests.
     */
    private fun refreshPlaceSummaries() {
        viewModelScope.launch {
            val summaries = _state.value.allPlaces.mapNotNull { place ->
                container.repository.cachedOnly(place)?.let { place.id to it }
            }.toMap()
            _state.update { it.copy(placeSummaries = it.placeSummaries + summaries) }
        }
    }

    // -----------------------------------------------------------------------
    // Location
    // -----------------------------------------------------------------------

    fun resolveLocation() {
        viewModelScope.launch {
            if (!container.location.hasPermission()) {
                _state.update { it.copy(locationState = LocationPermissionState.Denied) }
                if (_state.value.selectedPlace != null) loadForecast()
                return@launch
            }
            when (val result = container.location.current()) {
                is LocationResult.Success -> {
                    val label = result.label ?: _state.value.copy.t("Posizione attuale", "Current location")
                    val place = Place.forCurrentLocation(result.coordinates, label)
                    val wasEmpty = _state.value.currentLocationPlace == null
                    _state.update {
                        it.copy(
                            currentLocationPlace = place,
                            locationState = LocationPermissionState.Granted,
                            selectedPlaceId = it.selectedPlaceId ?: place.id,
                        )
                    }
                    if (wasEmpty || _state.value.selectedPlaceId == place.id) loadForecast()
                }
                LocationResult.PermissionDenied ->
                    _state.update { it.copy(locationState = LocationPermissionState.Denied) }
                LocationResult.Disabled ->
                    _state.update { it.copy(locationState = LocationPermissionState.Disabled) }
                LocationResult.Unavailable ->
                    _state.update { it.copy(locationState = LocationPermissionState.Unavailable) }
            }
            if (_state.value.bundle == null) loadForecast()
        }
    }

    // -----------------------------------------------------------------------
    // Forecast
    // -----------------------------------------------------------------------

    fun refresh() = loadForecast(force = true)

    private fun loadForecast(force: Boolean = false) {
        val current = _state.value
        val place = current.selectedPlace ?: return

        // Developer mock states bypass the network but run through the real
        // synthesis pipeline, so what appears on screen is produced by the same
        // code path as live data.
        val mock = MockWeather.State.fromKey(current.settings.mockState)
        if (mock != null) {
            val bundle = MockWeather.bundle(
                state = mock,
                place = place,
                nowEpoch = System.currentTimeMillis() / 1000,
                copy = current.copy,
            )
            forecastJob?.cancel()
            _state.update { it.copy(forecast = ForecastResource.Data(bundle, refreshing = false)) }
            return
        }

        forecastJob?.cancel()
        forecastJob = viewModelScope.launch {
            container.repository.stream(place, current.copy, forceRefresh = force).collect { resource ->
                _state.update { state ->
                    val summaries = (resource as? ForecastResource.Data)
                        ?.let { state.placeSummaries + (place.id to it.bundle) }
                        ?: state.placeSummaries
                    state.copy(forecast = resource, placeSummaries = summaries)
                }
            }
        }
        viewModelScope.launch {
            val status = runCatching { container.repository.radarStatus(place) }.getOrNull()
            _state.update { it.copy(radar = status) }
        }
    }

    fun selectPlace(placeId: String) {
        if (placeId == _state.value.selectedPlaceId) return
        _state.update { it.copy(selectedPlaceId = placeId, forecast = ForecastResource.Loading) }
        viewModelScope.launch { container.places.select(placeId) }
        loadForecast()
    }

    // -----------------------------------------------------------------------
    // Places
    // -----------------------------------------------------------------------

    fun search(query: String) {
        _state.update { it.copy(searchQuery = query, searching = query.isNotBlank()) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            // Enough to let a fast typist finish a word, short enough that the
            // list feels like it is keeping up.
            kotlinx.coroutines.delay(220)
            val results = runCatching {
                container.geocodingProvider.search(query, _state.value.copy.lang.name.lowercase())
            }.getOrDefault(emptyList())
            _state.update {
                if (it.searchQuery == query) it.copy(searchResults = results, searching = false) else it
            }
        }
    }

    fun addPlace(place: Place) {
        viewModelScope.launch {
            container.places.add(place)
            container.places.select(place.id)
            _state.update { it.copy(selectedPlaceId = place.id, searchQuery = "", searchResults = emptyList()) }
            loadForecast()
        }
    }

    fun removePlace(placeId: String) {
        viewModelScope.launch {
            container.places.remove(placeId)
            container.cache.evict(placeId)
            if (_state.value.selectedPlaceId == placeId) {
                val next = _state.value.allPlaces.firstOrNull { it.id != placeId }
                _state.update { it.copy(selectedPlaceId = next?.id) }
                loadForecast()
            }
        }
    }

    fun movePlace(from: Int, to: Int) {
        viewModelScope.launch { container.places.move(from, to) }
    }

    // -----------------------------------------------------------------------
    // Settings
    // -----------------------------------------------------------------------

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { container.settings.setTheme(mode) }
    fun setTemperatureUnit(unit: TemperatureUnit) = viewModelScope.launch { container.settings.setTemperatureUnit(unit) }
    fun setSpeedUnit(unit: SpeedUnit) = viewModelScope.launch { container.settings.setSpeedUnit(unit) }
    fun setPrecipUnit(unit: PrecipUnit) = viewModelScope.launch { container.settings.setPrecipUnit(unit) }
    fun setPressureUnit(unit: PressureUnit) = viewModelScope.launch { container.settings.setPressureUnit(unit) }
    fun setLanguage(lang: Lang?) = viewModelScope.launch { container.settings.setLanguage(lang) }
    fun setReduceMotion(value: Boolean?) = viewModelScope.launch { container.settings.setReduceMotion(value) }
    fun setRainNotifications(value: Boolean) = viewModelScope.launch { container.settings.setRainNotifications(value) }
    fun setSevereNotifications(value: Boolean) = viewModelScope.launch { container.settings.setSevereNotifications(value) }
    fun setDeveloperMode(value: Boolean) = viewModelScope.launch { container.settings.setDeveloperMode(value) }
    fun setMockState(key: String?) = viewModelScope.launch { container.settings.setMockState(key) }

    fun clearCache() = viewModelScope.launch { container.repository.clearCache() }

    companion object {
        fun factory(container: Container, language: Lang) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SerenoViewModel(container, language) as T
        }
    }
}
