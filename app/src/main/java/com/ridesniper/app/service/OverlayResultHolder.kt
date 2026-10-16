package com.ridesniper.app.service

import com.ridesniper.app.model.RideCalculationResult
import com.ridesniper.app.ocr.OcrParseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Bridges the foreground service (which runs the capture/OCR/calculation
 * pipeline) with the Activity UI (which shows the debug screen and the
 * manual-correction sheet), without requiring a bound service connection.
 */
object OverlayResultHolder {

    private val _lastParseResult = MutableStateFlow<OcrParseResult?>(null)
    var lastParseResult: OcrParseResult?
        get() = _lastParseResult.value
        set(value) { _lastParseResult.value = value }
    val lastParseResultFlow: StateFlow<OcrParseResult?> = _lastParseResult

    private val _lastCalculationResult = MutableStateFlow<RideCalculationResult?>(null)
    var lastCalculationResult: RideCalculationResult?
        get() = _lastCalculationResult.value
        set(value) { _lastCalculationResult.value = value }
    val lastCalculationResultFlow: StateFlow<RideCalculationResult?> = _lastCalculationResult

    // True while the foreground service has the bubble up, so the app can show Running / Stop.
    private val _serviceRunning = MutableStateFlow(false)
    var serviceRunning: Boolean
        get() = _serviceRunning.value
        set(value) { _serviceRunning.value = value }
    val serviceRunningFlow: StateFlow<Boolean> = _serviceRunning
}
