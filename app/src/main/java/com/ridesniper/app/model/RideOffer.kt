package com.ridesniper.app.model

/**
 * Raw values extracted (or manually entered) for a single ride offer, before
 * any calculation is applied.
 */
data class RideOfferInput(
    val payout: Double,
    val pickupKm: Double,
    val tripKm: Double,
    val pickupMinutes: Double,
    val tripMinutes: Double,
    val destinationText: String = "",
    val isAirport: Boolean = false,
    val isSurge: Boolean = false,
    val isReservation: Boolean = false,
    val ocrConfidence: Float = 1.0f
)

/**
 * Fully computed result for an offer: every derived number plus the final
 * recommendation and warning flags. This is what the overlay card, the
 * result screen, and ride history all render from.
 */
data class RideCalculationResult(
    val input: RideOfferInput,
    val totalKm: Double,
    val totalMinutes: Double,
    val grossPerKm: Double,
    val grossPerMinute: Double,
    val fuelCost: Double,
    val wearCost: Double,
    val fuelCostPerKm: Double,
    val wearCostPerKm: Double,
    val totalVehicleCostPerKm: Double,
    val estimatedProfit: Double,
    val netPerKm: Double,
    val netPerHour: Double,
    val recommendation: Recommendation,
    val warnings: List<WarningFlag>,
    val strategyUsed: StrategyPreset,
    val destinationCategory: DestinationCategory,
    val effectiveRequiredPerKm: Double,
    val effectiveRequiredPerMinute: Double
)
