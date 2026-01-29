package com.scout.routeplanner.ui

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.scout.routeplanner.R
import com.scout.routeplanner.data.RouteLeg
import com.scout.routeplanner.databinding.FragmentProgressBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ProgressFragment : Fragment() {

    private var _binding: FragmentProgressBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SolverViewModel by activityViewModels()

    private lateinit var adapter: ProgressAdapter
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateScheduleStatus()
            handler.postDelayed(this, 30000) // Update every 30 seconds
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ProgressAdapter { checkpointName, isVisited ->
            if (isVisited) {
                viewModel.markVisited(checkpointName)
            } else {
                viewModel.unmarkVisited(checkpointName)
            }
        }

        binding.recyclerProgress.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerProgress.adapter = adapter

        // Observe route card and tracking state
        viewModel.routeCard.observe(viewLifecycleOwner) { card ->
            viewModel.trackingState.observe(viewLifecycleOwner) { state ->
                if (card != null) {
                    updateProgressList(card, state)
                }
            }
        }

        binding.buttonBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.buttonReset.setOnClickListener {
            viewModel.resetTracking()
            viewModel.startTracking()
        }

        // Start periodic updates
        handler.post(updateRunnable)
    }

    private fun updateProgressList(
        routeCard: List<RouteLeg>,
        trackingState: SolverViewModel.TrackingState
    ) {
        val visited = trackingState.visitedCheckpoints

        // Build list of checkpoints from route card
        // Start is always first, then each "to" checkpoint
        val checkpoints = mutableListOf<ProgressItem>()

        // Add Start
        val firstLeg = routeCard.firstOrNull()
        checkpoints.add(ProgressItem(
            name = "Start",
            scheduledArrival = firstLeg?.depart ?: "10:00",
            isVisited = true,  // Start is always visited
            isCurrent = false,
            isStart = true
        ))

        // Track which is the current (first non-visited) checkpoint
        var foundCurrent = false

        for (leg in routeCard) {
            val cpName = leg.to
            val isVisited = visited.contains(cpName)
            val isCurrent = !isVisited && !foundCurrent

            if (isCurrent) foundCurrent = true

            checkpoints.add(ProgressItem(
                name = cpName,
                scheduledArrival = leg.arrival,
                isVisited = isVisited,
                isCurrent = isCurrent,
                isStart = false
            ))
        }

        adapter.submitList(checkpoints)

        // Update progress count (exclude Start from count)
        val totalCps = checkpoints.size - 1  // Exclude Start
        val visitedCount = checkpoints.count { it.isVisited && !it.isStart }
        binding.textProgress.text = getString(R.string.progress_format, visitedCount, totalCps)

        // Update schedule status
        updateScheduleStatus()
    }

    private fun updateScheduleStatus() {
        val routeCard = viewModel.routeCard.value ?: return
        val trackingState = viewModel.trackingState.value ?: return
        val startedAt = trackingState.startedAt ?: return
        val visited = trackingState.visitedCheckpoints

        // Find the next unvisited checkpoint
        var nextCheckpoint: RouteLeg? = null
        for (leg in routeCard) {
            if (!visited.contains(leg.to)) {
                nextCheckpoint = leg
                break
            }
        }

        if (nextCheckpoint == null) {
            // All checkpoints visited
            binding.textScheduleStatus.text = "Route Complete!"
            binding.textScheduleStatus.setTextColor(Color.parseColor("#2E7D32"))
            return
        }

        // Calculate expected vs actual time
        val scheduledArrival = parseTime(nextCheckpoint.arrival)
        val now = Calendar.getInstance()

        if (scheduledArrival == null) {
            binding.textScheduleStatus.text = getString(R.string.on_schedule)
            binding.textScheduleStatus.setTextColor(Color.parseColor("#2E7D32"))
            return
        }

        // Convert scheduled time to today's date for comparison
        val scheduledTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, scheduledArrival.first)
            set(Calendar.MINUTE, scheduledArrival.second)
            set(Calendar.SECOND, 0)
        }

        val diffMinutes = (now.timeInMillis - scheduledTime.timeInMillis) / 60000

        when {
            diffMinutes in -5..5 -> {
                binding.textScheduleStatus.text = getString(R.string.on_schedule)
                binding.textScheduleStatus.setTextColor(Color.parseColor("#2E7D32"))
            }
            diffMinutes < -5 -> {
                val aheadMins = (-diffMinutes).toInt()
                val timeStr = formatTimeDiff(aheadMins)
                binding.textScheduleStatus.text = getString(R.string.ahead_of_schedule, timeStr)
                binding.textScheduleStatus.setTextColor(Color.parseColor("#1976D2"))
            }
            else -> {
                val behindMins = diffMinutes.toInt()
                val timeStr = formatTimeDiff(behindMins)
                binding.textScheduleStatus.text = getString(R.string.behind_schedule, timeStr)
                binding.textScheduleStatus.setTextColor(Color.parseColor("#D32F2F"))
            }
        }
    }

    private fun parseTime(timeStr: String): Pair<Int, Int>? {
        return try {
            val parts = timeStr.split(":")
            if (parts.size == 2) {
                Pair(parts[0].toInt(), parts[1].toInt())
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun formatTimeDiff(minutes: Int): String {
        return if (minutes >= 60) {
            val hours = minutes / 60
            val mins = minutes % 60
            if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
        } else {
            "${minutes}m"
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroyView()
        _binding = null
    }
}
