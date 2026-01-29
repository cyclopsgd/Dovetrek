package com.scout.routeplanner.solver

import com.scout.routeplanner.data.DistanceRecord
import com.scout.routeplanner.data.OpeningsData
import com.scout.routeplanner.data.RouteConfig
import com.scout.routeplanner.data.SolverResult

class NativeSolver {

    companion object {
        init {
            System.loadLibrary("routesolver")
        }

        private const val ALL_NODES = 19
        private const val START_IDX = 17
        private const val FINISH_IDX = 18
    }

    private external fun solveNative(
        travelTimeMatrix: FloatArray,
        openingsFlat: BooleanArray,
        finishOpenings: BooleanArray,
        slotStarts: IntArray,
        speed: Float, dwell: Int, naismith: Float,
        startTime: Int, endTime: Int,
        nCheckpoints: Int, nSlots: Int
    ): IntArray

    fun solve(
        openingsData: OpeningsData,
        distances: Map<Pair<String, String>, DistanceRecord>,
        config: RouteConfig,
        excludedCheckpoints: Set<String> = emptySet()
    ): SolverResult {
        val allNames = openingsData.cpNames
        val intermediateCps = allNames.filter { it != "Start" && it != "Finish" && it !in excludedCheckpoints }
        val n = intermediateCps.size
        val nSlots = openingsData.slotStarts.size

        // Build index mappings
        val cpToIdx = intermediateCps.withIndex().associate { (i, name) -> name to i }

        fun nodeIndex(name: String): Int = when (name) {
            "Start" -> START_IDX
            "Finish" -> FINISH_IDX
            else -> cpToIdx[name] ?: -1
        }

        fun nodeName(idx: Int): String = when (idx) {
            START_IDX -> "Start"
            FINISH_IDX -> "Finish"
            else -> intermediateCps[idx]
        }

        // Build travel time matrix
        val travelTimeMatrix = FloatArray(ALL_NODES * ALL_NODES) { Float.MAX_VALUE }
        for (i in 0 until ALL_NODES) {
            for (j in 0 until ALL_NODES) {
                if (i == j) continue
                val fromName = nodeName(i)
                val toName = nodeName(j)
                val record = distances[Pair(fromName, toName)] ?: continue
                val tt = (record.distance / config.speed) * 60f + (record.heightGain / config.naismith)
                travelTimeMatrix[i * ALL_NODES + j] = tt
            }
        }

        // Build openings flat array (n x nSlots)
        val openingsFlat = BooleanArray(n * nSlots)
        for (i in 0 until n) {
            val name = intermediateCps[i]
            val slots = openingsData.openings[name] ?: continue
            for (s in 0 until nSlots) {
                openingsFlat[i * nSlots + s] = if (s < slots.size) slots[s] == 1 else false
            }
        }

        // Finish openings
        val finishSlots = openingsData.openings["Finish"] ?: List(nSlots) { 0 }
        val finishOpenings = BooleanArray(nSlots) { s ->
            if (s < finishSlots.size) finishSlots[s] == 1 else false
        }

        val slotStarts = openingsData.slotStarts.toIntArray()

        // Call native solver
        val rawResult = solveNative(
            travelTimeMatrix, openingsFlat, finishOpenings, slotStarts,
            config.speed, config.dwell, config.naismith,
            config.startTime, config.endTime,
            n, nSlots
        )

        // Parse result: [count, route_length, finish_time_x100, route[0], ...]
        val count = rawResult[0]
        val routeLength = rawResult[1]
        val finishTime = rawResult[2] / 100.0f
        val route = (0 until routeLength).map { nodeName(rawResult[3 + it]) }

        return SolverResult(count, route, finishTime)
    }
}
