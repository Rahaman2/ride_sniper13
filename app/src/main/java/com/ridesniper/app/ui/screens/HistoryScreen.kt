package com.ridesniper.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ridesniper.app.data.database.AcceptedState
import com.ridesniper.app.data.database.RideEntity
import com.ridesniper.app.data.repository.StatsFilter
import com.ridesniper.app.model.Features
import com.ridesniper.app.model.Recommendation
import com.ridesniper.app.ui.RideSniperViewModel
import com.ridesniper.app.ui.components.*
import com.ridesniper.app.util.CsvExporter
import com.ridesniper.app.util.Fmt
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(viewModel: RideSniperViewModel) {
    val history by viewModel.history.collectAsState()
    val filter by viewModel.currentStatsFilter.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
        ScreenHeader(
            title = "History",
            subtitle = "Every offer you analysed.",
            trailing = {
                FilledTonalButton(
                    enabled = !exporting,
                    onClick = {
                        if (history.isEmpty()) {
                            viewModel.notify("No rides to export yet")
                            return@FilledTonalButton
                        }
                        exporting = true
                        scope.launch {
                            try {
                                val csv = viewModel.exportCsv()
                                context.startActivity(CsvExporter.writeAndGetShareIntent(context, csv))
                                viewModel.notify("Exported ${history.size} rides")
                            } catch (e: Exception) {
                                viewModel.notify("Export failed. Try again.")
                            } finally {
                                exporting = false
                            }
                        }
                    }
                ) {
                    if (exporting) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Export")
                }
            }
        )

        Spacer(Modifier.height(16.dp))
        FilterRow(filter, onSelect = { viewModel.setStatsFilter(it) })
        Spacer(Modifier.height(16.dp))

        if (history.isEmpty()) {
            RsCard {
                EmptyState(
                    icon = Icons.Filled.History,
                    title = "No rides in this period",
                    body = "Offers you analyse with the bubble will show up here."
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(history, key = { it.id }) { ride ->
                    HistoryRow(
                        ride = ride,
                        onMark = { accepted -> viewModel.markRideAccepted(ride.id, accepted) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

/** Shared by History and Stats. */
@Composable
fun FilterRow(selected: StatsFilter, onSelect: (StatsFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatsFilter.entries.forEach { f ->
            FilterChip(
                selected = selected == f,
                onClick = { onSelect(f) },
                label = { Text(f.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun HistoryRow(ride: RideEntity, onMark: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val fmt = remember { SimpleDateFormat("d MMM, HH:mm", Fmt.DISPLAY_LOCALE) }
    val recommendation = runCatching { Recommendation.valueOf(ride.recommendation) }.getOrNull()

    RsCard(modifier = modifier, contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (recommendation != null) RecommendationBadge(recommendation)
            Spacer(Modifier.weight(1f))
            Text(
                fmt.format(Date(ride.timestamp)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                Fmt.money(if (Features.VEHICLE_COSTS) ride.estimatedProfit else ride.offerAmount),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${Fmt.km(ride.totalKm)} · ${Fmt.minutes(ride.totalMinutes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
        Text(
            if (Features.TIME_RATES) {
                "${Fmt.perKm(ride.grossPerKm)} · ${Fmt.perMinute(ride.grossPerMinute)} · ${Fmt.perHour(ride.netPerHour)}"
            } else {
                "${Fmt.perKm(ride.grossPerKm)} · pickup ${Fmt.km(ride.pickupKm)} · trip ${Fmt.km(ride.tripKm)}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (ride.destinationText.isNotBlank()) {
            Text("To ${ride.destinationText}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val accepted = ride.acceptedState == AcceptedState.ACCEPTED.name
            val declined = ride.acceptedState == AcceptedState.DECLINED.name
            FilterChip(
                selected = accepted,
                onClick = { if (!accepted) onMark(true) },
                label = { Text(if (accepted) "Accepted" else "I took it") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
            FilterChip(
                selected = declined,
                onClick = { if (!declined) onMark(false) },
                label = { Text(if (declined) "Declined" else "I declined") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
