package com.ridesniper.app.calculator

import com.ridesniper.app.model.*

/**
 * Pure, deterministic profitability calculator. No Android dependencies so it is
 * trivially unit-testable in isolation from OCR/overlay/service code.
 */
object RideCalculator {

    // Money thresholds for warnings and zone adjustments, in rand.
    private const val BAD_RETURN_ZONE_SURCHARGE_PER_KM = 1.00
    private const val HIGH_WEAR_NET_PER_KM = 3.00
    private const val BAD_DEAL_MIN_PROFIT = 25.0
    private const val BAD_DEAL_NET_PER_KM = 2.00
    private const val GREAT_DEAL_MIN_PROFIT = 60.0

    fun calculate(input: RideOfferInput, settings: AppSettings, zoneRating: ZoneRating = ZoneRating.NEUTRAL): RideCalculationResult {
        val totalKm = (input.pickupKm + input.tripKm).coerceAtLeast(0.01)
        val totalMinutes = (input.pickupMinutes + input.tripMinutes).coerceAtLeast(0.01)

        val grossPerKm = input.payout / totalKm
        val grossPerMinute = input.payout / totalMinutes

        // With vehicle costs switched off, profit equals the payout and every "net" figure is gross.
        val costs = settings.includeVehicleCosts
        // L/100 km -> litres per km, times R/litre.
        val fuelCostPerKm = if (costs) settings.fuelPricePerLitre * settings.litresPer100Km / 100.0 else 0.0
        val wearCostPerKm = if (costs) settings.wearCostPerKm else 0.0
        val fuelCost = fuelCostPerKm * totalKm
        val wearCost = wearCostPerKm * totalKm
        val totalVehicleCostPerKm = fuelCostPerKm + wearCostPerKm

        val estimatedProfit = input.payout - fuelCost - wearCost
        val netPerKm = estimatedProfit / totalKm
        val netPerHour = (estimatedProfit / totalMinutes) * 60.0

        val destinationCategory = classifyDestination(input.destinationText, input.isAirport)
        val isAirportRide = destinationCategory == DestinationCategory.AIRPORT

        // Determine the effective required thresholds for THIS ride, layering
        // long-ride, airport, and bad-return-zone adjustments on top of the
        // strategy baseline. The strictest applicable requirement wins.
        var requiredPerKm = settings.minimumPerKm
        var preferredPerKm = settings.preferredPerKm

        if (totalKm > settings.longRideThresholdKm) {
            requiredPerKm = maxOf(requiredPerKm, settings.longRidePreferredPerKm)
        }
        if (isAirportRide) {
            requiredPerKm = maxOf(requiredPerKm, settings.airportMinimumPerKm)
            preferredPerKm = maxOf(preferredPerKm, settings.airportPreferredPerKm)
        }
        if (zoneRating == ZoneRating.BAD_RETURN) {
            // Bad return zones raise the bar since the drive back is likely unpaid.
            requiredPerKm += BAD_RETURN_ZONE_SURCHARGE_PER_KM
            preferredPerKm += BAD_RETURN_ZONE_SURCHARGE_PER_KM
        }

        val requiredPerMinute = settings.minimumPerMinute
        val preferredPerMinute = settings.preferredPerMinute

        val recommendation = classify(
            grossPerKm = grossPerKm,
            grossPerMinute = grossPerMinute,
            requiredPerKm = requiredPerKm,
            preferredPerKm = preferredPerKm,
            requiredPerMinute = requiredPerMinute,
            preferredPerMinute = preferredPerMinute,
            hardDeclinePerKm = settings.hardDeclinePerKm
        )

        val warnings = buildWarnings(
            input = input,
            settings = settings,
            totalKm = totalKm,
            grossPerKm = grossPerKm,
            grossPerMinute = grossPerMinute,
            requiredPerKm = requiredPerKm,
            requiredPerMinute = requiredPerMinute,
            preferredPerKm = preferredPerKm,
            preferredPerMinute = preferredPerMinute,
            estimatedProfit = estimatedProfit,
            fuelCost = fuelCost,
            isAirportRide = isAirportRide,
            zoneRating = zoneRating
        )

        return RideCalculationResult(
            input = input,
            totalKm = totalKm,
            totalMinutes = totalMinutes,
            grossPerKm = grossPerKm,
            grossPerMinute = grossPerMinute,
            fuelCost = fuelCost,
            wearCost = wearCost,
            fuelCostPerKm = fuelCostPerKm,
            wearCostPerKm = wearCostPerKm,
            totalVehicleCostPerKm = totalVehicleCostPerKm,
            estimatedProfit = estimatedProfit,
            netPerKm = netPerKm,
            netPerHour = netPerHour,
            recommendation = recommendation,
            warnings = warnings,
            strategyUsed = settings.strategy,
            destinationCategory = destinationCategory,
            effectiveRequiredPerKm = requiredPerKm,
            effectiveRequiredPerMinute = requiredPerMinute
        )
    }

    private fun classify(
        grossPerKm: Double,
        grossPerMinute: Double,
        requiredPerKm: Double,
        preferredPerKm: Double,
        requiredPerMinute: Double,
        preferredPerMinute: Double,
        hardDeclinePerKm: Double
    ): Recommendation {
        if (grossPerKm < hardDeclinePerKm) return Recommendation.HARD_DECLINE

        val meetsGreen = grossPerKm >= preferredPerKm && grossPerMinute >= preferredPerMinute
        if (meetsGreen) return Recommendation.TAKE

        val belowRed = grossPerKm < requiredPerKm || grossPerMinute < requiredPerMinute
        if (belowRed) return Recommendation.DECLINE

        // Falls between the required minimum and the preferred/green bar -> MAYBE.
        return Recommendation.MAYBE
    }

    private fun buildWarnings(
        input: RideOfferInput,
        settings: AppSettings,
        totalKm: Double,
        grossPerKm: Double,
        grossPerMinute: Double,
        requiredPerKm: Double,
        requiredPerMinute: Double,
        preferredPerKm: Double,
        preferredPerMinute: Double,
        estimatedProfit: Double,
        fuelCost: Double,
        isAirportRide: Boolean,
        zoneRating: ZoneRating
    ): List<WarningFlag> {
        val flags = mutableListOf<WarningFlag>()

        if (input.pickupKm > settings.longPickupThresholdKm) flags += WarningFlag.LONG_PICKUP
        if (input.tripKm > settings.longRideThresholdKm) flags += WarningFlag.LONG_RIDE
        if (grossPerKm < requiredPerKm) flags += WarningFlag.LOW_PAY_PER_KM
        if (grossPerMinute < requiredPerMinute) flags += WarningFlag.LOW_PAY_PER_MINUTE
        if (isAirportRide) flags += WarningFlag.AIRPORT_RISK
        if (zoneRating == ZoneRating.BAD_RETURN) flags += WarningFlag.DEADHEAD_RISK

        // Fuel heavy: fuel cost eats an outsized share of the payout.
        if (input.payout > 0 && fuelCost / input.payout > 0.25) flags += WarningFlag.FUEL_HEAVY

        // High wear: long total mileage combined with weak net-per-km.
        val netPerKm = estimatedProfit / totalKm
        if (settings.includeVehicleCosts && totalKm > settings.longRideThresholdKm && netPerKm < HIGH_WEAR_NET_PER_KM) flags += WarningFlag.HIGH_WEAR

        if (estimatedProfit < BAD_DEAL_MIN_PROFIT || netPerKm < BAD_DEAL_NET_PER_KM) flags += WarningFlag.BAD_DEAL

        if (grossPerKm >= preferredPerKm && grossPerMinute >= preferredPerMinute && estimatedProfit > GREAT_DEAL_MIN_PROFIT) {
            flags += WarningFlag.GREAT_DEAL
        }

        return flags
    }

    private fun classifyDestination(destinationText: String, explicitAirportFlag: Boolean): DestinationCategory {
        val upper = destinationText.uppercase()
        val airportMatch = explicitAirportFlag || AIRPORT_KEYWORDS.any { upper.contains(it) }
        return when {
            airportMatch -> DestinationCategory.AIRPORT
            listOf("CBD", "CITY CENTRE", "CITY CENTER", "DOWNTOWN").any { upper.contains(it) } -> DestinationCategory.DOWNTOWN
            destinationText.isBlank() -> DestinationCategory.UNKNOWN
            else -> DestinationCategory.SUBURBAN
        }
    }
}
