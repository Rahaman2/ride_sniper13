package com.ridesniper.app.data.repository

import com.ridesniper.app.data.database.AcceptedState
import com.ridesniper.app.data.database.RideDao
import com.ridesniper.app.data.database.RideEntity
import com.ridesniper.app.model.RideCalculationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

enum class StatsFilter(val label: String, val windowMillis: Long?) {
    TODAY("Today", 24L * 60 * 60 * 1000),
    LAST_7_DAYS("7 days", 7L * 24 * 60 * 60 * 1000),
    LAST_30_DAYS("30 days", 30L * 24 * 60 * 60 * 1000),
    ALL_TIME("All time", null)
}

data class RideStats(
    val offersAnalyzed: Int = 0,
    val offersAccepted: Int = 0,
    val offersDeclined: Int = 0,
    val acceptanceRate: Double = 0.0,
    val avgGrossPerKm: Double = 0.0,
    val avgGrossPerMinute: Double = 0.0,
    val avgNetPerKm: Double = 0.0,
    val avgNetPerHour: Double = 0.0,
    val avgPayout: Double = 0.0,
    val avgPickupKm: Double = 0.0,
    val avgTripKm: Double = 0.0,
    val avgTotalKm: Double = 0.0,
    val kmAvoidedByDeclining: Double = 0.0,
    val fuelAvoided: Double = 0.0,
    val wearAvoided: Double = 0.0,
    val moneyPreservedByDeclining: Double = 0.0,
    val bestRide: RideEntity? = null,
    val worstRide: RideEntity? = null,
    val mostProfitableHour: Int? = null,
    val leastProfitableHour: Int? = null
)

class RideRepository(private val dao: RideDao) {

    fun observeHistory(filter: StatsFilter): Flow<List<RideEntity>> {
        val window = filter.windowMillis
        return if (window == null) {
            dao.observeAll()
        } else {
            dao.observeSince(System.currentTimeMillis() - window)
        }
    }

    suspend fun saveResult(result: RideCalculationResult): Long {
        val entity = RideEntity(
            timestamp = System.currentTimeMillis(),
            offerAmount = result.input.payout,
            pickupKm = result.input.pickupKm,
            tripKm = result.input.tripKm,
            totalKm = result.totalKm,
            pickupMinutes = result.input.pickupMinutes,
            tripMinutes = result.input.tripMinutes,
            totalMinutes = result.totalMinutes,
            destinationText = result.input.destinationText,
            airportFlag = result.destinationCategory.name == "AIRPORT",
            grossPerKm = result.grossPerKm,
            grossPerMinute = result.grossPerMinute,
            fuelCost = result.fuelCost,
            wearCost = result.wearCost,
            estimatedProfit = result.estimatedProfit,
            netPerKm = result.netPerKm,
            netPerHour = result.netPerHour,
            recommendation = result.recommendation.name,
            warningFlags = result.warnings.joinToString(",") { it.name },
            strategyMode = result.strategyUsed.name,
            ocrConfidence = result.input.ocrConfidence
        )
        return dao.insert(entity)
    }

    suspend fun markAccepted(id: Long, accepted: Boolean) {
        dao.setAcceptedState(id, if (accepted) AcceptedState.ACCEPTED.name else AcceptedState.DECLINED.name)
    }

    suspend fun computeStats(filter: StatsFilter): RideStats {
        val rides = if (filter.windowMillis == null) dao.getAll() else dao.getSince(System.currentTimeMillis() - filter.windowMillis)
        if (rides.isEmpty()) return RideStats()

        val accepted = rides.count { it.acceptedState == AcceptedState.ACCEPTED.name }
        val declined = rides.count { it.acceptedState == AcceptedState.DECLINED.name }
        val declinedRides = rides.filter { it.acceptedState == AcceptedState.DECLINED.name }

        val avg = { values: List<Double> -> if (values.isEmpty()) 0.0 else values.sum() / values.size }

        val byHour = rides.groupBy { hourOf(it.timestamp) }
        val avgProfitByHour = byHour.mapValues { (_, list) -> avg(list.map { it.estimatedProfit }) }
        val mostProfitableHour = avgProfitByHour.maxByOrNull { it.value }?.key
        val leastProfitableHour = avgProfitByHour.minByOrNull { it.value }?.key

        return RideStats(
            offersAnalyzed = rides.size,
            offersAccepted = accepted,
            offersDeclined = declined,
            acceptanceRate = if (accepted + declined > 0) accepted.toDouble() / (accepted + declined) else 0.0,
            avgGrossPerKm = avg(rides.map { it.grossPerKm }),
            avgGrossPerMinute = avg(rides.map { it.grossPerMinute }),
            avgNetPerKm = avg(rides.map { it.netPerKm }),
            avgNetPerHour = avg(rides.map { it.netPerHour }),
            avgPayout = avg(rides.map { it.offerAmount }),
            avgPickupKm = avg(rides.map { it.pickupKm }),
            avgTripKm = avg(rides.map { it.tripKm }),
            avgTotalKm = avg(rides.map { it.totalKm }),
            kmAvoidedByDeclining = declinedRides.sumOf { it.totalKm },
            fuelAvoided = declinedRides.sumOf { it.fuelCost },
            wearAvoided = declinedRides.sumOf { it.wearCost },
            moneyPreservedByDeclining = declinedRides.sumOf { (it.fuelCost + it.wearCost) },
            bestRide = rides.maxByOrNull { it.estimatedProfit },
            worstRide = rides.minByOrNull { it.estimatedProfit },
            mostProfitableHour = mostProfitableHour,
            leastProfitableHour = leastProfitableHour
        )
    }

    suspend fun exportCsv(filter: StatsFilter): String {
        val rides = if (filter.windowMillis == null) dao.getAll() else dao.getSince(System.currentTimeMillis() - filter.windowMillis)
        val header = listOf(
            "timestamp", "payoutZAR", "pickupKm", "tripKm", "totalKm",
            "pickupMinutes", "tripMinutes", "totalMinutes", "grossPerKm", "grossPerMinute",
            "fuelCost", "wearCost", "estimatedProfit", "netPerKm", "netPerHour",
            "recommendation", "acceptedDeclined"
        ).joinToString(",")

        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val rows = rides.sortedBy { it.timestamp }.map { r ->
            listOf(
                fmt.format(Date(r.timestamp)), r.offerAmount, r.pickupKm, r.tripKm, r.totalKm,
                r.pickupMinutes, r.tripMinutes, r.totalMinutes, r.grossPerKm, r.grossPerMinute,
                r.fuelCost, r.wearCost, r.estimatedProfit, r.netPerKm, r.netPerHour,
                r.recommendation, r.acceptedState
            ).joinToString(",")
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private fun hourOf(timestampMillis: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestampMillis
        return cal.get(Calendar.HOUR_OF_DAY)
    }
}
