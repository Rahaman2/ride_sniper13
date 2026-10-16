package com.ridesniper.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AcceptedState { ACCEPTED, DECLINED, UNKNOWN }

@Entity(tableName = "ride_history")
data class RideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val offerAmount: Double,
    val pickupKm: Double,
    val tripKm: Double,
    val totalKm: Double,
    val pickupMinutes: Double,
    val tripMinutes: Double,
    val totalMinutes: Double,
    val destinationText: String,
    val airportFlag: Boolean,
    val grossPerKm: Double,
    val grossPerMinute: Double,
    val fuelCost: Double,
    val wearCost: Double,
    val estimatedProfit: Double,
    val netPerKm: Double,
    val netPerHour: Double,
    val recommendation: String,
    val warningFlags: String, // comma-separated WarningFlag names
    val strategyMode: String,
    val ocrConfidence: Float,
    val acceptedState: String = AcceptedState.UNKNOWN.name
)
