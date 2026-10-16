package com.ridesniper.app.ocr

import org.junit.Assert.*
import org.junit.Test

class OcrParserTest {

    @Test
    fun `parses a clean well-formatted offer`() {
        val raw = """
            R125.50
            Pickup 4 min
            2.1 km
            Trip 13 min
            5.4 km
            12 Long St -> Sandton City
        """.trimIndent()

        val result = OcrParser.parse(raw, mlKitConfidence = 0.9f)

        assertEquals(125.50, result.payout.value!!, 0.001)
        assertEquals(2.1, result.pickupKm.value!!, 0.001)
        assertEquals(5.4, result.tripKm.value!!, 0.001)
        assertEquals(4.0, result.pickupMinutes.value!!, 0.001)
        assertEquals(13.0, result.tripMinutes.value!!, 0.001)
        assertTrue(result.missingFields().isEmpty())
    }

    @Test
    fun `tolerates comma as decimal separator`() {
        val raw = "R125,50\n2,1 km\n18 min"
        val result = OcrParser.parse(raw, mlKitConfidence = 0.8f)
        assertEquals(125.50, result.payout.value!!, 0.001)
        assertEquals(2.1, result.pickupKm.value!!, 0.001)
    }

    @Test
    fun `tolerates a space between R and the amount`() {
        val raw = "R 85.40\n2.0 km\n10 min"
        val result = OcrParser.parse(raw, mlKitConfidence = 0.8f)
        assertEquals(85.40, result.payout.value!!, 0.001)
    }

    @Test
    fun `reads amounts with thousands separators`() {
        assertEquals(1250.0, OcrParser.parse("R1 250,00\n3.0 km\n20 min", 0.8f).payout.value!!, 0.001)
        assertEquals(1250.0, OcrParser.parse("R1,250.00\n3.0 km\n20 min", 0.8f).payout.value!!, 0.001)
    }

    @Test
    fun `regional road numbers are not mistaken for the fare`() {
        // R512 is a road to Lanseria; without cents it must lose to the real fare.
        val raw = "R65.20\nPickup 4 min\n1.5 km\nTrip 12 min\n8.0 km\nR512 to Lanseria"
        val result = OcrParser.parse(raw, mlKitConfidence = 0.8f)
        assertEquals(65.20, result.payout.value!!, 0.001)
    }

    @Test
    fun `R at the end of a word is not read as rand`() {
        val raw = "CAR 300\nR48.30\n2.0 km\n9 min"
        val result = OcrParser.parse(raw, mlKitConfidence = 0.8f)
        assertEquals(48.30, result.payout.value!!, 0.001)
    }

    @Test
    fun `tolerates misread zero as letter O between digits`() {
        val raw = "R1O5.5O\n3.2 km\n12 min"
        val result = OcrParser.parse(raw, mlKitConfidence = 0.7f)
        assertEquals(105.50, result.payout.value!!, 0.001)
    }

    @Test
    fun `tolerates misread one as letter l or I when adjacent to a real digit`() {
        // "l1.00" -> the leading 'l' sits immediately next to the real digit '1' and
        // gets normalized to "11.00"; "1l km" similarly normalizes to "11 km".
        val raw = "Rl1.00\n1l km\n12 min"
        val result = OcrParser.parse(raw, mlKitConfidence = 0.7f)
        assertEquals(11.00, result.payout.value!!, 0.001)
        assertEquals(11.0, result.pickupKm.value!!, 0.001)
    }

    @Test
    fun `detects airport keyword OR Tambo`() {
        val raw = "R280.00\nPickup 6 min\n1.8 km\nTrip 22 min\n24.2 km\nOR Tambo International Airport"
        val result = OcrParser.parse(raw, mlKitConfidence = 0.85f)
        assertTrue(result.isAirport)
    }

    @Test
    fun `detects surge and reservation keywords`() {
        val surgeRaw = "R182.20\nIncreased\n2.0 km\n10 min"
        val reservationRaw = "R400.00\nReservation\n3.0 km\n25 min"

        assertTrue(OcrParser.parse(surgeRaw, 0.8f).isSurge)
        assertTrue(OcrParser.parse(reservationRaw, 0.8f).isReservation)
    }

    @Test
    fun `missing fields are reported for manual correction`() {
        val raw = "R150.00\nsome garbled text with no distances"
        val result = OcrParser.parse(raw, mlKitConfidence = 0.5f)
        assertTrue(result.missingFields().isNotEmpty())
        assertNull(result.toInputOrNull())
    }

    @Test
    fun `falls back to positional order when labels are absent`() {
        // No "Pickup"/"Trip" labels — just two km values and two min values in order.
        val raw = "R97.50\n1.2 km\n6.5 km\n5 min\n17 min"
        val result = OcrParser.parse(raw, mlKitConfidence = 0.6f)
        assertEquals(1.2, result.pickupKm.value!!, 0.001)
        assertEquals(6.5, result.tripKm.value!!, 0.001)
        assertEquals(5.0, result.pickupMinutes.value!!, 0.001)
        assertEquals(17.0, result.tripMinutes.value!!, 0.001)
    }

    @Test
    fun `payout picks the largest rand figure on screen`() {
        // Uber sometimes shows a small per-km or surge sub-figure alongside the main offer.
        val raw = "R224.40\nR9.10/km\n3.5 km\n20 min"
        val result = OcrParser.parse(raw, mlKitConfidence = 0.8f)
        assertEquals(224.40, result.payout.value!!, 0.001)
    }
}
