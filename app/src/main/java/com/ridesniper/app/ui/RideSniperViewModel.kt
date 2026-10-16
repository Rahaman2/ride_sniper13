package com.ridesniper.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ridesniper.app.RideSniperApp
import com.ridesniper.app.calculator.RideCalculator
import com.ridesniper.app.data.database.RideEntity
import com.ridesniper.app.data.repository.RideRepository
import com.ridesniper.app.data.repository.RideStats
import com.ridesniper.app.data.repository.StatsFilter
import com.ridesniper.app.model.*
import com.ridesniper.app.ocr.OcrParseResult
import com.ridesniper.app.service.OverlayResultHolder
import com.ridesniper.app.settings.SettingsDataStore
import com.ridesniper.app.util.DestinationRiskStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RideSniperViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val repository: RideRepository,
    private val destinationRiskStore: DestinationRiskStore
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        settingsDataStore.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val statsFilter = MutableStateFlow(StatsFilter.LAST_7_DAYS)
    val currentStatsFilter: StateFlow<StatsFilter> = statsFilter

    val history: StateFlow<List<RideEntity>> = statsFilter.flatMapLatest { repository.observeHistory(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _stats = MutableStateFlow(RideStats())
    val stats: StateFlow<RideStats> = _stats

    private val _pendingCorrection = MutableStateFlow<OcrParseResult?>(null)
    val pendingCorrection: StateFlow<OcrParseResult?> = _pendingCorrection

    private val _latestResult = MutableStateFlow<RideCalculationResult?>(null)
    val latestResult: StateFlow<RideCalculationResult?> = _latestResult

    init {
        viewModelScope.launch {
            statsFilter.collect { filter -> _stats.value = repository.computeStats(filter) }
        }
        viewModelScope.launch {
            OverlayResultHolder.lastCalculationResultFlow.collect { _latestResult.value = it }
        }
    }

    fun setStatsFilter(filter: StatsFilter) {
        statsFilter.value = filter
        viewModelScope.launch { _stats.value = repository.computeStats(filter) }
    }

    fun refreshStats() {
        viewModelScope.launch { _stats.value = repository.computeStats(statsFilter.value) }
    }

    fun requestManualCorrection(parsed: OcrParseResult) {
        _pendingCorrection.value = parsed
    }

    fun dismissManualCorrection() {
        _pendingCorrection.value = null
    }

    fun submitManualCorrection(input: RideOfferInput) {
        viewModelScope.launch {
            val currentSettings = settings.value
            val zone = destinationRiskStore.getRating(input.destinationText)
            val result = RideCalculator.calculate(input, currentSettings, zone)
            _latestResult.value = result
            OverlayResultHolder.lastCalculationResult = result
            repository.saveResult(result)
            _pendingCorrection.value = null
            refreshStats()
        }
    }

    fun analyzeManualEntry(input: RideOfferInput) = submitManualCorrection(input)

    fun markRideAccepted(id: Long, accepted: Boolean) {
        viewModelScope.launch {
            repository.markAccepted(id, accepted)
            refreshStats()
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsDataStore.update(transform) }
    }

    fun applyStrategyPreset(preset: StrategyPreset) {
        viewModelScope.launch { settingsDataStore.applyStrategyPreset(preset) }
    }

    fun setFuelPrice(price: Double) {
        viewModelScope.launch { settingsDataStore.setFuelPrice(price) }
    }

    fun setZoneRating(destinationText: String, rating: ZoneRating) {
        destinationRiskStore.setRating(destinationText, rating)
    }

    fun getZoneRating(destinationText: String): ZoneRating = destinationRiskStore.getRating(destinationText)

    suspend fun exportCsv(): String = repository.exportCsv(statsFilter.value)

    class Factory(private val app: RideSniperApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RideSniperViewModel(app.settingsDataStore, app.rideRepository, app.destinationRiskStore) as T
        }
    }
}
