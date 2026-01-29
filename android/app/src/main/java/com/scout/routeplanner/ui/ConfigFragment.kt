package com.scout.routeplanner.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.scout.routeplanner.databinding.FragmentConfigBinding

class ConfigFragment : Fragment() {

    private var _binding: FragmentConfigBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SolverViewModel by activityViewModels()
    private var hasAttemptedAutoLoad = false

    private val openingsPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Take persistable permission for future use
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore if permission cannot be persisted
            }
            viewModel.loadOpenings(uri)
            binding.textOpeningsFile.text = uri.lastPathSegment ?: "Selected"
        }
    }

    private val distancesPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Take persistable permission for future use
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore if permission cannot be persisted
            }
            viewModel.loadDistances(uri)
            binding.textDistancesFile.text = uri.lastPathSegment ?: "Selected"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Feature 1: Try to load last used files on startup
        if (!hasAttemptedAutoLoad) {
            hasAttemptedAutoLoad = true
            tryLoadLastFiles()
        }

        binding.buttonLoadOpenings.setOnClickListener {
            openingsPicker.launch(arrayOf("text/*", "*/*"))
        }

        binding.buttonLoadDistances.setOnClickListener {
            distancesPicker.launch(arrayOf("text/*", "*/*"))
        }

        binding.sliderSpeed.addOnChangeListener { _, value, fromUser ->
            binding.textSpeedValue.text = "Walking Speed: %.1f km/h".format(value)
            if (fromUser) {
                viewModel.saveSpeedPreference(value)
            }
        }

        binding.buttonSolveModeA.setOnClickListener {
            val speed = binding.sliderSpeed.value
            val dwell = binding.editDwell.text.toString().toIntOrNull() ?: 7
            viewModel.saveDwellPreference(dwell)
            viewModel.solveMaxCheckpoints(speed, dwell)
        }

        binding.buttonSolveModeB.setOnClickListener {
            val dwell = binding.editDwell.text.toString().toIntOrNull() ?: 7
            viewModel.saveDwellPreference(dwell)
            viewModel.solveMinSpeed(dwell)
        }

        viewModel.statusText.observe(viewLifecycleOwner) { text ->
            binding.textStatus.text = text
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            binding.buttonSolveModeA.isEnabled = !loading
            binding.buttonSolveModeB.isEnabled = !loading
        }

        viewModel.errorText.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.navigateToResults.observe(viewLifecycleOwner) { navigate ->
            if (navigate) {
                viewModel.onNavigatedToResults()
                (activity as? MainActivity)?.showResults()
            }
        }

        // Feature 3: Observe openings data to populate checkpoint exclusion checkboxes
        viewModel.openingsData.observe(viewLifecycleOwner) { data ->
            updateCheckpointExclusions(data)
        }
    }

    private fun tryLoadLastFiles() {
        val lastFiles = viewModel.getLastFilesInfo()

        // Restore saved speed and dwell values
        binding.sliderSpeed.value = lastFiles.speed
        binding.textSpeedValue.text = "Walking Speed: %.1f km/h".format(lastFiles.speed)
        binding.editDwell.setText(lastFiles.dwell.toString())

        // Try to auto-load files
        val contentResolver = requireContext().contentResolver
        val (openingsFile, distancesFile) = viewModel.tryLoadLastFiles { uri ->
            try {
                // Check if we still have permission
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                true
            } catch (e: SecurityException) {
                false
            }
        }

        openingsFile?.let { binding.textOpeningsFile.text = it }
        distancesFile?.let { binding.textDistancesFile.text = it }
    }

    private fun updateCheckpointExclusions(data: com.scout.routeplanner.data.OpeningsData?) {
        binding.checkpointContainer.removeAllViews()

        if (data == null) {
            binding.cardCheckpoints.visibility = View.GONE
            return
        }

        val intermediates = data.cpNames.filter { it != "Start" && it != "Finish" }
        if (intermediates.isEmpty()) {
            binding.cardCheckpoints.visibility = View.GONE
            return
        }

        binding.cardCheckpoints.visibility = View.VISIBLE
        val excluded = viewModel.excludedCheckpoints.value ?: mutableSetOf()

        intermediates.forEach { cpName ->
            val checkbox = CheckBox(requireContext()).apply {
                text = cpName
                isChecked = !excluded.contains(cpName)  // checked = included
                setOnCheckedChangeListener { _, isChecked ->
                    viewModel.toggleCheckpoint(cpName, !isChecked)  // excluded = not checked
                }
            }
            binding.checkpointContainer.addView(checkbox)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
