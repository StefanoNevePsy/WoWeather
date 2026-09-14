package app.sereno.weather.ui.days

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sereno.weather.design.Emphasis
import app.sereno.weather.design.GapSection
import app.sereno.weather.design.SText
import app.sereno.weather.design.SectionLabel
import app.sereno.weather.design.Sereno
import app.sereno.weather.design.Skeleton
import app.sereno.weather.design.Space
import app.sereno.weather.i18n.Copy
import app.sereno.weather.ui.AppState
import app.sereno.weather.ui.Formatter
import app.sereno.weather.ui.chart.DailyTrendChart
import app.sereno.weather.ui.components.DailyRow
import app.sereno.weather.ui.components.ScreenHeader
import app.sereno.weather.ui.components.SectionRule

/**
 * The extended forecast.
 *
 * The trend chart at the top is not decoration: it is how you see at a glance
 * that the cold snap arrives on Thursday, which is a question the list of rows
 * underneath answers only if you read all fourteen of them.
 */
@Composable
fun DaysScreen(
    state: AppState,
    formatter: Formatter,
    nowEpoch: Long,
    scrollState: ScrollState,
    onOpenDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = state.copy
    val bundle = state.bundle

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = Space.pageMargin),
    ) {
        Spacer(Modifier.height(Space.sm))
        ScreenHeader(
            title = copy.days,
            subtitle = state.selectedPlace?.name,
        )

        if (bundle == null || bundle.days.isEmpty()) {
            Spacer(Modifier.height(Space.blockGap))
            Skeleton(Modifier.fillMaxWidth().height(148.dp))
            Spacer(Modifier.height(Space.xl))
            repeat(8) {
                Skeleton(Modifier.fillMaxWidth().height(38.dp))
                Spacer(Modifier.height(Space.md))
            }
            Spacer(Modifier.height(Space.railClearance))
            return@Column
        }

        val days = bundle.days
        val periodMin = days.mapNotNull { it.temperatureMin }.minOrNull() ?: 0.0
        val periodMax = days.mapNotNull { it.temperatureMax }.maxOrNull() ?: 1.0

        Spacer(Modifier.height(Space.blockGap))
        SectionLabel(copy.t("Andamento · ${days.size} giorni", "Trend · ${days.size} days"))
        Spacer(Modifier.height(Space.md))
        DailyTrendChart(days = days, formatter = formatter)

        GapSection()
        SectionLabel(copy.t("Giorno per giorno", "Day by day"))
        Spacer(Modifier.height(Space.sm))

        days.forEachIndexed { index, day ->
            if (index > 0) SectionRule()
            DailyRow(
                day = day,
                label = formatter.dayLabel(day.epochSeconds, nowEpoch),
                formatter = formatter,
                copy = copy,
                periodMin = periodMin,
                periodMax = periodMax,
                onClick = { onOpenDay(day.epochSeconds) },
            )
        }

        Spacer(Modifier.height(Space.blockGap))
        ConfidenceNote(copy)

        Spacer(Modifier.height(Space.railClearance))
    }
}

/**
 * A short explanation of why the later days are drawn faintly.
 *
 * Confidence is the app's whole argument, so the one place it appears without
 * a number is the place that most needs a sentence explaining it.
 */
@Composable
private fun ConfidenceNote(copy: Copy) {
    SText(
        text = copy.t(
            "I giorni più lontani sono disegnati più tenui: oltre la settimana i modelli concordano meno e l'affidabilità cala.",
            "Later days are drawn more faintly: beyond a week the models agree less and confidence drops.",
        ),
        style = Sereno.type.caption,
        emphasis = Emphasis.tertiary,
    )
}
