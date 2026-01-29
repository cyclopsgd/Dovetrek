package com.scout.routeplanner.data

data class CheckpointOpenings(
    val name: String,
    val slots: BooleanArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CheckpointOpenings) return false
        return name == other.name && slots.contentEquals(other.slots)
    }
    override fun hashCode(): Int = 31 * name.hashCode() + slots.contentHashCode()
}

data class DistanceRecord(
    val distance: Float,
    val heightGain: Float
)

data class OpeningsData(
    val cpNames: List<String>,
    val slotStarts: List<Int>,
    val openings: Map<String, List<Int>>,
    val bngRefs: Map<String, String> = emptyMap()
)

data class RouteConfig(
    val speed: Float = 5.3f,
    val dwell: Int = 7,
    val naismith: Float = 10.0f,
    val startTime: Int = 600,
    val endTime: Int = 1020
)

data class SolverResult(
    val count: Int,
    val route: List<String>,
    val finishTime: Float
)

data class RouteLeg(
    val leg: Int,
    val from: String,
    val to: String,
    val distance: Float,
    val heightGain: Float,
    val travelMin: Float,
    val arrival: String,
    val timeSlot: String,
    val isOpen: Boolean,
    val waitMin: Float,
    val depart: String,
    val cumulativeMin: Float
)
