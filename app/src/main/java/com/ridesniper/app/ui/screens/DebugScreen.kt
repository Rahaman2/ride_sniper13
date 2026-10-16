package com.ridesniper.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ridesniper.app.service.OverlayResultHolder

@Composable
fun DebugScreen() {
    val parsed by OverlayResultHolder.lastParseResultFlow.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("OCR Debug", style = MaterialTheme.typography.headlineMedium)

        val p = parsed
        if (p == null) {
            Text("No OCR pass has run yet. Analyse an offer to populate this screen.", color = MaterialTheme.colorScheme.secondary)
            return@Column
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Confidence: %.0f%%".format(p.confidence * 100))
                FieldRow("Offer amount", p.payout.value?.toString(), p.payout.matchedRule)
                FieldRow("Pickup km", p.pickupKm.value?.toString(), p.pickupKm.matchedRule)
                FieldRow("Trip km", p.tripKm.value?.toString(), p.tripKm.matchedRule)
                FieldRow("Pickup minutes", p.pickupMinutes.value?.toString(), p.pickupMinutes.matchedRule)
                FieldRow("Trip minutes", p.tripMinutes.value?.toString(), p.tripMinutes.matchedRule)
                FieldRow("Destination", p.destinationText.ifBlank { null }, null)
                FieldRow("Airport", p.isAirport.toString(), null)
                FieldRow("Surge", p.isSurge.toString(), null)
                FieldRow("Reservation", p.isReservation.toString(), null)
            }
        }

        Text("Raw OCR text", style = MaterialTheme.typography.titleLarge)
        Card {
            Text(
                text = p.rawText.ifBlank { "(empty)" },
                modifier = Modifier.padding(16.dp),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun FieldRow(label: String, value: String?, rule: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.secondary)
        Text(buildString {
            append(value ?: "MISSING")
            if (rule != null) append("  [$rule]")
        })
    }
}
