package com.ridesniper.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridesniper.app.model.Recommendation
import com.ridesniper.app.model.RideCalculationResult
import com.ridesniper.app.model.StrategyPreset
import com.ridesniper.app.ui.RideSniperViewModel
import com.ridesniper.app.ui.theme.DeclineRed
import com.ridesniper.app.ui.theme.MaybeYellow
import com.ridesniper.app.ui.theme.TakeGreen
import com.ridesniper.app.util.Fmt

@Composable
fun HomeScreen(
    viewModel: RideSniperViewModel,
    hasOverlayPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onStartAnalyzer: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val latestResult by viewModel.latestResult.collectAsState()
    var fuelPriceText by remember(settings.fuelPricePerLitre) { mutableStateOf(Fmt.number(settings.fuelPricePerLitre)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("RIDE SNIPER", style = MaterialTheme.typography.headlineLarge)

        if (!hasOverlayPermission) {
            PermissionCard(
                title = "Overlay permission needed",
                description = "Allow Ride Sniper to display over other apps so the bubble can appear on top of Uber Driver.",
                buttonLabel = "Grant overlay permission",
                onClick = onRequestOverlayPermission
            )
        } else {
            PermissionCard(
                title = "Start the analyser",
                description = "Starts the floating bubble and grants one-time screen-capture access. You'll be asked again each time Android restarts capture (this is an OS restriction, not a bug).",
                buttonLabel = "Start Ride Sniper",
                onClick = onStartAnalyzer
            )
        }

        // Core Feature 11: quick fuel price entry (changes monthly in SA), no internet required.
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.LocalGasStation, contentDescription = null)
                    Text("Fuel price today", style = MaterialTheme.typography.titleLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(Fmt.CURRENCY, style = MaterialTheme.typography.headlineMedium)
                    OutlinedTextField(
                        value = fuelPriceText,
                        onValueChange = { fuelPriceText = it },
                        modifier = Modifier.width(120.dp),
                        singleLine = true
                    )
                    Text("/${Fmt.FUEL_UNIT}", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = {
                        Fmt.parseDecimal(fuelPriceText)?.let { viewModel.setFuelPrice(it) }
                    }) { Text("Save") }
                }
            }
        }

        // Strategy preset quick switch.
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Strategy", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StrategyPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = settings.strategy == preset,
                            onClick = { viewModel.applyStrategyPreset(preset) },
                            label = { Text(preset.label) }
                        )
                    }
                }
                Text(
                    "Preferred: ${Fmt.perKm(settings.preferredPerKm)} · ${Fmt.perMinute(settings.preferredPerMinute)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Text("Last analysed offer", style = MaterialTheme.typography.titleLarge)
        if (latestResult == null) {
            Text(
                "No offer analysed yet. Tap the floating bubble over Uber Driver to analyse one.",
                color = MaterialTheme.colorScheme.secondary
            )
        } else {
            ResultHud(latestResult!!)
        }
    }
}

@Composable
private fun PermissionCard(title: String, description: String, buttonLabel: String, onClick: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            Button(onClick = onClick) { Text(buttonLabel) }
        }
    }
}

@Composable
fun ResultHud(result: RideCalculationResult) {
    val color = when (result.recommendation) {
        Recommendation.TAKE -> TakeGreen
        Recommendation.MAYBE -> MaybeYellow
        Recommendation.DECLINE, Recommendation.HARD_DECLINE -> DeclineRed
    }
    val label = when (result.recommendation) {
        Recommendation.TAKE -> "TAKE"
        Recommendation.MAYBE -> "MAYBE"
        Recommendation.DECLINE -> "DECLINE"
        Recommendation.HARD_DECLINE -> "HARD DECLINE"
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = label,
            color = color,
            fontWeight = FontWeight.Black,
            fontSize = 64.sp
        )
        Text(
            "${Fmt.perKm(result.grossPerKm)}   ·   ${Fmt.perMinute(result.grossPerMinute)}",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            "${Fmt.money(result.estimatedProfit)} estimated profit   ·   ${Fmt.perHour(result.netPerHour)} net",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.secondary
        )

        if (result.warnings.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                result.warnings.take(6).forEach { w ->
                    AssistChip(onClick = {}, label = { Text(w.label) })
                }
            }
        }

        var expanded by remember { mutableStateOf(false) }
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Hide details" else "Show details")
        }
        if (expanded) {
            DetailGrid(result)
        }
    }
}

@Composable
private fun DetailGrid(result: RideCalculationResult) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DetailRow("Offer amount", Fmt.money(result.input.payout))
        DetailRow("Pickup distance", Fmt.km(result.input.pickupKm, 2))
        DetailRow("Trip distance", Fmt.km(result.input.tripKm, 2))
        DetailRow("Total distance", Fmt.km(result.totalKm, 2))
        DetailRow("Pickup minutes", Fmt.minutes(result.input.pickupMinutes, 1))
        DetailRow("Trip minutes", Fmt.minutes(result.input.tripMinutes, 1))
        DetailRow("Total minutes", Fmt.minutes(result.totalMinutes, 1))
        DetailRow("Estimated fuel cost", Fmt.money(result.fuelCost))
        DetailRow("Estimated wear cost", Fmt.money(result.wearCost))
        DetailRow("Estimated net profit", Fmt.money(result.estimatedProfit))
        DetailRow("Estimated net/km", Fmt.money(result.netPerKm))
        DetailRow("Estimated net/hour", Fmt.money(result.netPerHour))
        DetailRow("Destination", result.input.destinationText.ifBlank { "—" })
        DetailRow("Strategy used", result.strategyUsed.label)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.secondary)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
