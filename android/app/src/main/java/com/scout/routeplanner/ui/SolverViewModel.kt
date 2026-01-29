package com.scout.routeplanner.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.scout.routeplanner.data.BngConverter
import com.scout.routeplanner.data.CsvParser
import com.scout.routeplanner.data.DistanceRecord
import com.scout.routeplanner.data.OpeningsData
import com.scout.routeplanner.data.RouteConfig
import com.scout.routeplanner.data.RouteLeg
import com.scout.routeplanner.data.SolverResult
import com.scout.routeplanner.solver.NativeSolver
import com.scout.routeplanner.solver.RouteCardBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SolverViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = "dovetrek"
        private const val KEY_OPENINGS_URI = "openings_uri"
        private const val KEY_DISTANCES_URI = "distances_uri"
        private const val KEY_SPEED = "speed"
        private const val KEY_DWELL = "dwell"
    }

    private val solver = NativeSolver()

    // Preferences helpers
    private fun savePreference(key: String, value: String) {
        getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(key, value).apply()
    }

    private fun savePreference(key: String, value: Float) {
        getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(key, value).apply()
    }

    private fun savePreference(key: String, value: Int) {
        getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(key, value).apply()
    }

    private fun getStringPreference(key: String): String? {
        return getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key, null)
    }

    private fun getFloatPreference(key: String, default: Float): Float {
        return getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(key, default)
    }

    private fun getIntPreference(key: String, default: Int): Int {
        return getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(key, default)
    }

    // Excluded checkpoints for Feature 3
    private val _excludedCheckpoints = MutableLiveData<MutableSet<String>>(mutableSetOf())
    val excludedCheckpoints: LiveData<MutableSet<String>> = _excludedCheckpoints

    fun toggleCheckpoint(name: String, excluded: Boolean) {
        val current = _excludedCheckpoints.value ?: mutableSetOf()
        if (excluded) current.add(name) else current.remove(name)
        _excludedCheckpoints.value = current
    }

    fun clearExclusions() {
        _excludedCheckpoints.value = mutableSetOf()
    }

    // Tracking state for Feature 4
    data class TrackingState(
        val visitedCheckpoints: MutableSet<String> = mutableSetOf(),
        val startedAt: Long? = null
    )

    private val _trackingState = MutableLiveData(TrackingState())
    val trackingState: LiveData<TrackingState> = _trackingState

    fun markVisited(checkpoint: String) {
        val current = _trackingState.value ?: TrackingState()
        current.visitedCheckpoints.add(checkpoint)
        _trackingState.value = current
    }

    fun unmarkVisited(checkpoint: String) {
        val current = _trackingState.value ?: TrackingState()
        current.visitedCheckpoints.remove(checkpoint)
        _trackingState.value = current
    }

    fun startTracking() {
        _trackingState.value = TrackingState(
            visitedCheckpoints = mutableSetOf(),
            startedAt = System.currentTimeMillis()
        )
    }

    fun resetTracking() {
        _trackingState.value = TrackingState()
    }

    private val _openingsData = MutableLiveData<OpeningsData?>()
    val openingsData: LiveData<OpeningsData?> = _openingsData

    private val _distances = MutableLiveData<Map<Pair<String, String>, DistanceRecord>?>()
    val distances: LiveData<Map<Pair<String, String>, DistanceRecord>?> = _distances

    private val _solverResult = MutableLiveData<SolverResult?>()
    val solverResult: LiveData<SolverResult?> = _solverResult

    private val _routeCard = MutableLiveData<List<RouteLeg>?>()
    val routeCard: LiveData<List<RouteLeg>?> = _routeCard

    private val _summary = MutableLiveData<Map<String, String>?>()
    val summary: LiveData<Map<String, String>?> = _summary

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _statusText = MutableLiveData("")
    val statusText: LiveData<String> = _statusText

    private val _errorText = MutableLiveData<String?>()
    val errorText: LiveData<String?> = _errorText

    private val _navigateToResults = MutableLiveData(false)
    val navigateToResults: LiveData<Boolean> = _navigateToResults

    private val _modeBSpeed = MutableLiveData<Float?>(null)
    val modeBSpeed: LiveData<Float?> = _modeBSpeed

    private var currentConfig = RouteConfig()

    fun onNavigatedToResults() {
        _navigateToResults.value = false
    }

    fun loadOpenings(uri: Uri, persistUri: Boolean = true) {
        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw IllegalArgumentException("Cannot open file")
                    inputStream.use { CsvParser.parseOpenings(it) }
                }
                _openingsData.value = data
                clearExclusions()
                updateStatus()
                if (persistUri) {
                    savePreference(KEY_OPENINGS_URI, uri.toString())
                }
            } catch (e: Exception) {
                _errorText.value = "Error loading openings: ${e.message}"
            }
        }
    }

    fun loadDistances(uri: Uri, persistUri: Boolean = true) {
        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw IllegalArgumentException("Cannot open file")
                    inputStream.use { CsvParser.parseDistances(it) }
                }
                _distances.value = data
                updateStatus()
                if (persistUri) {
                    savePreference(KEY_DISTANCES_URI, uri.toString())
                }
            } catch (e: Exception) {
                _errorText.value = "Error loading distances: ${e.message}"
            }
        }
    }

    // Feature 1: Try to load last used files
    data class LastFilesResult(
        val openingsUri: Uri?,
        val distancesUri: Uri?,
        val speed: Float,
        val dwell: Int
    )

    fun getLastFilesInfo(): LastFilesResult {
        val openingsUriStr = getStringPreference(KEY_OPENINGS_URI)
        val distancesUriStr = getStringPreference(KEY_DISTANCES_URI)
        return LastFilesResult(
            openingsUri = openingsUriStr?.let { Uri.parse(it) },
            distancesUri = distancesUriStr?.let { Uri.parse(it) },
            speed = getFloatPreference(KEY_SPEED, 5.3f),
            dwell = getIntPreference(KEY_DWELL, 7)
        )
    }

    fun tryLoadLastFiles(
        takePersistablePermission: (Uri) -> Boolean
    ): Pair<String?, String?> {
        var openingsFileName: String? = null
        var distancesFileName: String? = null

        val lastFiles = getLastFilesInfo()

        lastFiles.openingsUri?.let { uri ->
            try {
                if (takePersistablePermission(uri)) {
                    loadOpenings(uri, persistUri = false)
                    openingsFileName = uri.lastPathSegment
                }
            } catch (e: Exception) {
                // Permission no longer valid, ignore
            }
        }

        lastFiles.distancesUri?.let { uri ->
            try {
                if (takePersistablePermission(uri)) {
                    loadDistances(uri, persistUri = false)
                    distancesFileName = uri.lastPathSegment
                }
            } catch (e: Exception) {
                // Permission no longer valid, ignore
            }
        }

        return Pair(openingsFileName, distancesFileName)
    }

    fun saveSpeedPreference(speed: Float) {
        savePreference(KEY_SPEED, speed)
    }

    fun saveDwellPreference(dwell: Int) {
        savePreference(KEY_DWELL, dwell)
    }

    private fun updateStatus() {
        val od = _openingsData.value
        val dist = _distances.value
        val parts = mutableListOf<String>()
        if (od != null) parts.add("${od.cpNames.size} checkpoints")
        if (dist != null) parts.add("${dist.size} distances")
        _statusText.value = if (parts.isNotEmpty()) "${parts.joinToString(", ")} loaded" else ""
    }

    fun solveMaxCheckpoints(speed: Float, dwell: Int) {
        val od = _openingsData.value
        val dist = _distances.value
        if (od == null || dist == null) {
            _errorText.value = "Please load both CSV files first"
            return
        }

        currentConfig = RouteConfig(speed = speed, dwell = dwell)
        _isLoading.value = true
        _errorText.value = null
        _modeBSpeed.value = null
        val excluded = _excludedCheckpoints.value ?: emptySet()

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    solver.solve(od, dist, currentConfig, excluded)
                }
                buildRouteCard(result)
                _solverResult.value = result
                _isLoading.value = false
                _navigateToResults.value = true
            } catch (e: Exception) {
                _isLoading.value = false
                _errorText.value = "Solver error: ${e.message}"
            }
        }
    }

    fun solveMinSpeed(dwell: Int) {
        val od = _openingsData.value
        val dist = _distances.value
        if (od == null || dist == null) {
            _errorText.value = "Please load both CSV files first"
            return
        }
        val excluded = _excludedCheckpoints.value ?: emptySet()
        val intermediateCps = od.cpNames.filter { it != "Start" && it != "Finish" && it !in excluded }
        val targetCount = intermediateCps.size

        _isLoading.value = true
        _errorText.value = null

        viewModelScope.launch {
            try {
                val (bestSpeed, bestResult) = withContext(Dispatchers.Default) {
                    var lo = 3.0f
                    var hi = 20.0f
                    val precision = 0.01f
                    var foundSpeed: Float? = null
                    var foundResult: SolverResult? = null

                    while (hi - lo > precision) {
                        val mid = (lo + hi) / 2f
                        val config = RouteConfig(speed = mid, dwell = dwell)
                        val result = solver.solve(od, dist, config, excluded)
                        if (result.count == targetCount) {
                            foundSpeed = mid
                            foundResult = result
                            hi = mid
                        } else {
                            lo = mid
                        }
                    }
                    Pair(foundSpeed, foundResult)
                }

                if (bestSpeed != null && bestResult != null) {
                    currentConfig = RouteConfig(speed = bestSpeed, dwell = dwell)
                    _modeBSpeed.value = bestSpeed
                    buildRouteCard(bestResult)
                    _solverResult.value = bestResult
                    _isLoading.value = false
                    _navigateToResults.value = true
                } else {
                    _isLoading.value = false
                    _errorText.value = "Cannot visit all checkpoints even at 20 km/h"
                }
            } catch (e: Exception) {
                _isLoading.value = false
                _errorText.value = "Solver error: ${e.message}"
            }
        }
    }

    private fun buildRouteCard(result: SolverResult) {
        val od = _openingsData.value ?: return
        val dist = _distances.value ?: return

        val card = RouteCardBuilder.build(result, od, dist, currentConfig)
        _routeCard.value = card
        _summary.value = RouteCardBuilder.computeSummary(card, result, currentConfig)
    }

    fun getRouteCardText(): String {
        val card = _routeCard.value ?: return ""
        val summaryMap = _summary.value ?: return ""
        val sb = StringBuilder()

        sb.appendLine("DoveTrek Route Summary")
        sb.appendLine("=".repeat(40))
        summaryMap.forEach { (k, v) -> sb.appendLine("  $k: $v") }
        sb.appendLine()
        sb.appendLine("Route Card")
        sb.appendLine("-".repeat(40))

        val header = "Leg | From | To | Dist | Height | Travel | Arrival | Slot | Open | Wait | Depart | Cumul"
        sb.appendLine(header)
        sb.appendLine("-".repeat(header.length))

        card.forEach { leg ->
            sb.appendLine(
                "${leg.leg} | ${leg.from} | ${leg.to} | " +
                "%.2f | %.0f | %.1f | ${leg.arrival} | ${leg.timeSlot} | " +
                "${if (leg.isOpen) "Yes" else "No"} | %.1f | ${leg.depart} | %.1f".format(
                    leg.distance, leg.heightGain, leg.travelMin, leg.waitMin, leg.cumulativeMin
                )
            )
        }
        return sb.toString()
    }

    fun getRouteCardHtml(): String {
        val card = _routeCard.value ?: return ""
        val summaryMap = _summary.value ?: return ""
        val modeBSpd = _modeBSpeed.value

        val sb = StringBuilder()
        sb.appendLine("<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>")
        sb.appendLine("<title>DoveTrek Route Card</title>")
        sb.appendLine("<style>")
        sb.appendLine("body{font-family:-apple-system,sans-serif;margin:16px;background:#fafafa;color:#333}")
        sb.appendLine(".card{background:#fff;border-radius:8px;padding:16px;margin-bottom:12px;box-shadow:0 1px 3px rgba(0,0,0,.12)}")
        sb.appendLine("h1{color:#2E7D32;font-size:20px;margin:0 0 4px} h2{color:#2E7D32;font-size:16px;margin:0 0 12px}")
        sb.appendLine(".subtitle{color:#666;font-size:14px;margin-bottom:16px}")
        sb.appendLine(".mode-b{color:#BF360C;font-weight:bold;font-size:15px;margin-bottom:8px}")
        sb.appendLine(".row{display:flex;justify-content:space-between;margin-bottom:4px;font-size:13px}")
        sb.appendLine(".row .label{color:#666} .row .value{font-weight:bold}")
        sb.appendLine(".route{font-size:12px;color:#555;margin-top:8px}")
        sb.appendLine("table{border-collapse:collapse;width:100%;font-size:11px}")
        sb.appendLine("th{background:#E8F5E9;color:#1B5E20;padding:6px 4px;text-align:right;white-space:nowrap}")
        sb.appendLine("th:nth-child(1),th:nth-child(2),th:nth-child(3){text-align:left}")
        sb.appendLine("td{padding:5px 4px;text-align:right;border-bottom:1px solid #eee}")
        sb.appendLine("td:nth-child(1),td:nth-child(2),td:nth-child(3){text-align:left}")
        sb.appendLine("tr:nth-child(even){background:#f5f5f5}")
        sb.appendLine(".open-yes{color:#2E7D32;font-weight:bold} .open-no{color:#d32f2f;font-weight:bold}")
        sb.appendLine("</style></head><body>")

        sb.appendLine("<div class='card'>")
        sb.appendLine("<h1>DoveTrek</h1><div class='subtitle'>Route Card</div>")
        if (modeBSpd != null) {
            sb.appendLine("<div class='mode-b'>Minimum speed for all CPs: %.2f km/h</div>".format(modeBSpd))
        }
        sb.appendLine("<h2>Route Summary</h2>")
        for ((k, v) in summaryMap) {
            if (k != "route") {
                val label = when (k) {
                    "checkpoints" -> "Checkpoints"
                    "speed" -> "Walking Speed"
                    "distance" -> "Total Distance"
                    "heightGain" -> "Height Gain"
                    "finishTime" -> "Finish Time"
                    else -> k
                }
                sb.appendLine("<div class='row'><span class='label'>$label</span><span class='value'>$v</span></div>")
            }
        }
        val route = summaryMap["route"] ?: ""
        sb.appendLine("<div class='route'>$route</div>")
        sb.appendLine("</div>")

        sb.appendLine("<div class='card' style='overflow-x:auto'>")
        sb.appendLine("<h2>Route Card</h2>")
        sb.appendLine("<table><thead><tr>")
        sb.appendLine("<th>#</th><th>From</th><th>To</th><th>Dist</th><th>Hght</th><th>Trvl</th><th>Arrive</th><th>Slot</th><th>Open</th><th>Wait</th><th>Depart</th><th>Cumul</th>")
        sb.appendLine("</tr></thead><tbody>")

        card.forEach { leg ->
            val openClass = if (leg.isOpen) "open-yes" else "open-no"
            val openText = if (leg.isOpen) "Yes" else "No"
            sb.appendLine("<tr>")
            sb.appendLine("<td>${leg.leg}</td><td>${leg.from}</td><td>${leg.to}</td>")
            sb.appendLine("<td>${"%.2f".format(leg.distance)}</td><td>${"%.0f".format(leg.heightGain)}</td><td>${"%.1f".format(leg.travelMin)}</td>")
            sb.appendLine("<td>${leg.arrival}</td><td>${leg.timeSlot}</td><td class='$openClass'>$openText</td>")
            sb.appendLine("<td>${"%.1f".format(leg.waitMin)}</td><td>${leg.depart}</td><td>${"%.0f".format(leg.cumulativeMin)}</td>")
            sb.appendLine("</tr>")
        }

        sb.appendLine("</tbody></table></div>")
        sb.appendLine("</body></html>")
        return sb.toString()
    }

    fun getGoogleMapsUrl(): String? {
        val result = _solverResult.value ?: return null
        val od = _openingsData.value ?: return null
        val bngRefs = od.bngRefs
        if (bngRefs.isEmpty()) return null

        // Build full route: Start -> route checkpoints -> Finish
        val fullRoute = listOf("Start") + result.route + listOf("Finish")

        val coords = fullRoute.mapNotNull { name ->
            val bng = bngRefs[name] ?: return@mapNotNull null
            BngConverter.convert(bng)
        }

        if (coords.size < 2) return null

        // Build Google Maps directions URL with slash-separated waypoints
        // data=!4m2!4m1!3e2 sets travel mode to walking
        val waypoints = coords.joinToString("/") { "%.6f,%.6f".format(it.lat, it.lng) }
        return "https://www.google.com/maps/dir/$waypoints/data=!4m2!4m1!3e2"
    }

    // Feature 2: GPX Export
    fun getRouteGpx(): String? {
        val result = _solverResult.value ?: return null
        val od = _openingsData.value ?: return null
        val card = _routeCard.value ?: return null
        val bngRefs = od.bngRefs
        if (bngRefs.isEmpty()) return null

        // Build full route: Start -> route checkpoints -> Finish
        val fullRoute = listOf("Start") + result.route + listOf("Finish")

        // Map checkpoint names to their coordinates and timing info
        val waypointsData = mutableListOf<Triple<String, BngConverter.LatLng, String>>()

        for ((index, name) in fullRoute.withIndex()) {
            val bng = bngRefs[name] ?: continue
            val latLng = BngConverter.convert(bng) ?: continue

            // Build description from route card
            val desc = when {
                index == 0 -> {
                    val leg = card.firstOrNull()
                    "Depart ${leg?.depart ?: "10:00"}"
                }
                index == fullRoute.lastIndex -> {
                    val leg = card.lastOrNull()
                    "Arrive ${leg?.arrival ?: "--"}"
                }
                else -> {
                    // Find the leg where this checkpoint is the destination
                    val arrivalLeg = card.find { it.to == name }
                    val departureLeg = card.find { it.from == name }
                    val arriveTime = arrivalLeg?.arrival ?: "--"
                    val departTime = departureLeg?.depart ?: "--"
                    "Arrive $arriveTime, Depart $departTime"
                }
            }
            waypointsData.add(Triple(name, latLng, desc))
        }

        if (waypointsData.size < 2) return null

        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val timestamp = dateFormat.format(Date())

        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="DoveTrek" xmlns="http://www.topografix.com/GPX/1/1">""")
        sb.appendLine("""  <metadata>""")
        sb.appendLine("""    <name>DoveTrek Route</name>""")
        sb.appendLine("""    <time>$timestamp</time>""")
        sb.appendLine("""  </metadata>""")

        // Waypoints
        for ((name, latLng, desc) in waypointsData) {
            sb.appendLine("""  <wpt lat="%.6f" lon="%.6f">""".format(latLng.lat, latLng.lng))
            sb.appendLine("""    <name>$name</name>""")
            sb.appendLine("""    <desc>$desc</desc>""")
            sb.appendLine("""  </wpt>""")
        }

        // Route
        sb.appendLine("""  <rte>""")
        sb.appendLine("""    <name>DoveTrek Route</name>""")
        for ((name, latLng, _) in waypointsData) {
            sb.appendLine("""    <rtept lat="%.6f" lon="%.6f">""".format(latLng.lat, latLng.lng))
            sb.appendLine("""      <name>$name</name>""")
            sb.appendLine("""    </rtept>""")
        }
        sb.appendLine("""  </rte>""")
        sb.appendLine("""</gpx>""")

        return sb.toString()
    }
}
