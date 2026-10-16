package com.ridesniper.app.model

import org.junit.Assert.*
import org.junit.Test

class StrategyPresetTest {

    @Test
    fun `default strategy is PICKY`() {
        assertEquals(StrategyPreset.PICKY, StrategyPreset.DEFAULT)
    }

    @Test
    fun `preset thresholds match spec`() {
        assertEquals(7.00, StrategyPreset.NORMAL.preferredPerKm, 0.001)
        assertEquals(1.50, StrategyPreset.NORMAL.preferredPerMinute, 0.001)

        assertEquals(9.00, StrategyPreset.PICKY.preferredPerKm, 0.001)
        assertEquals(2.00, StrategyPreset.PICKY.preferredPerMinute, 0.001)

        assertEquals(11.00, StrategyPreset.EXTREME.preferredPerKm, 0.001)
        assertEquals(2.50, StrategyPreset.EXTREME.preferredPerMinute, 0.001)
    }

    @Test
    fun `fromLabel resolves case-insensitively and falls back to default`() {
        assertEquals(StrategyPreset.EXTREME, StrategyPreset.fromLabel("extreme"))
        assertEquals(StrategyPreset.NORMAL, StrategyPreset.fromLabel("Normal"))
        assertEquals(StrategyPreset.DEFAULT, StrategyPreset.fromLabel("nonsense"))
    }

    @Test
    fun `default AppSettings match South African defaults`() {
        val settings = AppSettings()
        assertEquals(22.00, settings.fuelPricePerLitre, 0.001)
        assertEquals(6.5, settings.litresPer100Km, 0.001)
        assertEquals(1.00, settings.wearCostPerKm, 0.001)
        assertEquals(StrategyPreset.PICKY, settings.strategy)
        assertEquals(5.00, settings.hardDeclinePerKm, 0.001)
        assertEquals(7.00, settings.airportMinimumPerKm, 0.001)
        assertEquals(9.00, settings.airportPreferredPerKm, 0.001)
    }

    @Test
    fun `airport keyword list covers major South African airports`() {
        listOf("OR TAMBO", "LANSERIA", "CAPE TOWN INTERNATIONAL", "KING SHAKA").forEach {
            assertTrue(it, AIRPORT_KEYWORDS.contains(it))
        }
        assertFalse(AIRPORT_KEYWORDS.contains("AIRPORT"))
    }
}
