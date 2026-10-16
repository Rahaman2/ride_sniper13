package com.ridesniper.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ridesniper.app.model.AppSettings
import com.ridesniper.app.model.Features
import com.ridesniper.app.model.RideCalculationResult
import com.ridesniper.app.model.StrategyPreset
import com.ridesniper.app.overlay.costBreakdown
import com.ridesniper.app.overlay.headlineAmount
import com.ridesniper.app.overlay.headlineCaption
import com.ridesniper.app.overlay.perHourLabel
import com.ridesniper.app.ui.RideSniperViewModel
import com.ridesniper.app.ui.components.*
import com.ridesniper.app.ui.theme.DeclineRed
import com.ridesniper.app.ui.theme.TakeGreen
import com.ridesniper.app.util.Fmt

private enum class SetupState { NEEDS_OVERLAY, READY, RUNNING }

/** Sanity bounds for a typed fuel price, in rand per litre; catches typos like "225" or "2.25". */
private val FUEL_PRICE_RANGE = 5.0..60.0

@Composable
fun HomeScreen(
    viewModel: RideSniperViewModel,
    hasOverlayPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onStartAnalyzer: () -> Unit,
    onStopAnalyzer: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val latestResult by viewModel.latestResult.collectAsState()
    val running by viewModel.serviceRunning.collectAsState()
    val setup = when {
        running -> SetupState.RUNNING
        !hasOverlayPermission -> SetupState.NEEDS_OVERLAY
        else -> SetupState.READY
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        ScreenHeader(
            title = "Ride Sniper",
            subtitle = "Know what a ride pays before you accept.",
            trailing = { StatusPill(running) }
        )

        SetupCard(setup, onRequestOverlayPermission, onStartAnalyzer, onStopAnalyzer)

        StrategyCard(settings, onSelect = { viewModel.applyStrategyPreset(it) })

        if (Features.VEHICLE_COSTS) {
            RsCard {
                SectionLabel("Fuel price")
                NumberSettingField(
                    label = "Price today",
                    value = settings.fuelPricePerLitre,
                    unit = "R/${Fmt.FUEL_UNIT}",
                    validRange = FUEL_PRICE_RANGE,
                    onSave = {
                        viewModel.setFuelPrice(it)
                        viewModel.notify("Fuel price saved: ${Fmt.money(it)}/${Fmt.FUEL_UNIT}")
                    }
                )
            }
        }

        SectionLabel("Last offer")
        AnimatedContent(
            targetState = latestResult,
            transitionSpec = {
                (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 12 }) togetherWith fadeOut(tween(120))
            },
            label = "lastOffer"
        ) { result ->
            if (result == null) {
                RsCard {
                    EmptyState(
                        icon = Icons.Filled.Bolt,
                        title = "No offers yet",
                        body = "Start Ride Sniper, open Uber Driver and tap the bubble when an offer comes in."
                    )
                }
            } else {
                ResultHud(result)
            }
        }
    }
}

@Composable
private fun SetupCard(
    state: SetupState,
    onAllowBubble: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    RsCard {
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "setup"
        ) { s ->
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (s) {
                    SetupState.NEEDS_OVERLAY -> {
                        StepRow(1, "Allow the bubble", "Lets Ride Sniper float over Uber Driver.", done = false, active = true)
                        StepRow(2, "Start reading offers", "You'll be asked to allow screen capture.", done = false, active = false)
                        Button(onClick = onAllowBubble, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Text("Allow the bubble")
                        }
                    }
                    SetupState.READY -> {
                        StepRow(1, "Allow the bubble", "Done.", done = true, active = false)
                        StepRow(2, "Start reading offers", "You'll be asked to allow screen capture.", done = false, active = true)
                        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Start Ride Sniper")
                        }
                    }
                    SetupState.RUNNING -> {
                        Text("You're all set", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Open Uber Driver. When an offer comes in, tap the R bubble. Long-press the bubble to come back here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Stop Ride Sniper")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(number: Int, title: String, body: String, done: Boolean, active: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        val circle = when {
            done -> TakeGreen.copy(alpha = 0.15f)
            active -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
        Box(Modifier.size(32.dp).background(circle, CircleShape), contentAlignment = Alignment.Center) {
            if (done) {
                Icon(Icons.Filled.Check, contentDescription = "Done", tint = TakeGreen, modifier = Modifier.size(18.dp))
            } else {
                Text(
                    "$number",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (active || done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun strategyHint(preset: StrategyPreset): String = when (preset) {
    StrategyPreset.NORMAL -> "Take more rides, accept lower pay."
    StrategyPreset.PICKY -> "A balance of how many rides and how well they pay."
    StrategyPreset.EXTREME -> "Only the best-paying rides."
}

@Composable
private fun StrategyCard(settings: AppSettings, onSelect: (StrategyPreset) -> Unit) {
    RsCard {
        SectionLabel("Strategy")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StrategyPreset.entries.forEach { preset ->
                val selected = settings.strategy == preset
                FilterChip(
                    selected = selected,
                    onClick = { if (!selected) onSelect(preset) },
                    label = { Text(preset.label) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
        Text(strategyHint(settings.strategy), style = MaterialTheme.typography.bodyMedium)
        Text(
            "Green light from ${Fmt.perKm(settings.preferredPerKm)} and ${Fmt.perMinute(settings.preferredPerMinute)}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ResultHud(result: RideCalculationResult) {
    var expanded by remember { mutableStateOf(false) }

    RsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RecommendationBadge(result.recommendation)
            Spacer(Modifier.weight(1f))
            Text(
                "${result.strategyUsed.label} strategy",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(headlineCaption(result), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                headlineAmount(result),
                style = MaterialTheme.typography.displayMedium,
                color = if (result.estimatedProfit >= 0) MaterialTheme.colorScheme.onSurface else DeclineRed
            )
            if (Features.VEHICLE_COSTS) {
                Text(costBreakdown(result), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(Modifier.fillMaxWidth()) {
            Metric("Per km", Fmt.money(result.grossPerKm), Modifier.weight(1f))
            Metric("Per min", Fmt.money(result.grossPerMinute), Modifier.weight(1f))
            Metric(perHourLabel(), Fmt.money(result.netPerHour), Modifier.weight(1f))
        }

        if (result.warnings.isNotEmpty()) {
            Text(
                result.warnings.joinToString("  ·  ") { it.label },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
            Text(if (expanded) "Hide details" else "Show details")
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        }
        AnimatedVisibility(visible = expanded) {
            DetailGrid(result)
        }
    }
}

@Composable
private fun DetailGrid(result: RideCalculationResult) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailRow("Offer amount", Fmt.money(result.input.payout))
        DetailRow("Pickup", "${Fmt.km(result.input.pickupKm, 2)} · ${Fmt.minutes(result.input.pickupMinutes, 1)}")
        DetailRow("Trip", "${Fmt.km(result.input.tripKm, 2)} · ${Fmt.minutes(result.input.tripMinutes, 1)}")
        DetailRow("Total", "${Fmt.km(result.totalKm, 2)} · ${Fmt.minutes(result.totalMinutes, 1)}")
        if (Features.VEHICLE_COSTS) {
            DetailRow("Estimated fuel cost", Fmt.money(result.fuelCost))
            DetailRow("Estimated wear cost", Fmt.money(result.wearCost))
            DetailRow("Estimated net profit", Fmt.money(result.estimatedProfit))
            DetailRow("Estimated net/km", Fmt.money(result.netPerKm))
        }
        DetailRow("Destination", result.input.destinationText.ifBlank { "—" })
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
