package com.ridesniper.app.calculator

import com.ridesniper.app.model.*
import org.junit.Assert.*
import org.junit.Test

class RideCalculatorTest {

    private val defaultSettings = AppSettings() // PICKY: R9.00/km, R2.00/min, minimum R6.00/km, hard R5.00/km

    @Test
    fun `basic formulas compute correctly`() {
        val input = RideOfferInput(
            payout = 200.0, pickupKm = 2.0, tripKm = 8.0,
            pickupMinutes = 5.0, tripMinutes = 15.0
        )
        val result = RideCalculator.calculate(input, defaultSettings)

        assertEquals(10.0, result.totalKm, 0.001)
        assertEquals(20.0, result.totalMinutes, 0.001)
        assertEquals(20.0, result.grossPerKm, 0.001)
        assertEquals(10.0, result.grossPerMinute, 0.001)

        val expectedFuelCost = 10.0 * defaultSettings.litresPer100Km / 100.0 * defaultSettings.fuelPricePerLitre
        val expectedWearCost = 10.0 * defaultSettings.wearCostPerKm
        assertEquals(expectedFuelCost, result.fuelCost, 0.001)
        assertEquals(expectedWearCost, result.wearCost, 0.001)
        assertEquals(200.0 - expectedFuelCost - expectedWearCost, result.estimatedProfit, 0.001)
    }

    @Test
    fun `fuel cost uses litres per 100 km`() {
        // 100 km at 6.5 L/100 km and R22/litre = 6.5 L * R22 = R143.
        val settings = defaultSettings.copy(fuelPricePerLitre = 22.0, litresPer100Km = 6.5)
        val input = RideOfferInput(payout = 1000.0, pickupKm = 0.0, tripKm = 100.0, pickupMinutes = 0.0, tripMinutes = 90.0)
        val result = RideCalculator.calculate(input, settings)
        assertEquals(143.0, result.fuelCost, 0.001)
        assertEquals(1.43, result.fuelCostPerKm, 0.001)
    }

    @Test
    fun `great offer is TAKE under picky strategy`() {
        val input = RideOfferInput(payout = 120.0, pickupKm = 1.0, tripKm = 9.0, pickupMinutes = 3.0, tripMinutes = 21.0)
        // R120 / 10 km = R12.00/km (>= 9.00), R120 / 24 min = R5.00/min (>= 2.00)
        val result = RideCalculator.calculate(input, defaultSettings)
        assertEquals(Recommendation.TAKE, result.recommendation)
    }

    @Test
    fun `offer between minimum and preferred is MAYBE`() {
        val input = RideOfferInput(payout = 75.0, pickupKm = 2.0, tripKm = 8.0, pickupMinutes = 5.0, tripMinutes = 15.0)
        // R75 / 10 km = R7.50/km -> between minimumPerKm(6.00) and preferredPerKm(9.00)
        val result = RideCalculator.calculate(input, defaultSettings)
        assertEquals(Recommendation.MAYBE, result.recommendation)
    }

    @Test
    fun `offer below minimum is DECLINE`() {
        val input = RideOfferInput(payout = 55.0, pickupKm = 2.0, tripKm = 8.0, pickupMinutes = 5.0, tripMinutes = 15.0)
        // R55 / 10 km = R5.50/km -> below minimumPerKm(6.00), above hardDecline(5.00)
        val result = RideCalculator.calculate(input, defaultSettings)
        assertEquals(Recommendation.DECLINE, result.recommendation)
    }

    @Test
    fun `offer below hard decline floor is HARD_DECLINE`() {
        val input = RideOfferInput(payout = 45.0, pickupKm = 2.0, tripKm = 8.0, pickupMinutes = 5.0, tripMinutes = 15.0)
        // R45 / 10 km = R4.50/km -> below hardDeclinePerKm(5.00)
        val result = RideCalculator.calculate(input, defaultSettings)
        assertEquals(Recommendation.HARD_DECLINE, result.recommendation)
    }

    @Test
    fun `long ride raises required per km threshold`() {
        // 20 total km, trip 18 km > longRideThresholdKm(16) -> requires >= longRidePreferredPerKm(7.00)
        val input = RideOfferInput(payout = 130.0, pickupKm = 2.0, tripKm = 18.0, pickupMinutes = 5.0, tripMinutes = 40.0)
        // R130 / 20 km = R6.50/km -> below the long-ride-adjusted required (7.00) -> DECLINE despite being above base minimum
        val result = RideCalculator.calculate(input, defaultSettings)
        assertTrue(result.warnings.contains(WarningFlag.LONG_RIDE))
        assertEquals(Recommendation.DECLINE, result.recommendation)
    }

    @Test
    fun `airport destination applies stricter minimum`() {
        val input = RideOfferInput(
            payout = 65.0, pickupKm = 2.0, tripKm = 8.0, pickupMinutes = 5.0, tripMinutes = 15.0,
            destinationText = "OR Tambo International, Terminal A", isAirport = true
        )
        // R65 / 10 km = R6.50/km -> above base minimum(6.00) but below airportMinimumPerKm(7.00) -> DECLINE
        val result = RideCalculator.calculate(input, defaultSettings)
        assertEquals(DestinationCategory.AIRPORT, result.destinationCategory)
        assertTrue(result.warnings.contains(WarningFlag.AIRPORT_RISK))
        assertEquals(Recommendation.DECLINE, result.recommendation)
    }

    @Test
    fun `airport keyword detected from destination text alone`() {
        val input = RideOfferInput(
            payout = 300.0, pickupKm = 2.0, tripKm = 8.0, pickupMinutes = 5.0, tripMinutes = 15.0,
            destinationText = "King Shaka International Airport"
        )
        val result = RideCalculator.calculate(input, defaultSettings)
        assertEquals(DestinationCategory.AIRPORT, result.destinationCategory)
    }

    @Test
    fun `airport industria suburb is not treated as an airport`() {
        val input = RideOfferInput(
            payout = 100.0, pickupKm = 2.0, tripKm = 8.0, pickupMinutes = 5.0, tripMinutes = 15.0,
            destinationText = "Airport Industria, Cape Town"
        )
        val result = RideCalculator.calculate(input, defaultSettings)
        assertEquals(DestinationCategory.SUBURBAN, result.destinationCategory)
    }

    @Test
    fun `cbd destination is classified as downtown`() {
        val input = RideOfferInput(
            payout = 100.0, pickupKm = 2.0, tripKm = 8.0, pickupMinutes = 5.0, tripMinutes = 15.0,
            destinationText = "Durban CBD"
        )
        val result = RideCalculator.calculate(input, defaultSettings)
        assertEquals(DestinationCategory.DOWNTOWN, result.destinationCategory)
    }

    @Test
    fun `bad return zone increases required per km`() {
        val input = RideOfferInput(payout = 200.0, pickupKm = 2.0, tripKm = 8.0, pickupMinutes = 5.0, tripMinutes = 15.0)
        val neutral = RideCalculator.calculate(input, defaultSettings, ZoneRating.NEUTRAL)
        val badReturn = RideCalculator.calculate(input, defaultSettings, ZoneRating.BAD_RETURN)

        assertTrue(badReturn.effectiveRequiredPerKm > neutral.effectiveRequiredPerKm)
        assertTrue(badReturn.warnings.contains(WarningFlag.DEADHEAD_RISK))
    }

    @Test
    fun `long pickup warning fires above threshold`() {
        // Pickup 6 km > longPickupThresholdKm(5)
        val input = RideOfferInput(payout = 200.0, pickupKm = 6.0, tripKm = 6.0, pickupMinutes = 10.0, tripMinutes = 15.0)
        val result = RideCalculator.calculate(input, defaultSettings)
        assertTrue(result.warnings.contains(WarningFlag.LONG_PICKUP))
    }

    @Test
    fun `great deal flag requires strong km, minute, and profit`() {
        // R150 / 10 km = R15/km, R7.50/min, profit ~R126 > R60
        val input = RideOfferInput(payout = 150.0, pickupKm = 1.0, tripKm = 9.0, pickupMinutes = 2.0, tripMinutes = 18.0)
        val result = RideCalculator.calculate(input, defaultSettings)
        assertTrue(result.warnings.contains(WarningFlag.GREAT_DEAL))
    }

    @Test
    fun `normal strategy preset uses looser thresholds than picky`() {
        val normalSettings = defaultSettings.copy(
            strategy = StrategyPreset.NORMAL,
            preferredPerKm = StrategyPreset.NORMAL.preferredPerKm,
            preferredPerMinute = StrategyPreset.NORMAL.preferredPerMinute
        )
        val input = RideOfferInput(payout = 75.0, pickupKm = 2.0, tripKm = 8.0, pickupMinutes = 5.0, tripMinutes = 15.0)
        // R7.50/km meets NORMAL's preferred (7.00) -> TAKE, whereas under PICKY this was MAYBE.
        val result = RideCalculator.calculate(input, normalSettings)
        assertEquals(Recommendation.TAKE, result.recommendation)
    }
}
