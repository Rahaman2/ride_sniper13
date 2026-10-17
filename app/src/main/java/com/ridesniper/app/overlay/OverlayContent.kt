package com.ridesniper.app.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridesniper.app.model.Features
import com.ridesniper.app.model.Recommendation
import com.ridesniper.app.model.RideCalculationResult
import com.ridesniper.app.ui.theme.Accent
import com.ridesniper.app.ui.theme.DeclineRed
import com.ridesniper.app.ui.theme.MaybeYellow
import com.ridesniper.app.ui.theme.OutlineDark
import com.ridesniper.app.ui.theme.SurfaceDark
import com.ridesniper.app.ui.theme.TakeGreen
import com.ridesniper.app.ui.theme.TextPrimary
import com.ridesniper.app.ui.theme.TextSecondary
import com.ridesniper.app.util.Fmt

fun colorFor(rec: Recommendation): Color = when (rec) {
    Recommendation.TAKE -> TakeGreen
    Recommendation.MAYBE -> MaybeYellow
    Recommendation.DECLINE, Recommendation.HARD_DECLINE -> DeclineRed
}

fun labelFor(rec: Recommendation): String = when (rec) {
    Recommendation.TAKE -> "TAKE"
    Recommendation.MAYBE -> "MAYBE"
    Recommendation.DECLINE -> "DECLINE"
    Recommendation.HARD_DECLINE -> "HARD DECLINE"
}

/** The big number: what the ride pays, or what's left after costs when vehicle costs are on. */
fun headlineAmount(result: RideCalculationResult): String =
    Fmt.money(if (Features.VEHICLE_COSTS) result.estimatedProfit else result.input.payout)

fun headlineCaption(result: RideCalculationResult): String = when {
    !Features.VEHICLE_COSTS -> "This ride pays"
    result.estimatedProfit >= 0 -> "You keep, after fuel and wear"
    else -> "You lose, after fuel and wear"
}

/** "R80.00 offer − R14.30 fuel − R11.50 wear". Only shown when vehicle costs are on. */
fun costBreakdown(result: RideCalculationResult): String =
    "${Fmt.money(result.input.payout)} offer − ${Fmt.money(result.fuelCost)} fuel − ${Fmt.money(result.wearCost)} wear"

fun perHourLabel(): String = if (Features.VEHICLE_COSTS) "Net / hour" else "Per hour"

enum class MessageKind { PROGRESS, INFO, ERROR }

/** Fades and drops overlay content in when it first appears, instead of popping. */
@Composable
private fun EnterAnimated(content: @Composable () -> Unit) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(180)) +
            slideInVertically(tween(260)) { -it / 5 } +
            scaleIn(tween(260), initialScale = 0.96f)
    ) {
        content()
    }
}

/** The floating "R" bubble. Pulses with a spinner while reading; its ring shows the last verdict. */
@Composable
fun RsBubble(sizeDp: Int, isBusy: Boolean, lastRecommendation: Recommendation?) {
    val ringColor by animateColorAsState(
        lastRecommendation?.let { colorFor(it) } ?: Color.White.copy(alpha = 0.35f),
        animationSpec = tween(400),
        label = "bubbleRing"
    )
    // Only animate while busy, so the always-on bubble isn't redrawing every frame.
    val scale = if (isBusy) {
        val pulse = rememberInfiniteTransition(label = "bubblePulse")
        pulse.animateFloat(
            initialValue = 1f,
            targetValue = 1.07f,
            animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
            label = "bubbleScale"
        ).value
    } else 1f

    Box(
        modifier = Modifier
            .padding(6.dp)
            .size(sizeDp.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Accent)
            .border(BorderStroke(3.dp, ringColor), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size((sizeDp * 0.42f).dp),
                color = Color.White,
                strokeWidth = 2.5.dp
            )
        } else {
            Text(
                text = "R",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = (sizeDp / 2.4).sp
            )
        }
    }
}

@Composable
fun ResultCard(result: RideCalculationResult, lowConfidence: Boolean, onDismiss: () -> Unit) {
    val color = colorFor(result.recommendation)
    EnterAnimated {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceDark)
                .border(BorderStroke(1.5.dp, color.copy(alpha = 0.7f)), RoundedCornerShape(24.dp))
                .clickable(onClick = onDismiss)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = labelFor(result.recommendation),
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                )
                Spacer(Modifier.weight(1f))
                Text("Tap to close", color = TextSecondary, fontSize = 11.sp)
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = headlineCaption(result),
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Text(
                    text = headlineAmount(result),
                    color = if (result.estimatedProfit >= 0) TextPrimary else DeclineRed,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black
                )
                if (Features.VEHICLE_COSTS) {
                    Text(costBreakdown(result), color = TextSecondary, fontSize = 12.sp)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OverlayMetric("Per km", Fmt.money(result.grossPerKm))
                if (Features.TIME_RATES) {
                    OverlayMetric("Per min", Fmt.money(result.grossPerMinute))
                    OverlayMetric(perHourLabel(), Fmt.money(result.netPerHour))
                } else {
                    OverlayMetric("Total", Fmt.km(result.totalKm))
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(OutlineDark))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OverlayMetric("Pickup", "${Fmt.km(result.input.pickupKm)} · ${Fmt.minutes(result.input.pickupMinutes)}")
                OverlayMetric("Trip", "${Fmt.km(result.input.tripKm)} · ${Fmt.minutes(result.input.tripMinutes)}")
            }

            if (lowConfidence) {
                Text(
                    text = "Hard to read. Check these numbers. Long-press the bubble to fix them.",
                    color = MaybeYellow,
                    fontSize = 12.sp
                )
            }
            if (result.warnings.isNotEmpty()) {
                Text(
                    text = result.warnings.joinToString("  ·  ") { it.label },
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun OverlayMetric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, color = TextSecondary, fontSize = 11.sp)
        Text(text = value, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Short status line under the bubble: "Reading offer…", "No ride offer on screen", errors. */
@Composable
fun MessageCard(text: String, kind: MessageKind) {
    EnterAnimated {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceDark)
                .border(BorderStroke(1.dp, OutlineDark), RoundedCornerShape(18.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (kind) {
                MessageKind.PROGRESS -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Accent,
                    strokeWidth = 2.dp
                )
                MessageKind.INFO -> Box(Modifier.size(8.dp).background(TextSecondary, CircleShape))
                MessageKind.ERROR -> Box(Modifier.size(8.dp).background(DeclineRed, CircleShape))
            }
            Text(
                text = text,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }
    }
}
