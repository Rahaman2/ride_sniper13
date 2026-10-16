package com.ridesniper.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ridesniper.app.model.AppSettings
import com.ridesniper.app.ui.RideSniperViewModel
import com.ridesniper.app.util.Fmt
import java.util.Locale

@Composable
fun SettingsScreen(viewModel: RideSniperViewModel) {
    val settings by viewModel.settings.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        SettingsSection("Vehicle & cost") {
            NumberSetting("Fuel price (R/litre)", settings.fuelPricePerLitre) {
                viewModel.updateSettings { s -> s.copy(fuelPricePerLitre = it) }
            }
            NumberSetting("Fuel consumption (L/100 km)", settings.litresPer100Km) {
                viewModel.updateSettings { s -> s.copy(litresPer100Km = it) }
            }
            NumberSetting("Wear/maintenance reserve (R/km)", settings.wearCostPerKm) {
                viewModel.updateSettings { s -> s.copy(wearCostPerKm = it) }
            }
        }

        SettingsSection("Thresholds") {
            NumberSetting("Preferred R/km", settings.preferredPerKm) {
                viewModel.updateSettings { s -> s.copy(preferredPerKm = it) }
            }
            NumberSetting("Minimum R/km", settings.minimumPerKm) {
                viewModel.updateSettings { s -> s.copy(minimumPerKm = it) }
            }
            NumberSetting("Hard decline R/km", settings.hardDeclinePerKm) {
                viewModel.updateSettings { s -> s.copy(hardDeclinePerKm = it) }
            }
            NumberSetting("Preferred R/minute", settings.preferredPerMinute) {
                viewModel.updateSettings { s -> s.copy(preferredPerMinute = it) }
            }
            NumberSetting("Minimum R/minute", settings.minimumPerMinute) {
                viewModel.updateSettings { s -> s.copy(minimumPerMinute = it) }
            }
        }

        SettingsSection("Ride shape") {
            NumberSetting("Long pickup threshold (km)", settings.longPickupThresholdKm) {
                viewModel.updateSettings { s -> s.copy(longPickupThresholdKm = it) }
            }
            NumberSetting("Long ride threshold (km)", settings.longRideThresholdKm) {
                viewModel.updateSettings { s -> s.copy(longRideThresholdKm = it) }
            }
            NumberSetting("Long ride preferred R/km", settings.longRidePreferredPerKm) {
                viewModel.updateSettings { s -> s.copy(longRidePreferredPerKm = it) }
            }
        }

        SettingsSection("Airport") {
            NumberSetting("Airport minimum R/km", settings.airportMinimumPerKm) {
                viewModel.updateSettings { s -> s.copy(airportMinimumPerKm = it) }
            }
            NumberSetting("Airport preferred R/km", settings.airportPreferredPerKm) {
                viewModel.updateSettings { s -> s.copy(airportPreferredPerKm = it) }
            }
        }

        SettingsSection("Overlay & feedback") {
            SwitchSetting("Vibration on tap", settings.vibrationEnabled) {
                viewModel.updateSettings { s -> s.copy(vibrationEnabled = it) }
            }
            IntSetting("Overlay bubble size (dp)", settings.overlaySizeDp) {
                viewModel.updateSettings { s -> s.copy(overlaySizeDp = it) }
            }
        }

        SettingsSection("OCR & privacy") {
            SliderSetting(
                label = "OCR confidence threshold",
                value = settings.ocrConfidenceThreshold,
                onChange = { viewModel.updateSettings { s -> s.copy(ocrConfidenceThreshold = it) } }
            )
            SwitchSetting("Auto-delete screenshots (process in memory only)", settings.autoDeleteScreenshots) {
                viewModel.updateSettings { s -> s.copy(autoDeleteScreenshots = it) }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun NumberSetting(label: String, value: Double, onCommit: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(Fmt.number(value)) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.width(110.dp),
            singleLine = true,
            trailingIcon = null
        )
        TextButton(onClick = { Fmt.parseDecimal(text)?.let(onCommit) }) { Text("Set") }
    }
}

@Composable
private fun IntSetting(label: String, value: Int, onCommit: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.width(90.dp),
            singleLine = true
        )
        TextButton(onClick = { text.toIntOrNull()?.let(onCommit) }) { Text("Set") }
    }
}

@Composable
private fun SwitchSetting(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderSetting(label: String, value: Float, onChange: (Float) -> Unit) {
    Column {
        Text("$label: %.0f%%".format(Locale.US, value * 100))
        Slider(value = value, onValueChange = onChange, valueRange = 0f..1f)
    }
}
