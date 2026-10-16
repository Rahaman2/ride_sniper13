package com.ridesniper.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ridesniper.app.data.database.AcceptedState
import com.ridesniper.app.data.database.RideEntity
import com.ridesniper.app.data.repository.StatsFilter
import com.ridesniper.app.ui.RideSniperViewModel
import com.ridesniper.app.ui.theme.DeclineRed
import com.ridesniper.app.ui.theme.MaybeYellow
import com.ridesniper.app.ui.theme.TakeGreen
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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Ride History", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = {
                scope.launch {
                    val csv = viewModel.exportCsv()
                    val intent = CsvExporter.writeAndGetShareIntent(context, csv)
                    context.startActivity(intent)
                }
            }) { Text("Export CSV") }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsFilter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { viewModel.setStatsFilter(f) },
                    label = { Text(f.label) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (history.isEmpty()) {
            Text("No rides in this range yet.", color = MaterialTheme.colorScheme.secondary)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(history, key = { it.id }) { ride ->
                    HistoryRow(ride, onMark = { accepted -> viewModel.markRideAccepted(ride.id, accepted) })
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(ride: RideEntity, onMark: (Boolean) -> Unit) {
    val color = when (ride.recommendation) {
        "TAKE" -> TakeGreen
        "MAYBE" -> MaybeYellow
        else -> DeclineRed
    }
    val fmt = remember { SimpleDateFormat("d MMM, HH:mm", Fmt.DISPLAY_LOCALE) }

    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(ride.recommendation, color = color, fontWeight = FontWeight.Bold)
                Text(fmt.format(Date(ride.timestamp)), color = MaterialTheme.colorScheme.secondary)
            }
            Text(
                "${Fmt.money(ride.offerAmount)} offer · ${Fmt.km(ride.totalKm)} · ${Fmt.perKm(ride.grossPerKm)} · ${Fmt.perMinute(ride.grossPerMinute)}"
            )
            Text(
                "Net profit ${Fmt.money(ride.estimatedProfit)} · ${Fmt.perHour(ride.netPerHour)}",
                color = MaterialTheme.colorScheme.secondary
            )
            if (ride.destinationText.isNotBlank()) {
                Text("To: ${ride.destinationText}", color = MaterialTheme.colorScheme.secondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val accepted = ride.acceptedState == AcceptedState.ACCEPTED.name
                val declined = ride.acceptedState == AcceptedState.DECLINED.name
                FilterChip(selected = accepted, onClick = { onMark(true) }, label = { Text("Accepted") })
                FilterChip(selected = declined, onClick = { onMark(false) }, label = { Text("Declined") })
            }
        }
    }
}
