package com.ridesniper.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ridesniper.app.model.AppSettings
import com.ridesniper.app.model.Features
import com.ridesniper.app.ui.RideSniperViewModel
import com.ridesniper.app.ui.components.*
import com.ridesniper.app.util.Fmt
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(viewModel: RideSniperViewModel, onOpenDebug: () -> Unit) {
    val settings by viewModel.settings.collectAsState()

    // Saves one field and confirms it, e.g. "Minimum per km saved".
    fun saver(label: String, apply: (AppSettings, Double) -> AppSettings): (Double) -> Unit = { value ->
        viewModel.updateSettings { apply(it, value) }
        viewModel.notify("$label saved")
    }

    val perKm = "R/${Fmt.DISTANCE_UNIT}"

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        ScreenHeader("Settings", "Change a number, then tap ✓ or Done to save it.")

        if (Features.VEHICLE_COSTS) {
            SettingsSection("Vehicle costs") {
                NumberSettingField(
                    "Fuel price", settings.fuelPricePerLitre, unit = "R/${Fmt.FUEL_UNIT}", validRange = 5.0..60.0,
                    onSave = saver("Fuel price") { s, v -> s.copy(fuelPricePerLitre = v, lastFuelPriceEntry = v) }
                )
                NumberSettingField(
                    "Fuel consumption", settings.litresPer100Km, unit = "L/100 km", decimals = 1, validRange = 2.0..30.0,
                    onSave = saver("Fuel consumption") { s, v -> s.copy(litresPer100Km = v) }
                )
                NumberSettingField(
                    "Wear and maintenance", settings.wearCostPerKm, unit = perKm, validRange = 0.0..20.0,
                    onSave = saver("Wear cost") { s, v -> s.copy(wearCostPerKm = v) }
                )
            }
        }

        SettingsSection("Pay targets", "TAKE needs both preferred rates. Below the minimum is DECLINE.") {
            NumberSettingField(
                "Preferred per km", settings.preferredPerKm, unit = perKm, validRange = 0.0..100.0,
                onSave = saver("Preferred per km") { s, v -> s.copy(preferredPerKm = v) }
            )
            NumberSettingField(
                "Minimum per km", settings.minimumPerKm, unit = perKm, validRange = 0.0..100.0,
                onSave = saver("Minimum per km") { s, v -> s.copy(minimumPerKm = v) }
            )
            NumberSettingField(
                "Never below (hard decline)", settings.hardDeclinePerKm, unit = perKm, validRange = 0.0..100.0,
                onSave = saver("Hard decline") { s, v -> s.copy(hardDeclinePerKm = v) }
            )
            NumberSettingField(
                "Preferred per minute", settings.preferredPerMinute, unit = "R/min", validRange = 0.0..50.0,
                onSave = saver("Preferred per minute") { s, v -> s.copy(preferredPerMinute = v) }
            )
            NumberSettingField(
                "Minimum per minute", settings.minimumPerMinute, unit = "R/min", validRange = 0.0..50.0,
                onSave = saver("Minimum per minute") { s, v -> s.copy(minimumPerMinute = v) }
            )
        }

        SettingsSection("Ride length") {
            NumberSettingField(
                "Long pickup from", settings.longPickupThresholdKm, unit = Fmt.DISTANCE_UNIT, decimals = 1, validRange = 0.0..100.0,
                onSave = saver("Long pickup") { s, v -> s.copy(longPickupThresholdKm = v) }
            )
            NumberSettingField(
                "Long trip from", settings.longRideThresholdKm, unit = Fmt.DISTANCE_UNIT, decimals = 1, validRange = 0.0..300.0,
                onSave = saver("Long trip") { s, v -> s.copy(longRideThresholdKm = v) }
            )
            NumberSettingField(
                "Long trip needs at least", settings.longRidePreferredPerKm, unit = perKm, validRange = 0.0..100.0,
                onSave = saver("Long trip rate") { s, v -> s.copy(longRidePreferredPerKm = v) }
            )
        }

        SettingsSection("Airport trips") {
            NumberSettingField(
                "Airport minimum", settings.airportMinimumPerKm, unit = perKm, validRange = 0.0..100.0,
                onSave = saver("Airport minimum") { s, v -> s.copy(airportMinimumPerKm = v) }
            )
            NumberSettingField(
                "Airport preferred", settings.airportPreferredPerKm, unit = perKm, validRange = 0.0..100.0,
                onSave = saver("Airport preferred") { s, v -> s.copy(airportPreferredPerKm = v) }
            )
        }

        SettingsSection("Bubble") {
            SwitchRow(
                title = "Vibrate with the result",
                subtitle = "Different buzz for TAKE, MAYBE and DECLINE.",
                checked = settings.vibrationEnabled,
                onChange = { on ->
                    viewModel.updateSettings { it.copy(vibrationEnabled = on) }
                    viewModel.notify(if (on) "Vibration on" else "Vibration off")
                }
            )
            NumberSettingField(
                "Bubble size", settings.overlaySizeDp.toDouble(), unit = "dp", decimals = 0, validRange = 40.0..120.0,
                onSave = { v ->
                    viewModel.updateSettings { it.copy(overlaySizeDp = v.roundToInt()) }
                    viewModel.notify("Bubble size saved. Restart Ride Sniper to see it.")
                }
            )
        }

        SettingsSection("Reading offers") {
            ConfidenceSlider(
                value = settings.ocrConfidenceThreshold,
                onCommit = { v ->
                    viewModel.updateSettings { it.copy(ocrConfidenceThreshold = v) }
                    viewModel.notify("Confidence level saved")
                }
            )
            SwitchRow(
                title = "Process screenshots in memory only",
                subtitle = "Screenshots are never stored on the phone.",
                checked = settings.autoDeleteScreenshots,
                onChange = { on ->
                    viewModel.updateSettings { it.copy(autoDeleteScreenshots = on) }
                    viewModel.notify("Saved")
                }
            )
        }

        SettingsSection("Developer") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(onClick = onOpenDebug)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("OCR debug", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "See exactly what text was read from the last offer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, description: String? = null, content: @Composable ColumnScope.() -> Unit) {
    RsCard {
        SectionLabel(title)
        if (description != null) {
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

/**
 * Moves freely while dragging and saves once on release. Saving on every
 * movement wrote to storage dozens of times per drag and made the screen lag.
 */
@Composable
private fun ConfidenceSlider(value: Float, onCommit: (Float) -> Unit) {
    var dragValue by remember(value) { mutableFloatStateOf(value) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("Ask me to check below", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${(dragValue * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            "When the screen is hard to read, the result is marked so you can check the numbers.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = dragValue,
            onValueChange = { dragValue = it },
            onValueChangeFinished = { if (dragValue != value) onCommit(dragValue) },
            valueRange = 0f..1f
        )
    }
}
