package com.ridesniper.app.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ridesniper.app.model.AppSettings
import com.ridesniper.app.model.StrategyPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ride_sniper_settings")

/**
 * Persists every editable setting (Core Feature 10) via Jetpack DataStore.
 * Applying a StrategyPreset updates the preferred R/km and R/minute fields
 * to that preset's baseline; the user can still hand-edit them afterward.
 */
class SettingsDataStore(private val context: Context) {

    private object Keys {
        val FUEL_PRICE = doublePreferencesKey("fuel_price_per_litre")
        val LITRES_PER_100KM = doublePreferencesKey("litres_per_100km")
        val WEAR_COST = doublePreferencesKey("wear_cost_per_km")
        val STRATEGY = stringPreferencesKey("strategy")
        val PREFERRED_PER_KM = doublePreferencesKey("preferred_per_km")
        val MINIMUM_PER_KM = doublePreferencesKey("minimum_per_km")
        val HARD_DECLINE_PER_KM = doublePreferencesKey("hard_decline_per_km")
        val PREFERRED_PER_MINUTE = doublePreferencesKey("preferred_per_minute")
        val MINIMUM_PER_MINUTE = doublePreferencesKey("minimum_per_minute")
        val LONG_PICKUP_THRESHOLD = doublePreferencesKey("long_pickup_threshold_km")
        val LONG_RIDE_THRESHOLD = doublePreferencesKey("long_ride_threshold_km")
        val LONG_RIDE_PREFERRED_PER_KM = doublePreferencesKey("long_ride_preferred_per_km")
        val AIRPORT_MIN_PER_KM = doublePreferencesKey("airport_min_per_km")
        val AIRPORT_PREFERRED_PER_KM = doublePreferencesKey("airport_preferred_per_km")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val OVERLAY_SIZE = intPreferencesKey("overlay_size_dp")
        val OVERLAY_POS_X = intPreferencesKey("overlay_position_x")
        val OVERLAY_POS_Y = intPreferencesKey("overlay_position_y")
        val OCR_CONFIDENCE_THRESHOLD = floatPreferencesKey("ocr_confidence_threshold")
        val AUTO_DELETE_SCREENSHOTS = booleanPreferencesKey("auto_delete_screenshots")
        val LAST_FUEL_PRICE = doublePreferencesKey("last_fuel_price_entry")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { p ->
        val default = AppSettings()
        AppSettings(
            fuelPricePerLitre = p[Keys.FUEL_PRICE] ?: default.fuelPricePerLitre,
            litresPer100Km = p[Keys.LITRES_PER_100KM] ?: default.litresPer100Km,
            wearCostPerKm = p[Keys.WEAR_COST] ?: default.wearCostPerKm,
            strategy = StrategyPreset.fromLabel(p[Keys.STRATEGY] ?: default.strategy.label),
            preferredPerKm = p[Keys.PREFERRED_PER_KM] ?: default.preferredPerKm,
            minimumPerKm = p[Keys.MINIMUM_PER_KM] ?: default.minimumPerKm,
            hardDeclinePerKm = p[Keys.HARD_DECLINE_PER_KM] ?: default.hardDeclinePerKm,
            preferredPerMinute = p[Keys.PREFERRED_PER_MINUTE] ?: default.preferredPerMinute,
            minimumPerMinute = p[Keys.MINIMUM_PER_MINUTE] ?: default.minimumPerMinute,
            longPickupThresholdKm = p[Keys.LONG_PICKUP_THRESHOLD] ?: default.longPickupThresholdKm,
            longRideThresholdKm = p[Keys.LONG_RIDE_THRESHOLD] ?: default.longRideThresholdKm,
            longRidePreferredPerKm = p[Keys.LONG_RIDE_PREFERRED_PER_KM] ?: default.longRidePreferredPerKm,
            airportMinimumPerKm = p[Keys.AIRPORT_MIN_PER_KM] ?: default.airportMinimumPerKm,
            airportPreferredPerKm = p[Keys.AIRPORT_PREFERRED_PER_KM] ?: default.airportPreferredPerKm,
            vibrationEnabled = p[Keys.VIBRATION_ENABLED] ?: default.vibrationEnabled,
            overlaySizeDp = p[Keys.OVERLAY_SIZE] ?: default.overlaySizeDp,
            overlayPositionX = p[Keys.OVERLAY_POS_X] ?: default.overlayPositionX,
            overlayPositionY = p[Keys.OVERLAY_POS_Y] ?: default.overlayPositionY,
            ocrConfidenceThreshold = p[Keys.OCR_CONFIDENCE_THRESHOLD] ?: default.ocrConfidenceThreshold,
            autoDeleteScreenshots = p[Keys.AUTO_DELETE_SCREENSHOTS] ?: default.autoDeleteScreenshots,
            lastFuelPriceEntry = p[Keys.LAST_FUEL_PRICE] ?: default.lastFuelPriceEntry
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val current = toSettings(p)
            val updated = transform(current)
            writeAll(p, updated)
        }
    }

    suspend fun applyStrategyPreset(preset: StrategyPreset) {
        update { it.copy(strategy = preset, preferredPerKm = preset.preferredPerKm, preferredPerMinute = preset.preferredPerMinute) }
    }

    suspend fun setFuelPrice(price: Double) {
        update { it.copy(fuelPricePerLitre = price, lastFuelPriceEntry = price) }
    }

    private fun toSettings(p: Preferences): AppSettings {
        val default = AppSettings()
        return AppSettings(
            fuelPricePerLitre = p[Keys.FUEL_PRICE] ?: default.fuelPricePerLitre,
            litresPer100Km = p[Keys.LITRES_PER_100KM] ?: default.litresPer100Km,
            wearCostPerKm = p[Keys.WEAR_COST] ?: default.wearCostPerKm,
            strategy = StrategyPreset.fromLabel(p[Keys.STRATEGY] ?: default.strategy.label),
            preferredPerKm = p[Keys.PREFERRED_PER_KM] ?: default.preferredPerKm,
            minimumPerKm = p[Keys.MINIMUM_PER_KM] ?: default.minimumPerKm,
            hardDeclinePerKm = p[Keys.HARD_DECLINE_PER_KM] ?: default.hardDeclinePerKm,
            preferredPerMinute = p[Keys.PREFERRED_PER_MINUTE] ?: default.preferredPerMinute,
            minimumPerMinute = p[Keys.MINIMUM_PER_MINUTE] ?: default.minimumPerMinute,
            longPickupThresholdKm = p[Keys.LONG_PICKUP_THRESHOLD] ?: default.longPickupThresholdKm,
            longRideThresholdKm = p[Keys.LONG_RIDE_THRESHOLD] ?: default.longRideThresholdKm,
            longRidePreferredPerKm = p[Keys.LONG_RIDE_PREFERRED_PER_KM] ?: default.longRidePreferredPerKm,
            airportMinimumPerKm = p[Keys.AIRPORT_MIN_PER_KM] ?: default.airportMinimumPerKm,
            airportPreferredPerKm = p[Keys.AIRPORT_PREFERRED_PER_KM] ?: default.airportPreferredPerKm,
            vibrationEnabled = p[Keys.VIBRATION_ENABLED] ?: default.vibrationEnabled,
            overlaySizeDp = p[Keys.OVERLAY_SIZE] ?: default.overlaySizeDp,
            overlayPositionX = p[Keys.OVERLAY_POS_X] ?: default.overlayPositionX,
            overlayPositionY = p[Keys.OVERLAY_POS_Y] ?: default.overlayPositionY,
            ocrConfidenceThreshold = p[Keys.OCR_CONFIDENCE_THRESHOLD] ?: default.ocrConfidenceThreshold,
            autoDeleteScreenshots = p[Keys.AUTO_DELETE_SCREENSHOTS] ?: default.autoDeleteScreenshots,
            lastFuelPriceEntry = p[Keys.LAST_FUEL_PRICE] ?: default.lastFuelPriceEntry
        )
    }

    private fun writeAll(p: MutablePreferences, s: AppSettings) {
        p[Keys.FUEL_PRICE] = s.fuelPricePerLitre
        p[Keys.LITRES_PER_100KM] = s.litresPer100Km
        p[Keys.WEAR_COST] = s.wearCostPerKm
        p[Keys.STRATEGY] = s.strategy.label
        p[Keys.PREFERRED_PER_KM] = s.preferredPerKm
        p[Keys.MINIMUM_PER_KM] = s.minimumPerKm
        p[Keys.HARD_DECLINE_PER_KM] = s.hardDeclinePerKm
        p[Keys.PREFERRED_PER_MINUTE] = s.preferredPerMinute
        p[Keys.MINIMUM_PER_MINUTE] = s.minimumPerMinute
        p[Keys.LONG_PICKUP_THRESHOLD] = s.longPickupThresholdKm
        p[Keys.LONG_RIDE_THRESHOLD] = s.longRideThresholdKm
        p[Keys.LONG_RIDE_PREFERRED_PER_KM] = s.longRidePreferredPerKm
        p[Keys.AIRPORT_MIN_PER_KM] = s.airportMinimumPerKm
        p[Keys.AIRPORT_PREFERRED_PER_KM] = s.airportPreferredPerKm
        p[Keys.VIBRATION_ENABLED] = s.vibrationEnabled
        p[Keys.OVERLAY_SIZE] = s.overlaySizeDp
        p[Keys.OVERLAY_POS_X] = s.overlayPositionX
        p[Keys.OVERLAY_POS_Y] = s.overlayPositionY
        p[Keys.OCR_CONFIDENCE_THRESHOLD] = s.ocrConfidenceThreshold
        p[Keys.AUTO_DELETE_SCREENSHOTS] = s.autoDeleteScreenshots
        p[Keys.LAST_FUEL_PRICE] = s.lastFuelPriceEntry
    }
}
