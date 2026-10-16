package com.ridesniper.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ridesniper.app.data.repository.RideStats
import com.ridesniper.app.data.repository.StatsFilter
import com.ridesniper.app.ui.RideSniperViewModel
import com.ridesniper.app.util.Fmt
import java.util.Locale

@Composable
fun StatsScreen(viewModel: RideSniperViewModel) {
    val stats by viewModel.stats.collectAsState()
    val filter by viewModel.currentStatsFilter.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Statistics", style = MaterialTheme.typography.headlineMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsFilter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { viewModel.setStatsFilter(f) },
                    label = { Text(f.label) }
                )
            }
        }

        if (stats.offersAnalyzed == 0) {
            Text("No data in this range yet.", color = MaterialTheme.colorScheme.secondary)
            return@Column
        }

        StatSection("Volume") {
            StatLine("Offers analysed", "${stats.offersAnalyzed}")
            StatLine("Offers accepted", "${stats.offersAccepted}")
            StatLine("Offers declined", "${stats.offersDeclined}")
            StatLine("Acceptance rate", "%.0f%%".format(Locale.US, stats.acceptanceRate * 100))
        }

        StatSection("Averages") {
            StatLine("Avg gross R/km", Fmt.money(stats.avgGrossPerKm))
            StatLine("Avg gross R/minute", Fmt.money(stats.avgGrossPerMinute))
            StatLine("Avg net R/km", Fmt.money(stats.avgNetPerKm))
            StatLine("Avg net R/hour", Fmt.money(stats.avgNetPerHour))
            StatLine("Avg payout", Fmt.money(stats.avgPayout))
            StatLine("Avg pickup distance", Fmt.km(stats.avgPickupKm))
            StatLine("Avg trip distance", Fmt.km(stats.avgTripKm))
            StatLine("Avg total distance", Fmt.km(stats.avgTotalKm))
        }

        StatSection("Declines saved you") {
            StatLine("Kilometres avoided", Fmt.km(stats.kmAvoidedByDeclining))
            StatLine("Fuel avoided", Fmt.money(stats.fuelAvoided))
            StatLine("Wear avoided", Fmt.money(stats.wearAvoided))
            StatLine("Money preserved", Fmt.money(stats.moneyPreservedByDeclining))
        }

        StatSection("Extremes") {
            stats.bestRide?.let { StatLine("Best ride profit", Fmt.money(it.estimatedProfit)) }
            stats.worstRide?.let { StatLine("Worst ride profit", Fmt.money(it.estimatedProfit)) }
            stats.mostProfitableHour?.let { StatLine("Most profitable hour", Fmt.hour(it)) }
            stats.leastProfitableHour?.let { StatLine("Least profitable hour", Fmt.hour(it)) }
        }
    }
}

@Composable
private fun StatSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.secondary)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
