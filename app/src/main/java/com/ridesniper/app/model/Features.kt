package com.ridesniper.app.model

/**
 * Switches for features that are built but not shown to drivers yet.
 * Flip a value and rebuild to bring a feature back; no other code changes needed.
 */
object Features {
    /**
     * Fuel and wear deductions. Off for now: rides show what the trip pays.
     * Turning this on brings back the fuel price card, the cost settings,
     * "you keep" profit figures and the cost lines in stats.
     */
    const val VEHICLE_COSTS = false
}
