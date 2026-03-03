// Create: app/src/main/java/com/cellshield/app/btsdetection/DetectionBroadcaster.kt

package com.cellshield.app.btsdetection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Singleton broadcaster for detection results
 * Allows the background service to send results to the ViewModel/UI
 */
object DetectionBroadcaster {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _detectionResults = MutableSharedFlow<DetectionResult>(
        replay = 1, // Keep last result for new subscribers
        extraBufferCapacity = 10
    )

    val detectionResults: SharedFlow<DetectionResult> = _detectionResults.asSharedFlow()

    /**
     * Broadcast a detection result from the service (suspend version)
     */
    suspend fun broadcast(result: DetectionResult) {
        _detectionResults.emit(result)
    }

    /**
     * Non-suspend version for easy calling from Java/non-coroutine code
     */
    fun broadcastResult(result: DetectionResult) {
        scope.launch {
            _detectionResults.emit(result)
        }
    }
}