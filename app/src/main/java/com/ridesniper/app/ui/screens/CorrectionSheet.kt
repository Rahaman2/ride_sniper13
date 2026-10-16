package com.ridesniper.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ridesniper.app.model.RideOfferInput
import com.ridesniper.app.ocr.OcrParseResult
import com.ridesniper.app.util.Fmt

/**
 * Core Feature 4: shown whenever OCR confidence is low or a required field is
 * missing. Pre-fills anything OCR did find and lets the driver fix the rest
 * before recalculating.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrectionSheet(
    parsed: OcrParseResult,
    onDismiss: () -> Unit,
    onCalculate: (RideOfferInput) -> Unit
) {
    var payout by remember { mutableStateOf(parsed.payout.value?.toString().orEmpty()) }
    var pickupKm by remember { mutableStateOf(parsed.pickupKm.value?.toString().orEmpty()) }
    var tripKm by remember { mutableStateOf(parsed.tripKm.value?.toString().orEmpty()) }
    var pickupMinutes by remember { mutableStateOf(parsed.pickupMinutes.value?.toString().orEmpty()) }
    var tripMinutes by remember { mutableStateOf(parsed.tripMinutes.value?.toString().orEmpty()) }
    var destination by remember { mutableStateOf(parsed.destinationText) }

    val allFilled = Fmt.parseDecimal(payout) != null &&
        Fmt.parseDecimal(pickupKm) != null &&
        Fmt.parseDecimal(tripKm) != null &&
        Fmt.parseDecimal(pickupMinutes) != null &&
        Fmt.parseDecimal(tripMinutes) != null

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Confirm ride details", style = MaterialTheme.typography.headlineMedium)
            Text(
                "OCR confidence was low or a field was missing. Fix anything below, then calculate.",
                color = MaterialTheme.colorScheme.secondary
            )

            OutlinedTextField(payout, { payout = it }, label = { Text("Offer amount (${Fmt.CURRENCY})") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pickupKm, { pickupKm = it }, label = { Text("Pickup distance (${Fmt.DISTANCE_UNIT})") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(tripKm, { tripKm = it }, label = { Text("Trip distance (${Fmt.DISTANCE_UNIT})") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pickupMinutes, { pickupMinutes = it }, label = { Text("Pickup minutes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(tripMinutes, { tripMinutes = it }, label = { Text("Trip minutes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(destination, { destination = it }, label = { Text("Destination") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Button(
                enabled = allFilled,
                onClick = {
                    onCalculate(
                        RideOfferInput(
                            payout = Fmt.parseDecimal(payout)!!,
                            pickupKm = Fmt.parseDecimal(pickupKm)!!,
                            tripKm = Fmt.parseDecimal(tripKm)!!,
                            pickupMinutes = Fmt.parseDecimal(pickupMinutes)!!,
                            tripMinutes = Fmt.parseDecimal(tripMinutes)!!,
                            destinationText = destination,
                            isAirport = parsed.isAirport,
                            isSurge = parsed.isSurge,
                            isReservation = parsed.isReservation,
                            ocrConfidence = parsed.confidence
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("CALCULATE") }

            Spacer(Modifier.height(8.dp))
        }
    }
}
