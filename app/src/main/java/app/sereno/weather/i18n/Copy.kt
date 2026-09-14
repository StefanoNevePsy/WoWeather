package app.sereno.weather.i18n

import app.sereno.weather.domain.model.AlertKind
import app.sereno.weather.domain.model.AlertSeverity
import app.sereno.weather.domain.model.Condition
import app.sereno.weather.domain.model.ConfidenceBand
import app.sereno.weather.domain.model.ConfidenceFactor
import app.sereno.weather.domain.model.Provenance

enum class Lang { It, En;
    companion object {
        fun fromTag(tag: String): Lang = if (tag.lowercase().startsWith("it")) It else En
    }
}

/**
 * All user-visible text, in one place, in both languages.
 *
 * Sereno keeps copy in Kotlin rather than in string resources because a large
 * part of it is *generated* — the narrative line, the confidence explanation,
 * the nowcast phrasing — and splitting generated sentences from static labels
 * across two systems is how translations drift apart. One `Copy` instance is
 * provided through the composition and everything reads from it.
 */
class Copy(val lang: Lang) {

    /** Picks the Italian or English variant. Public so generated copy can use it too. */
    fun t(it: String, en: String): String = if (lang == Lang.It) it else en

    // --- navigation & screens ------------------------------------------------
    val today get() = t("Oggi", "Today")
    val days get() = t("Giorni", "Days")
    val models get() = t("Modelli", "Models")
    val map get() = t("Mappa", "Map")
    val places get() = t("Luoghi", "Places")
    val settings get() = t("Impostazioni", "Settings")
    val forecastLab get() = t("Forecast Lab", "Forecast Lab")
    val developer get() = t("Sviluppatore", "Developer")

    // --- general -------------------------------------------------------------
    val now get() = t("Adesso", "Now")
    val todayLabel get() = t("OGGI", "TODAY")
    val tomorrow get() = t("Domani", "Tomorrow")
    val yesterday get() = t("Ieri", "Yesterday")
    val retry get() = t("Riprova", "Retry")
    val cancel get() = t("Annulla", "Cancel")
    val done get() = t("Fatto", "Done")
    val close get() = t("Chiudi", "Close")
    val search get() = t("Cerca", "Search")
    val noData get() = "—"
    val loading get() = t("Caricamento", "Loading")
    val updatedAt get() = t("Aggiornato", "Updated")
    val justNow get() = t("adesso", "just now")
    val refresh get() = t("Aggiorna", "Refresh")
    val add get() = t("Aggiungi", "Add")
    val remove get() = t("Rimuovi", "Remove")
    val on get() = t("Attivo", "On")
    val off get() = t("Disattivo", "Off")

    // --- conditions ----------------------------------------------------------
    val feelsLike get() = t("Percepita", "Feels like")
    val wind get() = t("Vento", "Wind")
    val gusts get() = t("Raffiche", "Gusts")
    val humidity get() = t("Umidità", "Humidity")
    val uvIndex get() = t("Indice UV", "UV index")
    val uvShort get() = t("UV", "UV")
    val pressure get() = t("Pressione", "Pressure")
    val visibility get() = t("Visibilità", "Visibility")
    val airQuality get() = t("Qualità aria", "Air quality")
    val dewPoint get() = t("Punto di rugiada", "Dew point")
    val cloudCover get() = t("Nuvolosità", "Cloud cover")
    val precipitation get() = t("Precipitazioni", "Precipitation")
    val probability get() = t("Probabilità", "Probability")
    val sunrise get() = t("Alba", "Sunrise")
    val sunset get() = t("Tramonto", "Sunset")
    val high get() = t("Max", "High")
    val low get() = t("Min", "Low")
    val temperature get() = t("Temperatura", "Temperature")
    val snow get() = t("Neve", "Snow")

    fun conditionName(condition: Condition, intensity: Int = 0): String = when (condition) {
        Condition.Clear -> t("Sereno", "Clear")
        Condition.MainlyClear -> t("Prevalentemente sereno", "Mainly clear")
        Condition.PartlyCloudy -> t("Parzialmente nuvoloso", "Partly cloudy")
        Condition.Overcast -> t("Coperto", "Overcast")
        Condition.Fog -> t("Nebbia", "Fog")
        Condition.RimeFog -> t("Nebbia gelata", "Freezing fog")
        Condition.Drizzle -> when (intensity) {
            0 -> t("Pioviggine", "Light drizzle")
            1 -> t("Pioviggine", "Drizzle")
            else -> t("Pioviggine intensa", "Heavy drizzle")
        }
        Condition.FreezingDrizzle -> t("Pioviggine gelata", "Freezing drizzle")
        Condition.Rain -> when (intensity) {
            0 -> t("Pioggia debole", "Light rain")
            1 -> t("Pioggia", "Rain")
            else -> t("Pioggia intensa", "Heavy rain")
        }
        Condition.FreezingRain -> t("Pioggia gelata", "Freezing rain")
        Condition.RainShowers -> when (intensity) {
            0 -> t("Rovesci deboli", "Light showers")
            1 -> t("Rovesci", "Showers")
            else -> t("Rovesci intensi", "Heavy showers")
        }
        Condition.Snow -> when (intensity) {
            0 -> t("Neve debole", "Light snow")
            1 -> t("Neve", "Snow")
            else -> t("Neve intensa", "Heavy snow")
        }
        Condition.SnowGrains -> t("Nevischio", "Snow grains")
        Condition.SnowShowers -> t("Rovesci di neve", "Snow showers")
        Condition.Thunderstorm -> t("Temporale", "Thunderstorm")
        Condition.ThunderstormHail -> t("Temporale con grandine", "Thunderstorm with hail")
        Condition.Unknown -> t("Non disponibile", "Unavailable")
    }

    // --- confidence ----------------------------------------------------------
    val forecastConfidence get() = t("Affidabilità previsione", "Forecast confidence")
    val confidence get() = t("Affidabilità", "Confidence")

    fun confidenceBand(band: ConfidenceBand): String = when (band) {
        ConfidenceBand.VeryHigh -> t("Molto alta", "Very high")
        ConfidenceBand.High -> t("Alta", "High")
        ConfidenceBand.Moderate -> t("Media", "Moderate")
        ConfidenceBand.Low -> t("Bassa", "Low")
        ConfidenceBand.VeryLow -> t("Molto bassa", "Very low")
    }

    fun confidenceFactor(factor: ConfidenceFactor): String = when (factor) {
        ConfidenceFactor.TemperatureSpread -> t("Spread temperatura", "Temperature spread")
        ConfidenceFactor.PrecipitationAgreement -> t("Accordo sulle precipitazioni", "Precipitation agreement")
        ConfidenceFactor.AmountSpread -> t("Spread accumuli", "Amount spread")
        ConfidenceFactor.WindSpread -> t("Spread vento", "Wind spread")
        ConfidenceFactor.ModelCount -> t("Modelli disponibili", "Models available")
        ConfidenceFactor.LeadTime -> t("Distanza temporale", "Lead time")
    }

    fun provenanceNote(provenance: Provenance): String? = when (provenance) {
        Provenance.Derived -> t("Stimata da Sereno sull'accordo tra modelli", "Derived by Sereno from model agreement")
        Provenance.SingleModel -> t("Da un solo modello", "From a single model")
        Provenance.Missing -> t("Non fornita dai modelli", "Not provided by the models")
        Provenance.Blended -> null
    }

    // --- nowcast -------------------------------------------------------------
    fun rainStartingIn(low: Int, high: Int, snow: Boolean): String {
        val what = if (snow) t("Neve", "Snow") else t("Pioggia", "Rain")
        return if (low == high) t("$what prevista tra circa $low min", "$what expected in about $low min")
        else t("$what prevista tra $low–$high min", "$what expected in $low–$high min")
    }

    fun rainStoppingIn(low: Int, high: Int): String =
        if (low == high) t("Smette tra circa $low min", "Stopping in about $low min")
        else t("Smette tra $low–$high min", "Stopping in $low–$high min")

    fun noRainFor(hours: Int): String =
        t("Nessuna precipitazione prevista nelle prossime $hours ore",
            "No precipitation expected in the next $hours hours")

    fun intermittentFor(hours: Int): String =
        if (hours <= 1) t("Rovesci intermittenti per circa un'ora", "Intermittent showers for about an hour")
        else t("Rovesci intermittenti per circa $hours ore", "Intermittent showers for about $hours hours")

    val rainingNow get() = t("Sta piovendo", "It is raining")
    val snowingNow get() = t("Sta nevicando", "It is snowing")
    val nowcastUnavailable get() = t("Nowcast non disponibile", "Nowcast unavailable")

    // --- severe --------------------------------------------------------------
    fun severity(severity: AlertSeverity): String = when (severity) {
        AlertSeverity.Advisory -> t("Avviso", "Advisory")
        AlertSeverity.Watch -> t("Attenzione", "Watch")
        AlertSeverity.Warning -> t("Allerta", "Warning")
        AlertSeverity.Severe -> t("Allerta grave", "Severe")
    }

    fun alertKind(kind: AlertKind): String = when (kind) {
        AlertKind.Thunderstorm -> t("Temporali", "Thunderstorms")
        AlertKind.Hail -> t("Grandine", "Hail")
        AlertKind.Wind -> t("Vento forte", "Strong wind")
        AlertKind.Rain -> t("Pioggia intensa", "Heavy rain")
        AlertKind.Snow -> t("Neve", "Snow")
        AlertKind.Ice -> t("Gelo", "Ice")
        AlertKind.Heat -> t("Caldo estremo", "Extreme heat")
        AlertKind.Cold -> t("Freddo intenso", "Extreme cold")
        AlertKind.Fog -> t("Nebbia", "Fog")
        AlertKind.Other -> t("Avviso meteo", "Weather advisory")
    }

    // --- empty / error states -------------------------------------------------
    val offlineTitle get() = t("Sei offline", "You are offline")
    val offlineBody get() = t(
        "Mostriamo l'ultima previsione salvata. Si aggiornerà appena torna la rete.",
        "Showing the last saved forecast. It will refresh as soon as you are back online.",
    )
    val errorTitle get() = t("Non siamo riusciti ad aggiornare", "We could not refresh")
    val errorBody get() = t(
        "I modelli non hanno risposto. Riprova tra poco.",
        "The models did not respond. Try again shortly.",
    )
    val noPlacesTitle get() = t("Nessun luogo salvato", "No saved places")
    val noPlacesBody get() = t(
        "Cerca una città per iniziare, oppure attiva la posizione.",
        "Search for a city to begin, or turn on location.",
    )
    val noResults get() = t("Nessun risultato", "No results")
    val locationDeniedTitle get() = t("Posizione non disponibile", "Location unavailable")
    val locationDeniedBody get() = t(
        "Puoi comunque cercare e salvare le tue città.",
        "You can still search for and save your cities.",
    )
    val enableLocation get() = t("Attiva posizione", "Enable location")
    val searchPlaceholder get() = t("Cerca una città", "Search for a city")
    val useCurrentLocation get() = t("Usa la posizione attuale", "Use current location")

    // --- models screen --------------------------------------------------------
    val modelAgreement get() = t("Accordo tra modelli", "Model agreement")
    val strongAgreement get() = t("Forte accordo", "Strong agreement")
    val partialAgreement get() = t("Accordo parziale", "Partial agreement")
    val disagreement get() = t("Disaccordo", "Disagreement")
    val spread get() = t("Spread", "Spread")
    val unavailableHere get() = t("Non disponibile qui", "Not available here")
    val regionalModel get() = t("Modello regionale", "Regional model")
    val globalModel get() = t("Modello globale", "Global model")
    val resolution get() = t("Risoluzione", "Resolution")
    val horizon get() = t("Orizzonte", "Horizon")

    // --- settings -------------------------------------------------------------
    val appearance get() = t("Aspetto", "Appearance")
    val themeSystem get() = t("Automatico", "Automatic")
    val themeLight get() = t("Chiaro", "Light")
    val themeDark get() = t("Scuro", "Dark")
    val units get() = t("Unità", "Units")
    val language get() = t("Lingua", "Language")
    val reduceMotion get() = t("Riduci animazioni", "Reduce motion")
    val notifications get() = t("Notifiche", "Notifications")
    val rainAlerts get() = t("Avvisi di pioggia", "Rain alerts")
    val severeAlerts get() = t("Allerte meteo", "Severe weather alerts")
    val about get() = t("Informazioni", "About")
    val dataSources get() = t("Fonti dati", "Data sources")
    val privacy get() = t("Privacy", "Privacy")
    val privacyBody get() = t(
        "Sereno non ha account, non traccia nulla e non contiene pubblicità. Le tue località restano sul dispositivo.",
        "Sereno has no accounts, tracks nothing and contains no advertising. Your places never leave the device.",
    )

    // --- map -------------------------------------------------------------------
    val precipitationMap get() = t("Mappa precipitazioni", "Precipitation map")
    val radar get() = t("Radar", "Radar")
    val layers get() = t("Livelli", "Layers")
    val play get() = t("Riproduci", "Play")
    val pause get() = t("Pausa", "Pause")
    val radarUnavailable get() = t("Radar non disponibile", "Radar unavailable")

    // --- relative time ----------------------------------------------------------
    fun minutesAgo(minutes: Long): String = when {
        minutes < 1 -> justNow
        minutes == 1L -> t("1 minuto fa", "1 minute ago")
        minutes < 60 -> t("$minutes minuti fa", "$minutes minutes ago")
        minutes < 120 -> t("1 ora fa", "1 hour ago")
        minutes < 1440 -> t("${minutes / 60} ore fa", "${minutes / 60} hours ago")
        else -> t("${minutes / 1440} giorni fa", "${minutes / 1440} days ago")
    }

    fun inMinutes(minutes: Int): String =
        if (minutes < 60) t("tra $minutes min", "in $minutes min")
        else t("tra ${minutes / 60} h", "in ${minutes / 60} h")
}
