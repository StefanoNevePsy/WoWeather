package app.sereno.weather.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sereno.weather.design.Emphasis
import app.sereno.weather.design.GapRow
import app.sereno.weather.design.SText
import app.sereno.weather.design.SectionLabel
import app.sereno.weather.design.Sereno
import app.sereno.weather.design.Space
import app.sereno.weather.domain.model.Confidence
import app.sereno.weather.domain.model.ConfidenceBand
import app.sereno.weather.i18n.Copy

/**
 * Maps a confidence band onto the data palette.
 *
 * These are not a traffic light. Low confidence is not an error and must not
 * look like one — it is simply information, and rendering it in alarm-red would
 * teach people to distrust the app rather than to read the uncertainty.
 */
@Composable
fun confidenceColor(band: ConfidenceBand): Color {
    val data = Sereno.data
    return when (band) {
        ConfidenceBand.VeryHigh -> data.positive
        ConfidenceBand.High -> data.positive
        ConfidenceBand.Moderate -> data.caution
        ConfidenceBand.Low -> data.warning
        ConfidenceBand.VeryLow -> data.warning
    }
}

/**
 * The full confidence display.
 *
 * The number is never shown alone. A percentage with no explanation is a
 * mystery; the sentence underneath — generated from which models agree about
 * what — is the part that actually earns the user's trust, and it is why this
 * component always reserves room for it.
 */
@Composable
fun ConfidenceMeter(
    confidence: Confidence,
    copy: Copy,
    modifier: Modifier = Modifier,
    explanation: String? = null,
    showHeader: Boolean = true,
) {
    val type = Sereno.type
    val atmosphere = Sereno.atmosphere
    val color = confidenceColor(confidence.band)

    val described = "${copy.forecastConfidence}: ${copy.confidenceBand(confidence.band)}, ${confidence.percent}%"

    Column(modifier.fillMaxWidth()) {
        if (showHeader) {
            SectionLabel(copy.forecastConfidence)
            Spacer(Modifier.height(Space.lg))
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = described },
        ) {
            SText(
                text = copy.confidenceBand(confidence.band),
                style = type.title,
                color = color,
            )
            Spacer(Modifier.weight(1f))
            SText(
                text = "${confidence.percent}%",
                style = type.dataLarge,
                emphasis = Emphasis.secondary,
            )
        }

        Spacer(Modifier.height(Space.md))
        ConfidenceTrack(
            score = confidence.score,
            color = color,
            modifier = Modifier.fillMaxWidth(),
        )

        if (explanation != null) {
            Spacer(Modifier.height(Space.lg))
            SText(
                text = explanation,
                style = type.body,
                emphasis = Emphasis.secondary,
            )
        }

        if (confidence.modelCount > 0) {
            GapRow()
            SText(
                text = copy.t(
                    "Basata su ${confidence.modelCount} modelli",
                    "Based on ${confidence.modelCount} models",
                ),
                style = type.caption,
                emphasis = Emphasis.tertiary,
            )
        }
    }
}

/**
 * The confidence track.
 *
 * Five segments rather than a continuous bar, because confidence genuinely is
 * banded — the difference between 71% and 74% is noise, the difference between
 * "High" and "Moderate" is not. The partial fill of the final segment keeps the
 * precision available without implying it matters.
 */
@Composable
fun ConfidenceTrack(
    score: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp,
    segments: Int = 5,
) {
    val atmosphere = Sereno.atmosphere
    val motion = Sereno.motion
    val animated by animateFloatAsState(
        targetValue = score.coerceIn(0f, 1f),
        animationSpec = motion.draw(),
        label = "confidenceFill",
    )

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics { },
    ) {
        val gap = 3.dp.toPx()
        val segmentWidth = (size.width - gap * (segments - 1)) / segments
        val radius = CornerRadius(size.height / 2f)

        repeat(segments) { index ->
            val left = index * (segmentWidth + gap)
            drawRoundRect(
                color = atmosphere.ink(Emphasis.hairline),
                topLeft = Offset(left, 0f),
                size = Size(segmentWidth, size.height),
                cornerRadius = radius,
            )

            // How much of this particular segment the score reaches into.
            val segmentStart = index.toFloat() / segments
            val fill = ((animated - segmentStart) * segments).coerceIn(0f, 1f)
            if (fill > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(color.copy(alpha = 0.75f), color),
                        startX = left,
                        endX = left + segmentWidth,
                    ),
                    topLeft = Offset(left, 0f),
                    size = Size(segmentWidth * fill, size.height),
                    cornerRadius = radius,
                )
            }
        }
    }
}

/**
 * The compact form, for list rows.
 *
 * Small enough to sit at the end of a daily row without competing with the
 * temperature, but still a real reading rather than a coloured dot.
 */
@Composable
fun ConfidenceMicro(
    confidence: Confidence,
    copy: Copy,
    modifier: Modifier = Modifier,
    width: Dp = 26.dp,
) {
    val color = confidenceColor(confidence.band)
    val description = "${copy.confidence} ${copy.confidenceBand(confidence.band)}"
    Box(
        modifier
            .width(width)
            .semantics { contentDescription = description },
    ) {
        ConfidenceTrack(
            score = confidence.score,
            color = color,
            height = 2.5.dp,
            segments = 4,
        )
    }
}

/** A single dot, for the very tightest spots. */
@Composable
fun ConfidenceDot(confidence: Confidence, modifier: Modifier = Modifier, size: Dp = 6.dp) {
    val color = confidenceColor(confidence.band)
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        drawCircle(color.copy(alpha = 0.35f + confidence.score * 0.65f))
    }
}
