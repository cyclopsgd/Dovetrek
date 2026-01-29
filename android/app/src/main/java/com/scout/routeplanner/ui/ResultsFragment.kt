package com.scout.routeplanner.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.scout.routeplanner.R
import com.scout.routeplanner.databinding.FragmentResultsBinding

class ResultsFragment : Fragment() {

    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SolverViewModel by activityViewModels()

    private lateinit var adapter: RouteLegAdapter

    private val exportFilePicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        if (uri != null) {
            val html = viewModel.getRouteCardHtml()
            try {
                requireContext().contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(html.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(requireContext(), "Route card exported", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val gpxExportPicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri ->
        if (uri != null) {
            val gpx = viewModel.getRouteGpx()
            if (gpx != null) {
                try {
                    requireContext().contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(gpx.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(requireContext(), getString(R.string.gpx_exported), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), getString(R.string.gpx_export_failed) + ": ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(requireContext(), getString(R.string.no_coordinates), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = RouteLegAdapter()
        binding.recyclerRouteCard.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerRouteCard.adapter = adapter

        viewModel.summary.observe(viewLifecycleOwner) { summary ->
            if (summary != null) {
                binding.textCheckpoints.text = summary["checkpoints"] ?: "--"
                binding.textSpeed.text = summary["speed"] ?: "--"
                binding.textDistance.text = summary["distance"] ?: "--"
                binding.textHeightGain.text = summary["heightGain"] ?: "--"
                binding.textFinishTime.text = summary["finishTime"] ?: "--"
                binding.textRoute.text = summary["route"] ?: "--"
            }
        }

        viewModel.modeBSpeed.observe(viewLifecycleOwner) { speed ->
            if (speed != null) {
                binding.textModeBSpeed.text = "Minimum speed for all CPs: %.2f km/h".format(speed)
                binding.textModeBSpeed.visibility = View.VISIBLE
            } else {
                binding.textModeBSpeed.visibility = View.GONE
            }
        }

        viewModel.routeCard.observe(viewLifecycleOwner) { card ->
            if (card != null) {
                adapter.submitList(card)
            }
        }

        binding.buttonBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.buttonMaps.setOnClickListener {
            val url = viewModel.getGoogleMapsUrl()
            if (url != null) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), "No coordinates available", Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonExport.setOnClickListener {
            exportFilePicker.launch("DoveTrek_Route_Card.html")
        }

        binding.buttonShare.setOnClickListener {
            val text = viewModel.getRouteCardText()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, "DoveTrek Route Card")
            }
            startActivity(Intent.createChooser(intent, "Share Route Card"))
        }

        // Feature 2: GPX Export
        binding.buttonExportGpx.setOnClickListener {
            if (viewModel.getRouteGpx() != null) {
                gpxExportPicker.launch("DoveTrek_Route.gpx")
            } else {
                Toast.makeText(requireContext(), getString(R.string.no_coordinates), Toast.LENGTH_SHORT).show()
            }
        }

        // Feature 4: Start Progress Tracking
        binding.buttonTrack.setOnClickListener {
            viewModel.startTracking()
            (activity as? MainActivity)?.showProgress()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
