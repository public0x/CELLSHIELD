package com.cellshield.app.btsdetection

import android.content.Context
import android.os.Build
import android.telephony.*
import androidx.annotation.RequiresApi
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashSet
import kotlin.math.abs

/**
 * Core BTS Detection Engine
 * Analyzes cellular network behavior to detect fake base stations
 */
class BTSDetector(private val context: Context) {

    // Detection data
    private val suspiciousCells = HashSet<String>()
    private var alertCount = 0
    private val cellHistory = mutableListOf<CellEvent>()
    private var consecutiveSuspiciousCount = 0

    // Configuration - Tune these values for your region
    companion object {
        // Detection thresholds
        private const val MIN_CONSECUTIVE_ALERTS = 3 //test:1 , normal: 3
        private const val SIGNAL_STRENGTH_THRESHOLD = -60// dBm test:-100, normal: 60
        private const val MAX_CELLS_NORMAL = 5 //test:2, normal: 5
        private const val MIN_REGISTERED_CELLS = 1

        // Known legitimate operators - ADD YOUR LOCAL CARRIERS HERE
        private val KNOWN_OPERATORS = setOf(
            // Malaysia Known Carriers
           "50210", "50214", "50218", "502152", "50212", "50219", "50213", "50216", "502156", "502151", "502155", "50220", "50217", "502154", "50211",
            "502157", "502150", "502153", "502152", "50201"
        )
    }

    /**
     * Main detection method - analyzes current network state
     * @return DetectionResult with risk assessment and details
     */
    fun detectFakeBTS(telephonyManager: TelephonyManager): DetectionResult {
        try {
            val cellInfoList = telephonyManager.allCellInfo
            val simOperator = telephonyManager.simOperator ?: "Unknown"
            val networkOperator = telephonyManager.networkOperator ?: "Unknown"

            val enhancedResult = performEnhancedDetection(
                telephonyManager,
                cellInfoList,
                simOperator,
                networkOperator
            )

            val shouldAlert = isHighConfidenceDetection(enhancedResult)

            if (shouldAlert) {
                consecutiveSuspiciousCount++
                if (consecutiveSuspiciousCount >= MIN_CONSECUTIVE_ALERTS) {
                    alertCount++
                }
            } else {
                consecutiveSuspiciousCount = 0
            }

            return DetectionResult(
                isThreats = shouldAlert,
                riskLevel = enhancedResult.riskLevel,
                confidence = getConfidenceLevel(),
                suspiciousCells = enhancedResult.suspiciousCells.toList(),
                detectionReasons = enhancedResult.detectionReasons,
                isDowngrade = enhancedResult.isDowngrade,
                cellCount = cellInfoList?.size ?: 0,
                registeredCellCount = cellInfoList?.count { it.isRegistered } ?: 0,
                alertCount = alertCount,
                simOperator = simOperator,
                networkOperator = networkOperator,
                cellDetails = parseCellDetails(cellInfoList)
            )
        } catch (e: SecurityException) {
            return DetectionResult(
                isThreats = false,
                riskLevel = 0,
                confidence = "ERROR",
                errorMessage = "Permission denied: ${e.message}"
            )
        } catch (e: Exception) {
            return DetectionResult(
                isThreats = false,
                riskLevel = 0,
                confidence = "ERROR",
                errorMessage = "Detection error: ${e.message}"
            )
        }
    }

    private fun performEnhancedDetection(
        telephonyManager: TelephonyManager,
        cellInfoList: List<CellInfo>?,
        simOperator: String,
        networkOperator: String
    ): EnhancedDetectionResult {
        val result = EnhancedDetectionResult()

        if (cellInfoList == null) {
            result.detectionReasons.add("No cell info available")
            return result
        }

        // 1. Basic sanity checks
        val basicChecks = performBasicSanityChecks(cellInfoList, simOperator, networkOperator)
        result.combineWith(basicChecks)

        // 2. Signal analysis
        val signalAnalysis = analyzeSignalPatterns(cellInfoList)
        result.combineWith(signalAnalysis)

        // 3. Network behavior analysis
        val networkAnalysis = analyzeNetworkBehavior(telephonyManager, cellInfoList)
        result.combineWith(networkAnalysis)

        // 4. Historical analysis
        val historicalAnalysis = analyzeHistoricalPatterns(cellInfoList)
        result.combineWith(historicalAnalysis)

        // 5. Operator verification
        val operatorAnalysis = verifyOperatorConsistency(simOperator, networkOperator, cellInfoList)
        result.combineWith(operatorAnalysis)

        return result
    }

    private fun performBasicSanityChecks(
        cellInfoList: List<CellInfo>,
        simOperator: String,
        networkOperator: String
    ): EnhancedDetectionResult {
        val result = EnhancedDetectionResult()
        val registeredCells = cellInfoList.filter { it.isRegistered }

        if (registeredCells.isEmpty()) {
            result.riskLevel += 2
            result.detectionReasons.add("No registered cells - device may be camped on fake tower")
        }

        if (cellInfoList.size > MAX_CELLS_NORMAL) {
            result.riskLevel += 1
            result.detectionReasons.add("High number of visible cells (${cellInfoList.size})")
        }

        if (simOperator != networkOperator && simOperator.isNotEmpty() && networkOperator.isNotEmpty()) {
            result.riskLevel += 2
            result.detectionReasons.add("Operator mismatch: SIM($simOperator) vs Network($networkOperator)")
        }

        return result
    }

    private fun analyzeSignalPatterns(cellInfoList: List<CellInfo>): EnhancedDetectionResult {
        val result = EnhancedDetectionResult()
        var suspiciousSignalCount = 0

        for (cellInfo in cellInfoList) {
            try {
                when (cellInfo) {
                    is CellInfoLte -> {
                        val signal = cellInfo.cellSignalStrength
                        if (signal.rsrp > SIGNAL_STRENGTH_THRESHOLD) {
                            suspiciousSignalCount++
                            result.suspiciousCells.add("LTE-${cellInfo.cellIdentity.ci}")
                        }
                    }
                    is CellInfoGsm -> {
                        val signal = cellInfo.cellSignalStrength
                        if (signal.dbm > SIGNAL_STRENGTH_THRESHOLD) {
                            suspiciousSignalCount++
                            result.suspiciousCells.add("GSM-${cellInfo.cellIdentity.cid}")
                        }
                    }
                    is CellInfoWcdma -> {
                        val signal = cellInfo.cellSignalStrength
                        if (signal.dbm > SIGNAL_STRENGTH_THRESHOLD) {
                            suspiciousSignalCount++
                            result.suspiciousCells.add("WCDMA-${cellInfo.cellIdentity.cid}")
                        }
                    }
                    is CellInfoNr -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val signal = cellInfo.cellSignalStrength as? CellSignalStrengthNr
                            signal?.ssRsrp?.let { rsrp ->
                                if (rsrp > SIGNAL_STRENGTH_THRESHOLD) {
                                    suspiciousSignalCount++
                                    result.suspiciousCells.add("NR-${(cellInfo.cellIdentity as? CellIdentityNr)?.nci ?: "unknown"}")
                                }
                            }
                        }
                    }
                }
            } catch (e: SecurityException) {
                // Skip cells we can't access
            }
        }

        if (suspiciousSignalCount >= 2) {
            result.riskLevel += suspiciousSignalCount
            result.detectionReasons.add("Multiple very strong signals detected ($suspiciousSignalCount cells)")
        }

        return result
    }

    private fun analyzeNetworkBehavior(
        telephonyManager: TelephonyManager,
        cellInfoList: List<CellInfo>
    ): EnhancedDetectionResult {
        val result = EnhancedDetectionResult()

        try {
            val is2G = when (telephonyManager.networkType) {
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_EDGE -> true
                else -> false
            }

            if (is2G) {
                val hasAdvancedCells = cellInfoList.any { cellInfo ->
                    cellInfo is CellInfoLte || cellInfo is CellInfoNr
                }

                if (hasAdvancedCells) {
                    result.riskLevel += 3
                    result.isDowngrade = true
                    result.detectionReasons.add("2G downgrade with 4G/5G cells available")
                } else {
                    result.riskLevel += 1
                    result.detectionReasons.add("2G network (normal in some areas)")
                }
            }

            val networkTypes = cellInfoList.map { it.javaClass.simpleName }.distinct()
            if (networkTypes.size > 2) {
                result.riskLevel += 1
                result.detectionReasons.add("Multiple network technologies: ${networkTypes.joinToString()}")
            }

        } catch (e: SecurityException) {
            // Can't access network type
        }

        return result
    }

    private fun analyzeHistoricalPatterns(cellInfoList: List<CellInfo>): EnhancedDetectionResult {
        val result = EnhancedDetectionResult()
        val currentTime = System.currentTimeMillis()

        val currentEvent = CellEvent(
            timestamp = currentTime,
            cellCount = cellInfoList.size,
            suspiciousCount = result.suspiciousCells.size
        )
        cellHistory.add(currentEvent)

        cellHistory.removeAll { currentTime - it.timestamp > 30000 }

        if (cellHistory.size >= 3) {
            val cellCountChanges = cellHistory.windowed(2).map { (prev, curr) ->
                abs(curr.cellCount - prev.cellCount)
            }
            val rapidChanges = cellCountChanges.count { it > 2 }

            if (rapidChanges >= 2) {
                result.riskLevel += 2
                result.detectionReasons.add("Rapid cell changes detected")
            }
        }

        return result
    }

    private fun verifyOperatorConsistency(
        simOperator: String,
        networkOperator: String,
        cellInfoList: List<CellInfo>
    ): EnhancedDetectionResult {
        val result = EnhancedDetectionResult()
        val cellOperators = mutableSetOf<String>()

        for (cellInfo in cellInfoList) {
            try {
                val mccMnc = when (cellInfo) {
                    is CellInfoLte -> {
                        val identity = cellInfo.cellIdentity
                        "${identity.mccString ?: ""}${identity.mncString ?: ""}"
                    }
                    is CellInfoGsm -> {
                        val identity = cellInfo.cellIdentity
                        "${identity.mccString ?: ""}${identity.mncString ?: ""}"
                    }
                    is CellInfoWcdma -> {
                        val identity = cellInfo.cellIdentity
                        "${identity.mccString ?: ""}${identity.mncString ?: ""}"
                    }
                    is CellInfoNr -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val identity = cellInfo.cellIdentity as? CellIdentityNr
                            "${identity?.mccString ?: ""}${identity?.mncString ?: ""}"
                        } else ""
                    }
                    else -> ""
                }
                if (mccMnc.length >= 5) {
                    cellOperators.add(mccMnc)
                }
            } catch (e: SecurityException) {
                // Skip cells we can't access
            }
        }

        if (cellOperators.isNotEmpty() && simOperator.isNotEmpty()) {
            val mismatchedOperators = cellOperators.filter { it != simOperator }
            if (mismatchedOperators.isNotEmpty()) {
                result.riskLevel += 2
                result.detectionReasons.add("Cells from different operators: ${mismatchedOperators.joinToString()}")
            }
        }

        val unknownOperators = cellOperators.filter { it !in KNOWN_OPERATORS }
        if (unknownOperators.isNotEmpty()) {
            result.riskLevel += 1
            result.detectionReasons.add("Unknown operators: ${unknownOperators.joinToString()}")
        }

        return result
    }

    private fun isHighConfidenceDetection(result: EnhancedDetectionResult): Boolean {
        val riskThreshold = 5 //test: 2, normal: 5
        val minimumReasons = 2 //test:1, normal: 2

        return (result.riskLevel >= riskThreshold) ||
                (result.detectionReasons.size >= minimumReasons && result.riskLevel >= 3)
    }

    private fun getConfidenceLevel(): String {
        return when {
            consecutiveSuspiciousCount >= MIN_CONSECUTIVE_ALERTS -> "HIGH"
            consecutiveSuspiciousCount >= 1 -> "MEDIUM"
            else -> "LOW"
        }
    }

    private fun parseCellDetails(cellInfoList: List<CellInfo>?): List<CellDetail> {
        if (cellInfoList == null) return emptyList()

        return cellInfoList.mapIndexed { index, cellInfo ->
            try {
                when (cellInfo) {
                    is CellInfoLte -> {
                        val identity = cellInfo.cellIdentity
                        val signal = cellInfo.cellSignalStrength
                        CellDetail(
                            index = index + 1,
                            type = "LTE",
                            isRegistered = cellInfo.isRegistered,
                            mcc = identity.mccString ?: "Unknown",
                            mnc = identity.mncString ?: "Unknown",
                            cellId = identity.ci.toString(),
                            signalStrength = signal.rsrp,
                            additionalInfo = "TAC: ${identity.tac}, RSRQ: ${signal.rsrq} dB"
                        )
                    }
                    is CellInfoNr -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val identity = cellInfo.cellIdentity as? CellIdentityNr
                            val signal = cellInfo.cellSignalStrength as? CellSignalStrengthNr
                            CellDetail(
                                index = index + 1,
                                type = "5G NR",
                                isRegistered = cellInfo.isRegistered,
                                mcc = identity?.mccString ?: "Unknown",
                                mnc = identity?.mncString ?: "Unknown",
                                cellId = identity?.nci?.toString() ?: "Unknown",
                                signalStrength = signal?.ssRsrp ?: 0,
                                additionalInfo = "NCI: ${identity?.nci}"
                            )
                        } else {
                            CellDetail(index + 1, "5G NR", cellInfo.isRegistered)
                        }
                    }
                    is CellInfoGsm -> {
                        val identity = cellInfo.cellIdentity
                        val signal = cellInfo.cellSignalStrength
                        CellDetail(
                            index = index + 1,
                            type = "GSM (2G)",
                            isRegistered = cellInfo.isRegistered,
                            mcc = identity.mccString ?: "Unknown",
                            mnc = identity.mncString ?: "Unknown",
                            cellId = identity.cid.toString(),
                            signalStrength = signal.dbm,
                            additionalInfo = "LAC: ${identity.lac}"
                        )
                    }
                    is CellInfoWcdma -> {
                        val identity = cellInfo.cellIdentity
                        val signal = cellInfo.cellSignalStrength
                        CellDetail(
                            index = index + 1,
                            type = "WCDMA (3G)",
                            isRegistered = cellInfo.isRegistered,
                            mcc = identity.mccString ?: "Unknown",
                            mnc = identity.mncString ?: "Unknown",
                            cellId = identity.cid.toString(),
                            signalStrength = signal.dbm,
                            additionalInfo = "LAC: ${identity.lac}"
                        )
                    }
                    else -> CellDetail(index + 1, "Unknown", cellInfo.isRegistered)
                }
            } catch (e: Exception) {
                CellDetail(index + 1, "Error", false, errorMessage = e.message)
            }
        }
    }

    fun resetAlertCount() {
        alertCount = 0
    }

    fun clearHistory() {
        cellHistory.clear()
        suspiciousCells.clear()
        consecutiveSuspiciousCount = 0
    }
}

// Data classes
data class DetectionResult(
    val isThreats: Boolean,
    val riskLevel: Int,
    val confidence: String,
    val suspiciousCells: List<String> = emptyList(),
    val detectionReasons: List<String> = emptyList(),
    val isDowngrade: Boolean = false,
    val cellCount: Int = 0,
    val registeredCellCount: Int = 0,
    val alertCount: Int = 0,
    val simOperator: String = "Unknown",
    val networkOperator: String = "Unknown",
    val cellDetails: List<CellDetail> = emptyList(),
    val errorMessage: String? = null
)

data class CellDetail(
    val index: Int,
    val type: String,
    val isRegistered: Boolean,
    val mcc: String = "Unknown",
    val mnc: String = "Unknown",
    val cellId: String = "Unknown",
    val signalStrength: Int = 0,
    val additionalInfo: String = "",
    val errorMessage: String? = null
)

internal data class EnhancedDetectionResult(
    var riskLevel: Int = 0,
    var isDowngrade: Boolean = false,
    val suspiciousCells: MutableSet<String> = mutableSetOf(),
    val detectionReasons: MutableList<String> = mutableListOf()
) {
    fun combineWith(other: EnhancedDetectionResult) {
        this.riskLevel += other.riskLevel
        this.isDowngrade = this.isDowngrade || other.isDowngrade
        this.suspiciousCells.addAll(other.suspiciousCells)
        this.detectionReasons.addAll(other.detectionReasons)
    }
}

internal data class CellEvent(
    val timestamp: Long,
    val cellCount: Int,
    val suspiciousCount: Int
)