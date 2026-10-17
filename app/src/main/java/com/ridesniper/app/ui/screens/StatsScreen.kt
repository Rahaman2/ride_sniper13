package com.ridesniper.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ridesniper.app.model.Features
import com.ridesniper.app.ui.RideSniperViewModel
import com.ridesniper.app.ui.components.*
import com.ridesniper.app.util.Fmt
import kotlin.math.roundToInt

@Composable
fun StatsScreen(viewModel: RideSniperViewModel) {
    val stats by viewModel.stats.collectAsState()
    val filter by viewModel.currentStatsFilter.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader("Stats", "How your offers have paid.")
        FilterRow(filter, onSelect = { viewModel.setStatsFilter(it) })

        if (stats.offersAnalyzed == 0) {
            RsCard {
                EmptyState(
                    icon = Icons.Filled.BarChart,
                    title = "Nothing to show yet",
                    body = "Analyse a few offers and your averages will appear here."
                )
            }
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Offers", "${stats.offersAnalyzed}", Modifier.weight(1f))
            StatTile("Accepted", "${(stats.acceptanceRate * 100).roundToInt()}%", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Avg per km", Fmt.money(stats.avgGrossPerKm), Modifier.weight(1f))
            if (Features.TIME_RATES) {
                StatTile(if (Features.VEHICLE_COSTS) "Avg net / hour" else "Avg per hour", Fmt.money(stats.avgNetPerHour), Modifier.weight(1f))
            } else {
                StatTile("Avg payout", Fmt.money(stats.avgPayout), Modifier.weight(1f))
            }
        }

        StatSection("Averages") {
            if (Features.TIME_RATES) {
                StatLine("Payout", Fmt.money(stats.avgPayout))
                StatLine("Per minute", Fmt.money(stats.avgGrossPerMinute))
            }
            if (Features.VEHICLE_COSTS) StatLine("Net per km", Fmt.money(stats.avgNetPerKm))
            StatLine("Pickup distance", Fmt.km(stats.avgPickupKm))
            StatLine("Trip distance", Fmt.km(stats.avgTripKm))
            StatLine("Total distance", Fmt.km(stats.avgTotalKm))
        }

        StatSection("Your decisions") {
            StatLine("Accepted", "${stats.offersAccepted}")
            StatLine("Declined", "${stats.offersDeclined}")
            StatLine("Kilometres avoided by declining", Fmt.km(stats.kmAvoidedByDeclining))
            if (Features.VEHICLE_COSTS) {
                StatLine("Fuel avoided", Fmt.money(stats.fuelAvoided))
                StatLine("Wear avoided", Fmt.money(stats.wearAvoided))
                StatLine("Money preserved", Fmt.money(stats.moneyPreservedByDeclining))
            }
        }

        StatSection("Best and worst") {
            val amountOf = { profit: Double, payout: Double -> Fmt.money(if (Features.VEHICLE_COSTS) profit else payout) }
            stats.bestRide?.let { StatLine("Best ride", amountOf(it.estimatedProfit, it.offerAmount)) }
            stats.worstRide?.let { StatLine("Worst ride", amountOf(it.estimatedProfit, it.offerAmount)) }
            stats.mostProfitableHour?.let { StatLine("Best hour to drive", Fmt.hour(it)) }
            stats.leastProfitableHour?.let { StatLine("Weakest hour", Fmt.hour(it)) }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    RsCard(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun StatSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    RsCard {
        SectionLabel(title)
        content()
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
