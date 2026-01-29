package com.scout.routeplanner.solver

import com.scout.routeplanner.data.DistanceRecord
import com.scout.routeplanner.data.OpeningsData
import com.scout.routeplanner.data.RouteConfig
import com.scout.routeplanner.data.RouteLeg
import com.scout.routeplanner.data.SolverResult

object RouteCardBuilder {

    fun build(
        result: SolverResult,
        openingsData: OpeningsData,
        distances: Map<Pair<String, String>, DistanceRecord>,
        config: RouteConfig
    ): List<RouteLeg> {
        val intermediateCps = openingsData.cpNames.filter { it != "Start" && it != "Finish" }
        val cpToIdx = intermediateCps.withIndex().associate { (i, name) -> name to i }
        val nSlots = openingsData.slotStarts.size
        val slotStarts = openingsData.slotStarts

        val slotLabels = slotStarts.map { s ->
            val h = s / 60
            val m = s % 60
            "%d:%02d".format(h, m)
        }

        // Build openings lookup
        val openAt = Array(intermediateCps.size) { i ->
            val name = intermediateCps[i]
            val slots = openingsData.openings[name] ?: emptyList()
            BooleanArray(nSlots) { s -> s < slots.size && slots[s] == 1 }
        }
        val finishSlots = openingsData.openings["Finish"] ?: emptyList()
        val finishOpen = BooleanArray(nSlots) { s -> s < finishSlots.size && finishSlots[s] == 1 }

        // Full sequence: Start -> route -> Finish
        val fullSeq = listOf("Start") + result.route + listOf("Finish")
        val legs = mutableListOf<RouteLeg>()
        var currentTime = config.startTime.toFloat()

        for (legNum in 0 until fullSeq.size - 1) {
            val fromName = fullSeq[legNum]
            val toName = fullSeq[legNum + 1]

            val record = distances[Pair(fromName, toName)]
            val d = record?.distance ?: 0f
            val h = record?.heightGain ?: 0f
            val ttMin = (d / config.speed) * 60f + (h / config.naismith)

            val arrival = currentTime + ttMin
            val slotIdx = arrivalToSlotIndex(arrival, slotStarts)
            var slotLabel = if (slotIdx in 0 until nSlots) slotLabels[slotIdx] else "--"
            var isOpen: Boolean
            var wait: Float
            var depart: Float

            when {
                toName == "Finish" -> {
                    isOpen = if (slotIdx in 0 until nSlots) finishOpen[slotIdx] else false
                    if (isOpen) {
                        wait = 0f
                        depart = arrival
                    } else {
                        depart = arrival
                        for (s in maxOf(0, slotIdx) until nSlots) {
                            if (finishOpen[s]) {
                                depart = maxOf(arrival, slotStarts[s].toFloat())
                                slotLabel = slotLabels[s]
                                isOpen = true
                                break
                            }
                        }
                        wait = depart - arrival
                    }
                }
                toName != "Start" -> {
                    val cpIdx = cpToIdx[toName]
                    if (cpIdx != null && slotIdx in 0 until nSlots) {
                        isOpen = openAt[cpIdx][slotIdx]
                        if (isOpen) {
                            wait = 0f
                            depart = arrival + config.dwell
                        } else {
                            val nextOpen = findNextOpenTime(cpIdx, arrival, openAt, slotStarts, nSlots)
                            if (nextOpen != null) {
                                wait = nextOpen - arrival
                                depart = nextOpen + config.dwell
                                val ws = arrivalToSlotIndex(nextOpen, slotStarts)
                                if (ws in 0 until nSlots) slotLabel = slotLabels[ws]
                                isOpen = true
                            } else {
                                wait = 0f
                                depart = arrival + config.dwell
                                isOpen = false
                            }
                        }
                    } else {
                        isOpen = false
                        wait = 0f
                        depart = arrival + config.dwell
                    }
                }
                else -> {
                    isOpen = true
                    wait = 0f
                    depart = arrival + config.dwell
                }
            }

            legs.add(
                RouteLeg(
                    leg = legNum + 1,
                    from = fromName,
                    to = toName,
                    distance = d,
                    heightGain = h,
                    travelMin = ttMin,
                    arrival = formatTime(arrival),
                    timeSlot = slotLabel,
                    isOpen = isOpen,
                    waitMin = wait,
                    depart = formatTime(depart),
                    cumulativeMin = depart - config.startTime
                )
            )
            currentTime = depart
        }
        return legs
    }

    private fun arrivalToSlotIndex(arrivalMinutes: Float, slotStarts: List<Int>): Int {
        if (arrivalMinutes < slotStarts[0]) return -1
        val whole = arrivalMinutes.toInt()
        val h = whole / 60
        val m = whole % 60
        val slotTime = h * 60 + if (m > 30) 30 else 0
        val last = slotStarts.last()
        if (slotTime > last) return slotStarts.size - 1
        return slotStarts.indexOfFirst { it == slotTime }
    }

    private fun findNextOpenTime(
        cpIdx: Int, arrivalMinutes: Float,
        openAt: Array<BooleanArray>, slotStarts: List<Int>, nSlots: Int
    ): Float? {
        var slot = arrivalToSlotIndex(arrivalMinutes, slotStarts)
        if (slot < 0) slot = 0
        for (s in slot until nSlots) {
            if (openAt[cpIdx][s]) {
                return maxOf(arrivalMinutes, slotStarts[s].toFloat())
            }
        }
        return null
    }

    fun formatTime(minutes: Float): String {
        val h = minutes.toInt() / 60
        val m = minutes.toInt() % 60
        return "%d:%02d".format(h, m)
    }

    fun computeSummary(
        legs: List<RouteLeg>,
        result: SolverResult,
        config: RouteConfig
    ): Map<String, String> {
        val totalDist = legs.sumOf { it.distance.toDouble() }
        val totalHeight = legs.sumOf { it.heightGain.toDouble() }
        val totalWait = legs.sumOf { it.waitMin.toDouble() }
        val finishTime = if (legs.isNotEmpty()) legs.last().depart else "--"
        val routeStr = "Start -> ${result.route.joinToString(" -> ")} -> Finish"

        return mapOf(
            "checkpoints" to "${result.count} / ${result.route.size.coerceAtLeast(result.count)}",
            "speed" to "%.2f km/h".format(config.speed),
            "distance" to "%.2f km".format(totalDist),
            "heightGain" to "%.0f m".format(totalHeight),
            "waitTime" to "%.1f min".format(totalWait),
            "finishTime" to finishTime,
            "route" to routeStr
        )
    }
}
