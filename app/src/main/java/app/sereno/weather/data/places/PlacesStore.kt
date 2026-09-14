package app.sereno.weather.data.places

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.sereno.weather.domain.model.Place
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.placesStore: DataStore<Preferences> by preferencesDataStore(name = "sereno_places")

/**
 * The user's saved places, in the order they arranged them.
 *
 * Order is the list order — there is no sort key — because the user drags rows
 * to reorder and any secondary sort would fight that.
 */
class PlacesStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = stringPreferencesKey("places_json")
    private val selectedKey = stringPreferencesKey("selected_place_id")
    private val serializer = ListSerializer(Place.serializer())

    val places: Flow<List<Place>> = context.placesStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            prefs[key]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
        }

    val selectedPlaceId: Flow<String?> = context.placesStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it[selectedKey] }

    suspend fun save(list: List<Place>) {
        context.placesStore.edit { it[key] = json.encodeToString(serializer, list) }
    }

    suspend fun add(place: Place) {
        val current = places.first()
        if (current.any { it.id == place.id }) return
        save(current + place)
    }

    suspend fun remove(placeId: String) {
        save(places.first().filterNot { it.id == placeId })
    }

    suspend fun move(from: Int, to: Int) {
        val current = places.first().toMutableList()
        if (from !in current.indices || to !in current.indices) return
        current.add(to, current.removeAt(from))
        save(current)
    }

    suspend fun select(placeId: String) {
        context.placesStore.edit { it[selectedKey] = placeId }
    }
}
