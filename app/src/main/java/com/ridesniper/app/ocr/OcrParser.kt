package com.ridesniper.app.ocr

import com.ridesniper.app.model.AIRPORT_KEYWORDS
import com.ridesniper.app.model.RideOfferInput
import java.util.Locale

/**
 * Result of a parse attempt. Any field that couldn't be confidently extracted
 * is left null so the UI can fall back to the manual correction sheet for
 * exactly those fields, pre-filling everything else.
 */
data class ParsedField<T>(val value: T?, val matchedRule: String?)

data class OcrParseResult(
    val payout: ParsedField<Double>,
    val pickupKm: ParsedField<Double>,
    val tripKm: ParsedField<Double>,
    val pickupMinutes: ParsedField<Double>,
    val tripMinutes: ParsedField<Double>,
    val destinationText: String,
    val isAirport: Boolean,
    val isSurge: Boolean,
    val isReservation: Boolean,
    val rawText: String,
    val confidence: Float
) {
    fun missingFields(): List<String> = buildList {
        if (payout.value == null) add("Offer amount")
        if (pickupKm.value == null) add("Pickup km")
        if (tripKm.value == null) add("Trip km")
        if (pickupMinutes.value == null) add("Pickup minutes")
        if (tripMinutes.value == null) add("Trip minutes")
    }

    fun toInputOrNull(): RideOfferInput? {
        val p = payout.value ?: return null
        val pm = pickupKm.value ?: return null
        val tm = tripKm.value ?: return null
        val pmin = pickupMinutes.value ?: return null
        val tmin = tripMinutes.value ?: return null
        return RideOfferInput(
            payout = p, pickupKm = pm, tripKm = tm,
            pickupMinutes = pmin, tripMinutes = tmin,
            destinationText = destinationText, isAirport = isAirport,
            isSurge = isSurge, isReservation = isReservation, ocrConfidence = confidence
        )
    }
}

/**
 * Parses raw OCR text scraped from an Uber Driver ride-offer screen. Written
 * to tolerate spacing errors, line breaks, commas-as-decimals, OCR-misread
 * characters (O->0, l->1, S->5), varying UI layouts, dark mode, and font
 * size differences, since none of that is under this app's control.
 */
object OcrParser {

    // Rand: R85.40   R 85.40   R85,40 (comma-as-decimal)   R85   R1 250,00   R1,250.00
    // Thousands separators are only accepted with cents, so "R85 120 min" is not read as R85 120.
    // The "R" must not follow a letter, so words like "CAR 3" or "DRIVER 4" don't match.
    private val moneyRegex = Regex(
        """(?<![A-Za-z])R\s?(\d{1,3}(?:[ \u00A0,]\d{3})+[.,]\d{2}|\d{1,4}(?:[.,]\d{1,2})?)"""
    )

    // Distance: 2.1 km   5.4km   0.9 km.   12 km
    private val distanceRegex = Regex("""(\d{1,3}(?:[.,]\d{1,2})?)\s?km\b""", RegexOption.IGNORE_CASE)

    // Duration: 18 min   5 min   4mins
    private val durationRegex = Regex("""(\d{1,3})\s?min""", RegexOption.IGNORE_CASE)

    // Labeled pickup/trip lines: "Pickup 4 min", "Trip 13 min", "Pickup: 2.1 km"
    private val pickupLineRegex = Regex("""pickup[^0-9]{0,6}(\d{1,3}(?:[.,]\d{1,2})?)\s?(km|min)""", RegexOption.IGNORE_CASE)
    private val tripLineRegex = Regex("""trip[^0-9]{0,6}(\d{1,3}(?:[.,]\d{1,2})?)\s?(km|min)""", RegexOption.IGNORE_CASE)

    // The last "." or "," followed by 1-2 digits is the decimal separator ("1 250,00", "85.40").
    private val centsRegex = Regex("""[.,](\d{1,2})$""")

    private val surgeRegex = Regex("""increased|surge|boost""", RegexOption.IGNORE_CASE)
    private val reservationRegex = Regex("""reserve|reservation|scheduled""", RegexOption.IGNORE_CASE)

    fun parse(rawText: String, mlKitConfidence: Float): OcrParseResult {
        val normalized = normalize(rawText)
        val lines = normalized.lines().map { it.trim() }.filter { it.isNotBlank() }

        val payout = parsePayout(normalized)
        val (pickupKm, tripKm) = parseDistances(normalized, lines)
        val (pickupMinutes, tripMinutes) = parseDurations(normalized, lines)

        val destinationText = extractDestination(lines)
        val isAirport = AIRPORT_KEYWORDS.any { normalized.uppercase().contains(it) }
        val isSurge = surgeRegex.containsMatchIn(normalized)
        val isReservation = reservationRegex.containsMatchIn(normalized)

        val fieldsFound = listOf(payout, pickupKm, tripKm, pickupMinutes, tripMinutes).count { it.value != null }
        val heuristicConfidence = (fieldsFound / 5.0f) * mlKitConfidence.coerceIn(0f, 1f).let { if (it == 0f) 1f else it }

        return OcrParseResult(
            payout = payout,
            pickupKm = pickupKm,
            tripKm = tripKm,
            pickupMinutes = pickupMinutes,
            tripMinutes = tripMinutes,
            destinationText = destinationText,
            isAirport = isAirport,
            isSurge = isSurge,
            isReservation = isReservation,
            rawText = rawText,
            confidence = heuristicConfidence.coerceIn(0f, 1f)
        )
    }

    /** Fixes common OCR misreads before regex matching: O/o -> 0, l/I -> 1 in numeric contexts, stray commas. */
    private fun normalize(text: String): String {
        var t = text
        // Fix letter-for-digit swaps when adjacent to a digit on either side, to avoid
        // mangling real words while still catching cases like "1O.5O" -> "10.50".
        t = t.replace(Regex("""(?<=\d)[oO]|[oO](?=\d)"""), "0")
        t = t.replace(Regex("""(?<=\d)[lI]|[lI](?=\d)"""), "1")
        // Collapse repeated whitespace, keep line breaks.
        t = t.lines().joinToString("\n") { it.replace(Regex("""[ \t]+"""), " ").trim() }
        return t
    }

    private fun toDouble(raw: String): Double? =
        raw.replace(",", ".").toDoubleOrNull()

    private data class MoneyMatch(val value: Double, val hasCents: Boolean)

    /** Parses "1 250,00", "1,250.00", "85,40" or "85" into rand. */
    private fun parseMoney(raw: String): MoneyMatch? {
        val compact = raw.replace(" ", "").replace("\u00A0", "")
        val cents = centsRegex.find(compact)
        val whole = (if (cents != null) compact.substring(0, cents.range.first) else compact)
            .replace(",", "").replace(".", "")
        val value = "$whole.${cents?.groupValues?.get(1) ?: "0"}".toDoubleOrNull() ?: return null
        return MoneyMatch(value, hasCents = cents != null)
    }

    private fun parsePayout(text: String): ParsedField<Double> {
        val matches = moneyRegex.findAll(text).mapNotNull { parseMoney(it.groupValues[1]) }.toList()
        // South African regional roads (R21, R512, ...) look like rand amounts without cents,
        // so prefer amounts that show cents and only fall back to whole-rand matches if none do.
        val withCents = matches.filter { it.hasCents }
        val candidates = withCents.ifEmpty { matches }
        // The offer amount is almost always the largest rand figure on an Uber offer screen
        // (bigger than any per-km/per-minute sub-figures that sometimes appear).
        val value = candidates.maxOfOrNull { it.value }
        val rule = when {
            value == null -> null
            withCents.isNotEmpty() -> "moneyRegex.maxWithCents"
            else -> "moneyRegex.max"
        }
        return ParsedField(value, rule)
    }

    private fun parseDistances(text: String, lines: List<String>): Pair<ParsedField<Double>, ParsedField<Double>> {
        val pickupLabeled = pickupLineRegex.find(text)?.takeIf { it.groupValues[2].equals("km", true) }
            ?.let { toDouble(it.groupValues[1]) }
        val tripLabeled = tripLineRegex.find(text)?.takeIf { it.groupValues[2].equals("km", true) }
            ?.let { toDouble(it.groupValues[1]) }

        if (pickupLabeled != null && tripLabeled != null) {
            return ParsedField(pickupLabeled, "pickupLineRegex") to ParsedField(tripLabeled, "tripLineRegex")
        }

        // Fallback heuristic: collect all "X km" values in on-screen order. Uber offer
        // cards conventionally list pickup distance first, trip distance second.
        val allDistances = distanceRegex.findAll(text).mapNotNull { toDouble(it.groupValues[1]) }.toList()
        val pickup = pickupLabeled ?: allDistances.getOrNull(0)
        val trip = tripLabeled ?: allDistances.getOrNull(1) ?: allDistances.getOrNull(0)?.takeIf { allDistances.size == 1 }

        val pickupRule = if (pickupLabeled != null) "pickupLineRegex" else if (pickup != null) "distanceRegex.order[0]" else null
        val tripRule = if (tripLabeled != null) "tripLineRegex" else if (trip != null) "distanceRegex.order[1]" else null

        return ParsedField(pickup, pickupRule) to ParsedField(trip, tripRule)
    }

    private fun parseDurations(text: String, lines: List<String>): Pair<ParsedField<Double>, ParsedField<Double>> {
        val pickupLabeled = pickupLineRegex.find(text)?.takeIf { it.groupValues[2].equals("min", true) }
            ?.let { toDouble(it.groupValues[1]) }
        val tripLabeled = tripLineRegex.find(text)?.takeIf { it.groupValues[2].equals("min", true) }
            ?.let { toDouble(it.groupValues[1]) }

        if (pickupLabeled != null && tripLabeled != null) {
            return ParsedField(pickupLabeled, "pickupLineRegex") to ParsedField(tripLabeled, "tripLineRegex")
        }

        val allDurations = durationRegex.findAll(text).mapNotNull { toDouble(it.groupValues[1]) }.toList()
        val pickup = pickupLabeled ?: allDurations.getOrNull(0)
        val trip = tripLabeled ?: allDurations.getOrNull(1)

        val pickupRule = if (pickupLabeled != null) "pickupLineRegex" else if (pickup != null) "durationRegex.order[0]" else null
        val tripRule = if (tripLabeled != null) "tripLineRegex" else if (trip != null) "durationRegex.order[1]" else null

        return ParsedField(pickup, pickupRule) to ParsedField(trip, tripRule)
    }

    private fun extractDestination(lines: List<String>): String {
        // Heuristic: the destination line is usually the longest line that isn't a
        // money/km/min token by itself, and often follows an arrow or "to" marker.
        val candidate = lines.firstOrNull { line ->
            !moneyRegex.matches(line) &&
                !distanceRegex.matches(line) &&
                !durationRegex.matches(line) &&
                line.length > 4 &&
                (line.contains("->") || line.contains("to ", true) ||
                    AIRPORT_KEYWORDS.any { line.uppercase().contains(it) })
        }
        return candidate?.trim().orEmpty()
    }
}
