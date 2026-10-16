package com.ridesniper.app.model

/**
 * All user-editable settings, in rand (R), kilometres and litres.
 *
 * Defaults are a starting point for a typical South African e-hailing car
 * (small petrol hatch/sedan, ~6.5 L/100 km) and should be tuned by each
 * driver in Settings. Fuel price changes monthly; the driver updates it from
 * the Home screen.
 */
data class AppSettings(
    // Not stored: follows Features.VEHICLE_COSTS so the build decides. Tests can override it.
    val includeVehicleCosts: Boolean = Features.VEHICLE_COSTS,

    val fuelPricePerLitre: Double = 22.00,
    val litresPer100Km: Double = 6.5,
    val wearCostPerKm: Double = 1.00,

    val strategy: StrategyPreset = StrategyPreset.DEFAULT,

    // Editable overrides layered on top of the strategy preset.
    val preferredPerKm: Double = StrategyPreset.DEFAULT.preferredPerKm,
    val minimumPerKm: Double = 6.00,
    val hardDeclinePerKm: Double = 5.00,
    val preferredPerMinute: Double = StrategyPreset.DEFAULT.preferredPerMinute,
    val minimumPerMinute: Double = 1.50,

    val longPickupThresholdKm: Double = 5.0,
    val longRideThresholdKm: Double = 16.0,
    val longRidePreferredPerKm: Double = 7.00,

    val airportMinimumPerKm: Double = 7.00,
    val airportPreferredPerKm: Double = 9.00,

    val vibrationEnabled: Boolean = true,
    val overlaySizeDp: Int = 64,
    val overlayPositionX: Int = 0,
    val overlayPositionY: Int = 300,

    val ocrConfidenceThreshold: Float = 0.55f,
    val autoDeleteScreenshots: Boolean = true,

    val lastFuelPriceEntry: Double = 22.00
)

/**
 * Upper-case fragments that mark a destination as a South African airport.
 * Short codes are kept to the unambiguous ones; a bare "AIRPORT" is left out
 * on purpose because suburbs like Airport Industria (Cape Town) would match.
 */
val AIRPORT_KEYWORDS = listOf(
    "O.R. TAMBO",
    "OR TAMBO",
    "O R TAMBO",
    "JNB",
    "LANSERIA",
    "CAPE TOWN INTERNATIONAL",
    "CPT",
    "KING SHAKA",
    "DAWID STUURMAN",
    "BRAM FISCHER"
)
