package com.ridesniper.app.model

/**
 * The three built-in strategy presets. Values are R/km and R/minute preferred
 * minimums used as the baseline before per-ride adjustments (long ride, airport,
 * bad-return-zone) are applied.
 */
enum class StrategyPreset(
    val label: String,
    val preferredPerKm: Double,
    val preferredPerMinute: Double
) {
    NORMAL("Normal", 7.00, 1.50),
    PICKY("Picky", 9.00, 2.00),
    EXTREME("Extreme", 11.00, 2.50);

    companion object {
        val DEFAULT = PICKY

        fun fromLabel(label: String): StrategyPreset =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: DEFAULT
    }
}

enum class Recommendation { TAKE, MAYBE, DECLINE, HARD_DECLINE }

enum class DestinationCategory { AIRPORT, SUBURBAN, DOWNTOWN, UNKNOWN }

enum class ZoneRating { GOOD_RETURN, BAD_RETURN, NEUTRAL }

enum class WarningFlag(val label: String) {
    LONG_PICKUP("LONG PICKUP"),
    LONG_RIDE("LONG RIDE"),
    LOW_PAY_PER_KM("LOW PAY PER KM"),
    LOW_PAY_PER_MINUTE("LOW PAY PER MINUTE"),
    AIRPORT_RISK("AIRPORT RISK"),
    DEADHEAD_RISK("DEADHEAD RISK"),
    FUEL_HEAVY("FUEL HEAVY"),
    HIGH_WEAR("HIGH WEAR"),
    BAD_DEAL("BAD DEAL"),
    GREAT_DEAL("GREAT DEAL")
}
