package com.ridesniper.app.util

import org.junit.Assert.*
import org.junit.Test

class FmtTest {

    @Test
    fun `money is shown in rand with a dot decimal`() {
        assertEquals("R85.40", Fmt.money(85.4))
        assertEquals("-R12.00", Fmt.money(-12.0))
    }

    @Test
    fun `rates and distances carry their units`() {
        assertEquals("R9.50/km", Fmt.perKm(9.5))
        assertEquals("R2.00/min", Fmt.perMinute(2.0))
        assertEquals("R150.00/hr", Fmt.perHour(150.0))
        assertEquals("12.3 km", Fmt.km(12.34))
    }

    @Test
    fun `hours use the 24-hour clock`() {
        assertEquals("07:00", Fmt.hour(7))
        assertEquals("17:00", Fmt.hour(17))
    }

    @Test
    fun `typed numbers accept a comma or a dot`() {
        assertEquals(22.5, Fmt.parseDecimal("22,50")!!, 0.001)
        assertEquals(22.5, Fmt.parseDecimal(" 22.50 ")!!, 0.001)
        assertNull(Fmt.parseDecimal("abc"))
    }
}
